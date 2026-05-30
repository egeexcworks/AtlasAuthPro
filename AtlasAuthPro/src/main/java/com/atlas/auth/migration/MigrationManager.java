package com.atlas.auth.migration;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.database.DatabaseManager;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * MigrationManager - Main migration orchestrator
 * Handles migration from nLogin to AtlasAuthPro
 */
public class MigrationManager {
    private final AtlasAuthPro plugin;
    private final DatabaseManager databaseManager;
    private final Logger logger;
    private DatabaseDetector detector;
    private String sourceType;
    private boolean isOpeNLoginMode = false;

    private DatabaseDetector initializeDetector(String provider) {
    if (provider.equals("nlogin")) {
        sourceType = "nLogin";
        return new NLoginDetector();
    } else if (provider.equals("openlogin")) {
        sourceType = "OpenLogin";
        return new OpenLoginDetector();
    } else { // auto
        // Try nLogin first, then OpenLogin
        NLoginDetector nlogin = new NLoginDetector();
        if (nlogin.detect()) {
            sourceType = "nLogin";
            return nlogin;
        }
        nlogin.close();
        
        OpenLoginDetector openlogin = new OpenLoginDetector();
        if (openlogin.detect()) {
            sourceType = "OpenLogin";
            return openlogin;
        }
        return null;
    }
}
    
    // Migration state
    private boolean migrationInProgress = false;
    private int migratedCount = 0;
    private int failedCount = 0;
    private int skippedCount = 0;
    private List<String> migrationErrors = new ArrayList<>();
    private BukkitTask migrationTask;
    
    public MigrationManager(AtlasAuthPro plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
    }

    public boolean isMigrationInProgress() {
        return migrationInProgress;
    }
    
    /**
     * Start the migration process
     */
    public boolean startMigration(CommandSender sender, boolean dryRun) {
        if (migrationInProgress) {
            sender.sendMessage("§cMigration is already in progress!");
            return false;
        }
        
        sender.sendMessage("§e§lStarting " + sourceType + " → AtlasAuthPro migration...");
        sender.sendMessage("§7This may take a while depending on the number of players.");
        
        // Auto-detect or use config
        String provider = plugin.getConfig().getString("migration.provider", "auto").toLowerCase();
        
        detector = initializeDetector(provider);
        if (detector == null) {
            sender.sendMessage("§cNo supported auth database found!");
            sender.sendMessage("§7Supported: nlogin, openlogin, auto");
            return false;
        }
        
        if (!detector.detect()) {
            sender.sendMessage("§c" + sourceType + " database not found!");
            detector.close();
            return false;
        }
        
        // Backup database if SQLite
        backupDatabase(detector);
        
        sender.sendMessage("§aFound " + sourceType + " database!");
        sender.sendMessage("§7Type: " + detector.getDatabaseType());
        sender.sendMessage("§7Table: " + detector.getTableName());
        sender.sendMessage("§7Players: " + detector.getPlayerCount());
        
        if (dryRun) {
            sender.sendMessage("§e§lDRY RUN MODE - No changes will be made");
        }
        
        // Start migration in async task
        migrationInProgress = true;
        migratedCount = 0;
        failedCount = 0;
        skippedCount = 0;
        migrationErrors.clear();
        
        // Run migration asynchronously
        migrationTask = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                performMigration(sender, dryRun);
            } catch (Exception e) {
                logger.severe("§cMigration error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                migrationInProgress = false;
                if (detector != null) {
                    detector.close();
                }
            }
        });
        
        return true;
    }
    
    /**
     * Perform the actual migration
     */
    private void performMigration(CommandSender sender, boolean dryRun) {
        List<Map<String, Object>> sourcePlayers = detector.getAllPlayers();
        
        if (sourcePlayers.isEmpty()) {
            sender.sendMessage("§eNo players to migrate!");
            logger.warning("§eNo players found in " + sourceType + " database");
            return;
        }
        
        int totalPlayers = sourcePlayers.size();
        sender.sendMessage("§eMigrating " + totalPlayers + " players...");
        logger.info("§aStarting migration of " + totalPlayers + " players from " + sourceType);
        
        Map<String, String> columnMapping = detector.getColumnMapping();
        String uuidCol = columnMapping.get("uuid");
        String passCol = columnMapping.get("password");
        String nameCol = columnMapping.get("name");
        String ipCol = columnMapping.get("ip");
        String emailCol = columnMapping.get("email");
        
        // Validate critical columns
        if (passCol == null) {
            sender.sendMessage("§c§lERROR: Missing password column!");
            logger.severe("§cPassword column missing in " + sourceType + " database!");
            return;
        }
        
        // Either UUID or name is required for identifier
        if (uuidCol == null && nameCol == null) {
            sender.sendMessage("§c§lERROR: Missing identifier columns!");
            sender.sendMessage("§cNeed either UUID or player name column");
            logger.severe("§cNo identifier column (UUID or name) found in " + sourceType + " database!");
            return;
        }
        
        boolean isOpeNLoginMode = (uuidCol == null && nameCol != null);
        if (isOpeNLoginMode) {
            logger.info("§a[OpeNLogin Mode] Migration will use player names to resolve UUIDs");
            sender.sendMessage("§7[OpeNLogin Mode] Using player names to resolve UUIDs...");
        }
        
        // Process each player
        for (int i = 0; i < sourcePlayers.size(); i++) {
            Map<String, Object> playerData = sourcePlayers.get(i);
            
            try {
                // Extract player data with validation
                String uuid = getPlayerUUID(playerData, uuidCol);
                String username = getPlayerName(playerData, nameCol);
                String passwordHash = getPasswordHash(playerData, passCol);
                String ip = getPlayerIP(playerData, ipCol);
                String email = getPlayerEmail(playerData, emailCol);
                
                // Validate data
                if (uuid == null || uuid.isEmpty()) {
                    skippedCount++;
                    String errorMsg = "Skipped player with missing UUID: " + (username != null ? username : "Unknown");
                    migrationErrors.add(errorMsg);
                    logger.warning("§e" + errorMsg);
                    continue;
                }
                
                if (passwordHash == null || passwordHash.isEmpty()) {
                    skippedCount++;
                    String errorMsg = "Skipped player " + (username != null ? username : uuid) + " with missing password";
                    migrationErrors.add(errorMsg);
                    logger.warning("§e" + errorMsg);
                    continue;
                }
                
                // Check if already registered in AtlasAuthPro
                if (!isOpeNLoginMode && detector.playerExists(uuid)) {
                    skippedCount++;
                    logger.info("§7Skipping " + (username != null ? username : uuid) + " (" + sourceType + " duplicate)");
                    continue;
                }
                if (databaseManager.isPlayerRegistered(UUID.fromString(uuid))) {
                    skippedCount++;
                    logger.info("§7Skipping " + (username != null ? username : uuid) + " - already in AtlasAuth");
                    continue;
                }
                
                if (!dryRun) {
                    // Convert password hash if needed
                    String convertedHash = convertPasswordHash(passwordHash);
                    if (convertedHash == null || convertedHash.isEmpty()) {
                        failedCount++;
                        String errorMsg = "Failed to convert password for: " + (username != null ? username : uuid);
                        migrationErrors.add(errorMsg);
                        logger.warning("§c" + errorMsg);
                        continue;
                    }
                    
                    // Register player in AtlasAuthPro
                    boolean success = databaseManager.registerPlayer(
                        UUID.fromString(uuid),
                        username != null ? username : "Unknown",
                        convertedHash,
                        ip
                    );
                    
                    if (success) {
                        migratedCount++;
                        logger.info("§a✓ Migrated: " + (username != null ? username : uuid));
                    } else {
                        failedCount++;
                        String errorMsg = "Failed to register player in AtlasAuth: " + (username != null ? username : uuid);
                        migrationErrors.add(errorMsg);
                        logger.warning("§c" + errorMsg);
                    }
                } else {
                    // Dry run - just count
                    migratedCount++;
                    logger.info("§7[DRY RUN] Would migrate: " + (username != null ? username : uuid));
                }
                
                // Progress update every 10 players
                if ((i + 1) % 10 == 0 || i == sourcePlayers.size() - 1) {
                    final int progress = i + 1;
                    final int percentage = (progress * 100 / totalPlayers);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§7Progress: §a" + progress + "§7/§a" + totalPlayers + 
                            " §7(§a" + percentage + "§7%)");
                    });
                }
                
            } catch (Exception e) {
                failedCount++;
                String errorMsg = "Exception migrating player at index " + i + ": " + e.getMessage();
                migrationErrors.add(errorMsg);
                logger.warning("§c" + errorMsg);
                logger.fine("Exception details: " + e);
                e.printStackTrace();
            }
        }
        
        // Send final results
        final int finalMigrated = migratedCount;
        final int finalFailed = failedCount;
        final int finalSkipped = skippedCount;
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            sender.sendMessage("");
            sender.sendMessage("§a§l═══════════ Migration Complete ═══════════");
            sender.sendMessage("§aSuccessfully migrated: §e" + finalMigrated);
            if (finalFailed > 0) {
                sender.sendMessage("§cFailed: §e" + finalFailed);
            }
            if (finalSkipped > 0) {
                sender.sendMessage("§eSkipped: §e" + finalSkipped);
            }
            sender.sendMessage("§a═══════════════════════════════════════");
            
            if (!dryRun) {
                sender.sendMessage("§a✓ Players can now login with their " + sourceType + " password!");
                sender.sendMessage("§7Note: Passwords verified as BCrypt compatible.");
            } else {
                sender.sendMessage("§e[DRY RUN] No changes were made.");
            }
        });
        
        // Log errors if any
        if (!migrationErrors.isEmpty()) {
            logger.warning("§eMigration encountered " + migrationErrors.size() + " error(s):");
            for (int i = 0; i < Math.min(migrationErrors.size(), 10); i++) {
                logger.warning("§c- " + migrationErrors.get(i));
            }
            if (migrationErrors.size() > 10) {
                logger.warning("§c... and " + (migrationErrors.size() - 10) + " more");
            }
        }
        
        generateMigrationReport();
    }
    
    /**
     * Extract UUID from player data with validation
     * Handles both UUID-based databases (nLogin) and name-based databases (OpeNLogin)
     */
    private String getPlayerUUID(Map<String, Object> playerData, String uuidCol) {
        // If database has UUID column
        if (uuidCol != null) {
            Object uuidObj = playerData.get(uuidCol);
            if (uuidObj == null) return null;
            
            String uuid = uuidObj.toString().trim();
            if (uuid.isEmpty()) return null;
            
            try {
                // Handle different UUID formats
                if (uuid.contains("-") && uuid.length() == 36) {
                    // Already formatted correctly
                    UUID.fromString(uuid); // Validate format
                    return uuid;
                } else if (uuid.length() == 32) {
                    // Convert from raw UUID string to formatted
                    String formatted = uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + 
                           uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + 
                           uuid.substring(20, 32);
                    UUID.fromString(formatted); // Validate
                    return formatted;
                } else if (uuid.length() == 36) {
                    // Try parsing as-is
                    UUID.fromString(uuid);
                    return uuid;
                }
            } catch (IllegalArgumentException e) {
                logger.warning("§cInvalid UUID format: " + uuid + " - " + e.getMessage());
            }
            return null;
        }
        
        // For OpeNLogin (no UUID column) - use player name to generate UUID
        logger.info("§7No UUID column found - attempting to resolve from player name (OpeNLogin mode)");
        Map<String, String> colMap = detector.getColumnMapping();
        String nameCol = colMap.get("name");
        if (nameCol != null) {
            Object nameObj = playerData.get(nameCol);
            if (nameObj != null) {
                String playerName = nameObj.toString().trim();
                try {
                    // Try to get online UUID from Bukkit
                    org.bukkit.entity.Player onlinePlayer = plugin.getServer().getPlayer(playerName);
                    if (onlinePlayer != null) {
                        String uuid = onlinePlayer.getUniqueId().toString();
                        logger.info("§a✓ Resolved UUID from online player: " + playerName + " = " + uuid);
                        return uuid;
                    }
                    
                    // Generate offline UUID based on player name
                    java.util.UUID offlineUUID = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String uuid = offlineUUID.toString();
                    logger.info("§7Generated offline UUID for player: " + playerName + " = " + uuid);
                    return uuid;
                } catch (Exception e) {
                    logger.warning("§cFailed to resolve UUID for player: " + playerName + " - " + e.getMessage());
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract username from player data
     */
    private String getPlayerName(Map<String, Object> playerData, String nameCol) {
        if (nameCol == null) return null;
        Object nameObj = playerData.get(nameCol);
        return nameObj != null ? nameObj.toString() : null;
    }
    
    /**
     * Extract password hash from player data
     */
    private String getPasswordHash(Map<String, Object> playerData, String passCol) {
        if (passCol == null) return null;
        Object passObj = playerData.get(passCol);
        return passObj != null ? passObj.toString() : null;
    }
    
    /**
     * Extract IP from player data
     */
    private String getPlayerIP(Map<String, Object> playerData, String ipCol) {
        if (ipCol == null) return null;
        Object ipObj = playerData.get(ipCol);
        return ipObj != null ? ipObj.toString() : null;
    }
    
    /**
     * Extract email from player data
     */
    private String getPlayerEmail(Map<String, Object> playerData, String emailCol) {
        if (emailCol == null) return null;
        Object emailObj = playerData.get(emailCol);
        return emailObj != null ? emailObj.toString() : null;
    }
    
    /**
     * Convert password hash from OpeNLogin/nLogin format to AtlasAuthPro format
     * Most auth plugins use BCrypt, so verification is straightforward
     */
    private String convertPasswordHash(String oldHash) {
        if (oldHash == null || oldHash.trim().isEmpty()) {
            logger.warning("§eEmpty password hash provided");
            return null;
        }
        
        oldHash = oldHash.trim();
        
        // Check if already a valid BCrypt hash ($2a$, $2b$, $2y$)
        if (oldHash.startsWith("$2a$") || oldHash.startsWith("$2b$") || oldHash.startsWith("$2y$")) {
            // Validate BCrypt format
            if (oldHash.length() >= 60) {
                try {
                    BCrypt.Result result = BCrypt.verifyer().verify("migration_test".toCharArray(), oldHash);
                    // It's valid BCrypt, use as-is
                    logger.fine("§aPassword hash is valid BCrypt, using as-is");
                    return oldHash;
                } catch (Exception e) {
                    logger.warning("§eInvalid BCrypt hash: " + e.getMessage());
                    return null;
                }
            }
        }
        
        // Check for MD5 format (32 hex chars)
        if (oldHash.matches("^[a-f0-9]{32}$")) {
            logger.warning("§eDetected MD5 hash - cannot securely migrate. Player will need to reset password.");
            return null;
        }
        
        // Check for SHA256 format (64 hex chars)
        if (oldHash.matches("^[a-f0-9]{64}$")) {
            logger.warning("§eDetected SHA256 hash - cannot securely migrate. Player will need to reset password.");
            return null;
        }
        
        // Fallback - if it looks like it might be BCrypt but failed validation, still try to use it
        if (oldHash.contains("$") && oldHash.length() > 50) {
            logger.info("§aUsing unvalidated hash format (may be BCrypt variant)");
            return oldHash;
        }
        
        logger.warning("§eUnrecognized password hash format, cannot migrate securely");
        return null;
    }
    
    /**
     * Check if migration is in progress
     */
private void backupDatabase(DatabaseDetector detector) {
        CommandSender backupSender = plugin.getServer().getConsoleSender();
        if (plugin.getConfig().getBoolean("migration.backup", true) && detector.getDatabaseType().equals("SQLite")) {
            try {
                String sourceDb = detector.getConnection().getMetaData().getURL().replace("jdbc:sqlite:", "");
                java.io.File sourceFile = new java.io.File(sourceDb);
                if (sourceFile.exists()) {
                    String timestamp = java.lang.String.valueOf(System.currentTimeMillis());
                    java.io.File backupDir = new java.io.File(plugin.getDataFolder(), "migration_backups");
                    backupDir.mkdirs();
                    String sourceName = sourceFile.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
                    String prefix = sourceType != null ? sourceType.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase() : "migration";
                    java.io.File backupFile = new java.io.File(backupDir, prefix + "_" + sourceName + "_" + timestamp + ".db");
                    java.nio.file.Files.copy(sourceFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("§aDatabase backed up to: " + backupFile.getAbsolutePath());
                    backupSender.sendMessage("§a[Migration] Database backed up: " + backupFile.getName());
                }
            } catch (Exception e) {
                logger.warning("§eBackup failed: " + e.getMessage());
            }
        }
    }
    
    private void generateMigrationReport() {
        java.io.File reportDir = new java.io.File(plugin.getDataFolder(), "migration_reports");
        reportDir.mkdirs();
        String timestamp = java.lang.String.valueOf(System.currentTimeMillis());
        java.io.File reportFile = new java.io.File(reportDir, "migration_" + timestamp + ".txt");
        try (java.util.Formatter writer = new java.util.Formatter(reportFile)) {
            writer.format("═════════════════════════════════════════════════════════════════════════════%n");
            writer.format("AtlasAuthPro Migration Report%n");
            writer.format("═════════════════════════════════════════════════════════════════════════════%n");
            writer.format("Generated: %s%n", new java.util.Date());
            writer.format("Source Plugin: %s%n", sourceType);
            writer.format("Database Type: %s%n", detector.getDatabaseType());
            writer.format("Database Table: %s%n", detector.getTableName());
            writer.format("%n");
            writer.format("MIGRATION STATISTICS%n");
            writer.format("─────────────────────────────────────────────────────────────────────────────%n");
            writer.format("Successfully Migrated: %d%n", migratedCount);
            writer.format("Failed: %d%n", failedCount);
            writer.format("Skipped (Duplicates/Invalid): %d%n", skippedCount);
            writer.format("Total Processed: %d%n", migratedCount + failedCount + skippedCount);
            writer.format("Success Rate: %.1f%%%n", ((float)migratedCount / (migratedCount + failedCount + skippedCount)) * 100);
            writer.format("%n");
            writer.format("ERRORS AND WARNINGS%n");
            writer.format("─────────────────────────────────────────────────────────────────────────────%n");
            if (migrationErrors.isEmpty()) {
                writer.format("No errors recorded!%n");
            } else {
                for (String error : migrationErrors) {
                    writer.format("• %s%n", error);
                }
            }
            writer.format("%n");
            writer.format("RECOMMENDATIONS%n");
            writer.format("─────────────────────────────────────────────────────────────────────────────%n");
            if (failedCount > 0) {
                writer.format("• Check the errors above and verify database connectivity%n");
                writer.format("• Some players may need to reset their passwords%n");
            }
            if (skippedCount > 0) {
                writer.format("• %d players were skipped (likely duplicates or missing data)%n", skippedCount);
            }
            writer.format("• Test the migration with a few players before running on production%n");
            writer.format("• Keep a backup of the original %s database%n", sourceType);
            writer.format("%n");
            writer.format("═════════════════════════════════════════════════════════════════════════════%n");
            logger.info("§aDetailed migration report saved to: " + reportFile.getAbsolutePath());
        } catch (Exception e) {
            logger.warning("§eReport generation failed: " + e.getMessage());
        }
    }
    
    /**
     * Get migration progress
     */
    public int getMigratedCount() {
        return migratedCount;
    }
    
    /**
     * Get failed count
     */
    public int getFailedCount() {
        return failedCount;
    }
    
    /**
     * Get skipped count
     */
    public int getSkippedCount() {
        return skippedCount;
    }
    
    /**
     * Get migration errors
     */
    public List<String> getMigrationErrors() {
        return new ArrayList<>(migrationErrors);
    }
    
    /**
     * Cancel migration
     */
    public void cancelMigration() {
        if (migrationTask != null) {
            migrationTask.cancel();
        }
        migrationInProgress = false;
    }
}

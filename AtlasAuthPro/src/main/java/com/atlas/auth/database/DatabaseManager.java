package com.atlas.auth.database;

import com.atlas.auth.AtlasAuthPro;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class DatabaseManager {
    private Connection connection;
    private final Logger logger;
    
    public DatabaseManager() {
        this.logger = AtlasAuthPro.getInstance().getLogger();
    }
    
    public boolean initialize() {
        try {
            FileConfiguration config = AtlasAuthPro.getInstance().getConfig();
            String type = config.getString("database.type", "SQLite");
            
            if (type.equalsIgnoreCase("SQLite")) {
                // Load SQLite native library from JAR
                loadNativeLibrary();
                
                Class.forName("org.sqlite.JDBC");
                AtlasAuthPro.getInstance().getDataFolder().mkdirs();
                String dbPath = AtlasAuthPro.getInstance().getDataFolder() + "/" + config.getString("database.file", "auth.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            } else {
                // MySQL setup
                String host = config.getString("database.host", "localhost");
                int port = config.getInt("database.port", 3306);
                String database = config.getString("database.database", "atlasauth");
                String username = config.getString("database.username", "root");
                String password = config.getString("database.password", "");
                
                Class.forName("com.mysql.cj.jdbc.Driver");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
                connection = DriverManager.getConnection(url, username, password);
            }
            
            createTables();
            logger.info("§aDatabase connected successfully!");
            return true;
        } catch (Exception e) {
            logger.severe("§cDatabase connection failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Extract and load SQLite native library from JAR
     */
    private void loadNativeLibrary() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();
            String libName;
            
            if (os.contains("win")) {
                if (arch.contains("64")) {
                    libName = "sqlitejdbc.dll";
                } else {
                    libName = "sqlitejdbc.dll";
                }
            } else if (os.contains("mac") || os.contains("darwin")) {
                libName = "libsqlitejdbc.dylib";
            } else {
                libName = "libsqlitejdbc.so";
            }
            
            // Path within JAR
            String jarPath = "/org/sqlite/native/";
            if (os.contains("win")) {
                jarPath += "Windows/";
                if (arch.contains("64")) {
                    jarPath += "x86_64/";
                } else {
                    jarPath += "x86/";
                }
            } else if (os.contains("mac") || os.contains("darwin")) {
                jarPath += "Mac/";
                if (arch.contains("aarch64") || arch.contains("arm64")) {
                    jarPath += "aarch64/";
                } else {
                    jarPath += "x86_64/";
                }
            } else {
                jarPath += "Linux/";
                if (arch.contains("64") && !arch.contains("32")) {
                    jarPath += "x86_64/";
                } else if (arch.contains("aarch64")) {
                    jarPath += "aarch64/";
                } else if (arch.contains("arm")) {
                    jarPath += "arm/";
                } else {
                    jarPath += "x86/";
                }
            }
            jarPath += libName;
            
            // Try to extract and load
            try (InputStream is = getClass().getResourceAsStream(jarPath)) {
                if (is == null) {
                    logger.warning("Native library not found in JAR: " + jarPath + ", using default loading");
                    return;
                }
                
                // Create temp file
                File tempFile = File.createTempFile("sqlitejdbc", libName.contains(".dll") ? ".dll" : ".so");
                tempFile.deleteOnExit();
                
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                
                // Load the library
                System.load(tempFile.getAbsolutePath());
                logger.info("§aLoaded SQLite native library from: " + tempFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.warning("Failed to load native library: " + e.getMessage() + ", will try default loading");
            // Fall back to default loading - might work if server has SQLite available
        }
    }
    
    private void createTables() throws SQLException {
        String usersTable = "CREATE TABLE IF NOT EXISTS auth_users (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "uuid VARCHAR(36) UNIQUE NOT NULL," +
            "username VARCHAR(16) NOT NULL," +
            "password_hash TEXT NOT NULL," +
            "email VARCHAR(100)," +
            "ip_address VARCHAR(45)," +
            "registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "last_login TIMESTAMP," +
            "failed_attempts INTEGER DEFAULT 0," +
            "is_locked INTEGER DEFAULT 0," +
            "reset_token VARCHAR(64)," +
            "reset_expires TIMESTAMP," +
            "two_factor_secret VARCHAR(32)," +
            "two_factor_enabled INTEGER DEFAULT 0," +
            "discord_id VARCHAR(20)," +
            "pin_hash TEXT," +
            "trusted_devices TEXT" +
            ")";
        
        String sessionsTable = "CREATE TABLE IF NOT EXISTS auth_sessions (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "session_token VARCHAR(64) UNIQUE NOT NULL," +
            "ip_address VARCHAR(45)," +
            "device_info VARCHAR(256)," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "expires_at TIMESTAMP NOT NULL," +
            "FOREIGN KEY (user_uuid) REFERENCES auth_users(uuid) ON DELETE CASCADE" +
            ")";
        
        String ipLogTable = "CREATE TABLE IF NOT EXISTS auth_ip_log (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "ip_address VARCHAR(45) NOT NULL," +
            "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "success INTEGER DEFAULT 1," +
            "FOREIGN KEY (user_uuid) REFERENCES auth_users(uuid) ON DELETE CASCADE" +
            ")";
        
        // 2FA Token table for Discord 2FA
        String twoFactorTokensTable = "CREATE TABLE IF NOT EXISTS auth_2fa_tokens (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "token_hash TEXT NOT NULL," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "expires_at TIMESTAMP NOT NULL," +
            "used INTEGER DEFAULT 0," +
            "FOREIGN KEY (user_uuid) REFERENCES auth_users(uuid) ON DELETE CASCADE" +
            ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(usersTable);
            stmt.execute(sessionsTable);
            stmt.execute(ipLogTable);
            stmt.execute(twoFactorTokensTable);
            
            // Add discord_id column if it doesn't exist (for existing databases)
            try {
                stmt.execute("ALTER TABLE auth_users ADD COLUMN discord_id VARCHAR(20)");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
            
            // Add pin_hash column if it doesn't exist (for existing databases)
            try {
                stmt.execute("ALTER TABLE auth_users ADD COLUMN pin_hash TEXT");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
        }
    }
    
    public boolean isPlayerRegistered(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM auth_users WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.warning("Error checking registration: " + e.getMessage());
            return false;
        }
    }
    
    public boolean registerPlayer(UUID uuid, String username, String passwordHash, String ip) {
        String sql = "INSERT INTO auth_users (uuid, username, password_hash, ip_address) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setString(3, passwordHash);
            stmt.setString(4, ip);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error registering player: " + e.getMessage());
            return false;
        }
    }
    
    public Map<String, Object> getPlayerData(UUID uuid) {
        String sql = "SELECT * FROM auth_users WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    data.put(meta.getColumnName(i), rs.getObject(i));
                }
                return data;
            }
        } catch (SQLException e) {
            logger.warning("Error getting player data: " + e.getMessage());
        }
        return null;
    }

    public Map<String, Object> getPlayerDataByUsername(String username) {
        String sql = "SELECT * FROM auth_users WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    data.put(meta.getColumnName(i), rs.getObject(i));
                }
                return data;
            }
        } catch (SQLException e) {
            logger.warning("Error getting player data by username: " + e.getMessage());
        }
        return null;
    }

    public UUID getUuidByUsername(String username) {
        String sql = "SELECT uuid FROM auth_users WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String uuidValue = rs.getString("uuid");
                if (uuidValue != null && !uuidValue.isBlank()) {
                    return UUID.fromString(uuidValue);
                }
            }
        } catch (SQLException e) {
            logger.warning("Error getting UUID by username: " + e.getMessage());
        }
        return null;
    }

    public boolean isPlayerRegisteredByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM auth_users WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.warning("Error checking registration by username: " + e.getMessage());
            return false;
        }
    }

    public boolean updatePlayerUuid(UUID oldUuid, UUID newUuid) {
        String sql = "UPDATE auth_users SET uuid = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newUuid.toString());
            stmt.setString(2, oldUuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error updating player UUID: " + e.getMessage());
            return false;
        }
    }
    
    public boolean updatePlayerUuidByUsername(String username, UUID newUuid) {
        String sql = "UPDATE auth_users SET uuid = ? WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newUuid.toString());
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error updating player UUID by username: " + e.getMessage());
            return false;
        }
    }
    
    public boolean setPIN(UUID uuid, String pinHash) {
        String sql = "UPDATE auth_users SET pin_hash = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, pinHash);
            stmt.setString(2, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error setting PIN: " + e.getMessage());
            return false;
        }
    }
    
    public String getPINHash(UUID uuid) {
        String sql = "SELECT pin_hash FROM auth_users WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("pin_hash");
            }
        } catch (SQLException e) {
            logger.warning("Error getting PIN hash: " + e.getMessage());
        }
        return null;
    }
    
    public boolean hasPIN(UUID uuid) {
        String pinHash = getPINHash(uuid);
        return pinHash != null && !pinHash.isEmpty();
    }
    
    public List<Map<String, Object>> getAllPlayers(int limit, int offset) {
        List<Map<String, Object>> players = new ArrayList<>();
        String sql = "SELECT uuid, username, registered_at, last_login, is_locked FROM auth_users LIMIT ? OFFSET ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> player = new HashMap<>();
                player.put("uuid", rs.getString("uuid"));
                player.put("username", rs.getString("username"));
                player.put("registered_at", rs.getTimestamp("registered_at"));
                player.put("last_login", rs.getTimestamp("last_login"));
                player.put("is_locked", rs.getBoolean("is_locked"));
                players.add(player);
            }
        } catch (SQLException e) {
            logger.warning("Error getting all players: " + e.getMessage());
        }
        return players;
    }
    
    public int getTotalPlayers() {
        String sql = "SELECT COUNT(*) FROM auth_users";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            logger.warning("Error counting players: " + e.getMessage());
            return 0;
        }
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("Error closing database: " + e.getMessage());
        }
    }
    
    // ============================================
    // Discord 2FA Methods
    // ============================================
    
    /**
     * Get Discord ID linked to a player's account
     * @param uuid Player's UUID
     * @return Discord ID or null if not linked
     */
    public String getDiscordId(UUID uuid) {
        String sql = "SELECT discord_id FROM auth_users WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("discord_id");
            }
        } catch (SQLException e) {
            logger.warning("Error getting Discord ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Link Discord ID to a player's account
     * @param uuid Player's UUID
     * @param discordId Discord ID to link
     * @return true if successful
     */
    public boolean setDiscordId(UUID uuid, String discordId) {
        String sql = "UPDATE auth_users SET discord_id = ? WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            stmt.setString(2, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error setting Discord ID: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Enable Discord 2FA for a player
     * @param uuid Player's UUID
     * @return true if successful
     */
    public boolean enableDiscord2FA(UUID uuid) {
        String sql = "UPDATE auth_users SET two_factor_enabled = 1 WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error enabling Discord 2FA: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disable Discord 2FA for a player
     * @param uuid Player's UUID
     * @return true if successful
     */
    public boolean disableDiscord2FA(UUID uuid) {
        String sql = "UPDATE auth_users SET two_factor_enabled = 0, discord_id = NULL WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error disabling Discord 2FA: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Store a 2FA token (hashed) in the database
     * @param uuid Player's UUID
     * @param tokenHash Hashed token
     * @param expiresAt Timestamp when token expires
     * @return true if successful
     */
    public boolean store2FAToken(UUID uuid, String tokenHash, Timestamp expiresAt) {
        // First, invalidate any existing unused tokens for this player
        String deleteSql = "UPDATE auth_2fa_tokens SET used = 1 WHERE user_uuid = ? AND used = 0";
        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
            deleteStmt.setString(1, uuid.toString());
            deleteStmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error clearing old 2FA tokens: " + e.getMessage());
        }
        
        String sql = "INSERT INTO auth_2fa_tokens (user_uuid, token_hash, expires_at) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tokenHash);
            stmt.setTimestamp(3, expiresAt);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Error storing 2FA token: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Verify a 2FA token (check if valid and not expired)
     * @param uuid Player's UUID
     * @param tokenHash Hashed token to verify
     * @return true if token is valid
     */
    public boolean verify2FAToken(UUID uuid, String tokenHash) {
        String sql = "SELECT * FROM auth_2fa_tokens WHERE user_uuid = ? AND token_hash = ? AND used = 0 AND expires_at > CURRENT_TIMESTAMP";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tokenHash);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                // Mark token as used
                String updateSql = "UPDATE auth_2fa_tokens SET used = 1 WHERE id = ?";
                try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, rs.getInt("id"));
                    updateStmt.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) {
            logger.warning("Error verifying 2FA token: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Get all active 2FA tokens (for admin debugging)
     * @return List of active token data
     */
    public List<Map<String, Object>> getActive2FATokens() {
        List<Map<String, Object>> tokens = new ArrayList<>();
        String sql = "SELECT t.id, t.user_uuid, u.username, t.created_at, t.expires_at " +
                     "FROM auth_2fa_tokens t " +
                     "JOIN auth_users u ON t.user_uuid = u.uuid " +
                     "WHERE t.used = 0 AND t.expires_at > CURRENT_TIMESTAMP";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> token = new HashMap<>();
                token.put("id", rs.getInt("id"));
                token.put("user_uuid", rs.getString("user_uuid"));
                token.put("username", rs.getString("username"));
                token.put("created_at", rs.getTimestamp("created_at"));
                token.put("expires_at", rs.getTimestamp("expires_at"));
                tokens.add(token);
            }
        } catch (SQLException e) {
            logger.warning("Error getting active 2FA tokens: " + e.getMessage());
        }
        return tokens;
    }
    
    /**
     * Get player name by UUID
     * @param uuid Player's UUID
     * @return Player name or null
     */
    public String getPlayerName(UUID uuid) {
        String sql = "SELECT username FROM auth_users WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            logger.warning("Error getting player name: " + e.getMessage());
        }
        return null;
    }
}

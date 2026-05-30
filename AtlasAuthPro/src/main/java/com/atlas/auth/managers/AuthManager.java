package com.atlas.auth.managers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.bot.DiscordBot;
import com.atlas.auth.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    private final DatabaseManager databaseManager;    private final java.util.logging.Logger logger;    private PlayerManager playerManager;
    private final Map<UUID, Integer> loginAttempts = new HashMap<>();
    private final Map<UUID, String> resetTokens = new HashMap<>();
    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private final Map<UUID, String> pendingTwoFactorCodes = new HashMap<>();
    
    // Discord 2FA - Players waiting for 2FA verification after password login
    private final Map<UUID, Long> pending2FAVerification = new ConcurrentHashMap<>();
    
    public AuthManager(DatabaseManager databaseManager, PlayerManager playerManager) {
        this.databaseManager = databaseManager;
        this.playerManager = playerManager;
        this.logger = AtlasAuthPro.getInstance().getLogger();
    }
    
    public boolean register(Player player, String password, String confirmPassword) {
        UUID uuid = player.getUniqueId();
        
        if (databaseManager.isPlayerRegistered(uuid)) {
            sendMessage(player, "&cYou are already registered!");
            return false;
        }
        
        if (!password.equals(confirmPassword)) {
            sendMessage(player, "&cPasswords do not match!");
            return false;
        }
        
        if (password.length() < 6) {
            sendMessage(player, "&cPassword must be at least 6 characters!");
            return false;
        }
        
        // Hash password with BCrypt
        String hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        
        boolean success = databaseManager.registerPlayer(uuid, player.getName(), hashedPassword, ip);
        
        if (success) {
            // Show title
            player.sendTitle(
                colorize("&a&lREGISTERED!"),
                colorize("&7Welcome to the server"),
                10, 40, 10
            );
            
            // Play sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // Login automatically
            loginPlayer(player);
            sendMessage(player, "&aRegistration successful! You are now logged in.");
            return true;
        } else {
            sendMessage(player, "&cRegistration failed! Contact an admin.");
            return false;
        }
    }
    
    public boolean login(Player player, String password) {
        UUID uuid = player.getUniqueId();
        
        Map<String, Object> migratedData = null;
        if (!databaseManager.isPlayerRegistered(uuid)) {
            migratedData = databaseManager.getPlayerDataByUsername(player.getName());
            if (migratedData != null) {
                boolean updated = databaseManager.updatePlayerUuidByUsername(player.getName(), uuid);
                if (updated) {
                    logger.info("Updated migrated account UUID for player: " + player.getName());
                } else {
                    logger.warning("Failed to update migrated UUID for player: " + player.getName() + " - continuing with username-based login");
                }

                String storedHash = (String) migratedData.get("password_hash");
                if (storedHash == null || storedHash.isEmpty()) {
                    sendMessage(player, "&cAccount error! Contact admin.");
                    return false;
                }

                BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);
                if (result.verified) {
                    loginAttempts.put(uuid, 0);
                    loginPlayer(player);

                    player.sendTitle(
                        colorize("&a&lWELCOME BACK!"),
                        colorize("&7Enjoy your stay"),
                        10, 40, 10
                    );
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    sendMessage(player, "&a✔ Login successful!");
                    return true;
                }

                int attempts = loginAttempts.getOrDefault(uuid, 0) + 1;
                loginAttempts.put(uuid, attempts);
                int remaining = 3 - attempts;
                player.sendTitle(
                    colorize("&c&lWRONG PASSWORD"),
                    colorize("&7Attempts: &c" + attempts + "&7/3"),
                    10, 40, 10
                );
                sendMessage(player, "&c✖ Incorrect password (" + remaining + " attempts left)");
                if (attempts >= 3) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
                }
                return false;
            }
        }

        if (!databaseManager.isPlayerRegistered(uuid)) {
            sendMessage(player, "&cYou are not registered! Use &e/register");
            return false;
        }
        
        // Check login attempts
        int attempts = loginAttempts.getOrDefault(uuid, 0);
        if (attempts >= 3) {
            sendMessage(player, "&c🔒 Account locked! Too many failed attempts.");
            player.sendTitle("&c&lLOCKED!", "&7Too many failed attempts", 10, 40, 10);
            return false;
        }
        
        // Get player data
        Map<String, Object> playerData = databaseManager.getPlayerData(uuid);
        if (playerData == null) {
            sendMessage(player, "&cAccount error! Contact admin.");
            return false;
        }
        
        String storedHash = (String) playerData.get("password_hash");
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);
        
        if (result.verified) {
            // Successful login
            loginAttempts.put(uuid, 0);
            loginPlayer(player);
            
            // Show title
            player.sendTitle(
                colorize("&a&lWELCOME BACK!"),
                colorize("&7Enjoy your stay"),
                10, 40, 10
            );
            
            // Play sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            
            sendMessage(player, "&a✔ Login successful!");
            return true;
        } else {
            // Failed login
            attempts++;
            loginAttempts.put(uuid, attempts);
            int remaining = 3 - attempts;
            
            player.sendTitle(
                colorize("&c&lWRONG PASSWORD"),
                colorize("&7Attempts: &c" + attempts + "&7/3"),
                10, 40, 10
            );
            
            sendMessage(player, "&c✖ Incorrect password (" + remaining + " attempts left)");
            
            // Vibrate screen on 3rd attempt
            if (attempts >= 3) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
            }
            return false;
        }
    }
    
    private void loginPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        loggedInPlayers.add(uuid);
        if (playerManager != null) {
            playerManager.onPlayerLogin(player);
        }
    }
    
    public void logout(Player player) {
        UUID uuid = player.getUniqueId();
        loggedInPlayers.remove(uuid);
        if (playerManager != null) {
            playerManager.onPlayerLogout(player);
        }
        sendMessage(player, "&7You have been logged out.");
    }
    
    public boolean isLoggedIn(UUID uuid) {
        return loggedInPlayers.contains(uuid);
    }
    
    public String generateResetToken(Player player) {
        UUID uuid = player.getUniqueId();
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        resetTokens.put(uuid, token);
        
        // Store in database with expiry (1 hour)
        Timestamp expires = Timestamp.valueOf(LocalDateTime.now().plusHours(1));
        
        return token;
    }
    
    public boolean resetPassword(Player player, String token, String newPassword) {
        UUID uuid = player.getUniqueId();
        
        if (!resetTokens.containsKey(uuid) || !resetTokens.get(uuid).equals(token)) {
            sendMessage(player, "&cInvalid or expired token!");
            return false;
        }
        
        if (newPassword.length() < 6) {
            sendMessage(player, "&cPassword must be at least 6 characters!");
            return false;
        }
        
        // Hash new password
        String hashedPassword = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
        
        // Update in database (implement this)
        resetTokens.remove(uuid);
        
        sendMessage(player, "&a🔑 Password reset successfully! You can now login.");
        return true;
    }
    
    public boolean changePassword(Player player, String oldPassword, String newPassword) {
        UUID uuid = player.getUniqueId();
        
        if (!isLoggedIn(uuid)) {
            sendMessage(player, "&cYou must be logged in to change password!");
            return false;
        }
        
        Map<String, Object> playerData = databaseManager.getPlayerData(uuid);
        if (playerData == null) {
            sendMessage(player, "&cAccount error!");
            return false;
        }
        
        String storedHash = (String) playerData.get("password_hash");
        BCrypt.Result result = BCrypt.verifyer().verify(oldPassword.toCharArray(), storedHash);
        
        if (!result.verified) {
            sendMessage(player, "&cOld password is incorrect!");
            return false;
        }
        
        if (newPassword.length() < 6) {
            sendMessage(player, "&cNew password too short!");
            return false;
        }
        
        // Hash and update password (implement database update)
        sendMessage(player, "&a✔ Password changed successfully!");
        return true;
    }
    
    public Map<UUID, String> getOnlinePlayersData() {
        Map<UUID, String> players = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isLoggedIn(p.getUniqueId())) {
                players.put(p.getUniqueId(), p.getName());
            }
        }
        return players;
    }
    
    public void adminForceLogout(UUID targetUuid) {
        loggedInPlayers.remove(targetUuid);
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && playerManager != null) {
            playerManager.onPlayerLogout(target);
            sendMessage(target, "&c⚡ You have been force logged out by an admin!");
        }
    }
    
    public void adminResetPassword(UUID targetUuid) {
        // Generate temporary password
        String tempPassword = "reset_" + System.currentTimeMillis();
        
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            sendMessage(target, "&c⚡ Your password has been reset by an admin!");
            sendMessage(target, "&7Use /resetpassword to set a new one");
        }
    }
    
    private void sendMessage(Player player, String message) {
        boolean usePrefix = AtlasAuthPro.getInstance().getConfig().getBoolean("ui.use-prefix", true);
        
        if (usePrefix) {
            String prefix = AtlasAuthPro.getInstance().getConfig().getString("messages.prefix", "&8[&3AtlasAuth&8] &7");
            player.sendMessage(colorize(prefix + message));
        } else {
            player.sendMessage(colorize(message));
        }
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    // 2FA Methods
    
    /**
     * Check if Discord 2FA is enabled for a player
     * Fixed: handles INTEGER type from database correctly
     */
    public boolean isTwoFactorEnabled(UUID uuid) {
        Map<String, Object> data = databaseManager.getPlayerData(uuid);
        if (data != null && data.get("two_factor_enabled") != null) {
            // Database stores as INTEGER (0 or 1), convert properly
            Object value = data.get("two_factor_enabled");
            if (value instanceof Integer) {
                return ((Integer) value) == 1;
            } else if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        return false;
    }
    
    public void enableTwoFactor(Player player, String email) {
        UUID uuid = player.getUniqueId();
        
        // Generate 2FA code
        String code = generateTwoFactorCode();
        pendingTwoFactorCodes.put(uuid, code);
        
        // In a real implementation, send email here
        sendMessage(player, "&a2FA setup initiated!");
        sendMessage(player, "&eVerification code: &a" + code);
        sendMessage(player, "&7Use &a/2fa verify <code> &7to complete setup");
        
        // Log the request
        AtlasAuthPro.getInstance().getPluginLogger().info("2FA setup requested for " + player.getName() + " with email: " + email);
    }
    
    public void disableTwoFactor(Player player, String password) {
        UUID uuid = player.getUniqueId();
        
        // Verify password first
        Map<String, Object> playerData = databaseManager.getPlayerData(uuid);
        if (playerData == null) {
            sendMessage(player, "&cAccount error!");
            return;
        }
        
        String storedHash = (String) playerData.get("password_hash");
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);
        
        if (!result.verified) {
            sendMessage(player, "&cIncorrect password!");
            return;
        }
        
        // Disable 2FA in database (implement)
        sendMessage(player, "&a2FA has been disabled for your account.");
    }
    
    public void verifyTwoFactor(Player player, String code) {
        UUID uuid = player.getUniqueId();
        
        String expectedCode = pendingTwoFactorCodes.get(uuid);
        if (expectedCode == null || !expectedCode.equals(code)) {
            sendMessage(player, "&cInvalid verification code!");
            return;
        }
        
        // Enable 2FA in database (implement)
        pendingTwoFactorCodes.remove(uuid);
        sendMessage(player, "&a2FA enabled successfully! Your account is now protected.");
    }
    
    public void setupTwoFactor(Player player) {
        sendMessage(player, "&eTo enable 2FA, use: &a/2fa enable <email>");
    }
    
    private String generateTwoFactorCode() {
        Random rand = new Random();
        return String.format("%06d", rand.nextInt(1000000));
    }
    
    // Getter for PlayerManager
    public PlayerManager getPlayerManager() {
        return playerManager;
    }
    
    // ============================================
    // Discord 2FA Methods
    // ============================================
    
    /**
     * Check if Discord 2FA is enabled in config
     * @return true if enabled
     */
    public boolean isDiscord2FAEnabled() {
        return AtlasAuthPro.getInstance().getConfig().getBoolean("discord-2fa.enabled", false);
    }
    
    /**
     * Get Discord ID linked to player's account
     * @param uuid Player's UUID
     * @return Discord ID or null
     */
    public String getDiscordId(UUID uuid) {
        return databaseManager.getDiscordId(uuid);
    }
    
    /**
     * Link Discord account to Minecraft player and enable 2FA
     * @param player Player
     * @param discordId Discord ID to link
     * @return true if successful
     */
    public boolean linkDiscordAccount(Player player, String discordId) {
        UUID uuid = player.getUniqueId();
        
        // Validate Discord ID format
        DiscordBot bot = DiscordBot.getInstance();
        if (bot != null && !bot.isValidDiscordId(discordId)) {
            sendMessage(player, "&c✖ Invalid Discord ID format! Must be 17-19 digits.");
            return false;
        }
        
        // Link Discord ID to player
        if (databaseManager.setDiscordId(uuid, discordId)) {
            // Enable 2FA
            databaseManager.enableDiscord2FA(uuid);
            
            sendMessage(player, "&a✔ Discord account linked successfully!");
            sendMessage(player, "&a🔑 Discord 2FA is now enabled for your account!");
            sendMessage(player, "&7You will receive a verification code on Discord when logging in.");
            
            AtlasAuthPro.getInstance().getPluginLogger().info("Player " + player.getName() + " linked Discord: " + discordId);
            return true;
        } else {
            sendMessage(player, "&c✖ Failed to link Discord account!");
            return false;
        }
    }
    
    /**
     * Unlink Discord account and disable 2FA
     * @param player Player
     * @param password Player's password for verification
     * @return true if successful
     */
    public boolean unlinkDiscordAccount(Player player, String password) {
        UUID uuid = player.getUniqueId();
        
        // Verify password first
        Map<String, Object> playerData = databaseManager.getPlayerData(uuid);
        if (playerData == null) {
            sendMessage(player, "&c✖ Account error!");
            return false;
        }
        
        String storedHash = (String) playerData.get("password_hash");
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);
        
        if (!result.verified) {
            sendMessage(player, "&c✖ Incorrect password!");
            return false;
        }
        
        // Disable 2FA and remove Discord link
        if (databaseManager.disableDiscord2FA(uuid)) {
            sendMessage(player, "&a✔ Discord 2FA disabled!");
            sendMessage(player, "&7Your Discord account has been unlinked.");
            return true;
        } else {
            sendMessage(player, "&c✖ Failed to disable 2FA!");
            return false;
        }
    }
    
    /**
     * Check if player is pending 2FA verification (after password login)
     * @param uuid Player's UUID
     * @return true if pending
     */
    public boolean isPending2FAVerification(UUID uuid) {
        Long timestamp = pending2FAVerification.get(uuid);
        if (timestamp == null) {
            return false;
        }
        
        // Check if expired (2 minutes)
        long expiryMillis = AtlasAuthPro.getInstance().getConfig()
            .getLong("discord-2fa.token.expiry-seconds", 120) * 1000L;
        
        if (System.currentTimeMillis() - timestamp > expiryMillis) {
            // Expired, remove from pending
            pending2FAVerification.remove(uuid);
            return false;
        }
        
        return true;
    }
    
    /**
     * Initiate Discord 2FA after successful password login
     * Generates token, sends to Discord, and adds to pending verification
     * @param player Player
     * @return true if 2FA code sent successfully
     */
    public void initiateTwoFactorSetup(Player player, String discordUsername) {
        // Call the existing initiateDiscord2FA
        initiateDiscord2FA(player);
    }
    
    public void verifyTwoFactorSetup(Player player, String code) {
        // Call the existing verifyDiscord2FA
        verifyDiscord2FA(player, code);
    }
    
    public boolean initiateDiscord2FA(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Check if Discord 2FA is enabled
        if (!isDiscord2FAEnabled()) {
            return false;
        }
        
        // Check if player has linked Discord
        String discordId = databaseManager.getDiscordId(uuid);
        if (discordId == null || discordId.isEmpty()) {
            return false;
        }
        
        // Check if 2FA is enabled for this player
        if (!isTwoFactorEnabled(uuid)) {
            return false;
        }
        
        // Generate 2FA token
        String token = generateDiscord2FAToken();
        
        // Get token expiry from config
        int expirySeconds = (int) AtlasAuthPro.getInstance().getConfig()
            .getLong("discord-2fa.token.expiry-seconds", 120);
        
        // Hash token for storage (using BCrypt)
        String tokenHash = BCrypt.withDefaults().hashToString(10, token.toCharArray());
        
        // Store hashed token in database
        Timestamp expiresAt = Timestamp.valueOf(LocalDateTime.now().plusSeconds(expirySeconds));
        databaseManager.store2FAToken(uuid, tokenHash, expiresAt);
        
        // Send token via Discord DM
        DiscordBot bot = DiscordBot.getInstance();
        if (bot != null && bot.isConnected()) {
            boolean sent = bot.send2FACode(discordId, player.getName(), token, expirySeconds);
            
            if (sent) {
                // Add to pending verification
                pending2FAVerification.put(uuid, System.currentTimeMillis());
                
                sendMessage(player, "&a✔ 2FA code sent to your Discord!");
                sendMessage(player, "&ePlease check your Discord DMs and enter the code with &a/2fa verify <code>");
                sendMessage(player, "&7This code expires in " + expirySeconds + " seconds.");
                
                // Remove from fully logged in (they're in 2FA limbo)
                loggedInPlayers.remove(uuid);
                if (playerManager != null) {
                    playerManager.onPlayerLogout(player);
                }
                
                return true;
            } else {
                sendMessage(player, "&c✖ Failed to send 2FA code to Discord!");
                sendMessage(player, "&7Please check if your Discord DMs are open and try again.");
                return false;
            }
        } else {
            sendMessage(player, "&c✖ Discord bot is not connected!");
            sendMessage(player, "&7Please contact an administrator.");
            return false;
        }
    }
    
    /**
     * Verify 2FA token submitted by player
     * @param player Player
     * @param token Token to verify
     * @return true if verification successful
     */
    public boolean verifyDiscord2FA(Player player, String token) {
        UUID uuid = player.getUniqueId();
        
        // Check if player is in 2FA verification mode
        if (!isPending2FAVerification(uuid)) {
            sendMessage(player, "&c✖ No pending 2FA verification!");
            sendMessage(player, "&7Please login first with &a/login <password>");
            return false;
        }
        
        // Hash the provided token and verify against database
        String tokenHash = BCrypt.withDefaults().hashToString(10, token.toCharArray());
        
        if (databaseManager.verify2FAToken(uuid, tokenHash)) {
            // Remove from pending verification
            pending2FAVerification.remove(uuid);
            
            // Complete the login
            loginPlayer(player);
            
            player.sendTitle(
                colorize("&a&lVERIFIED!"),
                colorize("&7Welcome back"),
                10, 40, 10
            );
            
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            
            sendMessage(player, "&a✔ 2FA Verified! Login successful!");
            return true;
        } else {
            sendMessage(player, "&c✖ Invalid or expired token!");
            sendMessage(player, "&7Please check the code and try again.");
            sendMessage(player, "&7Token expires after " + 
                AtlasAuthPro.getInstance().getConfig().getLong("discord-2fa.token.expiry-seconds", 120) + 
                " seconds.");
            return false;
        }
    }
    
    /**
     * Generate a random 2FA token
     * Uses configurable length and characters from config
     * @return Generated token
     */
    private String generateDiscord2FAToken() {
        int length = (int) AtlasAuthPro.getInstance().getConfig()
            .getLong("discord-2fa.token.length", 8);
        
        String characters = AtlasAuthPro.getInstance().getConfig()
            .getString("discord-2fa.token.characters", "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
        
        Random rand = new Random();
        StringBuilder token = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            token.append(characters.charAt(rand.nextInt(characters.length())));
        }
        
        return token.toString();
    }
    
    /**
     * Admin: Reset 2FA for a player
     * @param targetUuid Player's UUID to reset
     * @return true if successful
     */
    public boolean adminReset2FA(UUID targetUuid) {
        boolean success = databaseManager.disableDiscord2FA(targetUuid);
        
        // Also remove from pending verification
        pending2FAVerification.remove(targetUuid);
        
        // Force logout if logged in
        if (loggedInPlayers.contains(targetUuid)) {
            loggedInPlayers.remove(targetUuid);
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && playerManager != null) {
                playerManager.onPlayerLogout(target);
            }
        }
        
        return success;
    }
    
    /**
     * Admin: Get all active 2FA tokens (for debugging)
     * @return List of active token data
     */
    public List<Map<String, Object>> getActive2FATokens() {
        return databaseManager.getActive2FATokens();
    }
    
    /**
     * Login with PIN
     * @param player Player
     * @param pin PIN to verify
     * @return true if successful
     */
    public boolean loginWithPIN(Player player, String pin) {
        UUID uuid = player.getUniqueId();
        
        if (!databaseManager.isPlayerRegistered(uuid)) {
            sendMessage(player, "&cYou are not registered!");
            return false;
        }
        
        if (!databaseManager.hasPIN(uuid)) {
            sendMessage(player, "&cYou don't have a PIN set! Use &e/pin password <your_password> <new_pin>");
            return false;
        }
        
        String storedPINHash = databaseManager.getPINHash(uuid);
        if (storedPINHash == null || storedPINHash.isEmpty()) {
            sendMessage(player, "&cPIN not set!");
            return false;
        }
        
        // Verify PIN using BCrypt
        BCrypt.Result result = BCrypt.verifyer().verify(pin.toCharArray(), storedPINHash);
        
        if (result.verified) {
            loginAttempts.put(uuid, 0);
            loginPlayer(player);
            
            player.sendTitle(
                colorize("&a&lWELCOME BACK!"),
                colorize("&7Enjoy your stay"),
                10, 40, 10
            );
            
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            sendMessage(player, "&a✔ Login with PIN successful!");
            return true;
        } else {
            int attempts = loginAttempts.getOrDefault(uuid, 0) + 1;
            loginAttempts.put(uuid, attempts);
            int remaining = 3 - attempts;
            
            player.sendTitle(
                colorize("&c&lWRONG PIN"),
                colorize("&7Attempts: &c" + attempts + "&7/3"),
                10, 40, 10
            );
            
            sendMessage(player, "&c✖ Incorrect PIN (" + remaining + " attempts left)");
            
            if (attempts >= 3) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
            }
            return false;
        }
    }
    
    /**
     * Set PIN for player (requires password verification first)
     * @param player Player
     * @param password Player's password for verification
     * @param pin New PIN to set
     * @return true if successful
     */
    public boolean setPIN(Player player, String password, String pin) {
        UUID uuid = player.getUniqueId();
        
        if (!databaseManager.isPlayerRegistered(uuid)) {
            sendMessage(player, "&cYou are not registered!");
            return false;
        }
        
        // Verify password first
        Map<String, Object> playerData = databaseManager.getPlayerData(uuid);
        if (playerData == null) {
            sendMessage(player, "&cAccount error!");
            return false;
        }
        
        String storedHash = (String) playerData.get("password_hash");
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);
        
        if (!result.verified) {
            sendMessage(player, "&c✖ Incorrect password!");
            return false;
        }
        
        // Validate PIN length
        if (pin.length() < 4 || pin.length() > 6) {
            sendMessage(player, "&cPIN must be 4-6 digits!");
            return false;
        }
        
        // Check if PIN is numeric
        if (!pin.matches("^[0-9]+$")) {
            sendMessage(player, "&cPIN must contain only numbers!");
            return false;
        }
        
        // Hash PIN with BCrypt
        String hashedPIN = BCrypt.withDefaults().hashToString(12, pin.toCharArray());
        
        // Store PIN hash
        if (databaseManager.setPIN(uuid, hashedPIN)) {
            sendMessage(player, "&a✔ PIN set successfully!");
            sendMessage(player, "&7You can now login with &a/pin <your_pin>");
            return true;
        } else {
            sendMessage(player, "&cFailed to set PIN!");
            return false;
        }
    }
}


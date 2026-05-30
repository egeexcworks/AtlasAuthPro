package com.atlas.auth.utils;

import com.atlas.auth.AtlasAuthPro;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Language manager for multi-language support
 */
public class LangManager {
    private final AtlasAuthPro plugin;
    private FileConfiguration langConfig;
    private File langFile;
    private String currentLang;
    private final Map<String, FileConfiguration> loadedLanguages;
    
    public LangManager(AtlasAuthPro plugin) {
        this.plugin = plugin;
        this.loadedLanguages = new HashMap<>();
        loadLanguage();
    }
    
    /**
     * Load the language file based on configuration
     */
    public void loadLanguage() {
        currentLang = plugin.getConfig().getString("language", "en_US");
        
        // Check if language file exists
        langFile = new File(plugin.getDataFolder(), "lang/" + currentLang + ".yml");
        
        if (!langFile.exists()) {
            // Create default language file
            saveDefaultLanguage(currentLang);
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // Load all available languages
        loadAllLanguages();
    }
    
    /**
     * Save default language file
     */
    private void saveDefaultLanguage(String langCode) {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        // Create default English language file
        File defaultFile = new File(langDir, langCode + ".yml");
        
        if (!defaultFile.exists()) {
            try {
                defaultFile.createNewFile();
                
                // Write default language content
                YamlConfiguration defaultConfig = new YamlConfiguration();
                
                // Messages
                defaultConfig.set("prefix", "&8[&3AtlasAuth&8] &7");
                defaultConfig.set("not-logged-in", "&cPlease login or register first!");
                defaultConfig.set("wrong-password", "&cWrong password!");
                defaultConfig.set("not-registered", "&cYou are not registered! Use /register");
                defaultConfig.set("already-registered", "&cYou are already registered!");
                defaultConfig.set("passwords-not-match", "&cPasswords do not match!");
                defaultConfig.set("password-too-short", "&cPassword is too short! Min: {min}, Max: {max}");
                defaultConfig.set("password-weak", "&cPassword is too weak! Use a mix of letters, numbers, and special characters.");
                defaultConfig.set("account-locked", "&cAccount locked! Too many failed attempts.");
                defaultConfig.set("login-success", "&aLogin successful! Welcome back, {player}!");
                defaultConfig.set("register-success", "&aRegistration successful! Welcome, {player}!");
                defaultConfig.set("logout-success", "&7You have been logged out.");
                defaultConfig.set("password-changed", "&aPassword changed successfully!");
                defaultConfig.set("password-reset", "&aPassword has been reset by an admin. Use /resetpassword to create a new one.");
                
                // Admin messages
                defaultConfig.set("admin-no-permission", "&cYou need atlasauth.admin permission!");
                defaultConfig.set("admin-player-not-found", "&cPlayer not found!");
                defaultConfig.set("admin-password-reset", "&aPassword reset for {player}!");
                defaultConfig.set("admin-force-logout", "&aForce logged out {player}!");
                defaultConfig.set("admin-banned", "&aPlayer {player} has been banned from auth!");
                defaultConfig.set("admin-unbanned", "&aPlayer {player} has been unbanned!");
                defaultConfig.set("admin-confirm-action", "&c⚠ WARNING: This action requires confirmation. Click again to confirm.");
                defaultConfig.set("admin-action-cancelled", "&7Action cancelled.");
                
                // 2FA messages
                defaultConfig.set("2fa-enabled", "&a2FA enabled! Your account is now more secure.");
                defaultConfig.set("2fa-disabled", "&72FA disabled for your account.");
                defaultConfig.set("2fa-code-sent", "&aVerification code sent to your email: {email}");
                defaultConfig.set("2fa-invalid-code", "&cInvalid verification code!");
                defaultConfig.set("2fa-required", "&cThis account requires 2FA verification!");
                
                // Session messages
                defaultConfig.set("session-expired", "&cYour session has expired. Please login again.");
                defaultConfig.set("session-auto-logout", "&cYou have been logged out due to inactivity.");
                defaultConfig.set("trusted-device-added", "&aThis device has been trusted for auto-login.");
                
                // IP Tracking
                defaultConfig.set("new-ip-detected", "&e⚠ New IP address detected! If this wasn't you, change your password.");
                defaultConfig.set("suspicious-login", "&c⚠ Suspicious login detected from IP: {ip}");
                
                // Help messages
                defaultConfig.set("help-login", "&e/login <password> &7- Login to your account");
                defaultConfig.set("help-register", "&e/register <password> <confirm> &7- Register a new account");
                defaultConfig.set("help-logout", "&e/logout &7- Logout from your account");
                defaultConfig.set("help-changepassword", "&e/changepassword <old> <new> &7- Change your password");
                defaultConfig.set("help-resetpassword", "&e/resetpassword &7- Reset your password");
                
                defaultConfig.save(defaultFile);
                
            } catch (IOException e) {
                plugin.getPluginLogger().severe("Failed to create default language file: " + e.getMessage());
            }
        }
    }
    
    /**
     * Load all available language files
     */
    private void loadAllLanguages() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (langDir.exists() && langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String langCode = file.getName().replace(".yml", "");
                    loadedLanguages.put(langCode, YamlConfiguration.loadConfiguration(file));
                }
            }
        }
    }
    
    /**
     * Get a message in the current language
     */
    public String getMessage(String key) {
        return getMessage(key, currentLang);
    }
    
    /**
     * Get a message in a specific language
     */
    public String getMessage(String key, String langCode) {
        FileConfiguration lang = loadedLanguages.getOrDefault(langCode, langConfig);
        
        String message = lang.getString(key);
        if (message == null) {
            // Fallback to default
            message = langConfig.getString(key);
            if (message == null) {
                message = key; // Return key if not found
            }
        }
        
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Get a message with placeholders replaced
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        return Utils.replacePlaceholders(message, placeholders);
    }
    
    /**
     * Get colorized message
     */
    public String getColoredMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', getMessage(key));
    }
    
    /**
     * Get all available language codes
     */
    public Set<String> getAvailableLanguages() {
        return loadedLanguages.keySet();
    }
    
    /**
     * Reload the language manager
     */
    public void reloadLang() {
        loadLanguage();
    }
    
    /**
     * Get current language code
     */
    public String getCurrentLang() {
        return currentLang;
    }
}

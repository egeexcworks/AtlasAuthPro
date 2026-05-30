package com.atlas.auth;

import com.atlas.auth.bot.DiscordBot;
import com.atlas.auth.commands.*;
import com.atlas.auth.database.DatabaseManager;
import com.atlas.auth.events.PlayerListener;
import com.atlas.auth.gui.AdminGUI;
import com.atlas.auth.gui.GUIListener;
import com.atlas.auth.managers.AuthManager;
import com.atlas.auth.managers.PlayerManager;
import com.atlas.auth.migration.MigrationManager;
import com.atlas.auth.tasks.SessionCleanupTask;
import com.atlas.auth.utils.LangManager;
import com.atlas.auth.utils.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AtlasAuthPro - Advanced Authentication System for Minecraft Servers
 * 
 * Features:
 * - Secure BCrypt password hashing
 * - Session management with auto-logout
 * - IP tracking and device fingerprinting
 * - Two-factor authentication (2FA)
 * - GUI Admin Panel
 * - Multi-language support
 * - Trusted device auto-login
 * - Discord 2FA Integration
 * 
 * @author AtlasAuth Team
 * @version 2.0.1
 */
public class AtlasAuthPro extends JavaPlugin {
    
    // Singleton instance
    private static AtlasAuthPro instance;
    
    // Core managers
    private DatabaseManager databaseManager;
    private AuthManager authManager;
    private PlayerManager playerManager;
    private GUIListener guiListener;
    private AdminGUI adminGUI;
    private LangManager langManager;
    private Logger logger;
    private MigrationManager migrationManager;
    
    // Configuration
    private boolean isEnabled = false;
    
    @Override
    public void onLoad() {
        instance = this;
        
        // Create data folder if not exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize logger
        logger = new Logger(this);
        logger.info("Loading AtlasAuthPro v" + getDescription().getVersion() + "...");
    }
    
    @Override
    public void onEnable() {
        try {
            // Initialize database
            databaseManager = new DatabaseManager();
            if (!databaseManager.initialize()) {
                logger.severe("Failed to initialize database! Disabling plugin...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Initialize managers
            playerManager = new PlayerManager();
            playerManager.setDatabaseManager(databaseManager);
            authManager = new AuthManager(databaseManager, playerManager);
            guiListener = new GUIListener(authManager, databaseManager);
            adminGUI = new AdminGUI(authManager, databaseManager);
            langManager = new LangManager(this);
            migrationManager = new MigrationManager(this, databaseManager);
            
            // Register events
            getServer().getPluginManager().registerEvents(new PlayerListener(authManager, playerManager), this);
            getServer().getPluginManager().registerEvents(guiListener, this);
            
            // Register commands
            registerCommands();
            
            // Start scheduled tasks
            startScheduledTasks();
            
            // Initialize Discord bot for 2FA (async to not block startup)
            initializeDiscordBot();
            
            // Mark as enabled
            isEnabled = true;
            
            logger.info("§aAtlasAuthPro v" + getDescription().getVersion() + " enabled successfully!");
            logger.info("§7Database: " + getConfig().getString("database.type", "SQLite"));
            logger.info("§7Total Players: " + databaseManager.getTotalPlayers());
            
        } catch (Exception e) {
            logger.severe("Failed to enable AtlasAuthPro: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        if (!isEnabled) return;
        
        try {
            // Shutdown Discord bot
            shutdownDiscordBot();
            
            // Save all data
            if (databaseManager != null) {
                databaseManager.close();
            }
            
            // Logout all players
            for (var player : getServer().getOnlinePlayers()) {
                if (authManager != null && authManager.isLoggedIn(player.getUniqueId())) {
                    authManager.logout(player);
                }
            }
            
            logger.info("§cAtlasAuthPro disabled successfully!");
            
        } catch (Exception e) {
            logger.severe("Error during plugin disable: " + e.getMessage());
        }
        
        instance = null;
    }
    
    /**
     * Register all plugin commands
     */
    private void registerCommands() {
        // Login command
        PluginCommand loginCmd = getCommand("login");
        if (loginCmd != null) {
            loginCmd.setExecutor(new LoginCommand(authManager, playerManager));
            loginCmd.setPermission("atlasauth.login");
            loginCmd.setPermissionMessage(getConfig().getString("messages.admin-no-permission", 
                "&cYou don't have permission to use this command!"));
        }
        
        // Register command
        PluginCommand registerCmd = getCommand("register");
        if (registerCmd != null) {
            registerCmd.setExecutor(new RegisterCommand(authManager, playerManager));
            registerCmd.setPermission("atlasauth.register");
        }
        
        // Logout command
        PluginCommand logoutCmd = getCommand("logout");
        if (logoutCmd != null) {
            logoutCmd.setExecutor(new LogoutCommand(authManager));
            logoutCmd.setPermission("atlasauth.logout");
        }
        
        // Change password command
        PluginCommand changePassCmd = getCommand("changepassword");
        if (changePassCmd != null) {
            changePassCmd.setExecutor(new ChangePasswordCommand(authManager));
            changePassCmd.setPermission("atlasauth.changepassword");
        }
        
        // Reset password command
        PluginCommand resetPassCmd = getCommand("resetpassword");
        if (resetPassCmd != null) {
            resetPassCmd.setExecutor(new ResetPasswordCommand(authManager));
            resetPassCmd.setPermission("atlasauth.resetpassword");
        }
        
        // Auth command (main command)
        PluginCommand authCmd = getCommand("auth");
        if (authCmd != null) {
            authCmd.setExecutor(new AuthCommand(authManager, playerManager));
        }
        
        // Admin command
        PluginCommand adminCmd = getCommand("atlasadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(new AdminCommand(authManager, databaseManager));
            adminCmd.setPermission("atlasauth.admin");
        }
        
        // 2FA commands
        PluginCommand twoFactorCmd = getCommand("2fa");
        if (twoFactorCmd != null) {
            twoFactorCmd.setExecutor(new TwoFactorCommand(authManager));
            twoFactorCmd.setPermission("atlasauth.2fa");
        }
        
        // PIN command (login with PIN)
        PluginCommand pinCmd = getCommand("pin");
        if (pinCmd != null) {
            pinCmd.setExecutor(new PINCommand(authManager));
            pinCmd.setPermission("atlasauth.pin");
        }
        
        // Register PIN command (set PIN)
        PluginCommand registerPINCmd = getCommand("registerpin");
        if (registerPINCmd != null) {
            registerPINCmd.setExecutor(new RegisterPINCommand(authManager));
            registerPINCmd.setPermission("atlasauth.registerpin");
        }
        
        logger.info("All commands registered successfully!");

    }
    
    /**
     * Start scheduled tasks
     */
    private void startScheduledTasks() {
        // Session cleanup task
        long cleanupInterval = getConfig().getLong("performance.cleanup-interval", 300) * 20L;
        new SessionCleanupTask(databaseManager, authManager).runTaskTimerAsynchronously(this, cleanupInterval, cleanupInterval);
        
        // Player session check task
        long sessionCheckInterval = getConfig().getLong("security.session-duration", 86400) * 20L / 10; // Check 10 times per session
        if (sessionCheckInterval > 0) {
            new com.atlas.auth.tasks.SessionCheckTask(authManager).runTaskTimerAsynchronously(this, sessionCheckInterval, sessionCheckInterval);
        }
        
        logger.info("Scheduled tasks started!");
    }
    
    /**
     * Initialize Discord bot for 2FA functionality
     * This runs asynchronously to not block plugin startup
     */
    private void initializeDiscordBot() {
        // Check if Discord 2FA is enabled in config
        if (!getConfig().getBoolean("discord-2fa.enabled", false)) {
            logger.info("Discord 2FA is disabled in configuration");
            return;
        }
        
        // Initialize Discord bot asynchronously
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            DiscordBot bot = new DiscordBot();
            boolean success = bot.initialize();
            
            if (success) {
                logger.info("§aDiscord bot initialized successfully for 2FA!");
            } else {
                logger.warning("Failed to initialize Discord bot. 2FA will not work until configured correctly.");
            }
        });
    }
    
    /**
     * Shutdown Discord bot on plugin disable
     */
    private void shutdownDiscordBot() {
        DiscordBot bot = DiscordBot.getInstance();
        if (bot != null) {
            bot.shutdown();
        }
    }
    
    /**
     * Reload the plugin configuration
     */
    public void reloadPlugin() {
        reloadConfig();
        
        // Reinitialize language manager
        if (langManager != null) {
            langManager.reloadLang();
        }
        
        logger.info("Configuration reloaded!");
    }
    
    // Getters
    public static AtlasAuthPro getInstance() {
        return instance;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public AuthManager getAuthManager() {
        return authManager;
    }
    
    public PlayerManager getPlayerManager() {
        return playerManager;
    }
    
    public GUIListener getGuiListener() {
        return guiListener;
    }
    
    public AdminGUI getAdminGUI() {
        return adminGUI;
    }
    
    public LangManager getLangManager() {
        return langManager;
    }
    
    public Logger getPluginLogger() {
        return logger;
    }
    
    public boolean isPluginEnabled() {
        return isEnabled;
    }
    
    public MigrationManager getMigrationManager() {
        return migrationManager;
    }
}


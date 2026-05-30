package com.atlas.auth.utils;

import com.atlas.auth.AtlasAuthPro;
import org.bukkit.ChatColor;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Custom logger for AtlasAuthPro with file logging support
 */
public class Logger {
    private final AtlasAuthPro plugin;
    private final java.util.logging.Logger bukkitLogger;
    private final java.util.logging.Logger fileLogger;
    private final SimpleDateFormat dateFormat;
    private boolean fileLoggingEnabled;
    
    public Logger(AtlasAuthPro plugin) {
        this.plugin = plugin;
        this.bukkitLogger = plugin.getLogger();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // Initialize file logger
        this.fileLogger = java.util.logging.Logger.getLogger("AtlasAuthPro");
        this.fileLoggingEnabled = plugin.getConfig().getBoolean("logging.file-logging", true);
        
        if (fileLoggingEnabled) {
            setupFileLogging();
        }
    }
    
    private void setupFileLogging() {
        try {
            // Create logs directory
            File logsDir = new File(plugin.getDataFolder(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            // Create log file with date
            String logFileName = "logs/AtlasAuth_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
            FileHandler fileHandler = new FileHandler(plugin.getDataFolder() + "/" + logFileName, true);
            
            // Set formatter
            fileHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(record.getMillis())) + "] " 
                           + record.getLevel().getName() + ": " + record.getMessage() + "\n";
                }
            });
            
            fileLogger.addHandler(fileHandler);
            fileLogger.setLevel(Level.ALL);
            
        } catch (IOException e) {
            bukkitLogger.warning("Failed to setup file logging: " + e.getMessage());
            fileLoggingEnabled = false;
        }
    }
    
    public void info(String message) {
        String formatted = formatMessage(message);
        bukkitLogger.info(ChatColor.stripColor(formatted));
        if (fileLoggingEnabled) {
            fileLogger.info(ChatColor.stripColor(formatted));
        }
    }
    
    public void warning(String message) {
        String formatted = formatMessage(message);
        bukkitLogger.warning(ChatColor.stripColor(formatted));
        if (fileLoggingEnabled) {
            fileLogger.warning(ChatColor.stripColor(formatted));
        }
    }
    
    public void severe(String message) {
        String formatted = formatMessage(message);
        bukkitLogger.severe(ChatColor.stripColor(formatted));
        if (fileLoggingEnabled) {
            fileLogger.severe(ChatColor.stripColor(formatted));
        }
    }
    
    public void debug(String message) {
        if (plugin.getConfig().getBoolean("logging.debug", false)) {
            String formatted = formatMessage(message);
            bukkitLogger.info("[DEBUG] " + ChatColor.stripColor(formatted));
        }
    }
    
    public void logAdminAction(String admin, String action, String target) {
        if (plugin.getConfig().getBoolean("logging.log-admin-actions", true)) {
            String logMessage = String.format("ADMIN ACTION: %s performed '%s' on %s", admin, action, target);
            info(logMessage);
        }
    }
    
    public void logLogin(String player, String ip, boolean success) {
        if (plugin.getConfig().getBoolean("logging.log-logins", true)) {
            String status = success ? "SUCCESS" : "FAILED";
            String logMessage = String.format("LOGIN: %s from IP %s - %s", player, ip, status);
            info(logMessage);
        }
    }
    
    public void logRegistration(String player, String ip) {
        if (plugin.getConfig().getBoolean("logging.log-registrations", true)) {
            String logMessage = String.format("REGISTRATION: %s from IP %s", player, ip);
            info(logMessage);
        }
    }
    
    private String formatMessage(String message) {
        // Add prefix
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&3AtlasAuth&8] &7");
        return ChatColor.translateAlternateColorCodes('&', prefix) + ChatColor.translateAlternateColorCodes('&', message);
    }
}

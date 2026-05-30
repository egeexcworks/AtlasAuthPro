package com.atlas.auth.migration;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Abstract base for auth detectors (nLogin, OpenLogin, etc.)
 * Common interface for MigrationManager
 */
public abstract class AbstractAuthDetector {
    
    /**
     * Attempt to detect the auth database
     * @return true if database was found and connected
     */
    public abstract boolean detect();
    
    /**
     * Get all player data from source database
     */
    public abstract List<Map<String, Object>> getAllPlayers();
    
    /**
     * Check if player exists in source database
     */
    public abstract boolean playerExists(String uuid);
    
    /**
     * Get password hash for player from source database
     */
    public abstract String getPasswordHash(String uuid);
    
    /**
     * Close database connection
     */
    public abstract void close();
    
    // Common getters (implemented by subclasses)
    public abstract boolean isFound();
    
    public abstract String getDatabaseType();
    
    public abstract String getTableName();
    
    public abstract Map<String, String> getColumnMapping();
    
    public abstract Connection getConnection();
    
    /**
     * Get player count
     */
    public abstract int getPlayerCount();
}

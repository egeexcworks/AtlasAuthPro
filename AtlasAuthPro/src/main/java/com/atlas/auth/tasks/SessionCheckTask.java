package com.atlas.auth.tasks;

import com.atlas.auth.managers.AuthManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Session check task - checks player sessions for inactivity timeout
 */
public class SessionCheckTask extends BukkitRunnable {
    private final AuthManager authManager;
    
    public SessionCheckTask(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    @Override
    public void run() {
        // This task can be used to check for session timeout
        // Implementation depends on session tracking mechanism
        // For now, players stay logged in until they disconnect or manually logout
    }
}

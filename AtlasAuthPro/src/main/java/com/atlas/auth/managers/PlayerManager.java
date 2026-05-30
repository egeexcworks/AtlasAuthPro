package com.atlas.auth.managers;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class PlayerManager {
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> blindedPlayers = new HashSet<>();
    private final Map<UUID, Integer> actionBarTasks = new HashMap<>();
    private DatabaseManager databaseManager;
    
    public void setDatabaseManager(DatabaseManager dbManager) {
        this.databaseManager = dbManager;
    }
    
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Check if player is registered (new vs returning player)
        boolean isRegistered = false;
        if (databaseManager != null) {
            isRegistered = databaseManager.isPlayerRegistered(uuid);
            if (!isRegistered) {
                Map<String, Object> migratedData = databaseManager.getPlayerDataByUsername(player.getName());
                if (migratedData != null) {
                    AtlasAuthPro.getInstance().getLogger().info("Detected migrated account on join for " + player.getName() + " using username lookup.");
                    isRegistered = true;
                    boolean updated = databaseManager.updatePlayerUuidByUsername(player.getName(), uuid);
                    if (!updated) {
                        AtlasAuthPro.getInstance().getLogger().warning("Failed to update migrated UUID for " + player.getName() + ". Login will still work with username lookup.");
                    }
                }
            }
        }
        
        // Get UI settings
        boolean useTitleAnimation = AtlasAuthPro.getInstance().getConfig().getBoolean("ui.title-animation", true);
        
        if (useTitleAnimation) {
            // Show title message based on registration status
            String title;
            String subtitle;
            
            if (isRegistered) {
                // Returning player - show login prompt
                title = AtlasAuthPro.getInstance().getConfig().getString("messages.join-title", "&3&lATLAS AUTH");
                subtitle = AtlasAuthPro.getInstance().getConfig().getString("messages-v2.login-prompt", "&e⚠ &cPlease login with: &a/login <password>");
            } else {
                // New player - show register prompt
                title = AtlasAuthPro.getInstance().getConfig().getString("messages.join-title", "&3&lATLAS AUTH");
                subtitle = AtlasAuthPro.getInstance().getConfig().getString("messages-v2.register-prompt", "&e⚠ &cPlease register: &a/register <password> <confirm>");
            }
            
            player.sendTitle(
                colorize(title),
                colorize(subtitle),
                10, 60, 10
            );
        }
        
        // Start action bar messages (if enabled)
        boolean actionBarEnabled = AtlasAuthPro.getInstance().getConfig().getBoolean("ui.actionbar-reminder", true);
        if (actionBarEnabled) {
            startActionBarMessages(player, isRegistered);
        }
        
        // Apply blindness if enabled
        if (AtlasAuthPro.getInstance().getConfig().getBoolean("security.blindness-effect", true)) {
            applyBlindness(player);
            blindedPlayers.add(uuid);
        }
        
        // Freeze player
        freezePlayer(player);
        frozenPlayers.add(uuid);
        
        // Send welcome message with appropriate prompt
        String prefix = AtlasAuthPro.getInstance().getConfig().getString("messages.prefix", "&8[&3AtlasAuth&8] &7");
        boolean usePrefix = AtlasAuthPro.getInstance().getConfig().getBoolean("ui.use-prefix", true);
        
        if (usePrefix) {
            if (isRegistered) {
                player.sendMessage(colorize(prefix + "&7Welcome back! &ePlease login with &a/login <password>"));
            } else {
                player.sendMessage(colorize(prefix + "&7Welcome! &ePlease register with &a/register <password> <confirm>"));
            }
        } else {
            if (isRegistered) {
                player.sendMessage(colorize("&a✔ Welcome back! &ePlease login with &a/login <password>"));
            } else {
                player.sendMessage(colorize("&a✔ Welcome! &ePlease register with &a/register <password> <confirm>"));
            }
        }
    }
    
    public void onPlayerLogin(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Remove effects
        removeBlindness(player);
        blindedPlayers.remove(uuid);
        
        unfreezePlayer(player);
        frozenPlayers.remove(uuid);
        
        // Stop action bar messages
        stopActionBarMessages(player);
        
        // Show login success action bar
        sendActionBar(player, colorize("&a✓ Logged in successfully!"));
        
        // Clear any existing effects
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
    
public void onPlayerLogout(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Check registration status
        boolean isRegistered = false;
        if (databaseManager != null) {
            isRegistered = databaseManager.isPlayerRegistered(uuid);
        }
        
        // Re-apply effects
        if (AtlasAuthPro.getInstance().getConfig().getBoolean("security.blindness-effect", true)) {
            applyBlindness(player);
            blindedPlayers.add(uuid);
        }
        
        freezePlayer(player);
        frozenPlayers.add(uuid);
        
        // Restart action bar messages
        boolean actionBarEnabled = AtlasAuthPro.getInstance().getConfig().getBoolean("ui.actionbar-reminder", true);
        if (actionBarEnabled) {
            startActionBarMessages(player, isRegistered);
        }
    }
    
    private void applyBlindness(Player player) {
        // Infinite blindness effect
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.BLINDNESS,
            999999, // Very long duration
            1,
            false,
            false,
            false
        ));
        
        // Also add slowness to prevent jumping
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS,
            999999,
            255, // Max level to prevent any movement
            false,
            false,
            false
        ));
    }
    
    private void removeBlindness(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
    
    private void freezePlayer(Player player) {
        // Player is frozen by event cancellation
        // No additional action needed here
    }
    
    private void unfreezePlayer(Player player) {
        // No action needed, events will no longer be cancelled
    }
    
private void startActionBarMessages(Player player, boolean isRegistered) {
        UUID uuid = player.getUniqueId();
        
        // Cancel existing task if any
        stopActionBarMessages(player);
        
        // Create messages based on registration status
        final String[] messages;
        if (isRegistered) {
            messages = new String[] {
                "&e⚠ &cYou must login to play!",
                "&eUse &a/login <password>&e to authenticate",
                "&eType &a/login &ein chat",
                "&cWrong password = account lockout!"
            };
        } else {
            messages = new String[] {
                "&e⚠ &cYou must register to play!",
                "&eUse &a/register <password> <confirm>",
                "&eType &a/register &ein chat",
                "&ePassword must be 6+ characters"
            };
        }
        
        // Create new task
        int taskId = new BukkitRunnable() {
            int messageIndex = 0;
            
            @Override
            public void run() {
                if (!player.isOnline() || !frozenPlayers.contains(uuid)) {
                    this.cancel();
                    return;
                }
                
                sendActionBar(player, colorize(messages[messageIndex]));
                messageIndex = (messageIndex + 1) % messages.length;
            }
        }.runTaskTimer(AtlasAuthPro.getInstance(), 0L, 60L).getTaskId(); // Change message every 3 seconds
        
        actionBarTasks.put(uuid, taskId);
    }
    
    private void stopActionBarMessages(Player player) {
        UUID uuid = player.getUniqueId();
        if (actionBarTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(actionBarTasks.get(uuid));
            actionBarTasks.remove(uuid);
        }
    }
    
    private void sendActionBar(Player player, String message) {
        player.sendActionBar(message);
    }
    
    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }
    
    public boolean isBlinded(UUID uuid) {
        return blindedPlayers.contains(uuid);
    }
    
    public void cleanupPlayer(UUID uuid) {
        frozenPlayers.remove(uuid);
        blindedPlayers.remove(uuid);
        if (actionBarTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(actionBarTasks.get(uuid));
            actionBarTasks.remove(uuid);
        }
    }
    
    private String colorize(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}

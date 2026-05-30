package com.atlas.auth.events;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.managers.AuthManager;
import com.atlas.auth.managers.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final AuthManager authManager;
    private final PlayerManager playerManager;
    
    public PlayerListener(AuthManager authManager, PlayerManager playerManager) {
        this.authManager = authManager;
        this.playerManager = playerManager;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerManager.onPlayerJoin(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        playerManager.cleanupPlayer(uuid);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!authManager.isLoggedIn(uuid)) {
            // Check if player actually moved (not just looked around)
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                
                event.setCancelled(true);
                
                // Send action bar reminder
                if (AtlasAuthPro.getInstance().getConfig().getBoolean("features.actionbar-messages", true)) {
                    player.sendActionBar(colorize("&cYou must login or register to move!"));
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            sendNotLoggedInMessage(player);
        }
    }
    
    private static final Set<String> SENSITIVE_COMMAND_LABELS = new HashSet<>(Arrays.asList(
        "login", "l", "register", "reg", "resetpassword", "rp", "changepassword", "cp", "changepass",
        "auth", "2fa", "registerpin", "pin"
    ));

    private static final Set<String> SENSITIVE_SUBCOMMANDS = new HashSet<>(Arrays.asList(
        "login", "register", "resetpassword", "changepassword", "changepass", "cp", "rp", "disable", "unlink"
    ));

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        if (message.isEmpty()) {
            return;
        }

        String[] parts = message.split("\\s+");
        String commandLabel = parts[0].replaceFirst("^/", "").toLowerCase();
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        if (isSensitiveAuthCommand(commandLabel, args)) {
            executeSecureCommand(player, commandLabel, args);
            event.setMessage(buildSanitizedMessage(commandLabel, args));
            event.setCancelled(true);
            return;
        }

        if (!authManager.isLoggedIn(player.getUniqueId())) {
            // Allow only auth commands
            if (!isAuthCommand(message.toLowerCase())) {
                event.setCancelled(true);
                sendNotLoggedInMessage(player);
            }
        }
    }

    private boolean isSensitiveAuthCommand(String commandLabel, String[] args) {
        if (!SENSITIVE_COMMAND_LABELS.contains(commandLabel)) {
            return false;
        }

        if (!commandLabel.equals("auth") && !commandLabel.equals("2fa")) {
            return true;
        }

        if (args.length == 0) {
            return false;
        }

        return SENSITIVE_SUBCOMMANDS.contains(args[0].toLowerCase());
    }

    private String buildSanitizedMessage(String commandLabel, String[] args) {
        StringBuilder safeMessage = new StringBuilder("/").append(commandLabel);

        if (args.length == 0) {
            return safeMessage.toString();
        }

        if (commandLabel.equals("auth") || commandLabel.equals("2fa")) {
            safeMessage.append(" ").append(args[0].toLowerCase());
            for (int i = 1; i < args.length; i++) {
                safeMessage.append(" ******");
            }
            return safeMessage.toString();
        }

        for (String arg : args) {
            safeMessage.append(" ******");
        }
        return safeMessage.toString();
    }

    private void executeSecureCommand(Player player, String commandLabel, String[] args) {
        String commandName = resolveCommandName(commandLabel);
        PluginCommand pluginCommand = AtlasAuthPro.getInstance().getCommand(commandName);
        if (pluginCommand != null) {
            pluginCommand.execute(player, commandName, args);
            return;
        }

        // Fallback: if plugin command cannot be resolved, execute through Bukkit dispatch to preserve behavior.
        String safeCommand = commandLabel + (args.length > 0 ? " " + String.join(" ", args) : "");
        Bukkit.getServer().dispatchCommand(player, safeCommand);
    }

    private String resolveCommandName(String label) {
        switch (label) {
            case "l":
                return "login";
            case "reg":
                return "register";
            case "rp":
                return "resetpassword";
            case "cp":
            case "changepass":
                return "changepassword";
            default:
                return label;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            sendNotLoggedInMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            sendNotLoggedInMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
                sendNotLoggedInMessage(player);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            sendNotLoggedInMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            sendNotLoggedInMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
            sendNotLoggedInMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player) {
            Player player = (Player) event.getEntered();
            
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
                sendNotLoggedInMessage(player);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
    
    private boolean isAuthCommand(String cmd) {
        return cmd.startsWith("/login") || 
               cmd.startsWith("/register") || 
               cmd.startsWith("/resetpassword") ||
               cmd.startsWith("/auth") ||
               cmd.startsWith("/l ") || 
               cmd.startsWith("/reg ") ||
               cmd.startsWith("/rp ") ||
               cmd.equals("/l") ||
               cmd.equals("/reg") ||
               cmd.equals("/rp");
    }
    
    private void sendNotLoggedInMessage(Player player) {
        String prefix = AtlasAuthPro.getInstance().getConfig().getString("messages.prefix", "&8[&3Auth&8] &7");
        String message = AtlasAuthPro.getInstance().getConfig().getString("messages.not-logged-in", "&cPlease login or register first!");
        player.sendMessage(colorize(prefix + message));
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
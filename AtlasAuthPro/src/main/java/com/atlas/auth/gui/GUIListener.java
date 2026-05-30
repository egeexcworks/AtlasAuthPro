package com.atlas.auth.gui;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.database.DatabaseManager;
import com.atlas.auth.managers.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GUIListener implements Listener {
    private final AuthManager authManager;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> currentGUI = new HashMap<>();
    
    // Modern color palette
    private final ChatColor PRIMARY = ChatColor.DARK_AQUA;
    private final ChatColor ACCENT = ChatColor.AQUA;
    private final ChatColor SUCCESS = ChatColor.GREEN;
    private final ChatColor WARNING = ChatColor.YELLOW;
    private final ChatColor DANGER = ChatColor.RED;
    private final ChatColor TEXT = ChatColor.GRAY;
    private final ChatColor HIGHLIGHT = ChatColor.WHITE;
    
    public GUIListener(AuthManager authManager, DatabaseManager databaseManager) {
        this.authManager = authManager;
        this.databaseManager = databaseManager;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        ItemStack item = event.getCurrentItem();
        
        if (item == null || item.getType() == Material.AIR) return;
        
        String title = ChatColor.stripColor(event.getView().getTitle());
        
        // Check if it's our GUI
        if (title.contains("AtlasAuthPro Admin") || 
            title.contains("Players") || 
            title.contains("Statistics") ||
            title.contains("Player Details") ||
            title.contains("Admin Panel")) {
            
            event.setCancelled(true);
            
            if (item.getType() == Material.PLAYER_HEAD) {
                // Player head clicked
                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta != null && meta.hasOwner()) {
                    Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
                    if (target != null) {
                        openPlayerDetails(player, target.getUniqueId());
                    }
                }
            } else if (item.getType() == Material.ARROW) {
                // Navigation
                if (item.getItemMeta().getDisplayName().contains("Previous")) {
                    int page = playerPages.getOrDefault(player.getUniqueId(), 0);
                    if (page > 0) {
                        openPlayerList(player, page - 1);
                    }
                } else if (item.getItemMeta().getDisplayName().contains("Next")) {
                    int page = playerPages.getOrDefault(player.getUniqueId(), 0);
                    openPlayerList(player, page + 1);
                } else if (item.getItemMeta().getDisplayName().contains("Back")) {
                    openMainMenu(player);
                }
            } else if (item.getType() == Material.OAK_DOOR || item.getType() == Material.DARK_OAK_DOOR) {
                openMainMenu(player);
            } else if (item.getType() == Material.BARRIER) {
                player.closeInventory();
            } else if (item.getType() == Material.PAPER || item.getType() == Material.BOOK) {
                openStatistics(player);
            } else if (item.getType() == Material.REDSTONE_TORCH || item.getType() == Material.TRIPWIRE_HOOK) {
                // Force reset password
                player.sendMessage(ChatColor.GREEN + "Use /atlasadmin forcepassword <player> in chat");
                player.closeInventory();
            } else if (item.getType() == Material.ANVIL) {
                // Database Migrator
                player.closeInventory();
                player.sendMessage(ChatColor.AQUA + "Starting migration... Please wait.");
                player.sendMessage(ChatColor.YELLOW + "Use: /atlasadmin migrate start");
                player.sendMessage(ChatColor.GRAY + "This will migrate from nLogin/OpenLogin to AtlasAuthPro");
            } else if (item.getType() == Material.TOTEM_OF_UNDYING || item.getType() == Material.EMERALD) {
                // Reset password for specific player
                player.sendMessage(ChatColor.YELLOW + "Right-click a player head to reset their password");
            } else if (item.getType() == Material.BOOKSHELF || item.getType() == Material.COMPASS) {
                // Confirm action
                if (item.getItemMeta().getDisplayName().contains("Confirm")) {
                    player.sendMessage(ChatColor.GREEN + "Action confirmed!");
                    player.closeInventory();
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        playerPages.remove(player.getUniqueId());
        currentGUI.remove(player.getUniqueId());
    }
    
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, 
            ChatColor.translateAlternateColorCodes('&', "&8&lATLAS AUTH &7- &3Admin Panel"));
        
        // Decorative border
        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        
        // Player Management - Using shield icon (better icon for player management)
        ItemStack players = createItem(Material.PLAYER_HEAD, 
            PRIMARY + "Player Management",
            TEXT + "View and manage all registered players",
            TEXT + "Reset passwords, view data, force logout",
            "",
            WARNING + "Click to open");
        gui.setItem(11, players);
        
        // Statistics - Using book icon
        ItemStack stats = createItem(Material.BOOK, 
            SUCCESS + "Statistics",
            TEXT + "View plugin statistics",
            TEXT + "Total players, online count, etc.",
            "",
            WARNING + "Click to open");
        gui.setItem(13, stats);
        
        // Password Reset Tool - Using key icon (tripwire hook represents key)
        ItemStack reset = createItem(Material.TRIPWIRE_HOOK, 
            DANGER + "Force Password Reset",
            TEXT + "Reset password for any player",
            TEXT + "Requires player name",
            "",
            WARNING + "Click to open chat command");
        gui.setItem(15, reset);
        
        // Database Migrator - Using anvil icon
        ItemStack migrator = createItem(Material.ANVIL, 
            ACCENT + "Database Migrator",
            TEXT + "Migrate from nLogin/OpenLogin",
            TEXT + "Automatically convert accounts",
            TEXT + "Backup created automatically",
            "",
            WARNING + "Click to start migration");
        gui.setItem(29, migrator);
        
        // Close button
        ItemStack close = createItem(Material.BARRIER, 
            DANGER + "Close",
            TEXT + "Close the admin panel");
        gui.setItem(31, close);
        
        // Watermark footer
        ItemStack watermark = createItem(Material.PAPER, 
            TEXT + " Atlas Auth • Secure Authentication",
            TEXT + "v2.0.2 - Advanced Authentication System");
        gui.setItem(33, watermark);
        
        currentGUI.put(player.getUniqueId(), "main");
        player.openInventory(gui);
    }
    
    public void openPlayerList(Player player, int page) {
        int itemsPerPage = 28;
        int offset = page * itemsPerPage;
        
        List<Map<String, Object>> players = databaseManager.getAllPlayers(itemsPerPage, offset);
        int totalPlayers = databaseManager.getTotalPlayers();
        int totalPages = (int) Math.ceil((double) totalPlayers / itemsPerPage);
        
        Inventory gui = Bukkit.createInventory(null, 54,
            ChatColor.translateAlternateColorCodes('&', 
                String.format("&8Players &7(%d/%d) &8- &3Page %d/%d", 
                    offset + 1, totalPlayers, page + 1, Math.max(1, totalPages))));
        
        // Add border
        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        
        // Add player heads with status indicators
        for (int i = 0; i < Math.min(players.size(), itemsPerPage); i++) {
            Map<String, Object> playerData = players.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            
            String username = (String) playerData.get("username");
            String uuid = (String) playerData.get("uuid");
            boolean isLocked = (Boolean) playerData.get("is_locked");
            
            // Status indicator in name
            String statusIcon = isLocked ? DANGER + "🔴 " : SUCCESS + "🟢 ";
            meta.setDisplayName(statusIcon + HIGHLIGHT + username);
            
            List<String> lore = new ArrayList<>();
            lore.add(TEXT + "UUID: " + HIGHLIGHT + uuid.substring(0, 8) + "...");
            lore.add(TEXT + "Registered: " + HIGHLIGHT + formatDate(playerData.get("registered_at")));
            lore.add(TEXT + "Last Login: " + HIGHLIGHT + formatDate(playerData.get("last_login")));
            
            // Status with color
            String statusText = isLocked ? DANGER + "🔴 LOCKED" : SUCCESS + "🟢 ACTIVE";
            lore.add(TEXT + "Status: " + statusText);
            
            lore.add("");
            lore.add(WARNING + "Left-Click: " + HIGHLIGHT + "View details");
            lore.add(WARNING + "Right-Click: " + HIGHLIGHT + "Reset password");
            
            // Hover tooltip
            lore.add("");
            lore.add(TEXT + "Hover for more info");
            
            meta.setLore(lore);
            
            // Try to get player for skull
            try {
                UUID playerUUID = UUID.fromString(uuid);
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUUID));
            } catch (Exception e) {
                // Use default head if can't get player
            }
            
            head.setItemMeta(meta);
            gui.setItem(i, head);
        }
        
        // Navigation buttons
        if (page > 0) {
            ItemStack prev = createItem(Material.ARROW, 
                SUCCESS + "◄ Previous Page",
                TEXT + "Go to previous page");
            gui.setItem(45, prev);
        }
        
        if (page < totalPages - 1 && totalPages > 1) {
            ItemStack next = createItem(Material.ARROW, 
                SUCCESS + "Next Page ►",
                TEXT + "Go to next page");
            gui.setItem(53, next);
        }
        
        // Back button
        ItemStack back = createItem(Material.DARK_OAK_DOOR, 
            WARNING + "⬆ Back to Main Menu",
            TEXT + "Return to admin panel");
        gui.setItem(49, back);
        
        // Page info
        ItemStack pageInfo = createItem(Material.PAPER,
            ACCENT + "Page " + (page + 1) + "/" + totalPages,
            TEXT + "Total Players: " + totalPlayers);
        gui.setItem(50, pageInfo);
        
        playerPages.put(player.getUniqueId(), page);
        currentGUI.put(player.getUniqueId(), "playerlist");
        player.openInventory(gui);
    }
    
    public void openPlayerDetails(Player admin, UUID targetId) {
        Inventory gui = Bukkit.createInventory(null, 36,
            ChatColor.translateAlternateColorCodes('&', "&8Player Details"));
        
        // Fill border
        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        
        // Get player head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetId));
        } catch (Exception e) {
            // Ignore
        }
        
        Map<String, Object> playerData = databaseManager.getPlayerData(targetId);
        boolean isLoggedIn = authManager.isLoggedIn(targetId);
        
        if (playerData != null) {
            String username = (String) playerData.get("username");
            String statusIcon = isLoggedIn ? SUCCESS + "🟢 " : DANGER + "🔴 ";
            meta.setDisplayName(statusIcon + HIGHLIGHT + username);
            
            List<String> lore = new ArrayList<>();
            lore.add(TEXT + "UUID: " + HIGHLIGHT + targetId.toString());
            lore.add(TEXT + "IP: " + HIGHLIGHT + (playerData.get("ip_address") != null ? playerData.get("ip_address") : "N/A"));
            lore.add(TEXT + "Registered: " + HIGHLIGHT + formatDate(playerData.get("registered_at")));
            lore.add(TEXT + "Last Login: " + HIGHLIGHT + formatDate(playerData.get("last_login")));
            lore.add(TEXT + "Status: " + (isLoggedIn ? SUCCESS + "🟢 LOGGED IN" : DANGER + "🔴 OFFLINE"));
            lore.add("");
            lore.add(TEXT + "Failed Attempts: " + HIGHLIGHT + playerData.get("failed_attempts"));
            lore.add("");
            lore.add(WARNING + "Actions:");
            
            meta.setLore(lore);
        }
        
        head.setItemMeta(meta);
        gui.setItem(13, head);
        
        // Reset Password button - Using key icon
        ItemStack resetBtn = createItem(Material.TRIPWIRE_HOOK, 
            DANGER + "🔑 Reset Password",
            TEXT + "Force reset player's password",
            TEXT + "They will need to use /resetpassword",
            "",
            WARNING + "Click to reset");
        gui.setItem(11, resetBtn);
        
        // Force Logout button - Using barrier
        ItemStack logoutBtn = createItem(Material.BARRIER, 
            WARNING + "⚡ Force Logout",
            TEXT + "Force player to logout",
            TEXT + "They will need to login again",
            "",
            WARNING + "Click to logout");
        gui.setItem(15, logoutBtn);
        
        // Back button
        ItemStack back = createItem(Material.ARROW, 
            WARNING + "⬆ Back to Player List",
            TEXT + "Return to player list");
        gui.setItem(22, back);
        
        // Confirmation note
        ItemStack info = createItem(Material.BOOK,
            TEXT + "Atlas Auth • Secure Authentication",
            TEXT + "v2.0.1 - Graphic Update");
        gui.setItem(26, info);
        
        currentGUI.put(admin.getUniqueId(), "playerdetails");
        admin.openInventory(gui);
    }
    
    public void openStatistics(Player player) {
        int totalPlayers = databaseManager.getTotalPlayers();
        Map<UUID, String> onlinePlayers = authManager.getOnlinePlayersData();
        
        Inventory gui = Bukkit.createInventory(null, 36,
            ChatColor.translateAlternateColorCodes('&', "&8&lStatistics"));
        
        // Fill border
        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        
        // Total Players
        ItemStack total = createItem(Material.PLAYER_HEAD, 
            PRIMARY + "Player Statistics",
            TEXT + "Total Registered: " + HIGHLIGHT + totalPlayers,
            TEXT + "Online Now: " + SUCCESS + "🟢 " + onlinePlayers.size(),
            TEXT + "Offline: " + DANGER + "🔴 " + (totalPlayers - onlinePlayers.size()),
            "",
            TEXT + "Server: " + HIGHLIGHT + Bukkit.getServer().getName());
        gui.setItem(11, total);
        
        // System Info
        ItemStack info = createItem(Material.BOOK, 
            SUCCESS + "System Information",
            TEXT + "Plugin: AtlasAuthPro " + ACCENT + "v2.0.1",
            TEXT + "Minecraft: 1.21.1",
            TEXT + "",
            TEXT + "Features:",
            HIGHLIGHT + " ✓ Title Messages",
            HIGHLIGHT + " ✓ Blindness Effect", 
            HIGHLIGHT + " ✓ GUI Admin Panel",
            HIGHLIGHT + " ✓ Password Reset",
            HIGHLIGHT + " ✓ Modern UI",
            "",
            SUCCESS + " All systems operational");
        gui.setItem(13, info);
        
        // Database Info
        ItemStack db = createItem(Material.CHEST, 
            WARNING + "Database Info",
            TEXT + "Type: " + HIGHLIGHT + "SQLite",
            TEXT + "File: " + HIGHLIGHT + "auth.db",
            TEXT + "Status: " + SUCCESS + "🟢 CONNECTED",
            "",
            TEXT + "Tables:",
            HIGHLIGHT + " • auth_users",
            HIGHLIGHT + " • auth_sessions",
            HIGHLIGHT + " • auth_ip_log");
        gui.setItem(15, db);
        
        // Back button
        ItemStack back = createItem(Material.DARK_OAK_DOOR, 
            WARNING + "⬆ Back to Main Menu",
            TEXT + "Return to admin panel");
        gui.setItem(22, back);
        
        // Watermark
        ItemStack watermark = createItem(Material.PAPER,
            TEXT + " Atlas Auth • Secure Authentication",
            TEXT + "v2.0.1 - Graphic Update");
        gui.setItem(26, watermark);
        
        currentGUI.put(player.getUniqueId(), "statistics");
        player.openInventory(gui);
    }
    
    // Helper methods
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
    
    private void fillBorder(Inventory inv, Material material) {
        ItemStack border = new ItemStack(material);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);
        
        // Fill top and bottom rows
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(inv.getSize() - 9 + i, border);
        }
        
        // Fill sides
        for (int i = 9; i < inv.getSize() - 9; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }
    }
    
    private String formatDate(Object date) {
        if (date == null) return "Never";
        return date.toString();
    }
}

package com.atlas.auth.gui;

import com.atlas.auth.database.DatabaseManager;
import com.atlas.auth.managers.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class AdminGUI {
    private final AuthManager authManager;
    private final DatabaseManager databaseManager;
    
    public AdminGUI(AuthManager authManager, DatabaseManager databaseManager) {
        this.authManager = authManager;
        this.databaseManager = databaseManager;
    }
    
    public void openMainMenu(Player player) {
        // Redirect to GUI listener
        Bukkit.getPluginManager().getPlugin("AtlasAuthPro")
            .getServer().getPluginManager()
            .getPlugin("AtlasAuthPro")
            .getServer().getScheduler()
            .runTask(Bukkit.getPluginManager().getPlugin("AtlasAuthPro"), 
                () -> new GUIListener(authManager, databaseManager).openMainMenu(player));
    }
    
    public void openPlayerList(Player player, int page) {
        Bukkit.getPluginManager().getPlugin("AtlasAuthPro")
            .getServer().getPluginManager()
            .getPlugin("AtlasAuthPro")
            .getServer().getScheduler()
            .runTask(Bukkit.getPluginManager().getPlugin("AtlasAuthPro"), 
                () -> new GUIListener(authManager, databaseManager).openPlayerList(player, page));
    }
    
    public void openStatistics(Player player) {
        Bukkit.getPluginManager().getPlugin("AtlasAuthPro")
            .getServer().getPluginManager()
            .getPlugin("AtlasAuthPro")
            .getServer().getScheduler()
            .runTask(Bukkit.getPluginManager().getPlugin("AtlasAuthPro"), 
                () -> new GUIListener(authManager, databaseManager).openStatistics(player));
    }
}

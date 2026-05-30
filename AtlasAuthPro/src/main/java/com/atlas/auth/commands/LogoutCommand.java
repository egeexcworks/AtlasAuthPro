package com.atlas.auth.commands;

import com.atlas.auth.managers.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Logout command - allows players to logout
 */
public class LogoutCommand implements CommandExecutor {
    private final AuthManager authManager;
    
    public LogoutCommand(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(colorize("&cYou are not logged in!"));
            return true;
        }
        
        authManager.logout(player);
        
        // Re-apply restrictions
        if (authManager.getPlayerManager() != null) {
            authManager.getPlayerManager().onPlayerLogout(player);
        }
        
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

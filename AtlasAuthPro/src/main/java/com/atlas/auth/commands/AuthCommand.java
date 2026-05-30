package com.atlas.auth.commands;

import com.atlas.auth.managers.AuthManager;
import com.atlas.auth.managers.PlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuthCommand implements CommandExecutor {
    private final AuthManager authManager;
    private final PlayerManager playerManager;
    
    public AuthCommand(AuthManager authManager, PlayerManager playerManager) {
        this.authManager = authManager;
        this.playerManager = playerManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
            player.sendMessage(colorize("&3&lATLAS AUTH COMMANDS"));
            player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
            player.sendMessage(colorize("&e/auth login <password> &7- Login to account"));
            player.sendMessage(colorize("&e/auth register <pass> <confirm> &7- Register"));
            player.sendMessage(colorize("&e/auth logout &7- Logout from account"));
            player.sendMessage(colorize("&e/auth status &7- Check login status"));
            player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
            return true;
        }
        
        String action = args[0].toLowerCase();
        
        switch (action) {
            case "login":
                if (args.length < 2) {
                    player.sendMessage(colorize("&cUsage: /auth login <password>"));
                    return true;
                }
                authManager.login(player, args[1]);
                break;
                
            case "register":
                if (args.length < 3) {
                    player.sendMessage(colorize("&cUsage: /auth register <password> <confirm>"));
                    return true;
                }
                authManager.register(player, args[1], args[2]);
                break;
                
            case "logout":
                authManager.logout(player);
                break;
                
            case "status":
                boolean isLoggedIn = authManager.isLoggedIn(player.getUniqueId());
                player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                player.sendMessage(colorize("&3&lAUTH STATUS"));
                player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                player.sendMessage(colorize("&7Player: &e" + player.getName()));
                player.sendMessage(colorize("&7Status: " + (isLoggedIn ? "&aLogged In" : "&cNot Logged In")));
                player.sendMessage(colorize("&7UUID: &f" + player.getUniqueId()));
                player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                break;
                
            default:
                player.sendMessage(colorize("&cUnknown action! Use: login, register, logout, status"));
                break;
        }
        
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
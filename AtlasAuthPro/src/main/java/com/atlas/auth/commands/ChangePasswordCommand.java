package com.atlas.auth.commands;

import com.atlas.auth.managers.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChangePasswordCommand implements CommandExecutor {
    private final AuthManager authManager;
    
    public ChangePasswordCommand(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length != 2) {
            player.sendMessage(colorize("&cUsage: /changepassword <oldpassword> <newpassword>"));
            player.sendMessage(colorize("&7Alias: &e/cp <old> <new>"));
            return true;
        }
        
        String oldPassword = args[0];
        String newPassword = args[1];
        
        authManager.changePassword(player, oldPassword, newPassword);
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
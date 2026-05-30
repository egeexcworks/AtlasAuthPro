package com.atlas.auth.commands;

import com.atlas.auth.managers.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetPasswordCommand implements CommandExecutor {
    private final AuthManager authManager;
    
    public ResetPasswordCommand(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Generate reset token
            String token = authManager.generateResetToken(player);
            player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
            player.sendMessage(colorize("&a&lPASSWORD RESET TOKEN"));
            player.sendMessage(colorize("&7Your reset token: &e" + token));
            player.sendMessage(colorize("&7Expires in: &e1 hour"));
            player.sendMessage("");
            player.sendMessage(colorize("&7Usage: &e/resetpassword " + token + " <newpassword>"));
            player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
            return true;
        }
        
        if (args.length == 2) {
            String token = args[0];
            String newPassword = args[1];
            
            if (authManager.resetPassword(player, token, newPassword)) {
                player.sendMessage(colorize("&a✓ Password reset successfully!"));
                player.sendMessage(colorize("&7You can now login with your new password."));
            }
            return true;
        }
        
        player.sendMessage(colorize("&cUsage: /resetpassword - to get token"));
        player.sendMessage(colorize("&cUsage: /resetpassword <token> <newpassword> - to reset"));
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
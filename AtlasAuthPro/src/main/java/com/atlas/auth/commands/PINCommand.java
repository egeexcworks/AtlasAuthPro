package com.atlas.auth.commands;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.managers.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PINCommand implements CommandExecutor {
    private final AuthManager authManager;
    
    public PINCommand(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length != 1) {
            String usageMsg = AtlasAuthPro.getInstance().getConfig()
                .getString("messages-v2.pin-login-usage", "&7Usage: &e/pin <your_pin>");
            player.sendMessage(colorize(usageMsg));
            return true;
        }
        
        String pin = args[0];
        authManager.loginWithPIN(player, pin);
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

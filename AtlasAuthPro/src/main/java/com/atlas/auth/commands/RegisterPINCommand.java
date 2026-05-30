package com.atlas.auth.commands;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.managers.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterPINCommand implements CommandExecutor {
    private final AuthManager authManager;
    
    public RegisterPINCommand(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if player is logged in
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(colorize("&cYou must be logged in to set a PIN!"));
            return true;
        }
        
        if (args.length != 2) {
            String usageMsg = AtlasAuthPro.getInstance().getConfig()
                .getString("messages-v2.registerpin-usage", "&7Usage: &e/registerpin <your_password> <new_pin>");
            player.sendMessage(colorize(usageMsg));
            return true;
        }
        
        String password = args[0];
        String pin = args[1];
        
        // Set PIN
        authManager.setPIN(player, password, pin);
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

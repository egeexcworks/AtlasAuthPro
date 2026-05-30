package com.atlas.auth.commands;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.managers.AuthManager;
import com.atlas.auth.managers.PlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {
    private final AuthManager authManager;
    private final PlayerManager playerManager;
    
    public RegisterCommand(AuthManager authManager, PlayerManager playerManager) {
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
        
        if (args.length != 2) {
            // Get custom message from config if available
            String usageMsg = AtlasAuthPro.getInstance().getConfig()
                .getString("messages-v2.register-usage", "&7Usage: &e/register <password> <confirm> &7| Alias: &e/reg <password> <confirm>");
            player.sendMessage(colorize(usageMsg));
            return true;
        }
        
        String password = args[0];
        String confirm = args[1];
        
        authManager.register(player, password, confirm);
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

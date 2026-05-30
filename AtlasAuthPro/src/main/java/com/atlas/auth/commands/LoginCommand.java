package com.atlas.auth.commands;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.managers.AuthManager;
import com.atlas.auth.managers.PlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {
    private final AuthManager authManager;
    private final PlayerManager playerManager;
    
    public LoginCommand(AuthManager authManager, PlayerManager playerManager) {
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
        
        // Check if player is already logged in
        if (authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(colorize("&aYou are already logged in!"));
            return true;
        }
        
        // Check if player has pending 2FA verification
        if (authManager.isPending2FAVerification(player.getUniqueId())) {
            // Player is in 2FA verification mode, redirect to verify
            player.sendMessage(colorize("&cYou have a pending 2FA verification!"));
            player.sendMessage(colorize("&ePlease check your Discord DM and enter: &a/2fa verify <code>"));
            return true;
        }
        
        if (args.length != 1) {
            // Get custom message from config if available
            String usageMsg = AtlasAuthPro.getInstance().getConfig()
                .getString("messages-v2.login-usage", "&7Usage: &e/login <password> &7| Alias: &e/l <password>");
            player.sendMessage(colorize(usageMsg));
            return true;
        }
        
        String password = args[0];
        
        // Attempt login
        boolean passwordVerified = authManager.login(player, password);
        
        if (passwordVerified) {
            // Password was correct, now check if Discord 2FA is enabled for this player
            if (authManager.isDiscord2FAEnabled() && authManager.isTwoFactorEnabled(player.getUniqueId())) {
                // Initiate Discord 2FA
                // This will remove the player from logged in state and require 2FA verification
                boolean sent = authManager.initiateDiscord2FA(player);
                
                if (!sent) {
                    // Failed to send 2FA code, but still log them in (fail-open for convenience)
                    // Alternatively, you could deny login entirely
                    player.sendMessage(colorize("&eWarning: Could not send 2FA code. You are logged in but 2FA may not work."));
                }
                // Return true here - login was "successful" but requires 2FA
                return true;
            }
            // No 2FA, normal login complete
        }
        
        return true;
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}


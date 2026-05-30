package com.atlas.auth.commands;

import com.atlas.auth.bot.DiscordBot;
import com.atlas.auth.managers.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Two-Factor Authentication command (Discord DM-based)
 * 
 * Flow:
 * - /2fa - Show 2FA status and help
 * - /2fa link <discord_username> - Generate code and send to Discord DM
 * - /2fa verify <code> - Verify the code to enable 2FA
 * - /2fa disable - Disable 2FA (requires password)
 */
public class TwoFactorCommand implements CommandExecutor {
    private final AuthManager authManager;
    
    public TwoFactorCommand(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if Discord 2FA is enabled in config
        if (!authManager.isDiscord2FAEnabled()) {
            player.sendMessage(colorize("&cDiscord 2FA is not enabled on this server."));
            player.sendMessage(colorize("&7Ask an admin to enable it in config.yml"));
            return true;
        }
        
        if (args.length == 0) {
            // Show 2FA status and help
            showTwoFactorStatus(player);
            return true;
        }
        
        String action = args[0].toLowerCase();
        
        switch (action) {
            case "link":
            case "add":
            case "enable":
                if (args.length < 2) {
                    player.sendMessage(colorize("&cUsage: /2fa link <discord_username>"));
                    player.sendMessage(colorize("&7Example: /2fa link egeexc_2"));
                    player.sendMessage(colorize("&7Your Discord DM will receive a verification code."));
                    return true;
                }
                String discordUsername = args[1];
                authManager.initiateTwoFactorSetup(player, discordUsername);
                break;
                
            case "verify":
            case "confirm":
            case "code":
                if (args.length < 2) {
                    player.sendMessage(colorize("&cUsage: /2fa verify <code>"));
                    player.sendMessage(colorize("&7Check your Discord DM for the code!"));
                    return true;
                }
                String code = args[1];
                authManager.verifyTwoFactorSetup(player, code);
                break;
                
            case "disable":
            case "remove":
            case "delete":
                if (args.length < 2) {
                    player.sendMessage(colorize("&cUsage: /2fa disable <password>"));
                    return true;
                }
                String password = args[1];
                authManager.unlinkDiscordAccount(player, password);
                break;
                
            case "status":
            case "info":
                showTwoFactorStatus(player);
                break;
                
            case "help":
            default:
                showTwoFactorHelp(player);
                break;
        }
        
        return true;
    }
    
    private void showTwoFactorStatus(Player player) {
        boolean twoFactorEnabled = authManager.isTwoFactorEnabled(player.getUniqueId());
        String discordId = authManager.getDiscordId(player.getUniqueId());
        
        player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        player.sendMessage(colorize("&3&lDISCORD TWO-FACTOR AUTHENTICATION"));
        player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        player.sendMessage(colorize("&7Status: " + (twoFactorEnabled ? "&aEnabled" : "&cDisabled")));
        
        if (discordId != null && !discordId.isEmpty()) {
            player.sendMessage(colorize("&7Linked Discord ID: &e" + discordId));
        }
        
        if (!twoFactorEnabled) {
            player.sendMessage(colorize("&eTo enable 2FA, use: &a/2fa link <discord_username>"));
            player.sendMessage(colorize("&7Example: &a/2fa link egeexc_2"));
        } else {
            player.sendMessage(colorize("&a✔ Your account is protected with Discord 2FA!"));
            player.sendMessage(colorize("&eTo disable, use: &a/2fa disable <password>"));
        }
        
        // Check Discord bot status
        DiscordBot bot = DiscordBot.getInstance();
        if (bot != null && bot.isConnected()) {
            player.sendMessage(colorize("&a✔ Discord bot is connected"));
        } else {
            player.sendMessage(colorize("&c✖ Discord bot is not connected"));
        }
        
        player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        player.sendMessage(colorize("&7Type &a/2fa help &7for more commands"));
    }
    
    private void showTwoFactorHelp(Player player) {
        player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        player.sendMessage(colorize("&3&lDISCORD 2FA HELP"));
        player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        player.sendMessage(colorize("&e/2fa link <username> &7- Link Discord & enable 2FA"));
        player.sendMessage(colorize("&e/2fa verify <code> &7- Verify code from Discord DM"));
        player.sendMessage(colorize("&e/2fa disable <password> &7- Disable 2FA"));
        player.sendMessage(colorize("&e/2fa status &7- View 2FA status"));
        player.sendMessage(colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        
        // How it works
        player.sendMessage(colorize("&3&lHow to setup:"));
        player.sendMessage(colorize("&71. Type &a/2fa link <your_discord_username>"));
        player.sendMessage(colorize("&7   Example: &a/2fa link egeexc_2"));
        player.sendMessage(colorize("&72. Check your Discord DM for the verification code"));
        player.sendMessage(colorize("&73. Type &a/2fa verify <code> &7in Minecraft"));
        player.sendMessage(colorize("&74. Done! Your account is now protected."));
        
        // Check Discord bot status
        DiscordBot bot = DiscordBot.getInstance();
        if (bot != null && bot.isConnected()) {
            player.sendMessage(colorize("&a✔ Discord bot is connected"));
        } else {
            player.sendMessage(colorize("&c✖ Discord bot is not connected"));
        }
    }
    
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}


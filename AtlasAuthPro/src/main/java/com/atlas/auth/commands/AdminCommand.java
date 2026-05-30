package com.atlas.auth.commands;

import com.atlas.auth.AtlasAuthPro;
import com.atlas.auth.database.DatabaseManager;
import com.atlas.auth.gui.AdminGUI;
import com.atlas.auth.managers.AuthManager;
import com.atlas.auth.migration.MigrationManager;
import com.atlas.auth.migration.NLoginDetector;
import com.atlas.auth.migration.OpenLoginDetector;
import com.atlas.auth.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminCommand implements CommandExecutor {
    private final AuthManager authManager;
    private final DatabaseManager databaseManager;
    private final AdminGUI adminGUI;
    private final MigrationManager migrationManager;
    
    public AdminCommand(AuthManager authManager, DatabaseManager databaseManager) {
        this.authManager = authManager;
        this.databaseManager = databaseManager;
        this.adminGUI = new AdminGUI(authManager, databaseManager);
        this.migrationManager = AtlasAuthPro.getInstance().getMigrationManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check permission
        if (!player.hasPermission("atlasauth.admin")) {
            player.sendMessage(Utils.colorize("&cYou need the &eatlasauth.admin &cpermission!"));
            return true;
        }
        
        if (args.length == 0) {
            // Open GUI admin panel
            adminGUI.openMainMenu(player);
            return true;
        }
        
        String subcmd = args[0].toLowerCase();
        
        switch (subcmd) {
            case "forcereset":
                if (args.length < 2) {
                    player.sendMessage(Utils.colorize("&cUsage: /admin forcereset <player>"));
                    return true;
                }
                String targetName = args[1];
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) {
                    authManager.adminResetPassword(target.getUniqueId());
                    player.sendMessage(Utils.colorize("&aPassword reset for &e" + targetName));
                } else {
                    player.sendMessage(Utils.colorize("&cPlayer not found or offline!"));
                }
                break;
                
            case "forcelogout":
                if (args.length < 2) {
                    player.sendMessage(Utils.colorize("&cUsage: /admin forcelogout <player>"));
                    return true;
                }
                String logoutName = args[1];
                Player logoutTarget = Bukkit.getPlayer(logoutName);
                if (logoutTarget != null) {
                    authManager.adminForceLogout(logoutTarget.getUniqueId());
                    player.sendMessage(Utils.colorize("&aForce logged out &e" + logoutName));
                } else {
                    player.sendMessage(Utils.colorize("&cPlayer not found or offline!"));
                }
                break;
                
            // Discord 2FA Admin Commands
            case "reset2fa":
                if (args.length < 2) {
                    player.sendMessage(Utils.colorize("&cUsage: /admin reset2fa <player>"));
                    return true;
                }
                String reset2faName = args[1];
                handleReset2FA(player, reset2faName);
                break;
                
            case "tokens":
                handleListTokens(player);
                break;
                
            case "view":
                if (args.length < 2) {
                    player.sendMessage(Utils.colorize("&cUsage: /admin view <player>"));
                    return true;
                }
                String viewName = args[1];
                Player viewTarget = Bukkit.getPlayer(viewName);
                if (viewTarget != null) {
                    
                    player.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                    player.sendMessage(Utils.colorize("&aPlayer Info: &e" + viewTarget.getName()));
                    player.sendMessage(Utils.colorize("&7UUID: &f" + viewTarget.getUniqueId()));
                    player.sendMessage(Utils.colorize("&7IP: &f" + viewTarget.getAddress()));
                    player.sendMessage(Utils.colorize("&7Logged in: " + 
                        (authManager.isLoggedIn(viewTarget.getUniqueId()) ? "&aYes" : "&cNo")));
                    // Show 2FA status
                    if (authManager.isDiscord2FAEnabled()) {
                        player.sendMessage(Utils.colorize("&7Discord 2FA: " + 
                            (authManager.isTwoFactorEnabled(viewTarget.getUniqueId()) ? "&aEnabled" : "&cDisabled")));
                        String discordId = authManager.getDiscordId(viewTarget.getUniqueId());
                        if (discordId != null && !discordId.isEmpty()) {
                            player.sendMessage(Utils.colorize("&7Linked Discord: &e" + discordId));
                        }
                    }
                    player.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                } else {
                    player.sendMessage(Utils.colorize("&cPlayer not found or offline!"));
                }
                break;
                
            case "reload":
                Bukkit.getPluginManager().getPlugin("AtlasAuthPro").reloadConfig();
                player.sendMessage(Utils.colorize("&aConfig reloaded!"));
                break;
                
            case "list":
                adminGUI.openPlayerList(player, 0);
                break;
                
            case "stats":
                adminGUI.openStatistics(player);
                break;
                
            case "migrate":
                handleMigrationCommand(player, args);
                break;
                
            case "detect":
                handleMultiDetectCommand(player);
                break;
                
            default:
                player.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                player.sendMessage(Utils.colorize("&3&lAtlas Auth Admin"));
                player.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                player.sendMessage(Utils.colorize("&e/admin &7- GUI panel"));
                player.sendMessage(Utils.colorize("&e/admin forcereset <player> &7- Reset pw"));
                player.sendMessage(Utils.colorize("&e/admin forcelogout <player> &7- Force logout"));
                player.sendMessage(Utils.colorize("&e/admin reset2fa <player> &7- Reset 2FA"));
                player.sendMessage(Utils.colorize("&e/admin tokens &7- 2FA tokens (debug)"));
                player.sendMessage(Utils.colorize("&e/admin view <player> &7- Player info"));
                player.sendMessage(Utils.colorize("&e/admin list &7- Player list"));
                player.sendMessage(Utils.colorize("&e/admin stats &7- Statistics"));
                player.sendMessage(Utils.colorize("&e/admin reload &7- Reload config"));
                player.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
                break;
        }
        
        return true;
    }
    
    private void handleReset2FA(Player admin, String targetName) {
        Player targetPlayer = Bukkit.getPlayer(targetName);
        
        if (targetPlayer != null) {
            UUID targetUuid = targetPlayer.getUniqueId();
            if (authManager.adminReset2FA(targetUuid)) {
                admin.sendMessage(Utils.colorize("&a✔ Reset Discord 2FA for &e" + targetName));
                admin.sendMessage(Utils.colorize("&7Player force logged out."));
                
                targetPlayer.sendMessage(Utils.colorize("&c⚠ Discord 2FA reset by admin!"));
                targetPlayer.sendMessage(Utils.colorize("&7Re-link: &a/2fa link <discord_id>"));
            } else {
                admin.sendMessage(Utils.colorize("&c✖ Failed to reset 2FA for &e" + targetName));
            }
        } else {
            boolean found = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(targetName)) {
                    UUID uuid = p.getUniqueId();
                    if (authManager.adminReset2FA(uuid)) {
                        admin.sendMessage(Utils.colorize("&a✔ Reset Discord 2FA for &e" + targetName));
                        admin.sendMessage(Utils.colorize("&7Player force logged out."));
                        p.sendMessage(Utils.colorize("&c⚠ Discord 2FA reset by admin!"));
                    } else {
                        admin.sendMessage(Utils.colorize("&c✖ Failed to reset 2FA for &e" + targetName));
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                admin.sendMessage(Utils.colorize("&cPlayer &e" + targetName + " &cnot found."));
            }
        }
    }
    
    private void handleListTokens(Player admin) {
        AtlasAuthPro plugin = AtlasAuthPro.getInstance();
        if (!plugin.getConfig().getBoolean("debug.show-2fa-tokens", false)) {
            admin.sendMessage(Utils.colorize("&cToken listing disabled. Enable debug.show-2fa-tokens."));
            return;
        }
        if (!authManager.isDiscord2FAEnabled()) {
            admin.sendMessage(Utils.colorize("&cDiscord 2FA not enabled."));
            return;
        }
        List<Map<String, Object>> tokens = authManager.getActive2FATokens();
        admin.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        admin.sendMessage(Utils.colorize("&3&lActive 2FA Tokens (DEBUG)"));
        admin.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        if (tokens.isEmpty()) {
            admin.sendMessage(Utils.colorize("&7No active tokens."));
        } else {
            admin.sendMessage(Utils.colorize("&7Total: &e" + tokens.size()));
            for (Map<String, Object> token : tokens) {
                String username = (String) token.get("username");
                String uuid = (String) token.get("user_uuid");
                admin.sendMessage(Utils.colorize("&e" + username + " &7(" + uuid.substring(0, 8) + "...)"));
            }
        }
        admin.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
    }
    
    private void handleMigrationCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
            player.sendMessage(Utils.colorize("&3&lMigration"));
            player.sendMessage(Utils.colorize("&8&m⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
            player.sendMessage(Utils.colorize("&e/start &7- Start"));
            player.sendMessage(Utils.colorize("&e/dryrun &7- Preview"));
            player.sendMessage(Utils.colorize("&e/cancel"));
            player.sendMessage(Utils.colorize("&e/detect"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "start":
                if (migrationManager.isMigrationInProgress()) {
                    player.sendMessage(Utils.colorize("&cMigration in progress!"));
                } else {
                    migrationManager.startMigration(player, false);
                }
                break;
            case "dryrun":
            case "preview":
                if (migrationManager.isMigrationInProgress()) {
                    player.sendMessage(Utils.colorize("&cMigration in progress!"));
                } else {
                    migrationManager.startMigration(player, true);
                }
                break;
            case "cancel":
                if (!migrationManager.isMigrationInProgress()) {
                    player.sendMessage(Utils.colorize("&cNo migration in progress."));
                } else {
                    migrationManager.cancelMigration();
                    player.sendMessage(Utils.colorize("&aMigration cancelled!"));
                }
                break;
            default:
                player.sendMessage(Utils.colorize("&cUnknown: " + action));
        }
    }
    
    private void handleMultiDetectCommand(Player player) {
        player.sendMessage(Utils.colorize("&e🔍 Detecting auth plugins..."));
        Bukkit.getScheduler().runTaskAsynchronously(AtlasAuthPro.getInstance(), () -> {
            NLoginDetector nlogin = new NLoginDetector();
            nlogin.detect();
            OpenLoginDetector openlogin = new OpenLoginDetector();
            openlogin.detect();
            
            Bukkit.getScheduler().runTask(AtlasAuthPro.getInstance(), () -> {
                if (nlogin.isFound()) {
                    player.sendMessage(Utils.colorize("&a✓ nLogin/OpeNLogin found! &7(" + nlogin.getPlayerCount() + " players)"));
                } else {
                    player.sendMessage(Utils.colorize("&7⨯ nLogin not found"));
                }
                
                if (openlogin.isFound()) {
                    player.sendMessage(Utils.colorize("&a✓ OpenLogin/OpeNLogin found! &7(" + openlogin.getPlayerCount() + " players)"));
                } else {
                    player.sendMessage(Utils.colorize("&7⨯ OpenLogin not found"));
                }
                
                player.sendMessage(Utils.colorize("&eUse: &a/admin migrate auto| nlogin| openlogin"));
                nlogin.close();
                openlogin.close();
            });
        });
    }
}


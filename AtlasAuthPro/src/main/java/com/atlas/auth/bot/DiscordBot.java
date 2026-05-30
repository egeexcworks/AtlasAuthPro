package com.atlas.auth.bot;

import com.atlas.auth.AtlasAuthPro;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discord Bot Integration for AtlasAuthPro 2FA
 * 
 * This class handles:
 * - Connecting to Discord using a bot token
 * - Sending 2FA verification codes to a verification channel
 * - Managing Discord user ID lookups
 * 
 * To create a bot:
 * 1. Go to https://discord.com/developers/applications
 * 2. Create a new application
 * 3. Go to "Bot" and create a bot
 * 4. Copy the bot token
 * 5. Enable "Server Members Intent" in Bot settings
 * 6. Invite the bot to your server with proper permissions
 * 
 * @author AtlasAuth Team
 */
public class DiscordBot {
    
    private static DiscordBot instance;
    private JDA jda;
    private boolean isConnected = false;
    private String verifyChannelId = null;
    private final Map<String, String> pendingDiscordLinks = new ConcurrentHashMap<>(); // Minecraft UUID -> Discord ID
    private final Map<String, Long> sentMessages = new ConcurrentHashMap<>(); // playerName -> messageId for cleanup
    
    /**
     * Get the singleton instance of DiscordBot
     * @return DiscordBot instance
     */
    public static DiscordBot getInstance() {
        return instance;
    }
    
    /**
     * Initialize and start the Discord bot
     * @return true if successfully connected
     */
    public boolean initialize() {
        // Get token from config
        String token = AtlasAuthPro.getInstance().getConfig()
            .getString("discord-2fa.bot-token", "");
        
        // Check if Discord 2FA is enabled
        boolean enabled = AtlasAuthPro.getInstance().getConfig()
            .getBoolean("discord-2fa.enabled", false);
        
        if (!enabled) {
            AtlasAuthPro.getInstance().getPluginLogger().info("Discord 2FA is disabled in config");
            return false;
        }
        
        if (token == null || token.isEmpty()) {
            AtlasAuthPro.getInstance().getPluginLogger().severe("Discord bot token not configured! Add 'discord-2fa.bot-token' in config.yml");
            return false;
        }
        
        // Get verify channel ID from config
        verifyChannelId = AtlasAuthPro.getInstance().getConfig()
            .getString("discord-2fa.verify-channel-id", "");
        
        try {
            // Build JDA with necessary intents
            jda = JDABuilder.createDefault(token)
                .build()
                .awaitReady();
            
            isConnected = true;
            instance = this;
            
            AtlasAuthPro.getInstance().getPluginLogger().info("§aDiscord bot connected successfully!");
            AtlasAuthPro.getInstance().getPluginLogger().info("§7Bot username: " + jda.getSelfUser().getName());
            
            // Validate channel if configured
            if (verifyChannelId != null && !verifyChannelId.isEmpty()) {
                TextChannel channel = jda.getTextChannelById(verifyChannelId);
                if (channel != null) {
                    AtlasAuthPro.getInstance().getPluginLogger().info("§7Verification channel: " + channel.getName());
                } else {
                    AtlasAuthPro.getInstance().getPluginLogger().warning("§cVerification channel not found! Check the channel ID.");
                }
            } else {
                AtlasAuthPro.getInstance().getPluginLogger().warning("§cNo verification channel configured! Add 'discord-2fa.verify-channel-id' in config.yml");
            }
            
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AtlasAuthPro.getInstance().getPluginLogger().severe("§cDiscord bot initialization interrupted");
            return false;
        } catch (Exception e) {
            AtlasAuthPro.getInstance().getPluginLogger().severe("§cFailed to initialize Discord bot: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send 2FA verification token to the configured Discord verification channel
     * 
     * @param playerName The Minecraft player name
     * @param token The 2FA verification token
     * @param expirySeconds How many seconds until the token expires
     * @return true if message was sent successfully
     */
    public boolean sendVerificationToChannel(String playerName, String token, int expirySeconds) {
        if (!isConnected || jda == null) {
            AtlasAuthPro.getInstance().getPluginLogger().warning("Cannot send 2FA code: Discord bot not connected");
            return false;
        }
        
        // Get channel ID from config
        String channelId = AtlasAuthPro.getInstance().getConfig()
            .getString("discord-2fa.verify-channel-id", "");
        
        if (channelId == null || channelId.isEmpty()) {
            AtlasAuthPro.getInstance().getPluginLogger().severe("Verification channel not configured!");
            return false;
        }
        
        try {
            // Get the text channel
            TextChannel channel = jda.getTextChannelById(channelId);
            
            if (channel == null) {
                AtlasAuthPro.getInstance().getPluginLogger().severe("Could not find verification channel: " + channelId);
                return false;
            }
            
            // Build the verification message
            String message = buildVerificationMessage(playerName, token, expirySeconds);
            
            // Send message to channel
            Message sentMessage = channel.sendMessage(message).complete();
            
            // Store message ID for potential cleanup
            sentMessages.put(playerName, sentMessage.getIdLong());
            
            AtlasAuthPro.getInstance().getPluginLogger().info("[AtlasAuth] Sent Discord 2FA token for player " + playerName);
            return true;
            
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                AtlasAuthPro.getInstance().getPluginLogger().severe("Bot lacks permission to send messages in verification channel!");
            } else {
                AtlasAuthPro.getInstance().getPluginLogger().warning("Error sending to verification channel: " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            AtlasAuthPro.getInstance().getPluginLogger().warning("Error sending 2FA code to channel: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Build the verification message with the specified format
     */
    private String buildVerificationMessage(String playerName, String token, int expirySeconds) {
        return "🔐 **AtlasAuth Verification**\n\n" +
               "**Player:** " + playerName + "\n" +
               "**Token:** `" + token + "`\n\n" +
               "Use this command in Minecraft:\n" +
               "`/2fa verify " + token + "`\n\n" +
               "⏱ Expires in " + expirySeconds + " seconds.";
    }
    
    /**
     * Delete a verification message after successful authentication
     * 
     * @param playerName The Minecraft player name
     * @return true if message was deleted successfully
     */
    public boolean deleteVerificationMessage(String playerName) {
        Long messageId = sentMessages.remove(playerName);
        
        if (messageId == null) {
            return false;
        }
        
        // Get channel ID from config
        String channelId = AtlasAuthPro.getInstance().getConfig()
            .getString("discord-2fa.verify-channel-id", "");
        
        if (channelId == null || channelId.isEmpty()) {
            return false;
        }
        
        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.deleteMessageById(messageId).queue();
                AtlasAuthPro.getInstance().getPluginLogger().info("[AtlasAuth] Deleted verification message for player " + playerName);
                return true;
            }
        } catch (Exception e) {
            AtlasAuthPro.getInstance().getPluginLogger().warning("Error deleting verification message: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Send a 2FA verification code to a Discord user via DM (legacy method)
     * 
     * @param discordId The Discord user ID to send the message to
     * @param playerName The Minecraft player name (for display)
     * @param token The 2FA token to send
     * @param expirySeconds How many seconds until the token expires
     * @return true if message was sent successfully
     */
    public boolean send2FACode(String discordId, String playerName, String token, int expirySeconds) {
        if (!isConnected || jda == null) {
            AtlasAuthPro.getInstance().getPluginLogger().warning("Cannot send 2FA code: Discord bot not connected");
            return false;
        }
        
        try {
            // Retrieve the user by ID
            User user = jda.retrieveUserById(discordId).complete();
            
            if (user == null) {
                AtlasAuthPro.getInstance().getPluginLogger().warning("Could not find Discord user with ID: " + discordId);
                return false;
            }
            
            // Get DM message template from config
            String dmMessage = AtlasAuthPro.getInstance().getConfig()
                .getString("discord-2fa.dm-message", 
                    "Your Minecraft login verification code is: %token%\nThis code expires in %expiry% seconds.");
            
            // Replace placeholders
            dmMessage = dmMessage.replace("%player%", playerName)
                                 .replace("%token%", token)
                                 .replace("%expiry%", String.valueOf(expirySeconds));
            
            // Send DM to user
            user.openPrivateChannel().complete()
                .sendMessage(dmMessage).complete();
            
            AtlasAuthPro.getInstance().getPluginLogger().info("2FA code sent to Discord user: " + discordId + " (Minecraft: " + playerName + ")");
            return true;
            
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.CANNOT_SEND_TO_USER) {
                AtlasAuthPro.getInstance().getPluginLogger().warning("Cannot send DM to user " + discordId + ": User has DMs disabled");
            } else {
                AtlasAuthPro.getInstance().getPluginLogger().warning("Error sending DM to user " + discordId + ": " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            AtlasAuthPro.getInstance().getPluginLogger().warning("Error sending 2FA code: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate a Discord user ID format
     * Discord IDs are 17-19 digit numbers
     * 
     * @param discordId The Discord ID to validate
     * @return true if valid format
     */
    public boolean isValidDiscordId(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            return false;
        }
        
        // Discord IDs are numeric and 17-19 digits long
        return discordId.matches("^\\d{17,19}$");
    }
    
    /**
     * Check if a Discord user exists and is reachable
     * 
     * @param discordId The Discord ID to check
     * @return true if user exists
     */
    public boolean doesDiscordUserExist(String discordId) {
        if (!isConnected || jda == null) {
            return false;
        }
        
        try {
            User user = jda.retrieveUserById(discordId).complete();
            return user != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Add a pending Discord link request
     * Used when player initiates linking process
     * 
     * @param mcUuid Minecraft player UUID
     * @param discordId Discord ID to link
     */
    public void addPendingLink(String mcUuid, String discordId) {
        pendingDiscordLinks.put(mcUuid, discordId);
    }
    
    /**
     * Remove a pending Discord link request
     * 
     * @param mcUuid Minecraft player UUID
     */
    public void removePendingLink(String mcUuid) {
        pendingDiscordLinks.remove(mcUuid);
    }
    
    /**
     * Get a pending Discord link
     * 
     * @param mcUuid Minecraft player UUID
     * @return Discord ID or null if not pending
     */
    public String getPendingLink(String mcUuid) {
        return pendingDiscordLinks.get(mcUuid);
    }
    
    /**
     * Check if Discord bot is connected
     * 
     * @return true if connected
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Check if verification channel is configured
     * 
     * @return true if channel is configured
     */
    public boolean isChannelConfigured() {
        return verifyChannelId != null && !verifyChannelId.isEmpty();
    }
    
    /**
     * Shutdown the Discord bot
     */
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            isConnected = false;
            instance = null;
            AtlasAuthPro.getInstance().getPluginLogger().info("Discord bot disconnected");
        }
    }
    
    /**
     * Get the JDA instance (for advanced operations)
     * 
     * @return JDA instance or null if not connected
     */
    public JDA getJDA() {
        return jda;
    }
}

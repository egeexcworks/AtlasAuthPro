package com.atlas.auth.utils;

import org.bukkit.ChatColor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Common utility methods for AtlasAuthPro
 */
public class Utils {
    
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)&[0-9a-fk-or]");
    
    /**
     * Colorize string with Bukkit color codes
     * @param message Message to colorize
     * @return Colorized message
     */
    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Strip color codes from string
     * @param message Message to strip
     * @return Message without colors
     */
    public static String stripColor(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(colorize(message));
    }
    
    /**
     * Efficient placeholder replacement
     * @param message Original message
     * @param placeholders Placeholders map
     * @return Message with placeholders replaced
     */
    public static String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String replacement = entry.getValue() != null ? entry.getValue() : "";
            int lastIndex = 0;
            while ((lastIndex = sb.indexOf(placeholder, lastIndex)) != -1) {
                sb.replace(lastIndex, lastIndex + placeholder.length(), replacement);
                lastIndex += replacement.length();
            }
        }
        return sb.toString();
    }
    
    /**
     * Validate password strength
     * @param password Password to validate
     * @param minLength Min length
     * @param requireSpecial Require special chars
     * @param requireNumbers Require numbers
     * @return Strength score 0-100
     */
    public static int getPasswordStrength(String password, int minLength, boolean requireSpecial, boolean requireNumbers) {
        if (password == null || password.length() < minLength) return 0;
        
        int score = 0;
        int lengthScore = Math.min(100, password.length() * 10);
        score += lengthScore;
        
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasNumber = Pattern.compile("[0-9]").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[^A-Za-z0-9]").matcher(password).find();
        
        if (hasUpper) score += 15;
        if (hasLower) score += 15;
        if (hasNumber) score += 20;
        if (hasSpecial) score += 20;
        
        // No repeats
        long uniqueChars = password.chars().distinct().count();
        score += (int) (uniqueChars * 2);
        
        return Math.min(100, score);
    }
    
    /**
     * Generate secure random string
     * @param length Length of string
     * @return Random string
     */
    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789abcdefghijkmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(length);
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * Is valid UUID string
     * @param uuidStr UUID string
     * @return true if valid
     */
    public static boolean isValidUUID(String uuidStr) {
        try {
            java.util.UUID.fromString(uuidStr);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Is valid IP address
     * @param ip IP string
     * @return true if valid IPv4/IPv6
     */
    public static boolean isValidIP(String ip) {
        return Pattern.compile("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$").matcher(ip).matches();
    }
}


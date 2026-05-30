package com.atlas.auth.migration;

import com.atlas.auth.AtlasAuthPro;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * NLoginDetector - Auto-detects nLogin database installation
 * Supports both SQLite and MySQL database types
 */
public class NLoginDetector extends DatabaseDetector {
    private final Logger logger;
    private Connection nLoginConnection;
    private String databaseType;
    private String tableName;
    private final Map<String, String> columnMapping = new HashMap<>();
    private boolean nLoginFound = false;
    
    // Common table names used by nLogin
    private static final String[] COMMON_TABLE_NAMES = {
        "nlogin_users", "nlogin_accounts", "players", "accounts", "users"
    };
    
    // Common column names used by nLogin variants
    private static final List<String> UUID_COLUMNS = Arrays.asList(
        "uuid", "player_uuid", "owner", "playerid", "player_uuid"
    );
    private static final List<String> PASSWORD_COLUMNS = Arrays.asList(
        "password", "pass", "pw", "password_hash", "pwd"
    );
    private static final List<String> IP_COLUMNS = Arrays.asList(
        "ip", "ip_address", "lastip", "last_ip", "ipaddress"
    );
    private static final List<String> NAME_COLUMNS = Arrays.asList(
        "name", "username", "player", "playername", "user"
    );
    
public NLoginDetector() {
        super("nLogin");
        this.logger = AtlasAuthPro.getInstance().getLogger();
    }
    
    /**
     * Attempt to detect nLogin database
     * @return true if nLogin was found
     */
    public boolean detect() {
        logger.info("§eStarting nLogin/OpeNLogin database detection...");
        
        File pluginFolder = findAuthPluginFolder();
        if (pluginFolder != null) {
            logger.info("§aFound auth plugin folder: " + pluginFolder.getAbsolutePath());
            File configFile = new File(pluginFolder, "config.yml");
            if (configFile.exists()) {
                logger.info("§anLogin config file found!");
                if (detectFromConfig(configFile)) {
                    nLoginFound = true;
                    return true;
                }
            }
            
            if (detectSQLite(pluginFolder)) {
                nLoginFound = true;
                return true;
            }
        } else {
            logger.info("§7No nLogin/OpenLogin plugin folder found in plugins directory");
        }
        
        logger.warning("§cnLogin database not found!");
        return false;
    }
    
    private File findAuthPluginFolder() {
        File pluginsDir = new File("plugins");
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return null;
        }

        File[] folders = pluginsDir.listFiles(File::isDirectory);
        if (folders == null) {
            return null;
        }

        // Prefer exact nLogin / OpeNLogin / OpenLogin folders
        for (File folder : folders) {
            String name = folder.getName().toLowerCase();
            if (name.equals("nlogin") || name.equals("openlogin") || name.equals("opelogin")) {
                return folder;
            }
        }

        // Fallback to any folder containing login-like keywords
        for (File folder : folders) {
            String name = folder.getName().toLowerCase();
            if (name.contains("nlogin") || name.contains("openlogin") || name.contains("login")) {
                return folder;
            }
        }

        // Recursive search for nested folders that look like auth plugins
        File recursiveMatch = findAuthPluginFolderRecursive(pluginsDir);
        if (recursiveMatch != null) {
            return recursiveMatch;
        }

        // Fallback to any folder containing a likely auth database file
        return findDatabaseFolderInPlugins(pluginsDir);
    }

    private File findAuthPluginFolderRecursive(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName().toLowerCase();
                if (name.contains("nlogin") || name.contains("openlogin") || name.contains("login")) {
                    return file;
                }

                File found = findAuthPluginFolderRecursive(file);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private File findDatabaseFolderInPlugins(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findDatabaseFolderInPlugins(file);
                if (found != null) {
                    return found;
                }
            } else if (isPotentialAuthDatabaseFile(file)) {
                return directory;
            }
        }

        return null;
    }

    private boolean isPotentialAuthDatabaseFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String lower = file.getName().toLowerCase();
        return lower.equals("nlogin.db") || lower.equals("nlogin.sqlite") || lower.equals("nlogin.sqlite3") ||
               lower.equals("accounts.db") || lower.equals("account.db") || lower.equals("users.db") ||
               lower.equals("openlogin.db") || lower.equals("database.db") || lower.equals("auth.db") ||
               lower.equals("accounts.sqlite") || lower.equals("users.sqlite") || lower.equals("auth.sqlite");
    }

    private boolean detectSQLite(File nLoginFolder) {
        // Try common SQLite database filenames first
        String[] dbFiles = {"nlogin.db", "nlogin.sqlite", "nlogin.sqlite3", "database.db", "accounts.db", "account.db", "users.db", "accounts.sqlite", "users.sqlite", "auth.db", "auth.sqlite"};
        for (String dbFile : dbFiles) {
            File db = new File(nLoginFolder, dbFile);
            if (db.exists() && db.length() > 0) {
                if (connectToSQLite(db)) {
                    return true;
                }
            }
        }

        // Recursively search for any SQLite database file
        for (File db : scanForDatabaseFiles(nLoginFolder)) {
            if (connectToSQLite(db)) {
                return true;
            }
        }

        return false;
    }

    private boolean connectToSQLite(File dbFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            nLoginConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            databaseType = "SQLite";
            logger.info("§aFound nLogin SQLite database: " + dbFile.getName());
            return detectTable();
        } catch (Exception e) {
            logger.warning("§cFailed to open " + dbFile.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private List<File> scanForDatabaseFiles(File folder) {
        List<File> dbFiles = new ArrayList<>();
        collectDatabaseFiles(folder, dbFiles);
        return dbFiles;
    }

    private void collectDatabaseFiles(File folder, List<File> dbFiles) {
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectDatabaseFiles(file, dbFiles);
            } else if (isDatabaseFile(file)) {
                dbFiles.add(file);
            }
        }
    }

    private boolean isDatabaseFile(File file) {
        String lower = file.getName().toLowerCase();
        return lower.endsWith(".db") || lower.endsWith(".sqlite") || lower.endsWith(".sqlite3");
    }

    /**
     * Detect nLogin from config file
     */
    private boolean detectFromConfig(File configFile) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dbType = config.getString("database.type", "sqlite");
            
            if (dbType.equalsIgnoreCase("mysql") || dbType.equalsIgnoreCase("mariadb")) {
                return detectMySQL(config);
            } else {
                return detectSQLiteFromConfig(configFile.getParentFile(), config);
            }
        } catch (Exception e) {
            logger.warning("§cError reading nLogin config: " + e.getMessage());
            return detectSQLite(configFile.getParentFile());
        }
    }
    
    /**
     * Detect nLogin MySQL database
     */
    private boolean detectMySQL(FileConfiguration config) {
        try {
            String host = config.getString("database.host", "localhost");
            int port = config.getInt("database.port", 3306);
            String database = config.getString("database.database", "nlogin");
            String username = config.getString("database.username", "root");
            String password = config.getString("database.password", "");
            
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
            nLoginConnection = DriverManager.getConnection(url, username, password);
            
            databaseType = "MySQL";
            logger.info("§aConnected to nLogin MySQL database!");
            
            return detectTable();
        } catch (Exception e) {
            logger.warning("§cFailed to connect to nLogin MySQL: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Detect nLogin SQLite database from config
     */
    private boolean detectSQLiteFromConfig(File folder, FileConfiguration config) {
        String dbFile = config.getString("database.file", "nlogin.db");
        return detectSQLite(new File(folder, dbFile));
    }
    
    private boolean detectTable() {
        try {
            DatabaseMetaData meta = nLoginConnection.getMetaData();
            
            // Try common table names first
            for (String tableName : COMMON_TABLE_NAMES) {
                ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"});
                if (rs.next()) {
                    this.tableName = tableName;
                    logger.info("§aFound nLogin table: " + tableName);
                    return detectColumns(meta);
                }
            }
            
            // If no common names found, search all tables
            ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"});
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (isLikelyAuthTable(tableName)) {
                    this.tableName = tableName;
                    logger.info("§aFound potential nLogin table: " + tableName);
                    return detectColumns(meta);
                }
            }
            
            logger.warning("§cCould not find nLogin user table!");
            return false;
        } catch (SQLException e) {
            logger.warning("§cError detecting table: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if table name is likely an auth table
     */
    private boolean isLikelyAuthTable(String tableName) {
        String lower = tableName.toLowerCase();
        return lower.contains("login") || lower.contains("user") || 
               lower.contains("account") || lower.contains("player");
    }
    
    /**
     * Detect column structure
     */
    private boolean detectColumns(DatabaseMetaData meta) {
        try {
            ResultSet columns = meta.getColumns(null, null, tableName, null);
            List<String> foundColumns = new ArrayList<>();
            
            while (columns.next()) {
                foundColumns.add(columns.getString("COLUMN_NAME").toLowerCase());
            }
            
            // Map columns to standard names
            mapColumns(foundColumns);
            
            // Check for essential columns
            if (!columnMapping.containsKey("uuid") && !columnMapping.containsKey("password")) {
                logger.warning("§cCould not find required columns (uuid, password)!");
                return false;
            }
            
            logger.info("§aColumn mapping: " + columnMapping);
            nLoginFound = true;
            return true;
            
        } catch (SQLException e) {
            logger.warning("§cError detecting columns: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Map found columns to standard names
     */
    private void mapColumns(List<String> columns) {
        columnMapping.clear();
        
        for (String col : columns) {
            // UUID column
            if (col.equals("uuid") || col.equals("player_uuid") || col.equals("owner") || 
                col.equals("playerid") || col.equals("player_uuid")) {
                columnMapping.put("uuid", col);
            }
            // Password column
            else if (col.equals("password") || col.equals("pass") || col.equals("pw") || 
                     col.equals("password_hash") || col.equals("pwd")) {
                columnMapping.put("password", col);
            }
            // IP column
            else if (col.equals("ip") || col.equals("ip_address") || col.equals("lastip") ||
                     col.equals("last_ip") || col.equals("ipaddress")) {
                columnMapping.put("ip", col);
            }
            // Last login column
            else if (col.equals("lastlogin") || col.equals("last_login") || col.equals("lastlogin") ||
                     col.equals("lastjoin") || col.equals("last_join")) {
                columnMapping.put("lastlogin", col);
            }
            // Username column
            else if (col.equals("name") || col.equals("username") || col.equals("player") ||
                     col.equals("playername") || col.equals("user")) {
                columnMapping.put("name", col);
            }
            // Email column
            else if (col.equals("email") || col.equals("mail") || col.equals("e-mail")) {
                columnMapping.put("email", col);
            }
            // Registration date
            else if (col.equals("regdate") || col.equals("registerdate") || col.equals("registered") ||
                     col.equals("reg_date") || col.equals("created")) {
                columnMapping.put("regdate", col);
            }
        }
    }
    
    /**
     * Get all player data from nLogin
     */
    public List<Map<String, Object>> getAllPlayers() {
        List<Map<String, Object>> players = new ArrayList<>();
        
        if (!nLoginFound || nLoginConnection == null) {
            logger.warning("§cnLogin database not detected!");
            return players;
        }
        
        try {
            // Build dynamic query based on detected columns
            StringBuilder sql = new StringBuilder("SELECT * FROM ");
            sql.append(tableName);
            
            Statement stmt = nLoginConnection.createStatement();
            ResultSet rs = stmt.executeQuery(sql.toString());
            
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> playerData = new HashMap<>();
                
                for (int i = 1; i <= columnCount; i++) {
                    String colName = meta.getColumnName(i);
                    playerData.put(colName, rs.getObject(i));
                }
                
                players.add(playerData);
            }
            
            logger.info("§aFound " + players.size() + " players in nLogin database!");
            
        } catch (SQLException e) {
            logger.warning("§cError reading nLogin data: " + e.getMessage());
        }
        
        return players;
    }
    
    /**
     * Get player count from nLogin
     */
    public int getPlayerCount() {
        if (!nLoginFound || nLoginConnection == null) {
            return 0;
        }
        
        try {
            Statement stmt = nLoginConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.warning("§cError counting nLogin players: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Check if a player exists in nLogin
     */
    public boolean playerExists(String uuid) {
        if (!nLoginFound || nLoginConnection == null) {
            return false;
        }
        
        String uuidCol = columnMapping.get("uuid");
        if (uuidCol == null) {
            uuidCol = "uuid"; // fallback
        }
        
        try {
            PreparedStatement ps = nLoginConnection.prepareStatement(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + uuidCol + " = ?"
            );
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.warning("§cError checking player existence: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get password hash for a player
     */
    public String getPasswordHash(String uuid) {
        if (!nLoginFound || nLoginConnection == null) {
            return null;
        }
        
        String uuidCol = columnMapping.get("uuid");
        String passCol = columnMapping.get("password");
        
        if (uuidCol == null || passCol == null) {
            return null;
        }
        
        try {
            PreparedStatement ps = nLoginConnection.prepareStatement(
                "SELECT " + passCol + " FROM " + tableName + " WHERE " + uuidCol + " = ?"
            );
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.warning("§cError getting password hash: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Close connection
     */
    public void close() {
        try {
            if (nLoginConnection != null && !nLoginConnection.isClosed()) {
                nLoginConnection.close();
            }
        } catch (SQLException e) {
            logger.warning("§cError closing nLogin connection: " + e.getMessage());
        }
    }
    
    // Getters
    @Override
    public boolean isFound() {
        return nLoginFound;
    }
    
    public String getDatabaseType() {
        return databaseType;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public Map<String, String> getColumnMapping() {
        return columnMapping;
    }
    
    public Connection getConnection() {
        return nLoginConnection;
    }
}

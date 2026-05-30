package com.atlas.auth.migration;

import com.atlas.auth.AtlasAuthPro;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * OpenLoginDetector - Auto-detects OpenLogin & OpeNLogin database installation
 */
public class OpenLoginDetector extends DatabaseDetector {

    private final Logger logger;
    private Connection connection;
    private String databaseType;
    private String tableName;
    private final Map<String, String> columnMapping = new HashMap<>();
    private boolean found = false;

    private static final String[] TABLE_NAMES = {"accounts", "users", "openlogin_users", "players", "auth_users"};
    private static final String[] DB_FILES = {"accounts.db", "database.db", "openlogin.db", "users.db", "auth.db"};

public OpenLoginDetector() {
        super("OpenLogin");
        logger = AtlasAuthPro.getInstance().getLogger();
    }

    public boolean detect() {
        logger.info("§eDetecting OpenLogin/OpeNLogin...");
        
        File pluginFolder = findOpenLoginFolder();
        if (pluginFolder != null) {
            logger.info("§aFound " + pluginFolder.getName() + " folder!");
            if (detectFromFolder(pluginFolder)) {
                found = true;
                return true;
            }
        } else {
            logger.info("§7No OpenLogin/OpeNLogin plugin folder found in plugins directory");
        }
        logger.warning("§cNo OpenLogin/OpeNLogin found");
        return false;
    }

    private File findOpenLoginFolder() {
        File pluginsDir = new File("plugins");
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return null;
        }

        File[] folders = pluginsDir.listFiles(File::isDirectory);
        if (folders == null) {
            return null;
        }

        // Prefer exact OpenLogin / OpeNLogin folders
        for (File folder : folders) {
            String name = folder.getName().toLowerCase();
            if (name.equals("openlogin") || name.equals("opelogin") || name.equals("nlogin")) {
                return folder;
            }
        }

        // Fallback to any folder containing login-like names
        for (File folder : folders) {
            String name = folder.getName().toLowerCase();
            if (name.contains("openlogin") || name.contains("opelogin") || name.contains("nlogin") || name.contains("login")) {
                return folder;
            }
        }

        // Recursive search for nested folders that look like auth plugins
        File recursiveMatch = findOpenLoginFolderRecursive(pluginsDir);
        if (recursiveMatch != null) {
            return recursiveMatch;
        }

        // Fallback to any folder containing a likely auth database file
        return findDatabaseFolderInPlugins(pluginsDir);
    }

    private File findOpenLoginFolderRecursive(File directory) {
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
                if (name.contains("openlogin") || name.contains("opelogin") || name.contains("nlogin") || name.contains("login")) {
                    return file;
                }

                File found = findOpenLoginFolderRecursive(file);
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
        return lower.equals("accounts.db") || lower.equals("database.db") || lower.equals("openlogin.db") ||
               lower.equals("users.db") || lower.equals("auth.db") || lower.equals("accounts.sqlite") ||
               lower.equals("users.sqlite") || lower.equals("auth.sqlite") || lower.equals("openlogin.sqlite") ||
               lower.equals("nlogin.db") || lower.equals("nlogin.sqlite") || lower.equals("nlogin.sqlite3");
    }

    private boolean detectFromFolder(File folder) {
        File configFile = new File(folder, "config.yml");
        if (configFile.exists()) {
            return detectFromConfig(folder, configFile);
        }
        return detectSQLite(folder);
    }

    private boolean detectFromConfig(File folder, File configFile) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dbType = config.getString("storage.type", "").toLowerCase();
            
            // Check if MySQL is configured
            if (dbType.equalsIgnoreCase("mysql")) {
                logger.info("§7Config specifies MySQL storage");
                return detectMySQL(config);
            }
            
            // OpeNLogin might not have storage config, try config file settings first
            if (!dbType.isEmpty()) {
                return detectSQLiteFromConfig(folder, config);
            }
            
            // If no storage config, try common database files in folder
            logger.info("§7No storage config found, scanning folder for database files...");
            return detectSQLite(folder);
        } catch (Exception e) {
            logger.warning("§cConfig error: " + e.getMessage());
            // Fallback to scanning folder
            return detectSQLite(folder);
        }
    }

    private boolean detectSQLiteFromConfig(File folder, FileConfiguration config) {
        String dbFile = config.getString("storage.file", "");
        if (!dbFile.isEmpty()) {
            File dbFilePath = new File(folder, dbFile);
            logger.info("§7Trying config database file: " + dbFilePath.getName());
            return detectSQLite(dbFilePath);
        }
        // If no file specified, try common names
        return detectSQLite(folder);
    }

    private boolean detectSQLite(File dbFileOrFolder) {
        // If it's a folder, scan for database files
        if (dbFileOrFolder.isDirectory()) {
            logger.info("§7Scanning folder for database files: " + dbFileOrFolder.getName());
            
            // Try common database filenames first
            String[] commonNames = {"accounts.db", "database.db", "openlogin.db", "users.db", "auth.db", "data.db",
                                     "accounts.sqlite", "users.sqlite", "auth.sqlite", "openlogin.sqlite",
                                     "nlogin.db", "nlogin.sqlite", "nlogin.sqlite3"};
            for (String dbName : commonNames) {
                File dbFile = new File(dbFileOrFolder, dbName);
                if (dbFile.exists() && dbFile.length() > 0) {
                    logger.info("§7Found potential database: " + dbName);
                    if (connectAndDetect(dbFile)) {
                        return true;
                    }
                }
            }
            
            // Scan all .db/.sqlite/.sqlite3 files recursively
            List<File> foundFiles = scanForDatabaseFiles(dbFileOrFolder);
            if (!foundFiles.isEmpty()) {
                logger.info("§7Found " + foundFiles.size() + " database file(s) in folder");
                for (File dbFile : foundFiles) {
                    if (connectAndDetect(dbFile)) {
                        return true;
                    }
                }
            }
            
            logger.warning("§cNo valid database files found in folder");
            return false;
        } else {
            // It's a file, try to connect directly
            return connectAndDetect(dbFileOrFolder);
        }
    }
    
    private List<File> scanForDatabaseFiles(File folder) {
        List<File> foundFiles = new ArrayList<>();
        collectDatabaseFiles(folder, foundFiles);
        return foundFiles;
    }

    private void collectDatabaseFiles(File directory, List<File> foundFiles) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectDatabaseFiles(file, foundFiles);
            } else if (isDatabaseFile(file)) {
                foundFiles.add(file);
            }
        }
    }

    private boolean isDatabaseFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3");
    }
    
    private boolean connectAndDetect(File dbFile) {
        if (!dbFile.exists() || dbFile.length() == 0) {
            logger.fine("§7Skipping: " + dbFile.getName() + " (empty or not found)");
            return false;
        }
        
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            databaseType = "SQLite";
            logger.info("§a✓ Connected to SQLite: " + dbFile.getName());
            
            if (detectTable()) {
                logger.info("§a✓ Database validated with table: " + tableName);
                return true;
            }
        } catch (Exception e) {
            logger.fine("§7Failed to connect to " + dbFile.getName() + ": " + e.getMessage());
        }
        return false;
    }

    private boolean detectMySQL(FileConfiguration config) {
        try {
            String host = config.getString("storage.host", "localhost");
            int port = config.getInt("storage.port", 3306);
            String database = config.getString("storage.database", "openlogin");
            String username = config.getString("storage.username", "root");
            String password = config.getString("storage.password", "");
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
            connection = DriverManager.getConnection(url, username, password);
            databaseType = "MySQL";
            logger.info("§aMySQL connected");
            return detectTable();
        } catch (Exception e) {
            logger.warning("§cMySQL error: " + e.getMessage());
            return false;
        }
    }

    private boolean detectTable() {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            List<String> allTables = new ArrayList<>();
            
            // Get all tables
            ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"});
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                allTables.add(table);
            }
            
            logger.info("§7Found " + allTables.size() + " table(s) in database: " + allTables);
            
            // Try common table names first
            for (String table : TABLE_NAMES) {
                if (allTables.contains(table)) {
                    tableName = table;
                    logger.info("§a✓ Found table: " + table);
                    return detectColumns(meta);
                }
            }
            
            // Try to find any auth-like table
            for (String table : allTables) {
                if (isAuthTable(table)) {
                    tableName = table;
                    logger.info("§a✓ Found potential auth table: " + table);
                    return detectColumns(meta);
                }
            }
            
            logger.warning("§cNo auth table found. Tables: " + allTables);
            return false;
        } catch (SQLException e) {
            logger.warning("§cTable detection error: " + e.getMessage());
            return false;
        }
    }

    private boolean isAuthTable(String table) {
        return table.toLowerCase().matches(".*(user|account|player|login).*");
    }

    private boolean detectColumns(DatabaseMetaData meta) {
        try {
            ResultSet cols = meta.getColumns(null, null, tableName, "%");
            List<String> foundCols = new ArrayList<>();
            while (cols.next()) {
                foundCols.add(cols.getString("COLUMN_NAME").toLowerCase());
            }
            
            logger.info("§7Columns found in " + tableName + ": " + foundCols);
            
            mapColumns(foundCols);
            
            // OpeNLogin might not have UUID - that's OK if we have username and password
            if (columnMapping.containsKey("password")) {
                if (columnMapping.containsKey("uuid")) {
                    logger.info("§a✓ Database has UUID column: " + columnMapping.get("uuid"));
                } else if (columnMapping.containsKey("name")) {
                    logger.info("§a✓ Database has username column (OpeNLogin mode): " + columnMapping.get("name"));
                } else {
                    logger.warning("§cNo valid identifier column (UUID or name) found!");
                    return false;
                }
                
                logger.info("§a✓ Required password column: " + columnMapping.get("password"));
                return true;
            }
            
            logger.warning("§cMissing required password column. Mapped: " + columnMapping);
            return false;
        } catch (SQLException e) {
            logger.warning("§cColumn detection error: " + e.getMessage());
            return false;
        }
    }

    private void mapColumns(List<String> cols) {
        columnMapping.clear();
        for (String col : cols) {
            String colLower = col.toLowerCase();
            // UUID - OpeNLogin/OpenLogin common (but OpeNLogin might not have this!)
            if (colLower.equals("uuid") || colLower.equals("player_uuid") || 
                colLower.equals("user_uuid") || colLower.equals("playerid") || 
                colLower.equals("id") || colLower.equals("player") || 
                colLower.equals("uniqueid") || colLower.equals("unique_id")) {
                columnMapping.put("uuid", col);
            } 
            // Password - common formats
            else if (colLower.equals("password") || colLower.equals("pass_hash") || 
                     colLower.equals("password_hash") || colLower.equals("pass") || 
                     colLower.equals("pw") || colLower.equals("passhash") ||
                     colLower.equals("pwd") || colLower.equals("hashed_password")) {
                columnMapping.put("password", col);
            } 
            // IP/Last IP
            else if (colLower.equals("ip") || colLower.equals("ip_address") || 
                     colLower.equals("last_ip") || colLower.equals("lastip") ||
                     colLower.equals("last_address") || colLower.equals("ipaddress") ||
                     colLower.equals("address")) {
                columnMapping.put("ip", col);
            } 
            // Username
            else if (colLower.equals("username") || colLower.equals("name") || 
                     colLower.equals("player") || colLower.equals("user") ||
                     colLower.equals("playername") || colLower.equals("player_name")) {
                columnMapping.put("name", col);
            }
            // Email
            else if (colLower.equals("email") || colLower.equals("mail") ||
                     colLower.equals("email_address")) {
                columnMapping.put("email", col);
            }
            // Real name (OpeNLogin specific)
            else if (colLower.equals("realname") || colLower.equals("real_name") ||
                     colLower.equals("actual_name")) {
                columnMapping.put("realname", col);
            }
        }
        logger.info("§7OpenLogin column mapping: " + columnMapping);
    }


    public List<Map<String, Object>> getAllPlayers() {
        List<Map<String, Object>> players = new ArrayList<>();
        if (!found || connection == null) return players;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    data.put(meta.getColumnName(i), rs.getObject(i));
                }
                players.add(data);
            }
        } catch (SQLException e) {
            logger.warning("§cPlayers error: " + e.getMessage());
        }
        logger.info("§a" + players.size() + " players loaded");
        return players;
    }

    public int getPlayerCount() {
        if (!found || connection == null) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            logger.warning("§cCount error: " + e.getMessage());
            return 0;
        }
    }

    public boolean playerExists(String uuid) {
        if (!found || connection == null) return false;
        String uuidCol = columnMapping.getOrDefault("uuid", "uuid");
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM " + tableName + " WHERE " + uuidCol + " = ?")) {
            ps.setString(1, uuid);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            logger.warning("§cExists error: " + e.getMessage());
            return false;
        }
    }

    public String getPasswordHash(String uuid) {
        if (!found || connection == null) return null;
        String uuidCol = columnMapping.getOrDefault("uuid", "uuid");
        String passCol = columnMapping.getOrDefault("password", "password");
        try (PreparedStatement ps = connection.prepareStatement("SELECT " + passCol + " FROM " + tableName + " WHERE " + uuidCol + " = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            logger.warning("§cHash error: " + e.getMessage());
            return null;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("§cConnection close error: " + e.getMessage());
        }
    }

    @Override
    public boolean isFound() {
        return found;
    }

    public boolean isOpenLoginFound() {
        return found;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getTableName() {
        return tableName;
    }

    public Connection getConnection() {
        return connection;
    }

    public Map<String, String> getColumnMapping() {
        return new HashMap<>(columnMapping);
    }
}



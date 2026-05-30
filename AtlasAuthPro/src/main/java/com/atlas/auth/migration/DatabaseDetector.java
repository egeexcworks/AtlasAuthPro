package com.atlas.auth.migration;

import com.atlas.auth.AtlasAuthPro;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * DatabaseDetector - Modular database detection for auth plugins
 * Dynamically scans folders for .db/.sqlite/.sqlite3 files
 */
public class DatabaseDetector {
    private final Logger logger;
    private Connection connection;
    private String databaseType;
    private String tableName;
    private final Map<String, String> columnMapping = new HashMap<>();
    private boolean found = false;
    private String databasePath;
    private String pluginName;

    private static final String[] PLUGIN_FOLDERS = {"OpenLogin", "OpeNLogin", "nLogin"};
    private static final String[] DB_EXTENSIONS = {".db", ".sqlite", ".sqlite3"};

public DatabaseDetector() {
        this.logger = AtlasAuthPro.getInstance().getLogger();
        this.pluginName = "AuthPlugin";
    }

    public DatabaseDetector(String pluginName) {
        this.logger = AtlasAuthPro.getInstance().getLogger();
        this.pluginName = pluginName;
    }

    public boolean detect() {
        logger.info("§e[DEBUG] Scanning for " + pluginName + " database...");
        
        for (String folderName : PLUGIN_FOLDERS) {
            File pluginFolder = new File("plugins/" + folderName);
            if (pluginFolder.exists() && pluginFolder.isDirectory()) {
                logger.info("§aFound " + folderName + " folder: " + pluginFolder.getAbsolutePath());
                
                if (detectFromFolder(pluginFolder, folderName)) {
                    this.pluginName = folderName;
                    found = true;
                    return true;
                }
            } else {
                logger.info("§7" + folderName + " folder not found");
            }
        }
        
        logger.warning("§cNo " + pluginName + " database found");
        return false;
    }

    private boolean detectFromFolder(File folder, String folderName) {
        logger.info("§7Scanning folder contents...");
        
        // Try config first
        File configFile = new File(folder, "config.yml");
        if (configFile.exists()) {
            logger.info("§aConfig found: " + configFile.getName());
            if (detectFromConfig(folder, configFile)) {
                return true;
            }
        }

        // Dynamic DB file scan
        return scanDatabaseFiles(folder);
    }

    private boolean detectFromConfig(File folder, File configFile) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String dbType = config.getString("storage.type", "sqlite").toLowerCase();
            logger.info("§7Config DB type: " + dbType);
            
            if (dbType.equals("mysql")) {
                return detectMySQL(config);
            } else {
                String dbFile = config.getString("storage.file");
                if (dbFile != null && !dbFile.isEmpty()) {
                    logger.info("§7Config DB file: " + dbFile);
                    File dbFileObj = new File(folder, dbFile);
                    if (dbFileObj.exists()) {
                        return detectSQLite(dbFileObj);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("§cConfig parse error: " + e.getMessage());
        }
        return false;
    }

    private boolean scanDatabaseFiles(File folder) {
        logger.info("§7Scanning for database files (.db, .sqlite, .sqlite3)...");
        
        // Recursive scan
        File[] files = findDatabaseFiles(folder);
        if (files == null || files.length == 0) {
            logger.info("§7No database files found in folder (recursive)");
            return false;
        }

        logger.info("§aFound " + files.length + " database file(s):");
        for (File dbFile : files) {
            logger.info("§7Scanning: " + dbFile.getAbsolutePath());
            if (detectSQLite(dbFile)) {
                return true;
            }
        }
        return false;
    }

    private File[] findDatabaseFiles(File startDir) {
        List<File> dbFiles = new ArrayList<>();
        findDatabaseFilesRecursive(startDir, dbFiles);
        return dbFiles.toArray(new File[0]);
    }

    private void findDatabaseFilesRecursive(File dir, List<File> dbFiles) {
        if (!dir.isDirectory()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                findDatabaseFilesRecursive(file, dbFiles);
            } else if (isDatabaseFile(file)) {
                dbFiles.add(file);
            }
        }
    }

    private boolean isDatabaseFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3");
    }

    private boolean detectSQLite(File dbFile) {
        if (!dbFile.exists() || dbFile.length() == 0) {
            logger.info("§7Skipping empty DB: " + dbFile.getName());
            return false;
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            databaseType = "SQLite";
            databasePath = dbFile.getAbsolutePath();
            logger.info("§a✓ Connected to SQLite: " + dbFile.getName());
            
            if (detectTable()) {
                logger.info("§a✓ " + pluginName + " database validated: " + tableName + " table");
                return true;
            }
        } catch (Exception e) {
            logger.warning("§cSQLite connection failed for " + dbFile.getName() + ": " + e.getMessage());
        }
        return false;
    }

    private boolean detectMySQL(FileConfiguration config) {
        try {
            String host = config.getString("storage.host", "localhost");
            int port = config.getInt("storage.port", 3306);
            String database = config.getString("storage.database", pluginName.toLowerCase());
            String username = config.getString("storage.username");
            String password = config.getString("storage.password", "");
            
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
            connection = DriverManager.getConnection(url, username, password);
            databaseType = "MySQL";
            logger.info("§a✓ MySQL connected to " + database);
            return detectTable();
        } catch (Exception e) {
            logger.warning("§cMySQL connection failed: " + e.getMessage());
            return false;
        }
    }

    private boolean detectTable() {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            
            // Common auth tables
            String[] authTables = {"accounts", "users", "players", "auth_users", pluginName.toLowerCase() + "_users"};
            for (String table : authTables) {
                ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"});
                if (rs.next()) {
                    tableName = table;
                    logger.info("§a✓ Table found: " + table);
                    return detectColumns(meta);
                }
            }

            // Scan all tables
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                if (isAuthTable(table)) {
                    tableName = table;
                    logger.info("§a✓ Auth table detected: " + table);
                    return detectColumns(meta);
                }
            }
            logger.warning("§cNo auth table found");
            return false;
        } catch (SQLException e) {
            logger.warning("§cTable detection failed: " + e.getMessage());
            return false;
        }
    }

    private boolean detectColumns(DatabaseMetaData meta) {
        try {
            ResultSet cols = meta.getColumns(null, null, tableName, "%");
            List<String> foundCols = new ArrayList<>();
            while (cols.next()) {
                foundCols.add(cols.getString("COLUMN_NAME").toLowerCase());
            }
            
            mapColumns(foundCols);
            logger.info("§7Found columns: " + foundCols);
            logger.info("§7Column mapping: " + columnMapping);
            
            boolean hasUUID = columnMapping.containsKey("uuid");
            boolean hasPassword = columnMapping.containsKey("password");
            
            if (hasUUID && hasPassword) {
                logger.info("§a✓ Valid auth database confirmed!");
                return true;
            } else {
                logger.warning("§cMissing essential columns - UUID: " + hasUUID + ", Password: " + hasPassword);
            }
        } catch (SQLException e) {
            logger.warning("§cColumn detection failed: " + e.getMessage());
        }
        return false;
    }

    private boolean isAuthTable(String table) {
        String lower = table.toLowerCase();
        return lower.contains("account") || lower.contains("user") || 
               lower.contains("player") || lower.contains("auth") || 
               lower.contains("login") || lower.contains("pass");
    }

    private void mapColumns(List<String> cols) {
        columnMapping.clear();
        for (String col : cols) {
            if (col.matches("(uuid|player_uuid|user_uuid|playerid|id|player)")) {
                columnMapping.putIfAbsent("uuid", col);
            }
            if (col.matches("(password|pass(_?hash)?|pw?|pwd)")) {
                columnMapping.putIfAbsent("password", col);
            }
            if (col.matches("(ip|ip_address|last_ip?)")) {
                columnMapping.putIfAbsent("ip", col);
            }
            if (col.matches("(name|username|player_name)")) {
                columnMapping.putIfAbsent("name", col);
            }
        }
    }

    // Getters
    public boolean isFound() {
        return found;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getTableName() {
        return tableName;
    }

    public Map<String, String> getColumnMapping() {
        return new HashMap<>(columnMapping);
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public Connection getConnection() {
        return connection;
    }

    public List<Map<String, Object>> getAllPlayers() {
        if (!found || connection == null) return new ArrayList<>();
        
        List<Map<String, Object>> players = new ArrayList<>();
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
            logger.info("§aLoaded " + players.size() + " players from " + tableName);
        } catch (SQLException e) {
            logger.warning("§cQuery error: " + e.getMessage());
        }
        return players;
    }

    public int getPlayerCount() {
        if (!found || connection == null) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rs.next();
            return rs.getInt(1);
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
            ResultSet rs = ps.executeQuery();
            return rs.next();
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
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.warning("§cHash error: " + e.getMessage());
        }
        return null;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("§cClose error: " + e.getMessage());
        }
    }
}


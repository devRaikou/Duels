package me.raikou.duels.storage;

import me.raikou.duels.DuelsPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MySQLStorage implements Storage {

    private final DuelsPlugin plugin;
    private Connection connection;
    private final String host, database, username, password;
    private final int port;

    public MySQLStorage(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        this.database = plugin.getConfig().getString("storage.mysql.database", "duels");
        this.username = plugin.getConfig().getString("storage.mysql.username", "root");
        this.password = plugin.getConfig().getString("storage.mysql.password", "");
    }

    @Override
    public void connect() {
        try {
            synchronized (this) {
                if (connection != null && !connection.isClosed())
                    return;
                connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username,
                        password);
                plugin.getLogger().info("Connected to MySQL!");
                createTable();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to MySQL: " + e.getMessage());
        }
    }

    private void createTable() {
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS duel_stats (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "wins INT DEFAULT 0, " +
                        "losses INT DEFAULT 0, " +
                        "kills INT DEFAULT 0, " +
                        "deaths INT DEFAULT 0)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS kit_layouts (" +
                        "uuid VARCHAR(36), " +
                        "kit_name VARCHAR(64), " +
                        "layout_data TEXT, " +
                        "PRIMARY KEY (uuid, kit_name))")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS elo_ratings (" +
                        "uuid VARCHAR(36), " +
                        "kit_name VARCHAR(64), " +
                        "elo INT DEFAULT 1000, " +
                        "PRIMARY KEY (uuid, kit_name))")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public CompletableFuture<Void> saveUser(UUID uuid, int wins, int losses, int kills, int deaths) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO duel_stats (uuid, wins, losses, kills, deaths) VALUES (?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE wins=?, losses=?, kills=?, deaths=?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, wins);
                ps.setInt(3, losses);
                ps.setInt(4, kills);
                ps.setInt(5, deaths);
                ps.setInt(6, wins);
                ps.setInt(7, losses);
                ps.setInt(8, kills);
                ps.setInt(9, deaths);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<int[]> loadUser(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM duel_stats WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new int[] {
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("kills"),
                            rs.getInt("deaths")
                    };
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new int[] { 0, 0, 0, 0 };
        });
    }

    @Override
    public CompletableFuture<Void> saveKitLayout(UUID uuid, String kitName, String layoutData) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO kit_layouts (uuid, kit_name, layout_data) VALUES (?,?,?) " +
                            "ON DUPLICATE KEY UPDATE layout_data=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                ps.setString(3, layoutData);
                ps.setString(4, layoutData);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<String> loadKitLayout(UUID uuid, String kitName) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection
                    .prepareStatement("SELECT layout_data FROM kit_layouts WHERE uuid=? AND kit_name=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("layout_data");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> loadElo(UUID uuid, String kitName) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection
                    .prepareStatement("SELECT elo FROM elo_ratings WHERE uuid=? AND kit_name=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt("elo");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 1000; // Default starting ELO
        });
    }

    @Override
    public CompletableFuture<Void> saveElo(UUID uuid, String kitName, int elo) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO elo_ratings (uuid, kit_name, elo) VALUES (?,?,?) " +
                            "ON DUPLICATE KEY UPDATE elo=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                ps.setInt(3, elo);
                ps.setInt(4, elo);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}

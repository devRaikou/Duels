package me.raikou.duels.storage;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.leaderboard.LeaderboardEntry;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLiteStorage implements Storage {

    private final DuelsPlugin plugin;
    private Connection connection;

    public SQLiteStorage(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "database.db");
            if (!dataFolder.exists()) {
                try {
                    dataFolder.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create database.db!");
                }
            }

            // Explicitly load driver to ensure it's found (Paper usually shades it, but
            // good practice)
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                // Driver likely auto-loaded
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder.getAbsolutePath());
            plugin.getLogger().info("Connected to SQLite!");
            createTable();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to SQLite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTable() {
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS duel_stats (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "name VARCHAR(32) DEFAULT '', " +
                        "wins INT DEFAULT 0, " +
                        "losses INT DEFAULT 0, " +
                        "kills INT DEFAULT 0, " +
                        "deaths INT DEFAULT 0)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Add name column if it doesn't exist (migration)
        try (PreparedStatement ps = connection.prepareStatement(
                "ALTER TABLE duel_stats ADD COLUMN name VARCHAR(32) DEFAULT ''")) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Column already exists
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
    public CompletableFuture<Void> saveUser(UUID uuid, String name, int wins, int losses, int kills, int deaths) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO duel_stats (uuid, name, wins, losses, kills, deaths) VALUES (?,?,?,?,?,?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET name=?, wins=?, losses=?, kills=?, deaths=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setInt(3, wins);
                ps.setInt(4, losses);
                ps.setInt(5, kills);
                ps.setInt(6, deaths);
                ps.setString(7, name);
                ps.setInt(8, wins);
                ps.setInt(9, losses);
                ps.setInt(10, kills);
                ps.setInt(11, deaths);
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
                            "ON CONFLICT(uuid, kit_name) DO UPDATE SET layout_data=?")) {
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
                            "ON CONFLICT(uuid, kit_name) DO UPDATE SET elo=?")) {
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

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, name, wins, losses, kills, deaths FROM duel_stats ORDER BY wins DESC LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    entries.add(new LeaderboardEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("kills"),
                            rs.getInt("deaths")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return entries;
        });
    }
}

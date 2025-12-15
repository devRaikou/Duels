package me.raikou.duels.storage;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.leaderboard.LeaderboardEntry;
import me.raikou.duels.stats.PlayerStats;

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
        // Main stats table with extended fields
        try (PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS duel_stats (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "name VARCHAR(32) DEFAULT '', " +
                        "wins INT DEFAULT 0, " +
                        "losses INT DEFAULT 0, " +
                        "kills INT DEFAULT 0, " +
                        "deaths INT DEFAULT 0, " +
                        "current_streak INT DEFAULT 0, " +
                        "best_streak INT DEFAULT 0, " +
                        "last_played BIGINT DEFAULT 0, " +
                        "playtime BIGINT DEFAULT 0)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Migration: Add new columns if they don't exist
        addColumnIfNotExists("duel_stats", "name", "VARCHAR(32) DEFAULT ''");
        addColumnIfNotExists("duel_stats", "current_streak", "INT DEFAULT 0");
        addColumnIfNotExists("duel_stats", "best_streak", "INT DEFAULT 0");
        addColumnIfNotExists("duel_stats", "last_played", "BIGINT DEFAULT 0");
        addColumnIfNotExists("duel_stats", "playtime", "BIGINT DEFAULT 0");

        // Kit layouts table
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

        // ELO ratings table
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

    private void addColumnIfNotExists(String table, String column, String definition) {
        try (PreparedStatement ps = connection.prepareStatement(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Column already exists
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
    public CompletableFuture<Void> saveUser(UUID uuid, String name, PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO duel_stats (uuid, name, wins, losses, kills, deaths, current_streak, best_streak, last_played, playtime) "
                            +
                            "VALUES (?,?,?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET " +
                            "name=?, wins=?, losses=?, kills=?, deaths=?, current_streak=?, best_streak=?, last_played=?, playtime=?")) {
                // Insert values
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setInt(3, stats.getWins());
                ps.setInt(4, stats.getLosses());
                ps.setInt(5, stats.getKills());
                ps.setInt(6, stats.getDeaths());
                ps.setInt(7, stats.getCurrentStreak());
                ps.setInt(8, stats.getBestStreak());
                ps.setLong(9, stats.getLastPlayed());
                ps.setLong(10, stats.getPlaytime());
                // Update values
                ps.setString(11, name);
                ps.setInt(12, stats.getWins());
                ps.setInt(13, stats.getLosses());
                ps.setInt(14, stats.getKills());
                ps.setInt(15, stats.getDeaths());
                ps.setInt(16, stats.getCurrentStreak());
                ps.setInt(17, stats.getBestStreak());
                ps.setLong(18, stats.getLastPlayed());
                ps.setLong(19, stats.getPlaytime());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<PlayerStats> loadUserStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT wins, losses, kills, deaths, current_streak, best_streak, last_played, playtime " +
                            "FROM duel_stats WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new PlayerStats(
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("current_streak"),
                            rs.getInt("best_streak"),
                            rs.getLong("last_played"),
                            rs.getLong("playtime"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0);
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
            // Order by total games first (so all players who played appear), then by wins
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, name, wins, losses, kills, deaths, current_streak, best_streak, last_played, playtime "
                            +
                            "FROM duel_stats " +
                            "WHERE (wins + losses) > 0 " +
                            "ORDER BY wins DESC, (wins + losses) DESC, last_played DESC " +
                            "LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    entries.add(new LeaderboardEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("current_streak"),
                            rs.getInt("best_streak"),
                            rs.getLong("last_played"),
                            rs.getLong("playtime")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<Integer> getTotalPlayerCount() {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) as count FROM duel_stats WHERE (wins + losses) > 0")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt("count");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        });
    }

    @Override
    public CompletableFuture<Integer> getPlayerRank(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) + 1 as rank FROM duel_stats " +
                            "WHERE wins > (SELECT wins FROM duel_stats WHERE uuid = ?) " +
                            "OR (wins = (SELECT wins FROM duel_stats WHERE uuid = ?) " +
                            "AND (wins + losses) > (SELECT wins + losses FROM duel_stats WHERE uuid = ?))")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, uuid.toString());
                ps.setString(3, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt("rank");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return -1; // Not found
        });
    }
}

package me.raikou.duels.storage;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.leaderboard.LeaderboardEntry;
import me.raikou.duels.stats.PlayerStats;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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

        // Punishments table
        try (
                PreparedStatement ps = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS duels_punishments (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "player_name VARCHAR(32), " +
                                "issuer_name VARCHAR(32), " +
                                "type VARCHAR(16) NOT NULL, " +
                                "reason TEXT, " +
                                "timestamp BIGINT, " +
                                "duration BIGINT, " +
                                "active BOOLEAN, " +
                                "removed BOOLEAN, " +
                                "removed_by VARCHAR(32), " +
                                "removed_reason TEXT)")) {
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
                            "ON DUPLICATE KEY UPDATE " +
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
                // If column likely missing in old schema, try fetching without playtime or just
                // fail gracefully
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

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            // Order by wins first, then total games, supports all players who played
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
    public CompletableFuture<Void> savePunishment(me.raikou.duels.punishment.Punishment punishment) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO duels_punishments (uuid, player_name, issuer_name, type, reason, timestamp, duration, active, removed, removed_by, removed_reason) "
                            +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, punishment.getUuid().toString());
                ps.setString(2, punishment.getPlayerName());
                ps.setString(3, punishment.getIssuerName());
                ps.setString(4, punishment.getType().name());
                ps.setString(5, punishment.getReason());
                ps.setLong(6, punishment.getTimestamp());
                ps.setLong(7, punishment.getDuration());
                ps.setBoolean(8, punishment.isActive());
                ps.setBoolean(9, punishment.isRemoved());
                ps.setString(10, punishment.getRemovedBy());
                ps.setString(11, punishment.getRemovedReason());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<List<me.raikou.duels.punishment.Punishment>> getActivePunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<me.raikou.duels.punishment.Punishment> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM duels_punishments WHERE uuid=? AND active=true AND removed=false")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    me.raikou.duels.punishment.Punishment p = new me.raikou.duels.punishment.Punishment(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getString("issuer_name"),
                            me.raikou.duels.punishment.PunishmentType.valueOf(rs.getString("type")),
                            rs.getString("reason"),
                            rs.getLong("timestamp"),
                            rs.getLong("duration"),
                            rs.getBoolean("active"),
                            rs.getBoolean("removed"),
                            rs.getString("removed_by"),
                            rs.getString("removed_reason"));

                    // Check expiration on load (lazy check)
                    if (p.isExpired()) {
                        // Mark expired async? For now return it, manager will filter
                    }
                    list.add(p);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<List<me.raikou.duels.punishment.Punishment>> getPunishmentHistory(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<me.raikou.duels.punishment.Punishment> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM duels_punishments WHERE uuid=? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new me.raikou.duels.punishment.Punishment(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getString("issuer_name"),
                            me.raikou.duels.punishment.PunishmentType.valueOf(rs.getString("type")),
                            rs.getString("reason"),
                            rs.getLong("timestamp"),
                            rs.getLong("duration"),
                            rs.getBoolean("active"),
                            rs.getBoolean("removed"),
                            rs.getString("removed_by"),
                            rs.getString("removed_reason")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> expirePunishment(int id, String removedBy, String reason) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE duels_punishments SET active=false, removed=true, removed_by=?, removed_reason=? WHERE id=?")) {
                ps.setString(1, removedBy);
                ps.setString(2, reason);
                ps.setInt(3, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getPlayerRank(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // First get the player's stats
                int wins = 0;
                int games = 0;
                long lastPlayed = 0;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT wins, losses, last_played FROM duel_stats WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        wins = rs.getInt("wins");
                        games = wins + rs.getInt("losses");
                        lastPlayed = rs.getLong("last_played");
                    } else {
                        return 0; // Player not found (or unranked)
                    }
                }

                // Count players with better stats
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) as rank FROM duel_stats WHERE " +
                                "(wins > ?) OR " +
                                "(wins = ? AND (wins + losses) > ?) OR " +
                                "(wins = ? AND (wins + losses) = ? AND last_played > ?)")) {
                    ps.setInt(1, wins);
                    ps.setInt(2, wins);
                    ps.setInt(3, games);
                    ps.setInt(4, wins);
                    ps.setInt(5, games);
                    ps.setLong(6, lastPlayed);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        // Rank is number of people better + 1
                        return rs.getInt("rank") + 1;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        });
    }
}

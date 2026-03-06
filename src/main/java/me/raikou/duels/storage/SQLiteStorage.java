package me.raikou.duels.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.leaderboard.LeaderboardEntry;
import me.raikou.duels.stats.PlayerStats;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class SQLiteStorage implements Storage {

    private final DuelsPlugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService asyncExecutor;
    private final int queryTimeoutSeconds = 5;
    private final int startingElo;

    public SQLiteStorage(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.startingElo = plugin.getConfig().getInt("ranked.starting-elo", 1000);
    }

    @Override
    public void connect() {
        if (isConnected()) {
            return;
        }

        try {
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            if (!dbFile.exists()) {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            }

            int maxPoolSize = Math.max(1, plugin.getConfig().getInt("storage.pool.maximum-pool-size", 10));
            int minIdle = Math.max(1, plugin.getConfig().getInt("storage.pool.minimum-idle", 2));
            long connectionTimeoutMs = Math.max(1000L,
                    plugin.getConfig().getLong("storage.pool.connection-timeout-ms", 10000L));

            HikariConfig config = new HikariConfig();
            config.setPoolName("Duels-SQLite");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(Math.min(minIdle, maxPoolSize));
            config.setConnectionTimeout(connectionTimeoutMs);
            config.setConnectionTestQuery("SELECT 1");
            config.addDataSourceProperty("busy_timeout", "10000");

            this.dataSource = new HikariDataSource(config);
            this.asyncExecutor = createExecutor("duels-sqlite");

            createTablesAndMigrations();
            plugin.getLogger().info("Connected to SQLite via HikariCP.");
        } catch (Exception e) {
            logSqlError("Could not connect to SQLite", e);
            closeDataSource();
        }
    }

    @Override
    public void disconnect() {
        closeDataSource();
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            asyncExecutor = null;
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    private ExecutorService createExecutor(String threadPrefix) {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadPrefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(4, factory);
    }

    private void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Storage is not connected.");
        }
        return dataSource.getConnection();
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(queryTimeoutSeconds);
        return statement;
    }

    private void createTablesAndMigrations() throws SQLException {
        try (Connection connection = getConnection();
                PreparedStatement duelStats = prepare(connection, SqlQueries.CREATE_DUEL_STATS);
                PreparedStatement kitLayouts = prepare(connection, SqlQueries.CREATE_KIT_LAYOUTS);
                PreparedStatement eloRatings = prepare(connection, SqlQueries.CREATE_ELO_RATINGS);
                PreparedStatement punishments = prepare(connection, SqlQueries.CREATE_PUNISHMENTS_SQLITE)) {
            duelStats.executeUpdate();
            kitLayouts.executeUpdate();
            eloRatings.executeUpdate();
            punishments.executeUpdate();
        }

        addColumnIfNotExists("duel_stats", "name", "VARCHAR(32) DEFAULT ''");
        addColumnIfNotExists("duel_stats", "current_streak", "INT DEFAULT 0");
        addColumnIfNotExists("duel_stats", "best_streak", "INT DEFAULT 0");
        addColumnIfNotExists("duel_stats", "last_played", "BIGINT DEFAULT 0");
        addColumnIfNotExists("duel_stats", "playtime", "BIGINT DEFAULT 0");
    }

    private void addColumnIfNotExists(String table, String column, String definition) {
        String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
        try (Connection connection = getConnection();
                PreparedStatement statement = prepare(connection, sql)) {
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // no-op: column already exists
        }
    }

    private void logSqlError(String action, Exception exception) {
        plugin.getLogger().warning("[SQLite] " + action + ": " + exception.getMessage());
    }

    @Override
    public CompletableFuture<Void> saveUser(UUID uuid, String name, PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO duel_stats (uuid, name, wins, losses, kills, deaths, current_streak, best_streak, last_played, playtime)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name=?,
                        wins=?,
                        losses=?,
                        kills=?,
                        deaths=?,
                        current_streak=?,
                        best_streak=?,
                        last_played=?,
                        playtime=?
                    """;
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
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
            } catch (Exception e) {
                logSqlError("saveUser failed", e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<PlayerStats> loadUserStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT wins, losses, kills, deaths, current_streak, best_streak, last_played, playtime
                    FROM duel_stats WHERE uuid=?
                    """;
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
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
                }
            } catch (Exception e) {
                logSqlError("loadUserStats failed", e);
            }
            return new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0);
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> saveKitLayout(UUID uuid, String kitName, String layoutData) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO kit_layouts (uuid, kit_name, layout_data) VALUES (?,?,?)
                    ON CONFLICT(uuid, kit_name) DO UPDATE SET layout_data=?
                    """;
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                ps.setString(3, layoutData);
                ps.setString(4, layoutData);
                ps.executeUpdate();
            } catch (Exception e) {
                logSqlError("saveKitLayout failed", e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<String> loadKitLayout(UUID uuid, String kitName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT layout_data FROM kit_layouts WHERE uuid=? AND kit_name=?";
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("layout_data");
                    }
                }
            } catch (Exception e) {
                logSqlError("loadKitLayout failed", e);
            }
            return null;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Integer> loadElo(UUID uuid, String kitName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT elo FROM elo_ratings WHERE uuid=? AND kit_name=?";
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("elo");
                    }
                }
            } catch (Exception e) {
                logSqlError("loadElo failed", e);
            }
            return startingElo;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> saveElo(UUID uuid, String kitName, int elo) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO elo_ratings (uuid, kit_name, elo) VALUES (?,?,?)
                    ON CONFLICT(uuid, kit_name) DO UPDATE SET elo=?
                    """;
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitName);
                ps.setInt(3, elo);
                ps.setInt(4, elo);
                ps.executeUpdate();
            } catch (Exception e) {
                logSqlError("saveElo failed", e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            String sql = """
                    SELECT uuid, name, wins, losses, kills, deaths, current_streak, best_streak, last_played, playtime
                    FROM duel_stats
                    WHERE (wins + losses) > 0
                    ORDER BY (wins + losses) DESC, wins DESC, last_played DESC
                    LIMIT ?
                    """;
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
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
                }
            } catch (Exception e) {
                logSqlError("getTopPlayers failed", e);
            }
            return entries;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Integer> getTotalPlayerCount() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) as count FROM duel_stats WHERE (wins + losses) > 0";
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql);
                    ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            } catch (Exception e) {
                logSqlError("getTotalPlayerCount failed", e);
            }
            return 0;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> savePunishment(me.raikou.duels.punishment.Punishment punishment) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO duels_punishments
                    (uuid, player_name, issuer_name, type, reason, timestamp, duration, active, removed, removed_by, removed_reason)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """;
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
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
            } catch (Exception e) {
                logSqlError("savePunishment failed", e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<List<me.raikou.duels.punishment.Punishment>> getActivePunishments(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<me.raikou.duels.punishment.Punishment> list = new ArrayList<>();
            String sql = "SELECT * FROM duels_punishments WHERE uuid=? AND active=1 AND removed=0";
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
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
                }
            } catch (Exception e) {
                logSqlError("getActivePunishments failed", e);
            }
            return list;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<List<me.raikou.duels.punishment.Punishment>> getPunishmentHistory(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<me.raikou.duels.punishment.Punishment> list = new ArrayList<>();
            String sql = "SELECT * FROM duels_punishments WHERE uuid=? ORDER BY id DESC LIMIT ?";
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
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
                }
            } catch (Exception e) {
                logSqlError("getPunishmentHistory failed", e);
            }
            return list;
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> expirePunishment(int id, String removedBy, String reason) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE duels_punishments SET active=0, removed=1, removed_by=?, removed_reason=? WHERE id=?";
            try (Connection connection = getConnection();
                    PreparedStatement ps = prepare(connection, sql)) {
                ps.setString(1, removedBy);
                ps.setString(2, reason);
                ps.setInt(3, id);
                ps.executeUpdate();
            } catch (Exception e) {
                logSqlError("expirePunishment failed", e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Integer> getPlayerRank(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int wins;
                int games;
                long lastPlayed;
                String selfSql = "SELECT wins, losses, last_played FROM duel_stats WHERE uuid=?";
                try (Connection connection = getConnection();
                        PreparedStatement ps = prepare(connection, selfSql)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return 0;
                        }
                        wins = rs.getInt("wins");
                        games = wins + rs.getInt("losses");
                        lastPlayed = rs.getLong("last_played");
                    }
                }

                String rankSql = """
                        SELECT COUNT(*) as rank FROM duel_stats WHERE
                            (wins > ?)
                            OR (wins = ? AND (wins + losses) > ?)
                            OR (wins = ? AND (wins + losses) = ? AND last_played > ?)
                        """;
                try (Connection connection = getConnection();
                        PreparedStatement ps = prepare(connection, rankSql)) {
                    ps.setInt(1, wins);
                    ps.setInt(2, wins);
                    ps.setInt(3, games);
                    ps.setInt(4, wins);
                    ps.setInt(5, games);
                    ps.setLong(6, lastPlayed);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("rank") + 1;
                        }
                    }
                }
            } catch (Exception e) {
                logSqlError("getPlayerRank failed", e);
            }
            return 0;
        }, asyncExecutor);
    }
}

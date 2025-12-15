package me.raikou.duels.stats;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player statistics with immediate persistence and leaderboard updates.
 */
public class StatsManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    public StatsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load stats for a player from database into cache.
     */
    public void loadStats(Player player) {
        plugin.getStorage().loadUserStats(player.getUniqueId()).thenAccept(stats -> {
            statsCache.put(player.getUniqueId(), stats);
        });
    }

    /**
     * Save stats for a player to database and remove from cache.
     */
    public void saveStats(Player player) {
        PlayerStats stats = statsCache.get(player.getUniqueId());
        if (stats != null) {
            plugin.getStorage().saveUser(player.getUniqueId(), player.getName(), stats);
            statsCache.remove(player.getUniqueId());
        }
    }

    /**
     * Save stats immediately without removing from cache.
     * Used after duel ends to persist data right away.
     */
    public void saveStatsImmediately(Player player) {
        PlayerStats stats = statsCache.get(player.getUniqueId());
        if (stats != null) {
            plugin.getStorage().saveUser(player.getUniqueId(), player.getName(), stats)
                    .thenRun(() -> {
                        // Refresh leaderboard after saving
                        if (plugin.getLeaderboardManager() != null) {
                            plugin.getLeaderboardManager().refreshCache();
                        }
                    });
        }
    }

    /**
     * Get stats for a player (from cache or new empty stats).
     */
    public PlayerStats getStats(Player player) {
        return statsCache.computeIfAbsent(player.getUniqueId(), k -> new PlayerStats());
    }

    /**
     * Get stats by UUID (from cache or new empty stats).
     */
    public PlayerStats getStats(UUID uuid) {
        return statsCache.computeIfAbsent(uuid, k -> new PlayerStats());
    }

    /**
     * Record a win for a player and save immediately.
     */
    public void recordWin(Player player) {
        PlayerStats stats = getStats(player);
        stats.recordWin();
        statsCache.put(player.getUniqueId(), stats);
        saveStatsImmediately(player);
    }

    /**
     * Record a loss for a player and save immediately.
     */
    public void recordLoss(Player player) {
        PlayerStats stats = getStats(player);
        stats.recordLoss();
        statsCache.put(player.getUniqueId(), stats);
        saveStatsImmediately(player);
    }

    /**
     * Legacy method - adds win without immediate save.
     * 
     * @deprecated Use {@link #recordWin(Player)} instead
     */
    @Deprecated
    public void addWin(Player player) {
        PlayerStats stats = getStats(player);
        stats.setWins(stats.getWins() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }

    /**
     * Legacy method - adds loss without immediate save.
     * 
     * @deprecated Use {@link #recordLoss(Player)} instead
     */
    @Deprecated
    public void addLoss(Player player) {
        PlayerStats stats = getStats(player);
        stats.setLosses(stats.getLosses() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }

    /**
     * Legacy method - adds kill without immediate save.
     */
    public void addKill(Player player) {
        PlayerStats stats = getStats(player);
        stats.setKills(stats.getKills() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }

    /**
     * Legacy method - adds death without immediate save.
     */
    public void addDeath(Player player) {
        PlayerStats stats = getStats(player);
        stats.setDeaths(stats.getDeaths() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }

    /**
     * Get player's current rank in the leaderboard.
     */
    public void getPlayerRank(UUID uuid, java.util.function.Consumer<Integer> callback) {
        plugin.getStorage().getPlayerRank(uuid).thenAccept(rank -> {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(rank));
        });
    }

    // Session Tracking
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();

    public void startSession(Player player) {
        sessionStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void endSession(Player player) {
        Long start = sessionStartTimes.remove(player.getUniqueId());
        if (start != null) {
            long duration = System.currentTimeMillis() - start;
            PlayerStats stats = getStats(player);
            stats.setPlaytime(stats.getPlaytime() + duration);
            saveStats(player); // Save on quit
        }
    }

    // Helper to get live playtime including current session
    public long getPlaytime(Player player) {
        PlayerStats stats = getStats(player);
        long currentSession = 0;
        if (sessionStartTimes.containsKey(player.getUniqueId())) {
            currentSession = System.currentTimeMillis() - sessionStartTimes.get(player.getUniqueId());
        }
        return stats.getPlaytime() + currentSession;
    }
}

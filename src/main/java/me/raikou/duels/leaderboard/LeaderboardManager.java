package me.raikou.duels.leaderboard;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the leaderboard cache and provides access to top players.
 * Now supports all players who have played at least one duel (not just
 * winners).
 */
public class LeaderboardManager {

    private final DuelsPlugin plugin;
    private final AtomicReference<List<LeaderboardEntry>> cachedLeaderboard = new AtomicReference<>(List.of());
    private final AtomicInteger totalPlayerCount = new AtomicInteger(0);
    private volatile long lastUpdateTime = 0;
    private int cacheSeconds = 60;
    private int topCount = 10;

    public LeaderboardManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        // Initial load
        refreshCache();
        // Start auto-refresh task
        startAutoRefresh();
    }

    private void loadConfig() {
        this.cacheSeconds = plugin.getConfig().getInt("leaderboard.cache-duration", 60);
        this.topCount = plugin.getConfig().getInt("leaderboard.top-count", 10);
    }

    private void startAutoRefresh() {
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshCache();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * cacheSeconds, 20L * cacheSeconds);
    }

    /**
     * Refresh the leaderboard cache from database.
     * This is now called automatically after every duel ends.
     */
    public void refreshCache() {
        plugin.getStorage().getTopPlayers(topCount).thenAccept(entries -> {
            cachedLeaderboard.set(List.copyOf(entries));
            lastUpdateTime = System.currentTimeMillis();
            plugin.getLogger().info("[Leaderboard] Cache refreshed with " + entries.size() + " entries.");
        });

        plugin.getStorage().getTotalPlayerCount().thenAccept(count -> {
            totalPlayerCount.set(count);
        });
    }

    /**
     * Get a top player entry by position (1-indexed).
     * 
     * @param position 1 for first place, 2 for second, etc.
     * @return LeaderboardEntry or null if position is invalid
     */
    public LeaderboardEntry getTopPlayer(int position) {
        List<LeaderboardEntry> snapshot = cachedLeaderboard.get();
        if (position < 1 || position > snapshot.size()) {
            return null;
        }
        return snapshot.get(position - 1);
    }

    /**
     * Get the full cached leaderboard.
     */
    public List<LeaderboardEntry> getLeaderboard() {
        return cachedLeaderboard.get();
    }

    /**
     * Get the number of cached entries.
     */
    public int getLeaderboardSize() {
        return cachedLeaderboard.get().size();
    }

    /**
     * Get total number of players who have played.
     */
    public int getTotalPlayerCount() {
        return totalPlayerCount.get();
    }

    /**
     * Check if the cache is populated.
     */
    public boolean isCacheReady() {
        return !cachedLeaderboard.get().isEmpty();
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Get player's position in leaderboard asynchronously.
     */
    public void getPlayerRank(UUID uuid, java.util.function.Consumer<Integer> callback) {
        plugin.getStorage().getPlayerRank(uuid).thenAccept(rank -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> callback.accept(rank));
        });
    }
}

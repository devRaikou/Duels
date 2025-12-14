package me.raikou.duels.leaderboard;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the leaderboard cache and provides access to top players.
 */
public class LeaderboardManager {

    private final DuelsPlugin plugin;
    private List<LeaderboardEntry> cachedLeaderboard = new ArrayList<>();
    private long lastUpdateTime = 0;
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
     */
    public void refreshCache() {
        plugin.getStorage().getTopPlayers(topCount).thenAccept(entries -> {
            cachedLeaderboard = entries;
            lastUpdateTime = System.currentTimeMillis();
        });
    }

    /**
     * Get a top player entry by position (1-indexed).
     * 
     * @param position 1 for first place, 2 for second, etc.
     * @return LeaderboardEntry or null if position is invalid
     */
    public LeaderboardEntry getTopPlayer(int position) {
        if (position < 1 || position > cachedLeaderboard.size()) {
            return null;
        }
        return cachedLeaderboard.get(position - 1);
    }

    /**
     * Get the full cached leaderboard.
     */
    public List<LeaderboardEntry> getLeaderboard() {
        return new ArrayList<>(cachedLeaderboard);
    }

    /**
     * Get the number of cached entries.
     */
    public int getLeaderboardSize() {
        return cachedLeaderboard.size();
    }

    /**
     * Check if the cache is populated.
     */
    public boolean isCacheReady() {
        return !cachedLeaderboard.isEmpty();
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
}

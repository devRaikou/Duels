package me.raikou.duels.stats;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player statistics with deterministic async merge.
 */
public class StatsManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, StatsState> statsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();

    public StatsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @FunctionalInterface
    private interface StatsMutation {
        void apply(PlayerStats stats);
    }

    private static final class StatsState {
        private PlayerStats stats = new PlayerStats();
        private boolean loaded = false;
        private final List<StatsMutation> pendingMutations = new ArrayList<>();
        private CompletableFuture<Void> loadFuture = CompletableFuture.completedFuture(null);
    }

    private StatsState getState(UUID uuid) {
        return statsCache.computeIfAbsent(uuid, ignored -> new StatsState());
    }

    private PlayerStats copyStats(PlayerStats source) {
        return new PlayerStats(
                source.getWins(),
                source.getLosses(),
                source.getKills(),
                source.getDeaths(),
                source.getCurrentStreak(),
                source.getBestStreak(),
                source.getLastPlayed(),
                source.getPlaytime());
    }

    private void applyMutation(UUID uuid, StatsMutation mutation) {
        StatsState state = getState(uuid);
        synchronized (state) {
            mutation.apply(state.stats);
            if (!state.loaded) {
                state.pendingMutations.add(mutation);
            }
        }
    }

    /**
     * Load stats for a player from database into cache with mutation replay.
     */
    public void loadStats(Player player) {
        UUID uuid = player.getUniqueId();
        StatsState state = getState(uuid);
        state.loadFuture = plugin.getStorage().loadUserStats(uuid).thenAccept(loadedStats -> {
            synchronized (state) {
                PlayerStats merged = copyStats(loadedStats);
                for (StatsMutation mutation : state.pendingMutations) {
                    mutation.apply(merged);
                }
                state.stats = merged;
                state.pendingMutations.clear();
                state.loaded = true;
            }
        });
    }

    /**
     * Save stats for a player to database and remove from cache.
     */
    public void saveStats(Player player) {
        UUID uuid = player.getUniqueId();
        StatsState state = statsCache.get(uuid);
        if (state == null) {
            return;
        }
        state.loadFuture.thenRun(() -> {
            PlayerStats snapshot;
            synchronized (state) {
                snapshot = copyStats(state.stats);
            }
            plugin.getStorage().saveUser(uuid, player.getName(), snapshot).thenRun(() -> statsCache.remove(uuid));
        });
    }

    /**
     * Save stats immediately without removing from cache.
     */
    public void saveStatsImmediately(Player player) {
        UUID uuid = player.getUniqueId();
        StatsState state = statsCache.get(uuid);
        if (state == null) {
            return;
        }

        state.loadFuture.thenRun(() -> {
            PlayerStats snapshot;
            synchronized (state) {
                snapshot = copyStats(state.stats);
            }
            plugin.getStorage().saveUser(uuid, player.getName(), snapshot)
                    .thenRun(() -> {
                        if (plugin.getLeaderboardManager() != null) {
                            plugin.getLeaderboardManager().refreshCache();
                        }
                    });
        });
    }

    /**
     * Get stats for a player (cached view).
     */
    public PlayerStats getStats(Player player) {
        StatsState state = getState(player.getUniqueId());
        synchronized (state) {
            return copyStats(state.stats);
        }
    }

    /**
     * Get stats by UUID (cached view).
     */
    public PlayerStats getStats(UUID uuid) {
        StatsState state = getState(uuid);
        synchronized (state) {
            return copyStats(state.stats);
        }
    }

    /**
     * Record a win for a player and save immediately.
     */
    public void recordWin(Player player) {
        applyMutation(player.getUniqueId(), PlayerStats::recordWin);
        saveStatsImmediately(player);
    }

    /**
     * Record a loss for a player and save immediately.
     */
    public void recordLoss(Player player) {
        applyMutation(player.getUniqueId(), PlayerStats::recordLoss);
        saveStatsImmediately(player);
    }

    /**
     * Legacy method - adds win without immediate save.
     *
     * @deprecated Use {@link #recordWin(Player)} instead
     */
    @Deprecated
    public void addWin(Player player) {
        applyMutation(player.getUniqueId(), stats -> stats.setWins(stats.getWins() + 1));
    }

    /**
     * Legacy method - adds loss without immediate save.
     *
     * @deprecated Use {@link #recordLoss(Player)} instead
     */
    @Deprecated
    public void addLoss(Player player) {
        applyMutation(player.getUniqueId(), stats -> stats.setLosses(stats.getLosses() + 1));
    }

    /**
     * Legacy method - adds kill without immediate save.
     */
    public void addKill(Player player) {
        applyMutation(player.getUniqueId(), stats -> stats.setKills(stats.getKills() + 1));
    }

    /**
     * Legacy method - adds death without immediate save.
     */
    public void addDeath(Player player) {
        applyMutation(player.getUniqueId(), stats -> stats.setDeaths(stats.getDeaths() + 1));
    }

    /**
     * Get player's current rank in the leaderboard.
     */
    public void getPlayerRank(UUID uuid, java.util.function.Consumer<Integer> callback) {
        plugin.getStorage().getPlayerRank(uuid).thenAccept(rank -> {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(rank));
        });
    }

    public void startSession(Player player) {
        sessionStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void endSession(Player player) {
        Long start = sessionStartTimes.remove(player.getUniqueId());
        if (start != null) {
            long duration = System.currentTimeMillis() - start;
            applyMutation(player.getUniqueId(), stats -> stats.setPlaytime(stats.getPlaytime() + duration));
            saveStats(player);
        }
    }

    public long getPlaytime(Player player) {
        PlayerStats stats = getStats(player);
        long currentSession = 0;
        if (sessionStartTimes.containsKey(player.getUniqueId())) {
            currentSession = System.currentTimeMillis() - sessionStartTimes.get(player.getUniqueId());
        }
        return stats.getPlaytime() + currentSession;
    }
}

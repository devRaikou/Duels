package me.raikou.duels.match;

import me.raikou.duels.DuelsPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages match history and provides access to recent match results.
 * Stores the last N matches in memory for quick GUI access.
 */
public class MatchHistoryManager {

    private final DuelsPlugin plugin;
    private final Map<String, DuelResult> matchHistory = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Deque<String>> playerMatchHistory = new ConcurrentHashMap<>(); // Player -> List
                                                                                                     // of Match IDs
    private final int maxHistorySize;

    public MatchHistoryManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.maxHistorySize = plugin.getConfig().getInt("match-history.max-size", 100);
    }

    /**
     * Store a new match result.
     */
    public void addMatch(DuelResult result) {
        matchHistory.put(result.getMatchId(), result);

        // Add to winner history
        addMatchToPlayerHistory(result.getWinnerUuid(), result.getMatchId());
        // Add to loser history
        addMatchToPlayerHistory(result.getLoserUuid(), result.getMatchId());

        // Clean up old matches if over limit
        if (matchHistory.size() > maxHistorySize) {
            // Remove oldest entries (simple approach: remove first found)
            long oldestTime = Long.MAX_VALUE;
            String oldestId = null;
            for (Map.Entry<String, DuelResult> entry : matchHistory.entrySet()) {
                if (entry.getValue().getTimestamp() < oldestTime) {
                    oldestTime = entry.getValue().getTimestamp();
                    oldestId = entry.getKey();
                }
            }
            if (oldestId != null) {
                matchHistory.remove(oldestId);
            }
        }

        plugin.getLogger().info("[Match] Stored match result: " + result.getShortMatchId());
    }

    private void addMatchToPlayerHistory(UUID uuid, String matchId) {
        java.util.Deque<String> history = playerMatchHistory.computeIfAbsent(uuid,
                k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        history.addFirst(matchId);
        if (history.size() > 10) { // Keep last 10 per player for quick access
            history.removeLast();
        }
    }

    /**
     * Get recent matches for a player.
     */
    public java.util.List<DuelResult> getLastMatches(UUID playerUuid, int limit) {
        java.util.List<DuelResult> results = new java.util.ArrayList<>();
        java.util.Deque<String> history = playerMatchHistory.get(playerUuid);
        if (history != null) {
            int count = 0;
            for (String matchId : history) {
                if (count >= limit)
                    break;
                DuelResult result = matchHistory.get(matchId);
                if (result != null) {
                    results.add(result);
                    count++;
                }
            }
        }
        return results;
    }

    /**
     * Get a match result by ID (full or short ID).
     */
    public DuelResult getMatch(String matchId) {
        // Try exact match first
        DuelResult result = matchHistory.get(matchId);
        if (result != null)
            return result;

        // Try matching by short ID (prefix match)
        for (Map.Entry<String, DuelResult> entry : matchHistory.entrySet()) {
            if (entry.getKey().startsWith(matchId) || entry.getValue().getShortMatchId().equals(matchId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Get a player's last match result ID.
     */
    public String getPlayerLastMatchId(UUID playerUuid) {
        java.util.Deque<String> history = playerMatchHistory.get(playerUuid);
        return history != null ? history.peekFirst() : null;
    }

    /**
     * Get a player's last match result.
     */
    public DuelResult getPlayerLastMatch(UUID playerUuid) {
        String matchId = getPlayerLastMatchId(playerUuid);
        return matchId != null ? matchHistory.get(matchId) : null;
    }

    /**
     * Check if a match exists (full or short ID).
     */
    public boolean hasMatch(String matchId) {
        if (matchHistory.containsKey(matchId))
            return true;

        // Try matching by short ID
        for (Map.Entry<String, DuelResult> entry : matchHistory.entrySet()) {
            if (entry.getKey().startsWith(matchId) || entry.getValue().getShortMatchId().equals(matchId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get total stored matches count.
     */
    public int getStoredMatchCount() {
        return matchHistory.size();
    }
}

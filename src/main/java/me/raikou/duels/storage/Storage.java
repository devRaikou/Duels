package me.raikou.duels.storage;

import me.raikou.duels.leaderboard.LeaderboardEntry;
import me.raikou.duels.stats.PlayerStats;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage {

    void connect();

    void disconnect();

    /**
     * Save complete user stats including streak and last played.
     */
    CompletableFuture<Void> saveUser(UUID uuid, String name, PlayerStats stats);

    /**
     * Legacy save method for backwards compatibility.
     */
    default CompletableFuture<Void> saveUser(UUID uuid, String name, int wins, int losses, int kills, int deaths) {
        return saveUser(uuid, name, new PlayerStats(wins, losses, kills, deaths));
    }

    /**
     * Load complete user stats.
     */
    CompletableFuture<PlayerStats> loadUserStats(UUID uuid);

    /**
     * Legacy load method - returns [wins, losses, kills, deaths].
     */
    default CompletableFuture<int[]> loadUser(UUID uuid) {
        return loadUserStats(uuid).thenApply(
                stats -> new int[] { stats.getWins(), stats.getLosses(), stats.getKills(), stats.getDeaths() });
    }

    CompletableFuture<Void> saveKitLayout(UUID uuid, String kitName, String layoutData);

    CompletableFuture<String> loadKitLayout(UUID uuid, String kitName);

    CompletableFuture<Integer> loadElo(UUID uuid, String kitName);

    CompletableFuture<Void> saveElo(UUID uuid, String kitName, int elo);

    /**
     * Get top players sorted by total games (wins + losses) first, then by wins.
     * Includes ALL players who have played at least one game.
     */
    CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit);

    /**
     * Get total count of players in leaderboard.
     */
    CompletableFuture<Integer> getTotalPlayerCount();

    /**
     * Get player's rank in leaderboard.
     */
    CompletableFuture<Integer> getPlayerRank(UUID uuid);

    // Punishments
    CompletableFuture<Void> savePunishment(me.raikou.duels.punishment.Punishment punishment);

    CompletableFuture<List<me.raikou.duels.punishment.Punishment>> getActivePunishments(UUID uuid);

    CompletableFuture<List<me.raikou.duels.punishment.Punishment>> getPunishmentHistory(UUID uuid, int limit);

    CompletableFuture<Void> expirePunishment(int id, String removedBy, String reason);
}

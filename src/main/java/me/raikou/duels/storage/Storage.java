package me.raikou.duels.storage;

import me.raikou.duels.leaderboard.LeaderboardEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage {

    void connect();

    void disconnect();

    CompletableFuture<Void> saveUser(UUID uuid, String name, int wins, int losses, int kills, int deaths);

    // Returns array: [wins, losses, kills, deaths]
    CompletableFuture<int[]> loadUser(UUID uuid);

    CompletableFuture<Void> saveKitLayout(UUID uuid, String kitName, String layoutData);

    // Returns layoutData string or null
    CompletableFuture<String> loadKitLayout(UUID uuid, String kitName);

    // ELO per kit
    CompletableFuture<Integer> loadElo(UUID uuid, String kitName);

    CompletableFuture<Void> saveElo(UUID uuid, String kitName, int elo);

    // Leaderboard - returns top players sorted by wins DESC
    CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit);
}

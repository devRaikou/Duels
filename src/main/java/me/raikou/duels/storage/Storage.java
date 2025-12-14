package me.raikou.duels.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage {

    void connect();

    void disconnect();

    CompletableFuture<Void> saveUser(UUID uuid, int wins, int losses, int kills, int deaths);

    // Returns array: [wins, losses, kills, deaths]
    CompletableFuture<int[]> loadUser(UUID uuid);

    CompletableFuture<Void> saveKitLayout(UUID uuid, String kitName, String layoutData);

    // Returns layoutData string or null
    CompletableFuture<String> loadKitLayout(UUID uuid, String kitName);
}

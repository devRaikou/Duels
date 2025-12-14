package me.raikou.duels.stats;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    public StatsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadStats(Player player) {
        plugin.getStorage().loadUser(player.getUniqueId()).thenAccept(data -> {
            PlayerStats stats = new PlayerStats(data[0], data[1], data[2], data[3]);
            statsCache.put(player.getUniqueId(), stats);
        });
    }

    public void saveStats(Player player) {
        PlayerStats stats = statsCache.get(player.getUniqueId());
        if (stats != null) {
            plugin.getStorage().saveUser(player.getUniqueId(), player.getName(),
                    stats.getWins(), stats.getLosses(), stats.getKills(), stats.getDeaths());
            statsCache.remove(player.getUniqueId());
        }
    }

    public PlayerStats getStats(Player player) {
        return statsCache.getOrDefault(player.getUniqueId(), new PlayerStats(0, 0, 0, 0));
    }

    public void addWin(Player player) {
        PlayerStats stats = getStats(player);
        stats.setWins(stats.getWins() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }

    public void addLoss(Player player) {
        PlayerStats stats = getStats(player);
        stats.setLosses(stats.getLosses() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }

    public void addKill(Player player) {
        PlayerStats stats = getStats(player);
        stats.setKills(stats.getKills() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }

    public void addDeath(Player player) {
        PlayerStats stats = getStats(player);
        stats.setDeaths(stats.getDeaths() + 1);
        statsCache.put(player.getUniqueId(), stats);
    }
}

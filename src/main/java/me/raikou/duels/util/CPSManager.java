package me.raikou.duels.util;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks and calculates Clicks Per Second (CPS) for all players.
 */
public class CPSManager implements Listener {

    private final DuelsPlugin plugin;
    private final Map<UUID, ConcurrentLinkedQueue<Long>> clickHistory = new HashMap<>();
    private final Map<UUID, Integer> cpsCache = new HashMap<>();

    public CPSManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startUpdater();
    }

    private void startUpdater() {
        // Update CPS cache every 2 ticks for smooth display
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateCPS(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    /**
     * Track left clicks for CPS calculation
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            UUID uuid = event.getPlayer().getUniqueId();
            clickHistory.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>()).add(System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clickHistory.remove(uuid);
        cpsCache.remove(uuid);
    }

    private void updateCPS(Player player) {
        UUID uuid = player.getUniqueId();
        ConcurrentLinkedQueue<Long> clicks = clickHistory.get(uuid);

        if (clicks == null || clicks.isEmpty()) {
            cpsCache.put(uuid, 0);
            return;
        }

        // Remove clicks older than 1 second
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peek() > 1000) {
            clicks.poll();
        }

        cpsCache.put(uuid, clicks.size());
    }

    /**
     * Get the current CPS for a player
     */
    public int getCPS(Player player) {
        return cpsCache.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Get CPS by UUID (for opponent lookup)
     */
    public int getCPS(UUID uuid) {
        return cpsCache.getOrDefault(uuid, 0);
    }
}

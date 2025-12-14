package me.raikou.duels.util;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import me.raikou.duels.stats.PlayerStats;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages BossBars for players in lobby and during duels.
 */
public class BossBarManager implements Listener {

    private final DuelsPlugin plugin;
    private final Map<UUID, BossBar> playerBars = new HashMap<>();

    public BossBarManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startUpdater();

        // Show bossbar to all online players on reload
        for (Player player : Bukkit.getOnlinePlayers()) {
            showBossBar(player);
        }
    }

    private void startUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Ensure player has bossbar
                    if (!playerBars.containsKey(player.getUniqueId())) {
                        showBossBar(player);
                    }
                    updateBossBar(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Delay slightly to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            showBossBar(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideBossBar(event.getPlayer());
    }

    public void showBossBar(Player player) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) {
            return;
        }

        // Remove existing bar first
        BossBar existingBar = playerBars.get(player.getUniqueId());
        if (existingBar != null) {
            player.hideBossBar(existingBar);
        }

        // Create new bar
        BossBar bar = BossBar.bossBar(
                Component.text("Loading..."),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS);
        playerBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
        updateBossBar(player);
    }

    public void hideBossBar(Player player) {
        BossBar bar = playerBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void updateBossBar(Player player) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) {
            return;
        }

        BossBar bar = playerBars.get(player.getUniqueId());
        if (bar == null) {
            showBossBar(player);
            return;
        }

        Duel duel = plugin.getDuelManager().getDuel(player);
        String text;
        String colorStr;
        String styleStr;

        if (duel != null) {
            // In-game BossBar
            text = plugin.getConfig().getString("bossbar.game.text",
                    "<red>Fighting: <white>%opponent%</white> | Kit: <yellow>%kit%</yellow>");
            colorStr = plugin.getConfig().getString("bossbar.game.color", "RED");
            styleStr = plugin.getConfig().getString("bossbar.game.style", "SOLID");

            // Replace duel-specific placeholders
            Player opponent = getOpponent(player, duel);
            text = text.replace("%opponent%", opponent != null ? opponent.getName() : "Unknown");
            text = text.replace("%kit%", duel.getKitName() != null ? duel.getKitName() : "None");
        } else {
            // Lobby BossBar
            text = plugin.getConfig().getString("bossbar.lobby.text",
                    "<gradient:#FFD700:#FFA500>Welcome to Duels!</gradient>");
            colorStr = plugin.getConfig().getString("bossbar.lobby.color", "YELLOW");
            styleStr = plugin.getConfig().getString("bossbar.lobby.style", "SOLID");
        }

        // Apply common placeholders
        text = applyPlaceholders(text, player, duel);

        // Parse and apply
        Component textComponent = MessageUtil.parse(text);
        bar.name(textComponent);
        bar.color(parseColor(colorStr));
        bar.overlay(parseOverlay(styleStr));

        // Update progress based on context
        if (duel != null) {
            // Health-based progress
            bar.progress((float) Math.min(1.0, Math.max(0.0, player.getHealth() / 20.0)));
        } else {
            bar.progress(1.0f);
        }
    }

    private Player getOpponent(Player player, Duel duel) {
        for (UUID uuid : duel.getPlayers()) {
            if (!uuid.equals(player.getUniqueId())) {
                return Bukkit.getPlayer(uuid);
            }
        }
        return null;
    }

    private String applyPlaceholders(String text, Player player, Duel duel) {
        text = text.replace("%player%", player.getName());
        text = text.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        text = text.replace("%date%", java.time.LocalDate.now().toString());
        text = text.replace("%time%", java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")));

        // Queue status
        if (plugin.getQueueManager().isInQueue(player)) {
            text = text.replace("%queue%", "Searching...");
        } else {
            text = text.replace("%queue%", "Not in queue");
        }

        // Status
        if (duel != null) {
            text = text.replace("%status%", "In Duel");
        } else if (plugin.getQueueManager().isInQueue(player)) {
            text = text.replace("%status%", "In Queue");
        } else {
            text = text.replace("%status%", "In Lobby");
        }

        // Stats
        PlayerStats stats = plugin.getStatsManager().getStats(player);
        text = text.replace("%wins%", String.valueOf(stats.getWins()));
        text = text.replace("%losses%", String.valueOf(stats.getLosses()));
        text = text.replace("%kills%", String.valueOf(stats.getKills()));
        text = text.replace("%deaths%", String.valueOf(stats.getDeaths()));

        return text;
    }

    private BossBar.Color parseColor(String color) {
        try {
            return BossBar.Color.valueOf(color.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.YELLOW;
        }
    }

    private BossBar.Overlay parseOverlay(String style) {
        return switch (style.toUpperCase()) {
            case "SEGMENTED_6" -> BossBar.Overlay.NOTCHED_6;
            case "SEGMENTED_10" -> BossBar.Overlay.NOTCHED_10;
            case "SEGMENTED_12" -> BossBar.Overlay.NOTCHED_12;
            case "SEGMENTED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }

    public void cleanup() {
        for (Map.Entry<UUID, BossBar> entry : playerBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        playerBars.clear();
    }
}

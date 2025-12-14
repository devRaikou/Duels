package me.raikou.duels.util;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Manages nametags with health display during duels.
 */
public class NametagManager {

    private final DuelsPlugin plugin;

    public NametagManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        startUpdater();
    }

    private void startUpdater() {
        // Update nametags every 5 ticks (0.25 seconds) for smooth updates
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Duel duel = plugin.getDuelManager().getDuel(player);
                    if (duel != null) {
                        updateNametagForDuel(player, duel);
                    } else {
                        clearNametag(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    /**
     * Update nametag to show health during duel
     */
    private void updateNametagForDuel(Player player, Duel duel) {
        Scoreboard board = player.getScoreboard();

        // Get opponent
        Player opponent = null;
        for (UUID uuid : duel.getPlayers()) {
            if (!uuid.equals(player.getUniqueId())) {
                opponent = Bukkit.getPlayer(uuid);
                break;
            }
        }

        if (opponent == null)
            return;

        // Create/update team for opponent to show their health
        String teamName = "duel_" + player.getName().substring(0, Math.min(player.getName().length(), 8));
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }

        // Add opponent to team
        if (!team.hasEntry(opponent.getName())) {
            team.addEntry(opponent.getName());
        }

        // Set suffix with health
        double health = opponent.getHealth();

        // Create colored health display
        Component suffix = Component.text(" ")
                .append(Component.text("❤ ", NamedTextColor.RED))
                .append(Component.text(String.format("%.1f", health), getHealthColor(health)));

        team.suffix(suffix);
    }

    private NamedTextColor getHealthColor(double health) {
        if (health > 14)
            return NamedTextColor.GREEN;
        if (health > 8)
            return NamedTextColor.YELLOW;
        if (health > 4)
            return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    /**
     * Clear nametag when not in duel
     */
    private void clearNametag(Player player) {
        Scoreboard board = player.getScoreboard();
        String teamName = "duel_" + player.getName().substring(0, Math.min(player.getName().length(), 8));
        Team team = board.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }
}

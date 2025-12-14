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
 * Manages nametags with health display during duels using scoreboard teams.
 * Health is shown next to player name as suffix.
 */
public class NametagManager {

    private final DuelsPlugin plugin;

    public NametagManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        startUpdater();
    }

    private void startUpdater() {
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
     * Update nametag to show opponent's health as suffix
     */
    private void updateNametagForDuel(Player player, Duel duel) {
        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

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

        // Create/update team for opponent
        String teamName = "hp_" + opponent.getName().substring(0, Math.min(opponent.getName().length(), 12));
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }

        // Add opponent to team
        if (!team.hasEntry(opponent.getName())) {
            team.addEntry(opponent.getName());
        }

        // Set suffix with health: "17 ❤"
        int health = (int) Math.ceil(opponent.getHealth());
        Component suffix = Component.text(" ")
                .append(Component.text(String.valueOf(health), getHealthColor(opponent.getHealth())))
                .append(Component.text(" ❤", NamedTextColor.RED));

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
        for (Team team : board.getTeams()) {
            if (team.getName().startsWith("hp_")) {
                team.unregister();
            }
        }
    }

    public void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearNametag(player);
        }
    }
}

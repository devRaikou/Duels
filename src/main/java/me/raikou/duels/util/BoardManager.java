package me.raikou.duels.util;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoardManager {

    private final DuelsPlugin plugin;
    // Simple way: Keep a scoreboard per player or use a shared one for lobby?
    // Paper 1.21 allows modern scoreboards, but standard Bukkit API is safest for
    // compatibility.
    // We will use one scoreboard per player to avoid flicker/conflict.

    public BoardManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        startUpdater();
    }

    private void startUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateBoard(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Update every second
    }

    public void updateBoard(Player player) {
        Scoreboard board = player.getScoreboard();
        if (board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective("sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("sidebar", Criteria.DUMMY, Component.text("DUELS", NamedTextColor.GOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        Duel duel = plugin.getDuelManager().getDuel(player);

        // Reset scores (Simple clears all lines)
        // For production, updating existing lines (Teams) is better to prevent flicker.
        // But for this MVP level, simple reset is acceptable if flash is not too bad.
        // Actually, let's just use lines 15 down to 1.
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        if (duel != null) {
            // Game Board
            obj.getScore("§7Map: §f" + duel.getArena().getName()).setScore(5);
            obj.getScore("§7Opponent: §c" + getOpponentName(player, duel)).setScore(4);
            obj.getScore(" ").setScore(3);
            obj.getScore("§eplay.raikou.com").setScore(1);
        } else {
            // Lobby Board
            obj.getScore("§7Wins: §fUnknown").setScore(5); // Would need async fetch or cache
            obj.getScore("§7Queue: §f" + (plugin.getQueueManager().isInQueue(player) ? "Yes" : "No")).setScore(4);
            obj.getScore(" ").setScore(3);
            obj.getScore("§eplay.raikou.com").setScore(1);
        }
    }

    private String getOpponentName(Player player, Duel duel) {
        for (UUID uuid : duel.getPlayers()) {
            if (!uuid.equals(player.getUniqueId())) {
                Player opp = Bukkit.getPlayer(uuid);
                return opp != null ? opp.getName() : "Unknown";
            }
        }
        return "None";
    }
}

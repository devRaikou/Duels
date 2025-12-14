package me.raikou.duels.util;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import me.raikou.duels.duel.Duel;
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
            obj = board.registerNewObjective("sidebar", Criteria.DUMMY, me.raikou.duels.util.MessageUtil
                    .parse("<bold><gradient:#FFD700:#FFA500> DUELS </gradient></bold>"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        Duel duel = plugin.getDuelManager().getDuel(player);

        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        if (duel != null) {
            // Game Board
            obj.getScore("§7<gray>  " + java.time.LocalDate.now().toString()).setScore(10);
            obj.getScore("§1").setScore(9);
            obj.getScore("§fOpponent:").setScore(8);
            obj.getScore("§c " + getOpponentName(player, duel)).setScore(7);
            obj.getScore("§2").setScore(6);
            obj.getScore("§fMap:").setScore(5);
            obj.getScore("§a " + duel.getArena().getName()).setScore(4);
            obj.getScore("§3").setScore(3);
            obj.getScore("§epvp.raikou.com").setScore(1);
        } else {
            // Lobby Board
            obj.getScore("§7<gray>  " + java.time.LocalDate.now().toString()).setScore(10);
            obj.getScore("§1").setScore(9);
            obj.getScore("§fOnline:").setScore(8);
            obj.getScore("§a " + Bukkit.getOnlinePlayers().size()).setScore(7);
            obj.getScore("§2").setScore(6);
            obj.getScore("§fIn Queue:").setScore(5);
            obj.getScore("§e " + (plugin.getQueueManager().isInQueue(player) ? "Yes" : "No")).setScore(4);
            obj.getScore("§3").setScore(3);
            obj.getScore("§epvp.raikou.com").setScore(1);
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

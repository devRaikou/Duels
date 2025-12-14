package me.raikou.duels.util;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.raikou.duels.DuelsPlugin;
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

        String titleRaw = plugin.getConfig().getString("scoreboard.title",
                "<bold><gradient:#FFD700:#FFA500> DUELS </gradient></bold>");
        net.kyori.adventure.text.Component titleComp = me.raikou.duels.util.MessageUtil.parse(titleRaw);

        Objective obj = board.getObjective("sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("sidebar", Criteria.DUMMY, titleComp);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(titleComp);
        }

        Duel duel = plugin.getDuelManager().getDuel(player);
        java.util.List<String> lines;

        if (duel != null) {
            if (duel.isRanked()) {
                lines = plugin.getConfig().getStringList("scoreboard.game-ranked");
            } else {
                lines = plugin.getConfig().getStringList("scoreboard.game");
            }
        } else {
            lines = plugin.getConfig().getStringList("scoreboard.lobby");
        }

        // Clear existing scores
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        // Process lines in reverse order (Scoreboard scores go down)
        int score = lines.size();
        for (String line : lines) {
            // Replace Placeholders
            line = line.replace("%date%", java.time.LocalDate.now().toString());
            line = line.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
            line = line.replace("%queue%", plugin.getQueueManager().isInQueue(player) ? "Yes" : "No");
            line = line.replace("%ping%", String.valueOf(player.getPing()));

            if (duel != null) {
                line = line.replace("%opponent%", getOpponentName(player, duel));
                line = line.replace("%map%", duel.getArena().getName());

                // ELO placeholders for ranked
                if (duel.isRanked()) {
                    String kitName = duel.getKitName();
                    int playerElo = plugin.getStorage().loadElo(player.getUniqueId(), kitName).join();
                    int opponentElo = getOpponentElo(player, duel, kitName);
                    line = line.replace("%elo%", String.valueOf(playerElo));
                    line = line.replace("%opponent_elo%", String.valueOf(opponentElo));
                }
            } else {
                line = line.replace("%opponent%", "None");
                line = line.replace("%map%", "None");
                line = line.replace("%elo%", "N/A");
                line = line.replace("%opponent_elo%", "N/A");
            }

            line = convertLegacyToMiniMessage(line);
            net.kyori.adventure.text.Component comp = me.raikou.duels.util.MessageUtil.parse(line);
            String legacyLine = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(comp);

            obj.getScore(legacyLine).setScore(score);
            score--;
        }
    }

    private String convertLegacyToMiniMessage(String text) {
        return text
                .replace("§0", "<black>")
                .replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>")
                .replace("§5", "<dark_purple>")
                .replace("§6", "<gold>")
                .replace("§7", "<gray>")
                .replace("§8", "<dark_gray>")
                .replace("§9", "<blue>")
                .replace("§a", "<green>")
                .replace("§b", "<aqua>")
                .replace("§c", "<red>")
                .replace("§d", "<light_purple>")
                .replace("§e", "<yellow>")
                .replace("§f", "<white>")
                .replace("§k", "<obfuscated>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>")
                .replace("§r", "<reset>");
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

    private int getOpponentElo(Player player, Duel duel, String kitName) {
        for (UUID uuid : duel.getPlayers()) {
            if (!uuid.equals(player.getUniqueId())) {
                return plugin.getStorage().loadElo(uuid, kitName).join();
            }
        }
        return 1000;
    }
}

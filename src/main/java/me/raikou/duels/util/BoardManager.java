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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoardManager {

    private final DuelsPlugin plugin;
    // Cache scoreboards per player for reuse
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();

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
        // Get or create scoreboard for this player
        Scoreboard board = player.getScoreboard();

        // If player has main scoreboard, give them a new one
        if (board == null || board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            // Check if we have a cached board for this player
            board = playerBoards.get(player.getUniqueId());
            if (board == null) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                playerBoards.put(player.getUniqueId(), board);
            }
            player.setScoreboard(board);
        }

        String titleRaw = plugin.getConfig().getString("scoreboard.title",
                "<bold><gradient:#FFD700:#FFA500> DUELS </gradient></bold>");
        net.kyori.adventure.text.Component titleComp = me.raikou.duels.util.MessageUtil.parse(titleRaw);

        Objective obj = board.getObjective("sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("sidebar", Criteria.DUMMY, titleComp);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        } else {
            obj.displayName(titleComp);
        }

        // Clear old entries (entries set by us, leave team entries alone)
        for (String entry : board.getEntries()) {
            // Only reset scores, don't touch teams
            board.resetScores(entry);
        }

        Duel duel = plugin.getDuelManager().getDuel(player);

        List<String> lines;
        if (duel != null) {
            if (duel.isRanked()) {
                lines = plugin.getConfig().getStringList("scoreboard.game-ranked");
            } else {
                lines = plugin.getConfig().getStringList("scoreboard.game");
            }
        } else {
            lines = plugin.getConfig().getStringList("scoreboard.lobby");
        }

        // Unique invisible character codes for each line
        String[] uniqueCodes = { "§a§r", "§b§r", "§c§r", "§d§r", "§e§r", "§f§r", "§0§r", "§1§r", "§2§r", "§3§r",
                "§4§r", "§5§r", "§6§r", "§7§r", "§8§r", "§9§r", "§k§r", "§l§r", "§m§r", "§n§r", "§o§r" };
        int uniqueIndex = 0;

        int score = lines.size();

        for (String line : lines) {
            // Replace Placeholders
            line = line.replace("%date%", java.time.LocalDate.now().toString());
            line = line.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
            line = line.replace("%queue%", plugin.getQueueManager().isInQueue(player) ? "Yes" : "No");
            line = line.replace("%ping%", String.valueOf(player.getPing()));

            // CPS - requires CPSManager
            if (plugin.getCpsManager() != null) {
                line = line.replace("%cps%", String.valueOf(plugin.getCpsManager().getCPS(player)));
            } else {
                line = line.replace("%cps%", "0");
            }

            if (duel != null) {
                line = line.replace("%opponent%", getOpponentName(player, duel));
                line = line.replace("%map%", duel.getArena().getName());

                // Opponent CPS
                java.util.UUID opponentUUID = getOpponentUUID(player, duel);
                if (opponentUUID != null && plugin.getCpsManager() != null) {
                    line = line.replace("%opponent_cps%", String.valueOf(plugin.getCpsManager().getCPS(opponentUUID)));
                } else {
                    line = line.replace("%opponent_cps%", "0");
                }

                // ELO placeholders for ranked
                if (duel.isRanked()) {
                    String kitName = duel.getKitName();
                    int playerElo = plugin.getStorage().loadElo(player.getUniqueId(), kitName).join();
                    int opponentElo = getOpponentElo(player, duel, kitName);
                    line = line.replace("%elo%", String.valueOf(playerElo));
                    line = line.replace("%opponent_elo%", String.valueOf(opponentElo));
                } else {
                    line = line.replace("%elo%", "N/A");
                    line = line.replace("%opponent_elo%", "N/A");
                }
            } else {
                line = line.replace("%opponent%", "None");
                line = line.replace("%map%", "None");
                line = line.replace("%elo%", "N/A");
                line = line.replace("%opponent_elo%", "N/A");
                line = line.replace("%opponent_cps%", "0");
            }

            // Handle the line - check if it's a legacy color code line or MiniMessage
            String legacyLine;
            if (line.contains("§")) {
                // Already legacy format, use directly
                legacyLine = line;
            } else {
                // Convert MiniMessage to legacy
                line = convertLegacyToMiniMessage(line);
                net.kyori.adventure.text.Component comp = me.raikou.duels.util.MessageUtil.parse(line);
                legacyLine = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(comp);
            }

            // Add unique invisible suffix to prevent duplicate entries
            if (uniqueIndex < uniqueCodes.length) {
                legacyLine = legacyLine + uniqueCodes[uniqueIndex];
                uniqueIndex++;
            }

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
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>")
                .replace("§r", "<reset>");
    }

    private String getOpponentName(Player player, Duel duel) {
        for (java.util.UUID uuid : duel.getPlayers()) {
            if (!uuid.equals(player.getUniqueId())) {
                Player opponent = Bukkit.getPlayer(uuid);
                return opponent != null ? opponent.getName() : "Unknown";
            }
        }
        return "Unknown";
    }

    private java.util.UUID getOpponentUUID(Player player, Duel duel) {
        for (java.util.UUID uuid : duel.getPlayers()) {
            if (!uuid.equals(player.getUniqueId())) {
                return uuid;
            }
        }
        return null;
    }

    private int getOpponentElo(Player player, Duel duel, String kitName) {
        java.util.UUID opponentUUID = getOpponentUUID(player, duel);
        if (opponentUUID != null) {
            return plugin.getStorage().loadElo(opponentUUID, kitName).join();
        }
        return 1000;
    }

    /**
     * Clean up player's cached scoreboard on quit
     */
    public void cleanup(Player player) {
        playerBoards.remove(player.getUniqueId());
    }
}

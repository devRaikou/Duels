package me.raikou.duels.discord;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DiscordManager {

    private final DuelsPlugin plugin;
    private final DiscordLogger logger;

    public DiscordManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.logger = new DiscordLogger(plugin);
    }

    public void onDuelStart(Duel duel, String kitName) {
        if (!plugin.getConfig().getBoolean("discord.events.duel-start", true))
            return;

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Arena", duel.getArena().getName());
        fields.put("Kit", kitName);
        fields.put("Players", duel.getPlayers().size() + "");

        String playerNames = duel.getPlayers().stream()
                .map(uuid -> org.bukkit.Bukkit.getPlayer(uuid))
                .filter(p -> p != null)
                .map(Player::getName)
                .collect(Collectors.joining(" vs "));

        logger.log("⚔️ Duel Started", "**" + playerNames + "** have started a duel!", Color.YELLOW, fields);
    }

    public void onDuelEnd(Duel duel, UUID winnerId) {
        if (!plugin.getConfig().getBoolean("discord.events.duel-end", true))
            return;

        String winnerName = "None";
        if (winnerId != null) {
            Player p = org.bukkit.Bukkit.getPlayer(winnerId);
            if (p != null)
                winnerName = p.getName();
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Arena", duel.getArena().getName());
        fields.put("Winner", winnerName);

        long durationMillis = System.currentTimeMillis() - duel.getStartTime();
        long seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMillis);
        fields.put("Duration", seconds + "s");

        logger.log("🏆 Duel Ended", "Winner: **" + winnerName + "**", Color.GREEN, fields);
    }

    public void onPlayerJoin(Player player) {
        if (!plugin.getConfig().getBoolean("discord.events.player-join", true))
            return;

        logger.log("Player Joined", player.getName() + " has joined the server.", Color.CYAN, null);
    }

    public void onPlayerQuit(Player player) {
        if (!plugin.getConfig().getBoolean("discord.events.player-quit", true))
            return;

        logger.log("Player Left", player.getName() + " has left the server.", Color.RED, null);
    }

    /**
     * Send a custom embed (used by AntiCheat)
     */
    public void sendEmbed(String title, String description, int color) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false))
            return;

        logger.log(title, description, new Color(color), null);
    }
}

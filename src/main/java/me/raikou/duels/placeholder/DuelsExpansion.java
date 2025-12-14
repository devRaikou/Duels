package me.raikou.duels.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.leaderboard.LeaderboardEntry;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for Duels plugin.
 * 
 * Available placeholders:
 * %duels_top_1_name% - Name of player at position 1
 * %duels_top_1_wins% - Wins of player at position 1
 * %duels_top_1_losses% - Losses of player at position 1
 * %duels_top_1_kills% - Kills of player at position 1
 * %duels_top_1_deaths% - Deaths of player at position 1
 * (Supports positions 1-10)
 */
public class DuelsExpansion extends PlaceholderExpansion {

    private final DuelsPlugin plugin;

    public DuelsExpansion(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Raikou";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "duels";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Survive reloads
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Parse placeholders like: top_1_name, top_2_wins, etc.
        if (params.startsWith("top_")) {
            String[] parts = params.split("_");
            if (parts.length >= 3) {
                try {
                    int position = Integer.parseInt(parts[1]);
                    String stat = parts[2];

                    LeaderboardEntry entry = plugin.getLeaderboardManager().getTopPlayer(position);
                    if (entry == null) {
                        return "---";
                    }

                    return switch (stat.toLowerCase()) {
                        case "name" -> entry.name() != null && !entry.name().isEmpty()
                                ? entry.name()
                                : "Unknown";
                        case "wins" -> String.valueOf(entry.wins());
                        case "losses" -> String.valueOf(entry.losses());
                        case "kills" -> String.valueOf(entry.kills());
                        case "deaths" -> String.valueOf(entry.deaths());
                        case "winrate" -> String.format("%.1f%%", entry.getWinRate());
                        case "kd" -> String.format("%.2f", entry.getKDRatio());
                        default -> null;
                    };
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        return null;
    }
}

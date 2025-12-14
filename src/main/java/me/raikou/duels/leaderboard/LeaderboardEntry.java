package me.raikou.duels.leaderboard;

import java.util.UUID;

/**
 * Represents an entry in the leaderboard.
 */
public record LeaderboardEntry(
        UUID uuid,
        String name,
        int wins,
        int losses,
        int kills,
        int deaths) {
    public double getWinRate() {
        int total = wins + losses;
        return total > 0 ? (double) wins / total * 100 : 0;
    }

    public double getKDRatio() {
        return deaths > 0 ? (double) kills / deaths : kills;
    }
}

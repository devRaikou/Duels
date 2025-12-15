package me.raikou.duels.leaderboard;

import java.util.UUID;

/**
 * Represents an entry in the leaderboard with comprehensive statistics.
 */
public record LeaderboardEntry(
        UUID uuid,
        String name,
        int wins,
        int losses,
        int kills,
        int deaths,
        int currentStreak,
        int bestStreak,
        long lastPlayed,
        long playtime) {

    /**
     * Get the total number of games played.
     */
    public int getTotalGames() {
        return wins + losses;
    }

    /**
     * Get the win rate as a percentage.
     */
    public double getWinRate() {
        int total = wins + losses;
        return total > 0 ? (double) wins / total * 100 : 0;
    }

    /**
     * Get the K/D ratio.
     */
    public double getKDRatio() {
        return deaths > 0 ? (double) kills / deaths : kills;
    }

    /**
     * Check if this player has any games played.
     */
    public boolean hasPlayed() {
        return wins > 0 || losses > 0;
    }
}

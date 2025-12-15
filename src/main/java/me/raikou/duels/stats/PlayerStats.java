package me.raikou.duels.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Holds comprehensive player statistics for duels.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerStats {
    private int wins;
    private int losses;
    private int kills;
    private int deaths;
    private int currentStreak; // Current win streak (negative for loss streak)
    private int bestStreak; // Best win streak ever
    private long lastPlayed; // Timestamp of last duel
    private long playtime; // Total playtime in milliseconds

    /**
     * Constructor for basic stats (backwards compatibility).
     */
    public PlayerStats(int wins, int losses, int kills, int deaths) {
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.deaths = deaths;
        this.currentStreak = 0;
        this.bestStreak = 0;
        this.lastPlayed = 0;
        this.playtime = 0;
    }

    /**
     * Get total games played.
     */
    public int getTotalGames() {
        return wins + losses;
    }

    /**
     * Get win rate percentage.
     */
    public double getWinRate() {
        int total = getTotalGames();
        return total > 0 ? (double) wins / total * 100 : 0;
    }

    /**
     * Get K/D ratio.
     */
    public double getKDRatio() {
        return deaths > 0 ? (double) kills / deaths : kills;
    }

    /**
     * Record a win and update streak.
     */
    public void recordWin() {
        wins++;
        kills++; // Winner gets the kill

        if (currentStreak >= 0) {
            currentStreak++;
        } else {
            currentStreak = 1;
        }

        if (currentStreak > bestStreak) {
            bestStreak = currentStreak;
        }

        lastPlayed = System.currentTimeMillis();
    }

    /**
     * Record a loss and update streak.
     */
    public void recordLoss() {
        losses++;
        deaths++;

        if (currentStreak <= 0) {
            currentStreak--;
        } else {
            currentStreak = -1;
        }

        lastPlayed = System.currentTimeMillis();
    }
}

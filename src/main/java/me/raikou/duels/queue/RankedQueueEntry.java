package me.raikou.duels.queue;

import lombok.Getter;
import java.util.UUID;

@Getter
public class RankedQueueEntry {
    private final UUID player;
    private final String kitName;
    private final int elo;
    private final long queueTime;

    public RankedQueueEntry(UUID player, String kitName, int elo) {
        this.player = player;
        this.kitName = kitName;
        this.elo = elo;
        this.queueTime = System.currentTimeMillis();
    }

    /**
     * Get the current allowed ELO range based on time in queue.
     * Starts at 100, expands by 50 every 10 seconds, max 500.
     */
    public int getAllowedRange(int initialRange, int expansion, int maxRange) {
        long secondsInQueue = (System.currentTimeMillis() - queueTime) / 1000;
        int expansions = (int) (secondsInQueue / 10);
        return Math.min(initialRange + (expansions * expansion), maxRange);
    }

    public boolean canMatchWith(RankedQueueEntry other, int initialRange, int expansion, int maxRange) {
        int myRange = getAllowedRange(initialRange, expansion, maxRange);
        int theirRange = other.getAllowedRange(initialRange, expansion, maxRange);
        int maxAllowedRange = Math.max(myRange, theirRange);
        return Math.abs(this.elo - other.elo) <= maxAllowedRange;
    }
}

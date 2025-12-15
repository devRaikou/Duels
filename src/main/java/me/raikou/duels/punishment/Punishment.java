package me.raikou.duels.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Punishment {
    private int id;
    private UUID uuid;
    private String playerName;
    private String issuerName; // Name of staff or "Console"
    private PunishmentType type;
    private String reason;
    private long timestamp; // When it happened
    private long duration; // How long it lasts (0 = permanent)
    private boolean active;
    private boolean removed;
    private String removedBy;
    private String removedReason;

    // Check if expired
    public boolean isExpired() {
        if (!active || removed)
            return true;
        if (duration <= 0)
            return false; // Permanent
        return System.currentTimeMillis() > timestamp + duration;
    }

    public long getExpirationTime() {
        if (duration <= 0)
            return -1;
        return timestamp + duration;
    }
}

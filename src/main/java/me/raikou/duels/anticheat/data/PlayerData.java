package me.raikou.duels.anticheat.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.UUID;

/**
 * Validates and stores player data for the anti-cheat system.
 * This object is created when a player joins or enters a duel,
 * and is destroyed when they leave.
 */
public class PlayerData {

    private final UUID uuid;

    // Movement tracking
    private Location lastLocation;
    private Location lastOnGroundLocation;
    private boolean onGround;
    private long lastOnGroundTime;
    private int airTicks;

    // Combat tracking
    private long lastAttackTime;
    private final LinkedList<Long> clickSamples = new LinkedList<>();
    private long lastVelocityTaken;

    // Ping/Latency
    private int ping;
    private long lastPingUpdate;

    public PlayerData(Player player) {
        this.uuid = player.getUniqueId();
        this.lastLocation = player.getLocation();
        this.lastOnGroundLocation = player.getLocation();
        this.onGround = true;
        this.lastOnGroundTime = System.currentTimeMillis();
    }

    public void update(Player player) {
        // Update basic tracking
        this.lastLocation = player.getLocation();

        // Simple ping estimation or NMS fetch (abstracted here)
        if (System.currentTimeMillis() - lastPingUpdate > 2000) {
            this.ping = player.getPing();
            this.lastPingUpdate = System.currentTimeMillis();
        }
    }

    public void reset() {
        this.airTicks = 0;
        this.clickSamples.clear();
        this.lastAttackTime = 0;
        this.lastAttackTime = 0;
        this.onGround = true;
    }

    // Getters and Setters
    public UUID getUuid() {
        return uuid;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location lastLocation) {
        this.lastLocation = lastLocation;
    }

    public Location getLastOnGroundLocation() {
        return lastOnGroundLocation;
    }

    public void setLastOnGroundLocation(Location lastOnGroundLocation) {
        this.lastOnGroundLocation = lastOnGroundLocation;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        if (onGround)
            this.lastOnGroundTime = System.currentTimeMillis();
    }

    public long getLastOnGroundTime() {
        return lastOnGroundTime;
    }

    public int getAirTicks() {
        return airTicks;
    }

    public void setAirTicks(int airTicks) {
        this.airTicks = airTicks;
    }

    public long getLastAttackTime() {
        return lastAttackTime;
    }

    public void setLastAttackTime(long lastAttackTime) {
        this.lastAttackTime = lastAttackTime;
    }

    public void addClick() {
        long now = System.currentTimeMillis();
        clickSamples.add(now);
        // Keep last 20 clicks for analysis
        if (clickSamples.size() > 20) {
            clickSamples.removeFirst();
        }
    }

    public LinkedList<Long> getClickSamples() {
        return clickSamples;
    }

    public long getLastVelocityTaken() {
        return lastVelocityTaken;
    }

    public void setLastVelocityTaken(long lastVelocityTaken) {
        this.lastVelocityTaken = lastVelocityTaken;
    }

    public int getPing() {
        return ping;
    }
}

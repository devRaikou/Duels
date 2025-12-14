package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Improved flight detection with multiple methods:
 * - Air time tracking
 * - Vertical velocity analysis
 * - Ground distance checking
 */
public class FlightCheck extends AbstractCheck {

    private final Map<UUID, Long> airTimeStart = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Double> lastY = new HashMap<>();

    private static final int MAX_AIR_TICKS = 40; // 2 seconds without touching ground
    private static final double SUSPICIOUS_Y_INCREASE = 0.5; // blocks per check

    public FlightCheck(DuelsPlugin plugin) {
        super(plugin, "Flight", CheckType.MOVEMENT, "flight");
    }

    @Override
    public boolean check(Player player, Object... data) {
        if (!isEnabled())
            return false;

        UUID uuid = player.getUniqueId();

        // Skip if in creative/spectator mode
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            reset(uuid);
            return false;
        }

        // Skip if player has allowed fly
        if (player.getAllowFlight() || player.isFlying()) {
            reset(uuid);
            return false;
        }

        // Skip if player has levitation/slow falling
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) ||
                player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            reset(uuid);
            return false;
        }

        // Skip if player is in water/lava/climbing
        if (player.isInWater() || isInLiquid(player) || isClimbing(player)) {
            reset(uuid);
            return false;
        }

        Location loc = player.getLocation();
        double currentY = loc.getY();

        // Check if player is on ground or near ground
        boolean onGround = isOnGroundCustom(player);

        if (onGround) {
            reset(uuid);
            return false;
        }

        // Player is in air - start tracking
        int ticks = airTicks.getOrDefault(uuid, 0) + 1;
        airTicks.put(uuid, ticks);

        // Track Y position changes
        double previousY = lastY.getOrDefault(uuid, currentY);
        lastY.put(uuid, currentY);

        double yDiff = currentY - previousY;

        // Detection 1: Too long in air without ground
        if (ticks > MAX_AIR_TICKS) {
            return true;
        }

        // Detection 2: Ascending without jump (after initial jump ticks)
        // Normal jump: ~12 ticks of ascending, then falling
        if (ticks > 20 && yDiff > 0.1) {
            // Player is still going UP after 1 second - suspicious
            return true;
        }

        // Detection 3: Hovering (Y not changing but in air)
        if (ticks > 15 && Math.abs(yDiff) < 0.01 && !hasBlocksNearby(player)) {
            // Player is hovering in place
            return true;
        }

        // Detection 4: Consistent upward movement
        if (yDiff > SUSPICIOUS_Y_INCREASE) {
            // Moving up too fast
            return true;
        }

        return false;
    }

    private void reset(UUID uuid) {
        airTicks.remove(uuid);
        airTimeStart.remove(uuid);
        lastY.remove(uuid);
    }

    /**
     * Custom ground check - more reliable than Bukkit's
     */
    private boolean isOnGroundCustom(Player player) {
        Location loc = player.getLocation();

        // Check blocks directly below player
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                Block below = loc.clone().add(x, -0.1, z).getBlock();
                if (below.getType().isSolid()) {
                    return true;
                }
                // Also check slightly below feet
                Block belowFeet = loc.clone().add(x, -0.5, z).getBlock();
                if (belowFeet.getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBlocksNearby(Player player) {
        Location loc = player.getLocation();
        // Check in a small radius for any solid blocks
        for (int x = -1; x <= 1; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isInLiquid(Player player) {
        Material type = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType();
        return type == Material.WATER || type == Material.LAVA ||
                below == Material.WATER || below == Material.LAVA;
    }

    private boolean isClimbing(Player player) {
        Material type = player.getLocation().getBlock().getType();
        return type == Material.LADDER || type == Material.VINE ||
                type == Material.WEEPING_VINES || type == Material.TWISTING_VINES ||
                type == Material.WEEPING_VINES_PLANT || type == Material.TWISTING_VINES_PLANT ||
                type == Material.SCAFFOLDING;
    }

    public void cleanup(UUID uuid) {
        reset(uuid);
    }
}

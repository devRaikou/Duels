package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import me.raikou.duels.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Strict flight detection.
 * - Air time analysis
 * - Hover detection
 * - Upward velocity limits
 */
public class FlightCheck extends AbstractCheck {

    private static final int MAX_AIR_TICKS = 60; // 3 seconds (generous for knockback)

    public FlightCheck(DuelsPlugin plugin) {
        super(plugin, "Flight", CheckType.MOVEMENT, "flight");
    }

    @Override
    public boolean onMove(Player player, Location to, Location from, PlayerData data) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getAllowFlight() || player.isFlying()) {
            data.setAirTicks(0);
            return false;
        }

        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.isGliding() || player.isRiptiding()) {
            data.setAirTicks(0);
            return false;
        }

        boolean onGround = isOnGround(player);
        data.setOnGround(onGround);

        if (onGround) {
            data.setAirTicks(0);
            return false;
        }

        // Increment air ticks
        int airTicks = data.getAirTicks() + 1;
        data.setAirTicks(airTicks);

        double distY = to.getY() - from.getY();

        // 1. Hover Check
        if (airTicks > 10 && Math.abs(distY) < 0.005) {
            // Not falling despite being in air?
            // Verify no blocks nearby (cobwebs, ladders, honey)
            if (!isNearClimbable(player)) {
                return true;
            }
        }

        // 2. Rising invalidly
        if (distY > 0.5 && airTicks > 5) {
            // Rising too fast after initial jump
            if (!player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
                return true;
            }
        }

        // 3. Too long in air
        if (airTicks > MAX_AIR_TICKS) {
            // Maybe extreme knockback?
            // But 3 seconds is a LOT of air time for vanilla PvP.
            return true;
        }

        return false;
    }

    private boolean isOnGround(Player player) {
        // Simple bounding box check
        Location loc = player.getLocation();
        if (loc.getBlock().getRelative(0, -1, 0).getType().isSolid())
            return true;

        // Detailed check for edges
        return !loc.getBlock().getRelative(0, -1, 0).getCollisionShape().getBoundingBoxes().isEmpty();
    }

    private boolean isNearClimbable(Player player) {
        Location loc = player.getLocation();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block b = loc.getBlock().getRelative(x, y, z);
                    Material type = b.getType();
                    if (type == Material.LADDER || type == Material.VINE || type == Material.COBWEB
                            || type == Material.SCAFFOLDING)
                        return true;
                }
            }
        }
        return false;
    }
}

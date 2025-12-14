package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Improved reach detection with strict distance checking
 * Vanilla reach is 3.0 blocks, we allow small buffer for lag
 */
public class ReachCheck extends AbstractCheck {

    // Vanilla reach is 3.0, creative is 5.0
    // We use 3.1 for strict checking (tiny buffer for normal play)
    private static final double MAX_REACH = 3.1;
    private static final double STRICT_MAX_REACH = 3.5; // Flag immediately above this

    private final Map<UUID, Integer> reachFlags = new HashMap<>();

    public ReachCheck(DuelsPlugin plugin) {
        super(plugin, "Reach", CheckType.COMBAT, "reach");
    }

    @Override
    public boolean check(Player player, Object... data) {
        if (!isEnabled())
            return false;

        if (data.length < 1 || !(data[0] instanceof Player))
            return false;

        Player target = (Player) data[0];
        UUID uuid = player.getUniqueId();

        // Calculate distance between attacker's eye and target's hitbox
        Location attackerEye = player.getEyeLocation();
        Location targetLoc = target.getLocation();

        // Get closest point on target's hitbox
        double targetHalfWidth = 0.3; // Player hitbox is 0.6 wide
        double distance = getDistanceToHitbox(attackerEye, targetLoc, targetHalfWidth, target.getHeight());

        // Strict check - instant flag
        if (distance > STRICT_MAX_REACH) {
            return true;
        }

        // Normal check with accumulation
        if (distance > MAX_REACH) {
            int flags = reachFlags.getOrDefault(uuid, 0) + 1;
            reachFlags.put(uuid, flags);

            // Flag after 2 consecutive reach violations
            if (flags >= 2) {
                reachFlags.put(uuid, 0);
                return true;
            }
        } else {
            // Reset if a normal hit
            reachFlags.put(uuid, 0);
        }

        return false;
    }

    /**
     * Calculate distance from a point to a player's hitbox
     */
    private double getDistanceToHitbox(Location from, Location targetBase, double halfWidth, double height) {
        // Player hitbox dimensions: 0.6 x 1.8 (width x height)
        double minX = targetBase.getX() - halfWidth;
        double maxX = targetBase.getX() + halfWidth;
        double minY = targetBase.getY();
        double maxY = targetBase.getY() + height;
        double minZ = targetBase.getZ() - halfWidth;
        double maxZ = targetBase.getZ() + halfWidth;

        // Find closest point on hitbox to 'from' location
        double closestX = clamp(from.getX(), minX, maxX);
        double closestY = clamp(from.getY(), minY, maxY);
        double closestZ = clamp(from.getZ(), minZ, maxZ);

        // Calculate distance from 'from' to closest point
        double dx = from.getX() - closestX;
        double dy = from.getY() - closestY;
        double dz = from.getZ() - closestZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public void cleanup(UUID uuid) {
        reachFlags.remove(uuid);
    }
}

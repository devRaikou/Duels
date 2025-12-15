package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import me.raikou.duels.anticheat.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Improved reach detection using server-side raytracing and hitbox expansion.
 * Accounts for ping (latency) and movement.
 */
public class ReachCheck extends AbstractCheck {

    private static final double BASE_REACH = 3.1;
    private static final double PING_COMPENSATION_FACTOR = 0.002; // 2ms per 1 ping
    private static final double MOVING_AWAY_BUFFER = 0.3; // Extra buffer if target is running away

    public ReachCheck(DuelsPlugin plugin) {
        super(plugin, "Reach", CheckType.COMBAT, "reach");
    }

    @Override
    public boolean onAttack(Player attacker, org.bukkit.entity.Entity victimEntity, PlayerData data) {
        if (!(victimEntity instanceof Player victim))
            return false;

        if (attacker.getGameMode() == GameMode.CREATIVE)
            return false;

        // 1. Calculate max allowed reach based on ping
        double allowedReach = BASE_REACH;
        allowedReach += Math.min(0.5, data.getPing() * PING_COMPENSATION_FACTOR);

        // 2. Add buffer if target is moving away (simulating "combo" reach)
        Vector victimVelocity = victim.getVelocity();
        Vector attackerDir = attacker.getLocation().getDirection();
        if (victimVelocity.dot(attackerDir) > 0) {
            allowedReach += MOVING_AWAY_BUFFER;
        }

        // 3. Simple Distance Check First (Optimization)
        double simpleDist = attacker.getEyeLocation().distance(victim.getEyeLocation());
        if (simpleDist < allowedReach - 0.5)
            return false; // Definitely safe

        // 4. RayTrace Check (Precision)
        // Expand hitbox slightly for minor sync issues
        BoundingBox box = victim.getBoundingBox().expand(0.1);
        Location eye = attacker.getEyeLocation();

        RayTraceResult result = box.rayTrace(eye.toVector(), eye.getDirection(), 6.0);

        double actualReach;
        if (result != null) {
            actualReach = result.getHitPosition().distance(eye.toVector());
        } else {
            // Fallback: Closest point on box
            actualReach = distanceToBox(eye.toVector(), box);
        }

        if (actualReach > allowedReach) {
            // Flag!
            // We return true to cancel the hit if it's blatant
            return actualReach > allowedReach + 0.5; // Only cancel if > 3.6+ typically
        }

        return false;
    }

    private double distanceToBox(Vector point, BoundingBox box) {
        double dx = Math.max(Math.max(box.getMinX() - point.getX(), point.getX() - box.getMaxX()), 0);
        double dy = Math.max(Math.max(box.getMinY() - point.getY(), point.getY() - box.getMaxY()), 0);
        double dz = Math.max(Math.max(box.getMinZ() - point.getZ(), point.getZ() - box.getMaxZ()), 0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

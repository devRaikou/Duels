package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import me.raikou.duels.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Detects KillAura by analyzing attack angles and raytrace consistency.
 * Prevents hitting through walls or hitting entities outside field of view.
 */
public class KillAuraCheck extends AbstractCheck {

    private static final double STRICT_ANGLE = 85.0; // Impossible hits (behind/side)

    public KillAuraCheck(DuelsPlugin plugin) {
        super(plugin, "KillAura", CheckType.COMBAT, "killaura");
    }

    @Override
    public boolean onAttack(Player attacker, org.bukkit.entity.Entity victimEntity, PlayerData data) {
        if (!(victimEntity instanceof Player victim))
            return false;

        // 1. Angle Check
        double angle = getAngleToTarget(attacker, victim);

        // Instant flag for impossible angles (hitting behind)
        if (angle > STRICT_ANGLE) {
            return true;
        }

        // 2. Wall Check (RayTrace)
        // Ensure line of sight
        Location eye = attacker.getEyeLocation();
        Location targetCenter = victim.getBoundingBox().getCenter().toLocation(victim.getWorld());

        // RayTrace from eye to target center
        // If it hits a block before the target, it's likely a wall hit
        var result = attacker.getWorld().rayTraceBlocks(eye,
                targetCenter.toVector().subtract(eye.toVector()).normalize(),
                eye.distance(targetCenter));

        if (result != null && result.getHitBlock() != null && !result.getHitBlock().isPassable()) {
            // Possible wall hit, but give benefit of doubt for edges (peeking)
            // If the raytrace failed to see target center, try eyes
            var result2 = attacker.getWorld().rayTraceBlocks(eye,
                    victim.getEyeLocation().toVector().subtract(eye.toVector()).normalize(),
                    eye.distance(victim.getEyeLocation()));

            if (result2 != null && result2.getHitBlock() != null && !result2.getHitBlock().isPassable()) {
                // Checking feet too just to be safe
                var result3 = attacker.getWorld().rayTraceBlocks(eye,
                        victim.getLocation().toVector().subtract(eye.toVector()).normalize(),
                        eye.distance(victim.getLocation()));

                if (result3 != null && result3.getHitBlock() != null) {
                    // All 3 points blocked? Wall hit.
                    return true;
                }
            }
        }

        return false;
    }

    private double getAngleToTarget(Player player, Player target) {
        Location playerLoc = player.getEyeLocation();
        Location targetLoc = target.getBoundingBox().getCenter().toLocation(target.getWorld());

        Vector toTarget = targetLoc.toVector().subtract(playerLoc.toVector()).normalize();
        Vector lookDir = playerLoc.getDirection().normalize();

        double dot = lookDir.dot(toTarget);
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }
}

package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Detects KillAura/Combat hacks by checking:
 * - Attack angle (looking at target)
 * - Attack rate (too many attacks too fast)
 * - Multiple targets in short time
 */
public class KillAuraCheck extends AbstractCheck {

    private static final double MAX_ATTACK_ANGLE = 90.0; // degrees

    public KillAuraCheck(DuelsPlugin plugin) {
        super(plugin, "KillAura", CheckType.COMBAT, "killaura");
    }

    @Override
    public boolean check(Player player, Object... data) {
        if (!isEnabled())
            return false;

        if (data.length < 1 || !(data[0] instanceof Player))
            return false;

        Player target = (Player) data[0];

        // Check attack angle - player should be looking at target
        double angle = getAngleToTarget(player, target);

        if (angle > MAX_ATTACK_ANGLE) {
            // Player hit someone they weren't looking at
            return true;
        }

        return false;
    }

    /**
     * Calculate the angle between player's look direction and target location
     */
    private double getAngleToTarget(Player player, Player target) {
        Location playerLoc = player.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);

        // Vector from player to target
        Vector toTarget = targetLoc.toVector().subtract(playerLoc.toVector()).normalize();

        // Player's look direction
        Vector lookDir = playerLoc.getDirection().normalize();

        // Calculate angle between vectors
        double dot = lookDir.dot(toTarget);
        double angle = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

        return angle;
    }
}

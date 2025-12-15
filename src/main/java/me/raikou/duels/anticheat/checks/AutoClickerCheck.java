package me.raikou.duels.anticheat.checks;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.AbstractCheck;
import me.raikou.duels.anticheat.CheckType;
import me.raikou.duels.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.LinkedList;

/**
 * Detects AutoClickers and Macros by analyzing Click Per Second (CPS) patterns.
 * - Max CPS check
 * - Consistency check (Std Dev)
 */
public class AutoClickerCheck extends AbstractCheck {

    private static final int MAX_CPS = 22; // Hard limit for realistic clicking

    public AutoClickerCheck(DuelsPlugin plugin) {
        super(plugin, "AutoClicker", CheckType.COMBAT, "autoclicker");
    }

    // We use onAttack for left clicks that hit entities.
    // Ideally we would use InteractEvent for all clicks, but for now we focus on
    // combat clicks.
    @Override
    public boolean onAttack(Player attacker, org.bukkit.entity.Entity victim, PlayerData data) {
        data.addClick();

        LinkedList<Long> samples = data.getClickSamples();
        if (samples.size() < 10)
            return false;

        // Calculate CPS
        long windowStart = System.currentTimeMillis() - 1000;
        int cps = 0;
        for (Long time : samples) {
            if (time > windowStart)
                cps++;
        }

        if (cps > MAX_CPS) {
            return true;
        }

        // Consistency Check (Zero deviation is impossible for humans)
        if (cps > 10 && samples.size() >= 10) {
            double averageDelay = 0;
            // Calculate delays between clicks
            long[] delays = new long[samples.size() - 1];
            for (int i = 0; i < samples.size() - 1; i++) {
                delays[i] = samples.get(i + 1) - samples.get(i);
                averageDelay += delays[i];
            }
            averageDelay /= delays.length;

            double deviation = 0;
            for (long delay : delays) {
                deviation += Math.pow(delay - averageDelay, 2);
            }
            double stdDev = Math.sqrt(deviation / delays.length);

            // Extremely low standard deviation = macro
            if (stdDev < 5.0) { // < 5ms variance is very robotic
                return true;
            }
        }

        return false;
    }
}

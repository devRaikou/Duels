package me.raikou.duels.anticheat;

import org.bukkit.entity.Player;

/**
 * Interface for all anti-cheat checks.
 * Each check should implement this interface.
 */
public interface Check {

    /**
     * Get the name of this check
     */
    String getName();

    /**
     * Get the check type/category
     */
    CheckType getType();

    /**
     * Check if this check is enabled
     */
    boolean isEnabled();

    /**
     * Process a check on a player
     * 
     * @return true if violation detected
     */
    boolean check(Player player, Object... data);

    /**
     * Get the maximum violations before action
     */
    int getMaxViolations();
}

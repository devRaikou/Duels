package me.raikou.duels.anticheat;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.entity.Player;

/**
 * Base class for all checks with common functionality
 */
public abstract class AbstractCheck implements Check {

    protected final DuelsPlugin plugin;
    protected final String name;
    protected final CheckType type;
    protected final boolean enabled;
    protected final int maxViolations;

    public AbstractCheck(DuelsPlugin plugin, String name, CheckType type, String configPath) {
        this.plugin = plugin;
        this.name = name;
        this.type = type;
        this.enabled = plugin.getConfig().getBoolean("anticheat.checks." + configPath + ".enabled", true);
        this.maxViolations = plugin.getConfig().getInt("anticheat.checks." + configPath + ".max-violations", 5);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CheckType getType() {
        return type;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getMaxViolations() {
        return maxViolations;
    }

    /**
     * Check if player is in a duel (only check during duels)
     */
    protected boolean isInDuel(Player player) {
        return plugin.getDuelManager().getDuel(player) != null;
    }
}

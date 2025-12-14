package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Implements 1.8 combat mechanics in modern Minecraft versions.
 * - Removes attack cooldown (instant attack speed)
 * - Disables sweep attacks
 * - Fishing rod knockback (push away)
 * - Sword blocking (damage reduction only, no animation in 1.21+)
 */
public class CombatListener implements Listener {

    private final DuelsPlugin plugin;
    private final boolean enabled;
    private final Set<UUID> blockingPlayers = new HashSet<>();
    private final Set<UUID> rodCooldown = new HashSet<>();

    public CombatListener(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("combat.legacy-pvp", true);

        if (enabled) {
            plugin.getLogger().info("1.8 Legacy PvP combat system enabled!");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled)
            return;
        applyLegacyCombat(event.getPlayer());
    }

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        if (!enabled)
            return;
        Player player = event.getPlayer();
        // Stop blocking when switching items
        blockingPlayers.remove(player.getUniqueId());
        // Reapply on item switch to ensure attack speed stays
        applyLegacyCombat(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled)
            return;

        // Disable sweep attack damage
        if (event.getCause() == EntityDamageByEntityEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            return;
        }

        // Reduce damage if player is blocking with sword
        if (event.getEntity() instanceof Player victim) {
            if (blockingPlayers.contains(victim.getUniqueId())) {
                // Reduce damage by 50% when blocking
                event.setDamage(event.getDamage() * 0.5);
            }
        }
    }

    /**
     * Cancel the vanilla rod pull behavior when reeling catches a player
     * Don't cancel the event (would prevent hook retraction), just reset velocity
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (!enabled)
            return;

        Player fisher = event.getPlayer();

        // Only work in duels
        if (plugin.getDuelManager().getDuel(fisher) == null)
            return;

        // Cancel the pull velocity when reeling in a caught player
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (event.getCaught() instanceof Player target) {
                // Don't cancel event (would prevent hook retraction)
                // Instead, reset the player's velocity on next tick
                Vector currentVel = target.getVelocity();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    target.setVelocity(currentVel);
                });
            }
        }
    }

    /**
     * Fishing rod knockback - PUSH players AWAY when hook hits them
     * Only uses ProjectileHitEvent to avoid the pull effect on reel-in
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!enabled)
            return;

        if (event.getEntity() instanceof FishHook hook) {
            if (hook.getShooter() instanceof Player fisher) {
                // Only work in duels
                if (plugin.getDuelManager().getDuel(fisher) == null)
                    return;

                Entity hitEntity = event.getHitEntity();
                if (hitEntity instanceof Player target && !target.equals(fisher)) {
                    // Prevent double knockback with cooldown
                    if (rodCooldown.contains(target.getUniqueId()))
                        return;

                    applyRodKnockback(fisher, target);

                    // Add brief cooldown
                    rodCooldown.add(target.getUniqueId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        rodCooldown.remove(target.getUniqueId());
                    }, 10L);
                }
            }
        }
    }

    private void applyRodKnockback(Player fisher, Player target) {
        // Calculate direction from fisher to target (push away)
        Vector knockback = target.getLocation().toVector()
                .subtract(fisher.getLocation().toVector());

        // Only use horizontal components for direction
        knockback.setY(0);

        // Normalize and apply knockback values
        if (knockback.lengthSquared() > 0) {
            knockback.normalize();
        }

        // Stronger 1.8-style rod knockback
        double horizontalStrength = 0.5;
        double verticalStrength = 0.35;

        Vector finalVelocity = knockback.multiply(horizontalStrength).setY(verticalStrength);

        // Apply velocity on next tick, set directly for stronger effect
        Bukkit.getScheduler().runTask(plugin, () -> {
            target.setVelocity(finalVelocity);
        });
    }

    /**
     * Sword blocking - right-click with sword to block
     * No animation in 1.21+, only damage reduction
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwordBlock(PlayerInteractEvent event) {
        if (!enabled)
            return;

        Player player = event.getPlayer();

        // Only work in duels
        if (plugin.getDuelManager().getDuel(player) == null)
            return;

        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if holding a sword and right-clicking
        if (item != null && isSword(item.getType())) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // Start blocking (no animation, just damage reduction)
                if (!blockingPlayers.contains(player.getUniqueId())) {
                    blockingPlayers.add(player.getUniqueId());

                    // Auto-stop blocking after 1.5 seconds
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            blockingPlayers.remove(player.getUniqueId());
                        }
                    }.runTaskLater(plugin, 30L);
                }
                event.setCancelled(true);
            }
        }
    }

    private boolean isSword(Material material) {
        return material == Material.WOODEN_SWORD ||
                material == Material.STONE_SWORD ||
                material == Material.IRON_SWORD ||
                material == Material.GOLDEN_SWORD ||
                material == Material.DIAMOND_SWORD ||
                material == Material.NETHERITE_SWORD;
    }

    /**
     * Apply 1.8 combat mechanics to a player.
     * Sets attack speed to maximum (removes cooldown).
     */
    public void applyLegacyCombat(Player player) {
        if (!enabled)
            return;

        // Set attack speed to very high value (removes cooldown)
        try {
            AttributeInstance attackSpeed = player.getAttribute(Attribute.valueOf("GENERIC_ATTACK_SPEED"));
            if (attackSpeed != null) {
                attackSpeed.setBaseValue(1024.0);
            }
        } catch (IllegalArgumentException e) {
            try {
                AttributeInstance attackSpeed = player.getAttribute(Attribute.valueOf("ATTACK_SPEED"));
                if (attackSpeed != null) {
                    attackSpeed.setBaseValue(1024.0);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Could not apply legacy combat - ATTACK_SPEED attribute not found!");
            }
        }
    }

    /**
     * Reset player to default combat mechanics.
     */
    public void resetCombat(Player player) {
        blockingPlayers.remove(player.getUniqueId());
        try {
            AttributeInstance attackSpeed = player.getAttribute(Attribute.valueOf("GENERIC_ATTACK_SPEED"));
            if (attackSpeed != null) {
                attackSpeed.setBaseValue(4.0);
            }
        } catch (IllegalArgumentException e) {
            try {
                AttributeInstance attackSpeed = player.getAttribute(Attribute.valueOf("ATTACK_SPEED"));
                if (attackSpeed != null) {
                    attackSpeed.setBaseValue(4.0);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public boolean isBlocking(Player player) {
        return blockingPlayers.contains(player.getUniqueId());
    }
}

package me.raikou.duels.lobby;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles double-jump feature for players in the lobby.
 * When players press jump twice, they are launched forward with effects.
 */
public class DoubleJumpHandler implements Listener {

    private final DuelsPlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // Configuration
    private final boolean enabled;
    private final double horizontalMultiplier;
    private final double verticalMultiplier;
    private final long cooldownMs;

    public DoubleJumpHandler(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("lobby.double-jump.enabled", true);
        this.horizontalMultiplier = plugin.getConfig().getDouble("lobby.double-jump.horizontal-multiplier", 1.2);
        this.verticalMultiplier = plugin.getConfig().getDouble("lobby.double-jump.vertical-multiplier", 0.8);
        this.cooldownMs = plugin.getConfig().getLong("lobby.double-jump.cooldown-ms", 500);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Enable flight for player when they land on ground in lobby.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled)
            return;

        Player player = event.getPlayer();

        // Only in lobby (not in duel and not spectating)
        if (plugin.getDuelManager().isInDuel(player))
            return;
        if (plugin.getSpectatorManager().isSpectating(player))
            return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
            return;

        // Check if on ground and can enable flight
        if (player.isOnGround() && !player.isFlying()) {
            player.setAllowFlight(true);
        }
    }

    /**
     * Handle double-jump when player toggles flight (by pressing jump in air).
     */
    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!enabled)
            return;

        Player player = event.getPlayer();

        // Only in lobby (not in duel and not spectating)
        if (plugin.getDuelManager().isInDuel(player))
            return;
        if (plugin.getSpectatorManager().isSpectating(player))
            return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
            return;

        // This is a double-jump attempt
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        // Check cooldown
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid) && (now - cooldowns.get(uuid)) < cooldownMs) {
            return; // Still on cooldown
        }
        cooldowns.put(uuid, now);

        // Calculate launch direction (forward + up)
        Vector direction = player.getLocation().getDirection();
        direction.setY(0).normalize();

        // Apply velocity
        Vector velocity = direction.multiply(horizontalMultiplier);
        velocity.setY(verticalMultiplier);
        player.setVelocity(velocity);

        // Play effects
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                player.getLocation().add(0, 0.5, 0),
                20, 0.3, 0.1, 0.3, 0.05);
        player.getWorld().spawnParticle(
                Particle.FIREWORK,
                player.getLocation().add(0, 0.5, 0),
                10, 0.2, 0.1, 0.2, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1.5f);
    }

    /**
     * Enable flight when player joins.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled)
            return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            // Delay slightly to ensure player is positioned
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.getDuelManager().isInDuel(player) && !plugin.getSpectatorManager().isSpectating(player)) {
                    player.setAllowFlight(true);
                }
            }, 5L);
        }
    }

    /**
     * Cleanup on player quit.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Disable flight for a player (called when entering duel).
     */
    public void disableDoubleJump(Player player) {
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    /**
     * Enable double-jump for a player (called when leaving duel/returning to
     * lobby).
     */
    public void enableDoubleJump(Player player) {
        if (!enabled)
            return;
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
    }
}

package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class DuelListener implements Listener {

    private final DuelsPlugin plugin;

    public DuelListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getQueueManager().removeFromQueue(player);

        Duel duel = plugin.getDuelManager().getDuel(player);
        if (duel != null) {
            // Assume quitting = lose
            // Find winner
            UUID winner = null;
            for (UUID uuid : duel.getPlayers()) {
                if (!uuid.equals(player.getUniqueId())) {
                    winner = uuid;
                    break;
                }
            }
            if (winner != null) {
                duel.end(winner);
            } else {
                // Should not happen in 1v1
                duel.end(null);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Duel duel = plugin.getDuelManager().getDuel(victim);

        if (duel != null) {
            event.setCancelled(true); // Custom handling
            // Find killer/winner
            UUID winner = null;
            for (UUID uuid : duel.getPlayers()) {
                if (!uuid.equals(victim.getUniqueId())) {
                    winner = uuid;
                    break;
                }
            }
            duel.end(winner);
            // Respawn logic or spectacle logic here
            victim.spigot().respawn();
            victim.teleport(duel.getArena().getSpectatorSpawn());
        }
    }
}

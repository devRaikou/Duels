package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import org.bukkit.event.player.PlayerJoinEvent;

public class DuelListener implements Listener {

    private final DuelsPlugin plugin;

    public DuelListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getStatsManager().loadStats(player);
        if (plugin.getLobbyManager().isLobbySet()) {
            plugin.getLobbyManager().teleportToLobby(player);
            plugin.getLobbyManager().giveLobbyItems(player);
        }
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Give items if respawning in lobby context, or just generally allow them to
        // have it.
        // For now, always give on respawn if we are in lobby mode or not in game
        if (plugin.getDuelManager().getDuel(player) == null) {
            plugin.getLobbyManager().giveLobbyItems(player);
        }
    }

    @EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction().name().contains("RIGHT")) {
            org.bukkit.inventory.ItemStack item = event.getItem();
            if (item != null) {
                if (item.getType() == org.bukkit.Material.COMPASS) {
                    if (item.getItemMeta().displayName() != null) {
                        plugin.getGuiManager().openQueueGui(event.getPlayer());
                        event.setCancelled(true);
                    }
                } else if (item.getType() == org.bukkit.Material.BOOK) {
                    if (item.getItemMeta().displayName() != null) {
                        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText().serialize(item.getItemMeta().displayName());
                        if (plainName.contains("Kit Editor")) {
                            plugin.getKitEditorManager().openEditorSelectionGui(event.getPlayer());
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getStatsManager().saveStats(player);
        plugin.getQueueManager().removeFromQueue(player);
        plugin.getKitEditorManager().cancelEditing(player);

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

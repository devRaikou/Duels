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
        plugin.getDiscordManager().onPlayerJoin(player);
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
            if (item != null && item.getItemMeta() != null && item.getItemMeta().displayName() != null) {
                String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(item.getItemMeta().displayName());

                // Unranked - Iron Sword
                if (item.getType() == org.bukkit.Material.IRON_SWORD && plainName.contains("Unranked")) {
                    plugin.getGuiManager().openQueueGui(event.getPlayer(), me.raikou.duels.queue.QueueType.SOLO);
                    event.setCancelled(true);
                }
                // Ranked - Diamond Sword
                else if (item.getType() == org.bukkit.Material.DIAMOND_SWORD && plainName.contains("Ranked")) {
                    plugin.getGuiManager().openQueueGui(event.getPlayer(), me.raikou.duels.queue.QueueType.RANKED);
                    event.setCancelled(true);
                }
                // Kit Editor - Book
                else if (item.getType() == org.bukkit.Material.BOOK && plainName.contains("Kit Editor")) {
                    plugin.getKitEditorManager().openEditorSelectionGui(event.getPlayer());
                    event.setCancelled(true);
                }
                // Leave Queue - Red Dye
                else if (item.getType() == org.bukkit.Material.RED_DYE && plainName.contains("Leave Queue")) {
                    plugin.getQueueManager().removeFromQueue(event.getPlayer());
                    event.setCancelled(true);
                }
                // Leaderboard - Knowledge Book
                else if (item.getType() == org.bukkit.Material.KNOWLEDGE_BOOK && plainName.contains("Leaderboard")) {
                    plugin.getLeaderboardCommand().openLeaderboardGui(event.getPlayer());
                    event.setCancelled(true);
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
        plugin.getDiscordManager().onPlayerQuit(player);

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

    // --- Lobby Protection ---

    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getDuelManager().getDuel(player) == null) {
                event.setCancelled(true);
                if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
                    if (plugin.getLobbyManager().isLobbySet()) {
                        plugin.getLobbyManager().teleportToLobby(player);
                    } else {
                        player.teleport(org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onFood(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getDuelManager().getDuel(player) == null) {
                event.setCancelled(true);
                player.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getDuelManager().getDuel(player) == null) {
                // Allow kit editor
                if (plugin.getKitEditorManager().isEditing(player))
                    return;
                // Allow specific bypass permission? No, full lock for now.
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && player.isOp())
                    return;
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (plugin.getDuelManager().getDuel(event.getPlayer()) == null) {
            if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE && event.getPlayer().isOp())
                return;
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getDuelManager().getDuel(player) == null) {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE && player.isOp())
                    return;
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (plugin.getDuelManager().getDuel(event.getPlayer()) == null) {
            if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE && event.getPlayer().isOp())
                return;
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (plugin.getDuelManager().getDuel(event.getPlayer()) == null) {
            if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE && event.getPlayer().isOp())
                return;
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (plugin.getDuelManager().getDuel(event.getPlayer()) != null) {
            String message = event.getMessage().toLowerCase();
            String[] blocked = { "/lobby", "/hub", "/l", "/spawn", "/tp", "/tpa", "/tpahere", "/warp", "/home", "/back",
                    "/gamemode", "/gm", "/fly", "/minecraft:me" };

            // Allow admin bypass
            if (event.getPlayer().hasPermission("duels.admin"))
                return;

            for (String cmd : blocked) {
                if (message.startsWith(cmd + " ") || message.equals(cmd)) {
                    event.setCancelled(true);
                    me.raikou.duels.util.MessageUtil.sendError(event.getPlayer(), "duel.blocked-command");
                    return;
                }
            }
        }
    }
}

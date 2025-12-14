package me.raikou.duels.duel;

import lombok.Getter;
import lombok.Setter;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.arena.Arena;
import me.raikou.duels.arena.ArenaState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Duel {

    private final DuelsPlugin plugin;
    private final org.bukkit.World instanceWorld;
    private final Arena arena;
    private final List<UUID> players;
    private final List<UUID> alivePlayers;
    @Setter
    private DuelState state;
    @Getter
    private long startTime;
    private final String kitName;

    public Duel(DuelsPlugin plugin, Arena arena, List<UUID> players, String kitName, org.bukkit.World instanceWorld) {
        this.plugin = plugin;
        this.arena = arena;
        this.players = players;
        this.kitName = kitName;
        this.alivePlayers = new ArrayList<>(players);
        this.state = DuelState.STARTING;
        this.instanceWorld = instanceWorld;
    }

    public void start() {
        // Teleport players to the instance world locations
        // We map the relative coordinates from template arena to instance world
        Player p1 = Bukkit.getPlayer(players.get(0));
        Player p2 = Bukkit.getPlayer(players.get(1));

        if (p1 != null) {
            Location loc1 = arena.getSpawn1().clone();
            loc1.setWorld(instanceWorld);
            p1.teleport(loc1);
        }
        if (p2 != null) {
            Location loc2 = arena.getSpawn2().clone();
            loc2.setWorld(instanceWorld);
            p2.teleport(loc2);
        }

        // Simple countdown
        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (count == 0) {
                    state = DuelState.FIGHTING;
                    startTime = System.currentTimeMillis();
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.showTitle(net.kyori.adventure.title.Title.title(
                                    me.raikou.duels.util.MessageUtil.getRaw("titles.duel-start.title"),
                                    me.raikou.duels.util.MessageUtil.getRaw("titles.duel-start.subtitle")));
                            p.sendMessage(me.raikou.duels.util.MessageUtil.get("duel.start", "%opponent%",
                                    players.get(0).equals(uuid) ? Bukkit.getPlayer(players.get(1)).getName()
                                            : Bukkit.getPlayer(players.get(0)).getName(),
                                    "%kit%", kitName));
                        }
                    }
                    cancel();
                    return;
                }

                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.showTitle(net.kyori.adventure.title.Title.title(
                                me.raikou.duels.util.MessageUtil.getRaw("titles.countdown.title", "%count%",
                                        String.valueOf(count)),
                                Component.empty()));
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // reset logic
    public void reset() {
        // Teleport to spawn and reset players
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.setHealth(20);
                p.setFoodLevel(20);
                p.setSaturation(20);
                p.setFireTicks(0);
                p.setExp(0);
                p.setLevel(0);
                for (org.bukkit.potion.PotionEffect effect : p.getActivePotionEffects()) {
                    p.removePotionEffect(effect.getType());
                }

                if (plugin.getLobbyManager().isLobbySet()) {
                    plugin.getLobbyManager().teleportToLobby(p);
                    plugin.getLobbyManager().giveLobbyItems(p);
                } else {
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
            }
        }

        // Remove from manager
        plugin.getDuelManager().removeDuel(this);

        // Delete World
        if (instanceWorld != null) {
            plugin.getWorldManager().deleteDuelWorld(instanceWorld.getName());
        }
    }

    public void sendMessageToAll(Component message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null)
                p.sendMessage(message);
        }
    }

    public void end(UUID winner) {
        this.state = DuelState.ENDING;

        Player winnerPlayer = Bukkit.getPlayer(winner);
        String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";

        if (winnerPlayer != null) {
            plugin.getStatsManager().addWin(winnerPlayer);
            plugin.getStatsManager().addKill(winnerPlayer); // Assume winner got the kill
        }

        plugin.getDiscordManager().onDuelEnd(this, winner);

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (!uuid.equals(winner)) {
                    plugin.getStatsManager().addLoss(p);
                    plugin.getStatsManager().addDeath(p);
                }

                p.showTitle(net.kyori.adventure.title.Title.title(
                        me.raikou.duels.util.MessageUtil.getRaw("titles.duel-end.title"),
                        me.raikou.duels.util.MessageUtil.getRaw("titles.duel-end.subtitle", "%winner%", winnerName)));
                if (uuid.equals(winner)) {
                    // Logic to find the opponent name for the winner
                    String opponentName = "Unknown";
                    for (UUID other : players) {
                        if (!other.equals(winner)) {
                            Player op = Bukkit.getPlayer(other);
                            if (op != null)
                                opponentName = op.getName();
                            break;
                        }
                    }
                    p.sendMessage(me.raikou.duels.util.MessageUtil.get("duel.end-winner", "%opponent%",
                            opponentName));
                } else {
                    p.sendMessage(me.raikou.duels.util.MessageUtil.get("duel.end-loser", "%opponent%", winnerName));
                }
            }
        }

        // Cleanup after delay
        new BukkitRunnable() {
            @Override
            public void run() {
                reset();
            }
        }.runTaskLater(plugin, 60L); // 3 seconds
    }

}

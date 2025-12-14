package me.raikou.duels.duel;

import lombok.Getter;
import lombok.Setter;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.arena.Arena;
import me.raikou.duels.arena.ArenaState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Duel {

    private final DuelsPlugin plugin;
    private final Arena arena;
    private final List<UUID> players;
    private final List<UUID> alivePlayers;
    @Setter
    private DuelState state;

    public Duel(DuelsPlugin plugin, Arena arena, List<UUID> players) {
        this.plugin = plugin;
        this.arena = arena;
        this.players = players;
        this.alivePlayers = new ArrayList<>(players);
        this.state = DuelState.STARTING;
    }

    public void start() {
        arena.setState(ArenaState.RUNNING);

        // Teleport players
        Player p1 = Bukkit.getPlayer(players.get(0));
        Player p2 = Bukkit.getPlayer(players.get(1));

        if (p1 != null)
            p1.teleport(arena.getSpawn1());
        if (p2 != null)
            p2.teleport(arena.getSpawn2());

        // Simple countdown
        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (count == 0) {
                    state = DuelState.FIGHTING;
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.showTitle(net.kyori.adventure.title.Title.title(
                                    me.raikou.duels.util.MessageUtil.parse("<green>FIGHT!"),
                                    me.raikou.duels.util.MessageUtil.parse("<gray>Good luck!")));
                            p.sendMessage(me.raikou.duels.util.MessageUtil.prefix("<green>Duel Started!"));
                        }
                    }
                    cancel();
                    return;
                }

                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.showTitle(net.kyori.adventure.title.Title.title(
                                me.raikou.duels.util.MessageUtil.parse("<yellow>" + count),
                                Component.empty()));
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
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
        this.arena.setState(ArenaState.ENDING);

        Player winnerPlayer = Bukkit.getPlayer(winner);
        String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.showTitle(net.kyori.adventure.title.Title.title(
                        me.raikou.duels.util.MessageUtil.parse("<gold>VICTORY!"),
                        me.raikou.duels.util.MessageUtil.parse("<yellow>Winner: <white>" + winnerName)));
                p.sendMessage(
                        me.raikou.duels.util.MessageUtil.prefix("<gold>Duel Ended! Winner: <yellow>" + winnerName));
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

    public void reset() {
        // Teleport to spawn and reset players
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.setHealth(20);
                p.setFoodLevel(20);
                if (plugin.getLobbyManager().isLobbySet()) {
                    plugin.getLobbyManager().teleportToLobby(p);
                } else {
                    p.teleport(arena.getSpectatorSpawn()); // Fallback or basic world spawn
                }
            }
        }

        // Reset Arena
        arena.setState(ArenaState.WAITING);

        // Remove from manager
        plugin.getDuelManager().removeDuel(this);
    }
}

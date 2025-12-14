package me.raikou.duels.queue;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class QueueManager {

    private final DuelsPlugin plugin;
    // QueueType -> KitName -> List of Players
    private final Map<QueueType, Map<String, LinkedList<UUID>>> queues = new HashMap<>();

    public QueueManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        for (QueueType type : QueueType.values()) {
            queues.put(type, new HashMap<>());
        }
    }

    public void addToQueue(Player player, String kitName, QueueType type) {
        if (isInQueue(player)) {
            me.raikou.duels.util.MessageUtil.sendError(player, "queue.already-in");
            return;
        }

        // Check if kit exists
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            me.raikou.duels.util.MessageUtil.sendError(player, "duel.kit-not-found", "%kit%", kitName);
            return;
        }

        // Add to queue
        queues.get(type).computeIfAbsent(kitName, k -> new LinkedList<>()).add(player.getUniqueId());
        me.raikou.duels.util.MessageUtil.sendSuccess(player, "queue.join", "%kit%", kitName, "%type%", type.name());
        me.raikou.duels.util.MessageUtil.sendInfo(player, "queue.info", "%count%",
                String.valueOf(queues.get(type).get(kitName).size()), "%required%",
                String.valueOf(type.getRequiredPlayers()));

        // Check if enough players
        checkQueue(kitName, type);
    }

    public void removeFromQueue(Player player) {
        for (QueueType type : QueueType.values()) {
            for (List<UUID> list : queues.get(type).values()) {
                if (list.remove(player.getUniqueId())) {
                    me.raikou.duels.util.MessageUtil.sendSuccess(player, "queue.leave");
                    return;
                }
            }
        }
        me.raikou.duels.util.MessageUtil.sendError(player, "queue.not-in");
    }

    private void checkQueue(String kitName, QueueType type) {
        LinkedList<UUID> queue = queues.get(type).get(kitName);
        if (queue == null)
            return;

        if (queue.size() >= type.getRequiredPlayers()) {
            if (type == QueueType.SOLO) {
                // Poll 2 players
                UUID uuid1 = queue.poll();
                UUID uuid2 = queue.poll();

                if (uuid1 != null && uuid2 != null) {
                    Player p1 = Bukkit.getPlayer(uuid1);
                    Player p2 = Bukkit.getPlayer(uuid2);

                    if (p1 != null && p2 != null) {
                        plugin.getDuelManager().startDuel(p1, p2, kitName);
                    } else {
                        // Handle disconnect while in queue (simple: just drop them for now)
                        if (p1 != null)
                            addToQueue(p1, kitName, type);
                        if (p2 != null)
                            addToQueue(p2, kitName, type);
                    }
                }
            }
            // Todo: Implement TEAM (2v2) logic similarly or make startDuel accept list
        }
    }

    public boolean isInQueue(Player player) {
        for (QueueType type : QueueType.values()) {
            for (List<UUID> list : queues.get(type).values()) {
                if (list.contains(player.getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getQueueSize(String kitName) {
        // Assuming SOLO queue for GUI display for now
        LinkedList<UUID> list = queues.get(QueueType.SOLO).get(kitName);
        return list != null ? list.size() : 0;
    }
}

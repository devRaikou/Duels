package me.raikou.duels.manager;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class RequestManager {

    private final DuelsPlugin plugin;
    // Receiver UUID -> Sender UUID -> Request
    private final Map<UUID, Map<UUID, Request>> requests = new HashMap<>();

    public RequestManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    public void sendRequest(Player sender, Player target, String kitName) {
        UUID sUuid = sender.getUniqueId();
        UUID tUuid = target.getUniqueId();

        if (sUuid.equals(tUuid)) {
            MessageUtil.sendError(sender, "request.self-request");
            return;
        }

        // Check if already sent
        if (hasActiveRequest(target.getUniqueId(), sender.getUniqueId())) {
            MessageUtil.sendError(sender, "request.already-sent");
            return;
        }

        Request request = new Request(sUuid, tUuid, kitName, System.currentTimeMillis());
        requests.computeIfAbsent(tUuid, k -> new HashMap<>()).put(sUuid, request);

        // Notify Sender
        MessageUtil.sendSuccess(sender, "request.sent", "%player%", target.getName(), "%kit%", kitName);

        // Notify Target (Clickable)
        for (String line : plugin.getLanguageManager().getList("request.received")) {
            // Replace placeholders in list
            line = line.replace("%sender%", sender.getName()).replace("%kit%", kitName);
            target.sendMessage(MessageUtil.parse(line));
        }
    }

    public void acceptRequest(Player target, Player sender) {
        UUID tUuid = target.getUniqueId();
        UUID sUuid = sender.getUniqueId();

        if (!hasActiveRequest(tUuid, sUuid)) {
            MessageUtil.sendError(target, "request.no-request");
            return;
        }

        Request req = requests.get(tUuid).get(sUuid);
        requests.get(tUuid).remove(sUuid); // Remove request

        MessageUtil.sendSuccess(target, "request.accepted", "%sender%", sender.getName());

        // Start Duel (direct requests are unranked)
        plugin.getDuelManager().startDuel(sender, target, req.kitName, false);
    }

    public void denyRequest(Player target, Player sender) {
        UUID tUuid = target.getUniqueId();
        UUID sUuid = sender.getUniqueId();

        if (!hasActiveRequest(tUuid, sUuid)) {
            MessageUtil.sendError(target, "request.no-request");
            return;
        }

        requests.get(tUuid).remove(sUuid);

        MessageUtil.sendSuccess(target, "request.denied", "%sender%", sender.getName());
        if (sender.isOnline()) {
            sender.sendMessage(MessageUtil.get("request.denied-sender", "%target%", target.getName()));
        }
    }

    public boolean hasActiveRequest(UUID target, UUID sender) {
        return requests.containsKey(target) && requests.get(target).containsKey(sender);
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Map<UUID, Request>>> it = requests.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<UUID, Map<UUID, Request>> entry = it.next();
                    Map<UUID, Request> recipientRequests = entry.getValue();

                    Iterator<Map.Entry<UUID, Request>> reqIt = recipientRequests.entrySet().iterator();
                    while (reqIt.hasNext()) {
                        Request req = reqIt.next().getValue();
                        if (now - req.timestamp > 60000) { // 60 seconds
                            reqIt.remove();

                            // Notify expiration
                            Player sender = Bukkit.getPlayer(req.sender);
                            Player target = Bukkit.getPlayer(req.target);

                            if (target != null) {
                                target.sendMessage(MessageUtil.get("request.expired", "%sender%",
                                        sender != null ? sender.getName() : "Unknown"));
                            }
                            if (sender != null) {
                                sender.sendMessage(MessageUtil.get("request.expired-sender", "%target%",
                                        target != null ? target.getName() : "Unknown"));
                            }
                        }
                    }

                    if (recipientRequests.isEmpty()) {
                        it.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private static class Request {
        UUID sender;
        UUID target;
        String kitName;
        long timestamp;

        public Request(UUID sender, UUID target, String kitName, long timestamp) {
            this.sender = sender;
            this.target = target;
            this.kitName = kitName;
            this.timestamp = timestamp;
        }
    }
}

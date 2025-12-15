package me.raikou.duels.punishment;

import me.raikou.duels.DuelsPlugin;

import net.kyori.adventure.text.Component;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PunishmentListener implements Listener {

    private final DuelsPlugin plugin;

    public PunishmentListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        // We can't use the manager's cache here because it's async pre-login
        // and cache is loaded on join. But for bans we should check directly or wait.
        // Ideally, we load essential data here.

        // For performance, we might want a synchronous check or pre-load.
        // But since storage is async, we must block or use cache if persistent?
        // Let's rely on cached data if available or quick query.

        // Since we are adding this to an existing system, and storage is
        // CompletableFuture based,
        // we have to join() to block the login thread safely (as it is AsyncPreLogin).

        try {
            var punishments = plugin.getStorage().getActivePunishments(event.getUniqueId()).join();

            for (Punishment p : punishments) {
                if (p.getType() == PunishmentType.BAN && !p.isExpired()) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                            me.raikou.duels.util.MessageUtil.parse(plugin.getPunishmentManager().getBanKickMessage(p)));
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Error checking punishment data. Please try again."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPunishmentManager().loadPunishments(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPunishmentManager().unloadPunishments(event.getPlayer().getUniqueId());
    }

}

package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PingCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public PingCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "general.console-usage");
            return true;
        }

        int ping = player.getPing();
        String quality = getPingQuality(ping);
        String bar = getPingBar(ping);
        String location = plugin.getConfig().getString("server.location", "Unknown");

        // Header
        player.sendMessage(MessageUtil.parse(""));
        player.sendMessage(MessageUtil.getRaw("ping.header"));
        player.sendMessage(MessageUtil.getRaw("ping.divider"));

        // Ping
        String pingMsg = plugin.getLanguageManager().getMessage("ping.ping");
        pingMsg = pingMsg.replace("%quality%", quality).replace("%ping%", String.valueOf(ping));
        player.sendMessage(MessageUtil.parse(pingMsg));

        // Signal
        String signalMsg = plugin.getLanguageManager().getMessage("ping.signal");
        signalMsg = signalMsg.replace("%bar%", bar);
        player.sendMessage(MessageUtil.parse(signalMsg));

        // Location
        player.sendMessage(MessageUtil.parse(""));
        String locMsg = plugin.getLanguageManager().getMessage("ping.location");
        locMsg = locMsg.replace("%location%", location);
        player.sendMessage(MessageUtil.parse(locMsg));

        // Footer
        player.sendMessage(MessageUtil.getRaw("ping.divider"));
        player.sendMessage(MessageUtil.parse(""));

        return true;
    }

    private String getPingQuality(int ping) {
        if (ping < 50) {
            return "<green><bold>";
        } else if (ping < 100) {
            return "<yellow><bold>";
        } else if (ping < 200) {
            return "<gold><bold>";
        } else {
            return "<red><bold>";
        }
    }

    private String getPingBar(int ping) {
        StringBuilder bar = new StringBuilder();
        int bars = 5 - Math.min(4, ping / 50);

        for (int i = 0; i < 5; i++) {
            if (i < bars) {
                if (ping < 50) {
                    bar.append("<green>█</green>");
                } else if (ping < 100) {
                    bar.append("<yellow>█</yellow>");
                } else if (ping < 200) {
                    bar.append("<gold>█</gold>");
                } else {
                    bar.append("<red>█</red>");
                }
            } else {
                bar.append("<dark_gray>█</dark_gray>");
            }
        }
        return bar.toString();
    }
}

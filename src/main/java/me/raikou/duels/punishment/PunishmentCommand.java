package me.raikou.duels.punishment;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PunishmentCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public PunishmentCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!sender.hasPermission("duels.punish")) {
            MessageUtil.sendError(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("history")) {
            if (args.length < 2) {
                MessageUtil.sendError(sender, "punishment.usage.history");
                return true;
            }
            if (!(sender instanceof Player)) {
                MessageUtil.sendError(sender, "general.only-players");
                return true;
            }
            handleHistory((Player) sender, args[1]);
            return true;
        }

        if (sub.equals("unban") || sub.equals("unmute")) {
            if (args.length < 2) {
                MessageUtil.sendError(sender, "punishment.usage." + sub);
                return true;
            }
            handlePardon(sender, sub, args);
            return true;
        }

        // Punish commands: ban, tempban, mute, tempmute, kick, warn
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String targetName = args[1];
        UUID targetUuid = getUuid(targetName);

        // Offline check for bans/mutes is fine, but for kick we need online
        if (targetUuid == null) {
            MessageUtil.sendError(sender, "punishment.not-found");
            return true;
        }

        long duration = 0;
        String reason = MessageUtil.getString("punishment.default-reason");
        int reasonIndex = 2;

        PunishmentType type = null;

        switch (sub) {
            case "ban":
                type = PunishmentType.BAN;
                break;
            case "tempban":
                type = PunishmentType.BAN;
                if (args.length < 3) {
                    MessageUtil.sendError(sender, "punishment.usage.tempban");
                    return true;
                }
                duration = PunishmentManager.parseDuration(args[2]);
                reasonIndex = 3;
                break;
            case "mute":
                type = PunishmentType.MUTE;
                break;
            case "tempmute":
                type = PunishmentType.MUTE;
                if (args.length < 3) {
                    MessageUtil.sendError(sender, "punishment.usage.tempmute");
                    return true;
                }
                duration = PunishmentManager.parseDuration(args[2]);
                reasonIndex = 3;
                break;
            case "kick":
                type = PunishmentType.KICK;
                if (Bukkit.getPlayer(targetUuid) == null) {
                    MessageUtil.sendError(sender, "punishment.not-online");
                    return true;
                }
                break;
            case "warn":
                type = PunishmentType.WARN;
                break;
            default:
                sendHelp(sender);
                return true;
        }

        if (args.length > reasonIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = reasonIndex; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            reason = sb.toString().trim();
        }

        PunishmentType finalType = type;
        long finalDuration = duration;
        String finalReason = reason;

        plugin.getPunishmentManager()
                .punish(targetUuid, targetName, sender.getName(), finalType, finalReason, finalDuration)
                .thenRun(() -> {
                    MessageUtil.sendSuccess(sender, "punishment.success", "%player%", targetName, "%type%",
                            finalType.name());
                });

        return true;
    }

    private void handleHistory(Player sender, String targetName) {
        UUID targetUuid = getUuid(targetName);
        if (targetUuid == null) {
            MessageUtil.sendError(sender, "punishment.not-found");
            return;
        }
        sender.openInventory(new PunishmentGui(plugin, targetUuid, targetName).getInventory());
    }

    private void handlePardon(CommandSender sender, String cmd, String[] args) {
        String targetName = args[1];
        UUID targetUuid = getUuid(targetName);
        if (targetUuid == null) {
            MessageUtil.sendError(sender, "punishment.not-found");
            return;
        }

        String reason = "Appealed";
        if (args.length > 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            reason = sb.toString().trim();
        }

        PunishmentType type = cmd.equals("unban") ? PunishmentType.BAN : PunishmentType.MUTE;

        plugin.getPunishmentManager().pardon(targetUuid, type, sender.getName(), reason).thenAccept(success -> {
            if (success) {
                MessageUtil.sendSuccess(sender, "punishment.pardoned", "%player%", targetName);
            } else {
                MessageUtil.sendError(sender, "punishment.no-record", "%type%", type.name().toLowerCase(), "%player%",
                        targetName);
            }
        });
    }

    private void sendHelp(CommandSender sender) {
        for (String line : MessageUtil.getStringList("punishment.help")) {
            sender.sendMessage(MessageUtil.parse(line));
        }
    }

    private UUID getUuid(String name) {
        // Optimally, we use a fetcher or local cache. For now, try online first then
        // offline
        Player p = Bukkit.getPlayer(name);
        if (p != null)
            return p.getUniqueId();
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (!sender.hasPermission("duels.punish"))
            return Collections.emptyList();

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("ban");
            list.add("tempban");
            list.add("mute");
            list.add("tempmute");
            list.add("kick");
            list.add("warn");
            list.add("unban");
            list.add("unmute");
            list.add("history");
            return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        return null; // Return null for player name auto-complete
    }
}

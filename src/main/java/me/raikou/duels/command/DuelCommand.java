package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.queue.QueueType;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.command.TabCompleter;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import org.bukkit.util.StringUtil;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public DuelCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            me.raikou.duels.util.MessageUtil.sendError(sender, "general.only-players");
            return true;
        }

        if (args.length == 0) {
            sendPluginInfo(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                sendHelp(player);
                break;

            case "join":
                if (args.length < 2) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "Usage: <yellow>/duel join <kit>");
                    return true;
                }
                String kit = args[1];
                plugin.getQueueManager().addToQueue(player, kit, QueueType.SOLO);
                break;

            case "invite":
                if (args.length < 3) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "Usage: <yellow>/duel invite <player> <kit>");
                    return true;
                }
                Player target = org.bukkit.Bukkit.getPlayer(args[1]);
                if (target == null) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "general.player-not-found");
                    return true;
                }
                String kitName = args[2];
                if (plugin.getKitManager().getKit(kitName) == null) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "general.kit-not-found");
                    return true;
                }
                plugin.getRequestManager().sendRequest(player, target, kitName);
                break;

            case "accept":
                if (args.length < 2) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "Usage: <yellow>/duel accept <player>");
                    return true;
                }
                Player senderPlayer = org.bukkit.Bukkit.getPlayer(args[1]);
                if (senderPlayer == null) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "general.player-not-found");
                    return true;
                }
                plugin.getRequestManager().acceptRequest(player, senderPlayer);
                break;

            case "deny":
                if (args.length < 2) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "Usage: <yellow>/duel deny <player>");
                    return true;
                }
                Player sP = org.bukkit.Bukkit.getPlayer(args[1]);
                if (sP == null) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "general.player-not-found");
                    return true;
                }
                plugin.getRequestManager().denyRequest(player, sP);
                break;

            case "leave":
                // Handle both queue leave and spectator leave
                if (plugin.getSpectatorManager().isSpectating(player)) {
                    plugin.getSpectatorManager().stopSpectating(player);
                } else {
                    plugin.getQueueManager().removeFromQueue(player);
                }
                break;

            case "spectate":
            case "spec":
            case "watch":
                handleSpectate(player, args);
                break;

            case "matchresult":
                if (args.length < 2) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "Usage: <yellow>/duel matchresult <matchId>");
                    return true;
                }
                String matchId = args[1];
                if (plugin.getMatchHistoryManager().hasMatch(matchId)) {
                    plugin.getMatchResultGui().openMatchGui(player, matchId);
                } else {
                    me.raikou.duels.util.MessageUtil.sendError(player, "general.match-not-found");
                }
                break;

            case "admin":
                if (!player.hasPermission("duels.admin")) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "general.no-permission");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("Usage: /duel admin <arena|kit|reload>");
                    return true;
                }
                handleAdmin(player, args);
                break;

            default:
                me.raikou.duels.util.MessageUtil.sendError(player, "general.unknown-command");
                break;
        }

        return true;
    }

    private void handleAdmin(Player player, String[] args) {
        String sub = args[1].toLowerCase();

        if (sub.equals("reload")) {
            plugin.reloadConfig();
            plugin.getLanguageManager().loadLanguage();
            plugin.getArenaManager().loadArenas();
            plugin.getKitManager().loadKits();
            plugin.getLobbyManager().loadLobby();
            me.raikou.duels.util.MessageUtil.sendSuccess(player, "general.config-reloaded");
            return;
        }

        if (sub.equals("setlobby")) {
            plugin.getLobbyManager().setLobby(player.getLocation());
            me.raikou.duels.util.MessageUtil.sendSuccess(player, "general.lobby-set");
            return;
        }

        if (sub.equals("kit")) {
            if (args.length < 4) {
                player.sendMessage("Usage: /duel admin kit create <name>");
                return;
            }
            String action = args[2].toLowerCase();
            String name = args[3];

            if (action.equals("create")) {
                plugin.getKitManager().createKit(name, player);
                me.raikou.duels.util.MessageUtil.sendSuccess(player,
                        "admin.kit-created", "%kit%", name);
            }
            return;
        }

        if (sub.equals("arena")) {
            if (args.length < 4) {
                player.sendMessage("Usage: /duel admin arena <create|setspawn> <name> [1|2|spectator]");
                return;
            }
            String action = args[2].toLowerCase();
            String name = args[3];

            if (action.equals("create")) {
                plugin.getConfig().set("arenas." + name + ".world", player.getWorld().getName());
                plugin.saveConfig();
                plugin.getArenaManager().loadArenas();
                me.raikou.duels.util.MessageUtil.sendSuccess(player, "admin.arena-created", "%name%", name);
            } else if (action.equals("setspawn")) {
                if (args.length < 5) {
                    player.sendMessage("Usage: /duel admin arena setspawn <name> <1|2|spectator>");
                    return;
                }
                // Wait, logic above used 'name ' variable which is args[3].

                if (!plugin.getConfig().contains("arenas." + name)) {
                    me.raikou.duels.util.MessageUtil.sendError(player, "admin.arena-not-found", "%name%", name);
                    return;
                }

                String type = args[4].toLowerCase();
                Location loc = player.getLocation();
                String path = "arenas." + name + ".";

                if (type.equals("1"))
                    path += "spawn1";
                else if (type.equals("2"))
                    path += "spawn2";
                else if (type.equals("spectator"))
                    path += "spectator";
                else {
                    player.sendMessage("Invalid spawn type. Use 1, 2, or spectator.");
                    return;
                }

                plugin.getConfig().set(path + ".world", loc.getWorld().getName());
                plugin.getConfig().set(path + ".x", loc.getX());
                plugin.getConfig().set(path + ".y", loc.getY());
                plugin.getConfig().set(path + ".z", loc.getZ());
                plugin.getConfig().set(path + ".yaw", loc.getYaw());
                plugin.getConfig().set(path + ".pitch", loc.getPitch());

                plugin.saveConfig();
                plugin.getArenaManager().loadArenas();
                me.raikou.duels.util.MessageUtil.sendSuccess(player, "admin.spawn-set", "%type%", type, "%name%", name);
            }
        }
    }

    /**
     * Handle the /duel spectate command.
     * Opens spectator GUI or directly spectates a player's duel.
     */
    private void handleSpectate(Player player, String[] args) {
        // Check if player is already in a duel
        if (plugin.getDuelManager().isInDuel(player)) {
            me.raikou.duels.util.MessageUtil.sendError(player, "spectator.cannot-spectate-self");
            return;
        }

        // Check if already spectating
        if (plugin.getSpectatorManager().isSpectating(player)) {
            me.raikou.duels.util.MessageUtil.sendError(player, "spectator.already-spectating");
            return;
        }

        // If no player specified, open the GUI
        if (args.length < 2) {
            plugin.getSpectatorGui().openGui(player);
            return;
        }

        // Try to spectate a specific player's duel
        String targetName = args[1];
        Player target = org.bukkit.Bukkit.getPlayer(targetName);

        if (target == null) {
            me.raikou.duels.util.MessageUtil.sendError(player, "general.player-not-found");
            return;
        }

        me.raikou.duels.duel.Duel duel = plugin.getDuelManager().getDuel(target);
        if (duel == null) {
            me.raikou.duels.util.MessageUtil.sendError(player, "spectator.no-active-duels");
            return;
        }

        plugin.getSpectatorManager().startSpectating(player, duel);
    }

    private void sendPluginInfo(Player player) {
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<newline><gradient:#FFD700:#FFA500><bold>DUELS CORE</bold></gradient> <gray>v"
                        + plugin.getPluginMeta().getVersion() + "</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<gray>Created by <yellow>" + plugin.getPluginMeta().getAuthors().get(0) + "</yellow></gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<gray>Type <yellow>/duel help</yellow> for a list of commands.</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(" "));

    }

    private void sendHelp(Player player) {
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<newline><gradient:#FFD700:#FFA500><bold>DUELS HELP</bold></gradient>"));

        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/duel join <kit></yellow> <gray>Join a queue</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/duel leave</yellow> <gray>Leave the queue</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/lobby</yellow> <gray>Return to lobby</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/duel stats [player]</yellow> <gray>View statistics</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/duel invite <player> <kit></yellow> <gray>Send duel request</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/duel accept <player></yellow> <gray>Accept request</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/duel deny <player></yellow> <gray>Deny request</gray>"));
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                "<dark_gray>▪</dark_gray> <yellow>/duel spectate</yellow> <gray>Watch active duels</gray>"));

        if (player.hasPermission("duels.admin")) {
            player.sendMessage(
                    me.raikou.duels.util.MessageUtil.parse("<newline><gold><bold>ADMIN COMMANDS</bold></gold>"));
            player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                    "<dark_gray>▪</dark_gray> <yellow>/duel admin arena create <name></yellow>"));
            player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                    "<dark_gray>▪</dark_gray> <yellow>/duel admin arena setspawn <name> <1|2|spect></yellow>"));
            player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                    "<dark_gray>▪</dark_gray> <yellow>/duel admin kit create <name></yellow>"));
            player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                    "<dark_gray>▪</dark_gray> <yellow>/duel admin setlobby</yellow>"));
            player.sendMessage(me.raikou.duels.util.MessageUtil.parse(
                    "<dark_gray>▪</dark_gray> <yellow>/duel admin reload</yellow>"));
        }
        player.sendMessage(me.raikou.duels.util.MessageUtil.parse(" "));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (args.length == 1) {
            commands.add("join");
            commands.add("leave");
            commands.add("invite");
            commands.add("accept");
            commands.add("deny");
            commands.add("stats");
            commands.add("spectate");
            commands.add("help");
            if (sender.hasPermission("duels.admin")) {
                commands.add("admin");
            }
            StringUtil.copyPartialMatches(args[0], commands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join")) {
                commands.addAll(plugin.getKitManager().getKits().keySet());
            } else if (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("accept")
                    || args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("stats")) {
                return null; // Return null to show online players
            } else if (args[0].equalsIgnoreCase("admin")) {
                if (sender.hasPermission("duels.admin")) {
                    commands.add("arena");
                    commands.add("kit");
                    commands.add("setlobby");
                    commands.add("reload");
                }
            }
            StringUtil.copyPartialMatches(args[1], commands, completions);
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("invite")) {
                commands.addAll(plugin.getKitManager().getKits().keySet());
            } else if (args[0].equalsIgnoreCase("admin")) {
                if (args[1].equalsIgnoreCase("arena")) {
                    commands.add("create");
                    commands.add("setspawn");
                } else if (args[1].equalsIgnoreCase("kit")) {
                    commands.add("create");
                }
            }
            StringUtil.copyPartialMatches(args[2], commands, completions);
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena")) {
                if (args[2].equalsIgnoreCase("setspawn")) {
                    commands.addAll(plugin.getArenaManager().getArenas().keySet());
                }
            }
            StringUtil.copyPartialMatches(args[3], commands, completions);
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena")
                    && args[2].equalsIgnoreCase("setspawn")) {
                commands.add("1");
                commands.add("2");
                commands.add("spectator");
            }
            StringUtil.copyPartialMatches(args[4], commands, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}

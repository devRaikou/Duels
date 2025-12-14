package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.queue.QueueType;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DuelCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public DuelCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join":
                if (args.length < 2) {
                    player.sendMessage("Usage: /duel join <kit>");
                    return true;
                }
                String kit = args[1];
                plugin.getQueueManager().addToQueue(player, kit, QueueType.SOLO);
                break;

            case "leave":
                plugin.getQueueManager().removeFromQueue(player);
                break;

            case "admin":
                if (!player.hasPermission("duels.admin")) {
                    player.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("Usage: /duel admin <arena|kit|reload>");
                    return true;
                }
                handleAdmin(player, args);
                break;

            default:
                player.sendMessage("Unknown subcommand.");
                break;
        }

        return true;
    }

    private void handleAdmin(Player player, String[] args) {
        String sub = args[1].toLowerCase();

        if (sub.equals("reload")) {
            plugin.reloadConfig();
            plugin.getArenaManager().loadArenas();
            plugin.getKitManager().loadKits();
            player.sendMessage("Configuration reloaded.");
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
                player.sendMessage("Kit " + name + " created from your inventory.");
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
                player.sendMessage("Arena " + name + " created. Please set spawns (1, 2, spectator).");
            } else if (action.equals("setspawn")) {
                if (args.length < 5) {
                    player.sendMessage("Usage: /duel admin arena setspawn <name> <1|2|spectator>");
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
                player.sendMessage("Spawn " + type + " set for arena " + name + ".");
            }
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(" ");
        player.sendMessage("§6§lDUELS CORE §7- §eCommands");
        player.sendMessage("§7/duel join <kit> §f- Join the queue");
        player.sendMessage("§7/duel leave §f- Leave the queue");
        if (player.hasPermission("duels.admin")) {
            player.sendMessage("§7/duel admin arena create <name> §f- Create arena");
            player.sendMessage("§7/duel admin arena setspawn <name> <1|2|spectator> §f- Set spawns");
            player.sendMessage("§7/duel admin kit create <name> §f- Save inventory as kit");
            player.sendMessage("§7/duel admin reload §f- Reload config");
        }
        player.sendMessage(" ");
    }
}

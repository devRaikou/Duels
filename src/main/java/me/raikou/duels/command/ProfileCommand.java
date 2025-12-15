package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ProfileCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public ProfileCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Values are only for players.");
            return true;
        }

        if (args.length == 0) {
            plugin.getProfileGui().openProfile(player, player);
            return true;
        }

        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                MessageUtil.sendError(player, "command.player-not-found"); // Ensure this key exists or fallback
                return true;
            }
            plugin.getProfileGui().openProfile(player, target);
            return true;
        }

        return false;
    }
}

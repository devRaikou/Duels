package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LobbyCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public LobbyCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            me.raikou.duels.util.MessageUtil.sendError(sender, "general.only-players");
            return true;
        }

        plugin.getLobbyManager().teleportToLobby(player);
        return true;
    }
}

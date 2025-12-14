package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.stats.PlayerStats;
import me.raikou.duels.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class StatsCommand implements CommandExecutor {

    private final DuelsPlugin plugin;
    private final DecimalFormat df = new DecimalFormat("0.00");

    public StatsCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        Player target;

        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                me.raikou.duels.util.MessageUtil.sendError(sender, "general.player-not-found");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                me.raikou.duels.util.MessageUtil.sendError(sender, "general.console-usage");
                return true;
            }
            target = (Player) sender;
        }

        PlayerStats stats = plugin.getStatsManager().getStats(target);

        // Calculate K/D
        double kdRatio = 0.0;
        if (stats.getDeaths() > 0) {
            kdRatio = (double) stats.getKills() / stats.getDeaths();
        } else {
            kdRatio = stats.getKills();
        }

        sender.sendMessage(
                MessageUtil.getRaw("stats.header", "%player%", target.getName()));
        sender.sendMessage(MessageUtil.getRaw("stats.wins", "%amount%", String.valueOf(stats.getWins())));
        sender.sendMessage(MessageUtil.getRaw("stats.losses", "%amount%", String.valueOf(stats.getLosses())));
        sender.sendMessage(MessageUtil.getRaw("stats.kills", "%amount%", String.valueOf(stats.getKills())));
        sender.sendMessage(MessageUtil.getRaw("stats.deaths", "%amount%", String.valueOf(stats.getDeaths())));
        sender.sendMessage(MessageUtil.getRaw("stats.kd", "%ratio%", df.format(kdRatio)));

        // Show ELO for each kit
        sender.sendMessage(MessageUtil.getRaw("stats.elo-header"));
        for (String kitName : plugin.getKitManager().getKits().keySet()) {
            int elo = plugin.getStorage().loadElo(target.getUniqueId(), kitName).join();
            sender.sendMessage(MessageUtil.getRaw("stats.elo-kit", "%kit%", kitName, "%elo%", String.valueOf(elo)));
        }
        sender.sendMessage(MessageUtil.parse(" ")); // Empty line footer

        return true;
    }
}

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        int startingElo = plugin.getConfig().getInt("ranked.starting-elo", 1000);

        // Calculate K/D
        double kdRatio;
        if (stats.getDeaths() > 0) {
            kdRatio = (double) stats.getKills() / stats.getDeaths();
        } else {
            kdRatio = stats.getKills();
        }

        List<String> kits = new ArrayList<>(plugin.getKitManager().getKits().keySet());
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (String kitName : kits) {
            CompletableFuture<Integer> futureLine = plugin.getStorage()
                    .loadElo(target.getUniqueId(), kitName)
                    .handle((elo, throwable) -> throwable == null && elo != null ? elo : startingElo);
            futures.add(futureLine);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player playerSender && !playerSender.isOnline()) {
                    return;
                }

                sender.sendMessage(MessageUtil.getRaw("stats.header", "%player%", target.getName()));
                sender.sendMessage(MessageUtil.getRaw("stats.wins", "%amount%", String.valueOf(stats.getWins())));
                sender.sendMessage(MessageUtil.getRaw("stats.losses", "%amount%", String.valueOf(stats.getLosses())));
                sender.sendMessage(MessageUtil.getRaw("stats.kills", "%amount%", String.valueOf(stats.getKills())));
                sender.sendMessage(MessageUtil.getRaw("stats.deaths", "%amount%", String.valueOf(stats.getDeaths())));
                sender.sendMessage(MessageUtil.getRaw("stats.kd", "%ratio%", df.format(kdRatio)));
                sender.sendMessage(MessageUtil.getRaw("stats.elo-header"));

                for (int i = 0; i < kits.size(); i++) {
                    String kitName = kits.get(i);
                    int elo = futures.get(i).getNow(startingElo);
                    sender.sendMessage(MessageUtil.getLegacy("stats.elo-kit", "%kit%", kitName, "%elo%",
                            String.valueOf(elo)));
                }

                sender.sendMessage(MessageUtil.parse(" "));
            });
        });

        return true;
    }
}

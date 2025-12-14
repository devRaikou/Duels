package me.raikou.duels.command;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.leaderboard.LeaderboardEntry;
import me.raikou.duels.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaderboard command and GUI.
 */
public class LeaderboardCommand implements CommandExecutor, Listener {

    private final DuelsPlugin plugin;

    public LeaderboardCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "general.console-usage");
            return true;
        }

        openLeaderboardGui(player);
        return true;
    }

    public void openLeaderboardGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getGuiTitle());

        // Fill with black glass pane border
        ItemStack borderPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderPane.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.text(" "));
            borderPane.setItemMeta(borderMeta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, borderPane);
        }

        // Get leaderboard entries
        List<LeaderboardEntry> entries = plugin.getLeaderboardManager().getLeaderboard();

        // Slots for top 10 (centered layout)
        int[] topSlots = { 13, 21, 22, 23, 29, 30, 31, 32, 33, 40 };

        // Medal colors for top 3
        Material[] medalMaterials = {
                Material.GOLD_BLOCK, // 1st
                Material.IRON_BLOCK, // 2nd
                Material.COPPER_BLOCK // 3rd
        };

        for (int i = 0; i < Math.min(entries.size(), 10); i++) {
            LeaderboardEntry entry = entries.get(i);
            ItemStack item;

            if (i < 3) {
                // Top 3 with medal blocks
                item = new ItemStack(medalMaterials[i]);
            } else {
                // Try to get player head, fallback to paper
                item = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                if (skullMeta != null && entry.uuid() != null) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
                    item.setItemMeta(skullMeta);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String playerName = entry.name() != null && !entry.name().isEmpty()
                        ? entry.name()
                        : "Unknown";

                // Get rank format from localization
                String rankKey = switch (i) {
                    case 0 -> "gui.leaderboard.rank-1";
                    case 1 -> "gui.leaderboard.rank-2";
                    case 2 -> "gui.leaderboard.rank-3";
                    default -> "gui.leaderboard.rank-default";
                };

                String rankFormat = plugin.getLanguageManager().getMessage(rankKey);
                rankFormat = rankFormat.replace("%player%", playerName)
                        .replace("%rank%", String.valueOf(i + 1));
                meta.displayName(MiniMessage.miniMessage().deserialize(rankFormat));

                // Lore with stats from localization
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());

                lore.add(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getMessage("gui.leaderboard.wins")
                                .replace("%amount%", String.valueOf(entry.wins()))));
                lore.add(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getMessage("gui.leaderboard.losses")
                                .replace("%amount%", String.valueOf(entry.losses()))));
                lore.add(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getMessage("gui.leaderboard.kills")
                                .replace("%amount%", String.valueOf(entry.kills()))));
                lore.add(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getMessage("gui.leaderboard.deaths")
                                .replace("%amount%", String.valueOf(entry.deaths()))));
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getMessage("gui.leaderboard.winrate")
                                .replace("%rate%", String.format("%.1f%%", entry.getWinRate()))));
                lore.add(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getMessage("gui.leaderboard.kd")
                                .replace("%ratio%", String.format("%.2f", entry.getKDRatio()))));

                meta.lore(lore);
                item.setItemMeta(meta);
            }

            inv.setItem(topSlots[i], item);
        }

        // Info item in bottom center
        ItemStack infoItem = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(MiniMessage.miniMessage().deserialize(
                    plugin.getLanguageManager().getMessage("gui.leaderboard.info-title")));

            List<Component> lore = new ArrayList<>();
            int cacheDuration = plugin.getConfig().getInt("leaderboard.cache-duration", 60);
            int topCount = plugin.getConfig().getInt("leaderboard.top-count", 10);

            for (String line : plugin.getLanguageManager().getList("gui.leaderboard.info-lore")) {
                line = line.replace("%duration%", String.valueOf(cacheDuration))
                        .replace("%count%", String.valueOf(topCount));
                lore.add(MiniMessage.miniMessage().deserialize(line));
            }
            infoMeta.lore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(49, infoItem);

        player.openInventory(inv);
    }

    private Component getGuiTitle() {
        return MiniMessage.miniMessage().deserialize(
                plugin.getLanguageManager().getMessage("gui.leaderboard.title"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(getGuiTitle())) {
            event.setCancelled(true);
        }
    }
}

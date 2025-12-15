package me.raikou.duels.gui;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.match.DuelResult;
import me.raikou.duels.stats.PlayerStats;
import me.raikou.duels.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProfileGui implements Listener {

    private final DuelsPlugin plugin;

    public ProfileGui(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private Component getGuiTitle(String targetName) {
        String title = plugin.getLanguageManager().getMessage("gui.profile.title");
        if (title == null)
            title = "<bold>%player%'s Profile</bold>";
        return MiniMessage.miniMessage().deserialize(title.replace("%player%", targetName));
    }

    public void openProfile(Player viewer, Player target) {
        // Load target stats fresh
        plugin.getStatsManager().saveStatsImmediately(target); // Ensure latest if they are online
        plugin.getStatsManager().getPlayerRank(target.getUniqueId(), rank -> {
            openProfileInternal(viewer, target, rank);
        });
    }

    private void openProfileInternal(Player viewer, Player target, int rank) {
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, getGuiTitle(target.getName()));
        PlayerStats stats = plugin.getStatsManager().getStats(target);

        // Fill borders (Black Stained Glass)
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.empty());
        border.setItemMeta(borderMeta);

        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        // 1. Player Skull (Stats Summary) - Slot 4
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(target);

        String nameFormat = plugin.getLanguageManager().getMessage("gui.profile.skull-name");
        if (nameFormat == null)
            nameFormat = "<yellow>%player%</yellow>";
        skullMeta.displayName(MiniMessage.miniMessage().deserialize(nameFormat.replace("%player%", target.getName())));

        List<Component> lore = new ArrayList<>();
        // Fetch lore from config/lang or hardcode default structure
        // We will default to a nice structure and allow language manager to override
        // lines if they exist
        // Or better, define basic keys for each stat line.

        long playtimeMillis = plugin.getStatsManager().getPlaytime(target);
        String playtimeStr = formatDuration(playtimeMillis);

        lore.add(Component.empty());
        lore.add(Component.empty());
        lore.add(MessageUtil.getRaw("gui.profile.stats.wins", "%amount%", String.valueOf(stats.getWins())));
        lore.add(MessageUtil.getRaw("gui.profile.stats.losses", "%amount%", String.valueOf(stats.getLosses())));
        lore.add(MessageUtil.getRaw("gui.profile.stats.kd", "%ratio%", String.format("%.2f", stats.getKDRatio())));
        lore.add(MessageUtil.getRaw("gui.profile.stats.wl", "%rate%", String.format("%.2f", stats.getWinRate())));
        lore.add(Component.empty());
        lore.add(MessageUtil.getRaw("gui.profile.stats.rank", "%rank%", String.valueOf(rank)));
        lore.add(MessageUtil.getRaw("gui.profile.stats.playtime", "%time%", playtimeStr));

        skullMeta.lore(lore);
        skull.setItemMeta(skullMeta);
        inv.setItem(4, skull);

        // 2. Match History - Row 3 (Slots 19-25)
        List<DuelResult> history = plugin.getMatchHistoryManager().getLastMatches(target.getUniqueId(), 7);
        int[] historySlots = { 19, 20, 21, 22, 23, 24, 25 };

        for (int i = 0; i < historySlots.length; i++) {
            if (i >= history.size())
                break;
            DuelResult match = history.get(i);

            boolean isWinner = match.getWinnerUuid().equals(target.getUniqueId());
            Material iconMat = isWinner ? Material.LIME_DYE : Material.RED_DYE;
            ItemStack matchItem = new ItemStack(iconMat);
            ItemMeta meta = matchItem.getItemMeta();

            String resultKey = isWinner ? "gui.profile.match-history.victory" : "gui.profile.match-history.defeat";
            meta.displayName(MessageUtil.getRaw(resultKey));

            List<Component> matchLore = new ArrayList<>();
            matchLore.add(Component.empty());
            matchLore.add(Component.empty());
            matchLore.add(MessageUtil.getRaw("gui.profile.match-history.kit", "%kit%", match.getKitName()));
            matchLore.add(MessageUtil.getRaw("gui.profile.match-history.duration", "%duration%",
                    match.getFormattedDuration()));

            // Opponent name?
            UUID opponentUuid = isWinner ? match.getLoserUuid() : match.getWinnerUuid();
            String opponentName = Bukkit.getOfflinePlayer(opponentUuid).getName();
            // Warning: getOfflinePlayer might be slow/null name? Usually name is cached.
            if (opponentName == null)
                opponentName = MessageUtil.getString("gui.profile.match-history.unknown");

            matchLore.add(MessageUtil.getRaw("gui.profile.match-history.vs", "%opponent%", opponentName));

            meta.lore(matchLore);
            matchItem.setItemMeta(meta);
            inv.setItem(historySlots[i], matchItem);
        }

        // 3. Close Button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(MessageUtil.getRaw("gui.profile.close-button"));
        close.setItemMeta(closeMeta);
        inv.setItem(49, close);

        viewer.openInventory(inv);
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return String.format("%dh %dm", hours, minutes);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (PlainTextComponentSerializer.plainText().serialize(event.getView().title()).contains("Profile")) { // Simple
                                                                                                               // check,
                                                                                                               // ideally
                                                                                                               // check
                                                                                                               // exact
                                                                                                               // title
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                event.getWhoClicked().closeInventory();
            }
        }
    }
}

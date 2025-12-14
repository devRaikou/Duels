package me.raikou.duels.match;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Modern, minimal GUI for displaying match results and player inventories.
 */
public class MatchResultGui implements Listener {

    private final DuelsPlugin plugin;
    private static final String MATCH_GUI_MARKER = "Match ";
    private static final String INVENTORY_GUI_MARKER = "Inventory: ";

    public MatchResultGui(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open the modern match result GUI for a player.
     */
    public void openMatchGui(Player viewer, String matchId) {
        DuelResult result = plugin.getMatchHistoryManager().getMatch(matchId);
        if (result == null) {
            MessageUtil.sendError(viewer, "general.match-not-found");
            return;
        }

        // Create 3-row compact GUI
        String title = MessageUtil.getString("gui.match-result.title")
                .replace("%matchId%", result.getShortMatchId());
        Inventory gui = Bukkit.createInventory(null, 27, MessageUtil.parse(title));

        // Fill border with gray glass panes for clean look
        ItemStack borderPane = createGlassPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, borderPane);
        }

        // === Center Row: Winner | Info | Loser ===

        // Winner head (slot 10 - left side)
        ItemStack winnerHead = createPlayerHead(
                result.getWinnerUuid(),
                result.getWinnerName(),
                true,
                result.getFormattedWinnerHealth(),
                result.isRanked() ? result.getWinnerNewElo() : 0,
                result.isRanked() ? result.getWinnerEloChange() : 0,
                result.isRanked());
        gui.setItem(10, winnerHead);

        // Match info (slot 13 - center)
        ItemStack infoItem = createMatchInfoItem(result);
        gui.setItem(13, infoItem);

        // Loser head (slot 16 - right side)
        ItemStack loserHead = createPlayerHead(
                result.getLoserUuid(),
                result.getLoserName(),
                false,
                "0.0 ❤",
                result.isRanked() ? result.getLoserNewElo() : 0,
                result.isRanked() ? result.getLoserEloChange() : 0,
                result.isRanked());
        gui.setItem(16, loserHead);

        // Winner's main weapon (slot 11)
        if (result.getWinnerInventory() != null && !result.getWinnerInventory().isEmpty()) {
            ItemStack weapon = result.getWinnerInventory().get(0);
            if (weapon != null)
                gui.setItem(11, weapon);
        }

        // Loser's main weapon (slot 15)
        if (result.getLoserInventory() != null && !result.getLoserInventory().isEmpty()) {
            ItemStack weapon = result.getLoserInventory().get(0);
            if (weapon != null)
                gui.setItem(15, weapon);
        }

        // Navigation hints with colored panes
        gui.setItem(9, createGlassPane(Material.LIME_STAINED_GLASS_PANE)); // Winner side indicator
        gui.setItem(17, createGlassPane(Material.RED_STAINED_GLASS_PANE)); // Loser side indicator

        viewer.openInventory(gui);
    }

    /**
     * Create a glass pane item.
     */
    private ItemStack createGlassPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    /**
     * Create a player head with match info.
     */
    private ItemStack createPlayerHead(java.util.UUID uuid, String name, boolean isWinner,
            String health, int elo, int eloChange, boolean ranked) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));

        // Name with color
        NamedTextColor nameColor = isWinner ? NamedTextColor.GREEN : NamedTextColor.RED;
        meta.displayName(Component.text(name)
                .color(nameColor)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Status badge
        String statusKey = isWinner ? "gui.match-result.winner-label" : "gui.match-result.loser-label";
        lore.add(MessageUtil.parse(MessageUtil.getString(statusKey)));
        lore.add(Component.empty());

        // Health
        lore.add(MessageUtil.parse(MessageUtil.getString("gui.match-result.health")
                .replace("%health%", health)));

        // ELO if ranked
        if (ranked) {
            String changeColor = isWinner ? "<green>" : "<red>";
            String changePrefix = isWinner ? "+" : "";
            lore.add(MessageUtil.parse(MessageUtil.getString("gui.match-result.elo")
                    .replace("%elo%", String.valueOf(elo))
                    .replace("%change%", changeColor + "(" + changePrefix + eloChange + ")")));
        }

        lore.add(Component.empty());
        lore.add(MessageUtil.parse(MessageUtil.getString("gui.match-result.click-inventory")));

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Create the match info item.
     */
    private ItemStack createMatchInfoItem(DuelResult result) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(MessageUtil.parse(MessageUtil.getString("gui.match-result.match-info"))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Get type text for replacement
        String typeText = result.isRanked()
                ? MessageUtil.getString("gui.match-result.ranked")
                : MessageUtil.getString("gui.match-result.unranked");

        // Use localized lore from config
        List<String> matchInfoLore = MessageUtil.getStringList("gui.match-result.match-info-lore");
        for (String line : matchInfoLore) {
            String processed = line
                    .replace("%kit%", result.getKitName())
                    .replace("%type%", typeText)
                    .replace("%duration%", result.getFormattedDuration());
            lore.add(MessageUtil.parse(processed).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());

        // Match ID
        lore.add(Component.text("ID: ").color(NamedTextColor.DARK_GRAY)
                .append(Component.text(result.getShortMatchId()).color(NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Open inventory view GUI for a specific player from a match.
     */
    public void openInventoryGui(Player viewer, DuelResult result, boolean isWinner) {
        String playerName = isWinner ? result.getWinnerName() : result.getLoserName();
        List<ItemStack> inventory = isWinner ? result.getWinnerInventory() : result.getLoserInventory();
        ItemStack[] armor = isWinner ? result.getWinnerArmor() : result.getLoserArmor();

        String title = MessageUtil.getString("gui.match-result.inventory-title")
                .replace("%player%", playerName);
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtil.parse(title));

        // Fill with dark background
        ItemStack bgPane = createGlassPane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 36; i < 54; i++) {
            gui.setItem(i, bgPane);
        }

        // Fill inventory items (slots 0-35)
        if (inventory != null) {
            for (int i = 0; i < Math.min(36, inventory.size()); i++) {
                ItemStack item = inventory.get(i);
                if (item != null) {
                    gui.setItem(i, item);
                }
            }
        }

        // Armor slots (bottom row: 45-48)
        if (armor != null) {
            if (armor.length > 3 && armor[3] != null)
                gui.setItem(45, armor[3]); // Helmet
            if (armor.length > 2 && armor[2] != null)
                gui.setItem(46, armor[2]); // Chestplate
            if (armor.length > 1 && armor[1] != null)
                gui.setItem(47, armor[1]); // Leggings
            if (armor.length > 0 && armor[0] != null)
                gui.setItem(48, armor[0]); // Boots
        }

        // Back button (slot 53)
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(MessageUtil.parse(MessageUtil.getString("gui.match-result.back-button"))
                .decoration(TextDecoration.ITALIC, false));
        backButton.setItemMeta(backMeta);
        gui.setItem(53, backButton);

        // Store match ID for back navigation
        viewer.setMetadata("viewing_match", new org.bukkit.metadata.FixedMetadataValue(plugin, result.getMatchId()));

        viewer.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Component titleComponent = event.getView().title();
        String plainTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(titleComponent);

        // Handle Match GUI clicks (both English and Turkish)
        if (plainTitle.contains(MATCH_GUI_MARKER) || plainTitle.contains("Maç ")) {
            event.setCancelled(true);

            // Extract match ID from title
            String matchId = null;
            if (plainTitle.contains(MATCH_GUI_MARKER)) {
                matchId = plainTitle.substring(plainTitle.indexOf(MATCH_GUI_MARKER) + MATCH_GUI_MARKER.length());
            } else if (plainTitle.contains("Maç ")) {
                matchId = plainTitle.substring(plainTitle.indexOf("Maç ") + 4);
            }

            if (matchId == null)
                return;

            DuelResult result = plugin.getMatchHistoryManager().getMatch(matchId);
            if (result == null)
                return;

            int slot = event.getSlot();

            // Winner head clicked (slot 10)
            if (slot == 10) {
                openInventoryGui(player, result, true);
            }
            // Loser head clicked (slot 16)
            else if (slot == 16) {
                openInventoryGui(player, result, false);
            }
        }

        // Handle Inventory GUI clicks (both English and Turkish)
        if (plainTitle.contains(INVENTORY_GUI_MARKER) || plainTitle.contains("Envanter: ")) {
            event.setCancelled(true);

            // Back button clicked
            if (event.getSlot() == 53) {
                if (player.hasMetadata("viewing_match")) {
                    String matchId = player.getMetadata("viewing_match").get(0).asString();
                    player.removeMetadata("viewing_match", plugin);
                    openMatchGui(player, matchId);
                }
            }
        }
    }
}

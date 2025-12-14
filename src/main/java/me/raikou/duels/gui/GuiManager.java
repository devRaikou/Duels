package me.raikou.duels.gui;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.raikou.duels.queue.QueueType;

public class GuiManager implements Listener {

    private final DuelsPlugin plugin;

    private Component getGuiTitle() {
        String titleRaw = plugin.getLanguageManager().getMessage("gui.queue.title");
        return MiniMessage.miniMessage().deserialize(titleRaw);
    }

    public GuiManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Track which QueueType each player is selecting
    private final Map<UUID, QueueType> playerQueueTypeSelection = new HashMap<>();

    public void openQueueGui(Player player, QueueType type) {
        playerQueueTypeSelection.put(player.getUniqueId(), type);

        // Fixed 54-slot (6 rows) modern GUI
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, getGuiTitle());

        // Fill with black glass pane border
        ItemStack borderPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderPane.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.text(" "));
            borderPane.setItemMeta(borderMeta);
        }

        // Fill all slots with border first
        for (int i = 0; i < size; i++) {
            inv.setItem(i, borderPane);
        }

        // Kit placement slots - rows 2 and 3 (slots 10-16. 19-25), centered
        int[] kitSlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34 };

        int index = 0;
        for (Map.Entry<String, Kit> entry : plugin.getKitManager().getKits().entrySet()) {
            if (index >= kitSlots.length)
                break;

            String kitName = entry.getKey();
            Kit kit = entry.getValue();

            // Use icon from config
            ItemStack icon = new ItemStack(kit.getIcon());

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                // Name
                String nameFormat = plugin.getLanguageManager().getMessage("gui.queue.item-name");
                nameFormat = nameFormat.replace("%kit%", kitName);
                meta.displayName(MiniMessage.miniMessage().deserialize(nameFormat));

                // Lore
                java.util.List<Component> lore = new java.util.ArrayList<>();
                int queueCount = (type == QueueType.RANKED)
                        ? plugin.getQueueManager().getRankedQueueSize(kitName)
                        : plugin.getQueueManager().getQueueSize(kitName);
                for (String line : plugin.getLanguageManager().getList("gui.queue.lore")) {
                    line = line.replace("%count%", String.valueOf(queueCount));
                    lore.add(MiniMessage.miniMessage().deserialize(line));
                }
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            inv.setItem(kitSlots[index], icon);
            index++;
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(getGuiTitle())) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
                return;

            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();

            // Extract kit name from Display Name
            // This is a bit risky if display name has formatting, but for now we striped
            // colors.
            // Better way: NBT tags (PersistentDataContainer).
            // For simplicity in this iteration, we iterate kits and check match.
            // Or we just stored the kitname in a hidden way?
            // Let's iterate kits.

            // Simple approach: Strip color from title
            String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(clicked.getItemMeta().displayName());
            // displayName might be "Default", we check if we have a kit named that.

            // Actually, let's just use the index if possible? No, kits are map.
            // Let's use PersistentDataContainer for robustness.

            // Re-doing the icon creation to add NBT would be better but let's try matching
            // name first.
            // Checking kit names.

            for (String kitName : plugin.getKitManager().getKits().keySet()) {
                if (displayName.contains(kitName)) {
                    // Perform queue join with the selected type
                    QueueType type = playerQueueTypeSelection.getOrDefault(player.getUniqueId(), QueueType.SOLO);
                    player.closeInventory();
                    plugin.getQueueManager().addToQueue(player, kitName, type);
                    playerQueueTypeSelection.remove(player.getUniqueId());
                    return;
                }
            }
        }
    }
}

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

    public void openQueueGui(Player player) {
        // Calculate size: 9, 18, 27 etc. based on kit count
        int kitCount = plugin.getKitManager().getKits().size();
        int rows = (int) Math.ceil(kitCount / 9.0);
        if (rows == 0)
            rows = 1;
        int size = Math.min(rows * 9, 54);

        Inventory inv = Bukkit.createInventory(null, size, getGuiTitle());

        int index = 0;
        for (Map.Entry<String, Kit> entry : plugin.getKitManager().getKits().entrySet()) {
            if (index >= size)
                break;

            String kitName = entry.getKey();
            Kit kit = entry.getValue();

            ItemStack icon = new ItemStack(Material.PAPER); // Default
            if (kit.getItems() != null && !kit.getItems().isEmpty()) {
                icon = kit.getItems().get(0).clone(); // Use first item as icon
            }

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                // Name
                String nameFormat = plugin.getLanguageManager().getMessage("gui.queue.item-name");
                nameFormat = nameFormat.replace("%kit%", kitName);
                meta.displayName(MiniMessage.miniMessage().deserialize(nameFormat));

                // Lore
                java.util.List<Component> lore = new java.util.ArrayList<>();
                for (String line : plugin.getLanguageManager().getList("gui.queue.lore")) {
                    line = line.replace("%count%", String.valueOf(plugin.getQueueManager().getQueueSize(kitName)));
                    lore.add(MiniMessage.miniMessage().deserialize(line));
                }
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            inv.setItem(index, icon);
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
                    // Perform queue join
                    player.closeInventory();
                    player.performCommand("duel join " + kitName);
                    return;
                }
            }
        }
    }
}

package me.raikou.duels.editor;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.kit.Kit;
import me.raikou.duels.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class KitEditorManager implements Listener {

    private final DuelsPlugin plugin;
    // UUID -> KitName (Being edited)
    private final Map<UUID, String> editingPlayers = new HashMap<>();
    private final net.kyori.adventure.text.Component GUI_TITLE = MiniMessage.miniMessage()
            .deserialize("<gradient:#00AAFF:#00FFFF><bold>Kit Editor</bold></gradient>");

    public KitEditorManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isEditing(Player player) {
        return editingPlayers.containsKey(player.getUniqueId());
    }

    public void openEditorSelectionGui(Player player) {
        int kitCount = plugin.getKitManager().getKits().size();
        int rows = (int) Math.ceil(kitCount / 9.0);
        if (rows == 0)
            rows = 1;
        int size = Math.min(rows * 9, 54);

        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE);

        int index = 0;
        for (Map.Entry<String, Kit> entry : plugin.getKitManager().getKits().entrySet()) {
            if (index >= size)
                break;

            String kitName = entry.getKey();
            Kit kit = entry.getValue();

            ItemStack icon = new ItemStack(Material.PAPER);
            if (kit.getItems() != null && !kit.getItems().isEmpty()) {
                icon = kit.getItems().get(0).clone();
            }

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(MiniMessage.miniMessage().deserialize("<yellow>" + kitName));
                meta.lore(java.util.List.of(
                        MiniMessage.miniMessage().deserialize("<gray>Click to edit layout")));
                icon.setItemMeta(meta);
            }
            inv.setItem(index, icon);
            index++;
        }

        player.openInventory(inv);
    }

    public void startEditing(Player player, String kitName) {
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            MessageUtil.sendError(player, "Kit not found.");
            return;
        }

        editingPlayers.put(player.getUniqueId(), kitName);
        player.getInventory().clear();

        // Load existing layout if present, otherwise default
        plugin.getStorage().loadKitLayout(player.getUniqueId(), kitName).thenAccept(layoutData -> {
            // Must run on main thread to modify inventory (though usually safe if async
            // handling is careful, but Bukkit API)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline())
                    return;

                if (layoutData != null) {
                    applyLayout(player, kit, layoutData);
                } else {
                    applyDefaultKit(player, kit);
                }

                // Give "Save" Button
                ItemStack save = new ItemStack(Material.EMERALD_BLOCK);
                ItemMeta meta = save.getItemMeta();
                meta.displayName(MiniMessage.miniMessage().deserialize("<green><bold>SAVE & EXIT</bold>"));
                save.setItemMeta(meta);
                player.getInventory().setItem(35, save); // Slot 35 (Bottom Right of Inventory)

                MessageUtil.sendInfo(player,
                        "Rearrange your inventory. Click the <green>Emerald Block</green> (Inventory) to save.");
            });
        });
    }

    public void saveAndExit(Player player) {
        String kitName = editingPlayers.remove(player.getUniqueId());
        if (kitName == null)
            return;

        // Serialize layout
        StringBuilder layoutData = new StringBuilder();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            // Skip the Save Button (slot 35)
            if (i == 35 && item != null && item.getType() == Material.EMERALD_BLOCK)
                continue;

            if (item != null && item.getType() != Material.AIR) {
                layoutData.append(i).append(":").append(item.getType().name()).append(",");
            }
        }

        // Remove trailing comma
        if (layoutData.length() > 0 && layoutData.charAt(layoutData.length() - 1) == ',') {
            layoutData.setLength(layoutData.length() - 1);
        }

        plugin.getStorage().saveKitLayout(player.getUniqueId(), kitName, layoutData.toString());
        MessageUtil.sendSuccess(player, "Kit layout saved!");

        restoreLobby(player);
    }

    public void cancelEditing(Player player) {
        if (editingPlayers.remove(player.getUniqueId()) != null) {
            MessageUtil.sendError(player, "Editing cancelled.");
            restoreLobby(player);
        }
    }

    private void restoreLobby(Player player) {
        player.getInventory().clear(); // Ensure clear before adding lobby items
        if (plugin.getLobbyManager().isLobbySet()) {
            plugin.getLobbyManager().teleportToLobby(player);
            plugin.getLobbyManager().giveLobbyItems(player);
        }
    }

    private void applyDefaultKit(Player player, Kit kit) {
        for (ItemStack item : kit.getItems()) {
            if (item != null)
                player.getInventory().addItem(item);
        }
        // Armor
        player.getInventory().setHelmet(kit.getHelmet());
        player.getInventory().setChestplate(kit.getChestplate());
        player.getInventory().setLeggings(kit.getLeggings());
        player.getInventory().setBoots(kit.getBoots());
    }

    private void applyLayout(Player player, Kit kit, String layoutData) {
        // Map of Material -> Count available in kit
        // Real implementation in a complex kit editor needs precise item tracking (NBT
        // etc).
        // Since our kits are simple, we just check if the kit has this material.

        // First populate map of available items
        Map<Material, Integer> available = new HashMap<>();
        for (ItemStack item : kit.getItems()) {
            if (item != null && item.getType() != Material.AIR) {
                available.put(item.getType(), available.getOrDefault(item.getType(), 0) + item.getAmount());
            }
        }

        String[] entries = layoutData.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 2)
                continue;

            try {
                int slot = Integer.parseInt(parts[0]);
                Material mat = Material.getMaterial(parts[1]);

                if (mat != null && available.containsKey(mat) && available.get(mat) > 0) {
                    // Determine stack size to give (max stack size or what's left)
                    // Simplifying: give 1 or whatever standard stack is?
                    // Our simple serialization didn't save amount, only type.
                    // And default kit gives "swords" etc.
                    // This simple logic assumes 1 item per slot for tools/armor, or stacks handled
                    // simply.
                    // Let's just create the item.

                    // Limitation: If kit has 64 arrows and user puts arrow in slot 9, we assume a
                    // full stack or 1?
                    // Let's assume we pull from available pool (e.g. 64 arrows -> slot 9 gets 64).

                    // Find the actual ItemStack from the kit list to preserve meta (enchants etc)
                    ItemStack kitItem = null;
                    for (ItemStack kItem : kit.getItems()) {
                        if (kItem != null && kItem.getType() == mat) {
                            kitItem = kItem;
                            // Ideally we should mark this item as 'used' so we don't duplicate if multiple
                            // same type items exist
                            // But for now, simple implementation.
                            break;
                        }
                    }

                    if (kitItem != null) {
                        player.getInventory().setItem(slot, kitItem);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Armor remains constant typically in this simple editor, or we give everything
        // else?
        // Any items not placed by layout should be added to next available slots.
        // For simplicity, we just apply layout and if missing items, tough luck (or we
        // improve later).
        // Actually, safer to run standard kit give first, then re-arrange?
        // NO, that's messy.
        // Better: Apply Default Kit -> then rearrange based on layout logic?
        // Yes.
        player.getInventory().clear();
        applyDefaultKit(player, kit);

        Inventory temp = Bukkit.createInventory(null, 54);
        temp.setContents(player.getInventory().getContents());
        player.getInventory().clear();

        // now put items where they belong according to layout
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 2)
                continue;
            int slot = Integer.parseInt(parts[0]);
            Material mat = Material.getMaterial(parts[1]);

            // Find item in temp
            for (int i = 0; i < temp.getSize(); i++) {
                ItemStack item = temp.getItem(i);
                if (item != null && item.getType() == mat) {
                    player.getInventory().setItem(slot, item);
                    temp.setItem(i, null); // Remove so we don't use again
                    break;
                }
            }
        }

        // Put remaining items back
        for (ItemStack remaining : temp.getContents()) {
            if (remaining != null) {
                player.getInventory().addItem(remaining);
            }
        }
        // Restore armor (already set by default kit apply, but getInventory().clear()
        // wiped it above?
        // setContents clears armor? No, getContents is main inv.
        // Documentation: getContents() returns all slots including armor?
        // Bukkit: getContents() returns 41 items (36 main + 4 armor + offhand).
        // So yes, we need to be careful.
        // Let's just re-set armor.
        player.getInventory().setHelmet(kit.getHelmet());
        player.getInventory().setChestplate(kit.getChestplate());
        player.getInventory().setLeggings(kit.getLeggings());
        player.getInventory().setBoots(kit.getBoots());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().title().equals(GUI_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR)
                return;

            // Kit selection logic
            String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(clicked.getItemMeta().displayName());
            for (String kitName : plugin.getKitManager().getKits().keySet()) {
                if (displayName.contains(kitName)) {
                    player.closeInventory();
                    startEditing(player, kitName);
                    return;
                }
            }
        }

        if (isEditing(player)) {
            // Check for Save Button click
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.EMERALD_BLOCK && event.getSlot() == 35) {
                event.setCancelled(true);
                saveAndExit(player);
            }
            // Prevent dropping save button or moving it?
            if (event.getSlot() == 35)
                event.setCancelled(true);
        }
    }
}

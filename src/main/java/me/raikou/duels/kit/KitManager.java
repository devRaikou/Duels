package me.raikou.duels.kit;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KitManager {

    private final DuelsPlugin plugin;
    private final Map<String, Kit> kits = new HashMap<>();

    public KitManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        loadKits();
    }

    public void loadKits() {
        kits.clear();
        ConfigurationSection kitsSection = plugin.getConfig().getConfigurationSection("kits");
        if (kitsSection == null)
            return;

        for (String key : kitsSection.getKeys(false)) {
            ConfigurationSection section = kitsSection.getConfigurationSection(key);
            if (section == null)
                continue;

            List<String> itemStrings = section.getStringList("items");
            List<ItemStack> items = new ArrayList<>();

            for (String str : itemStrings) {
                items.add(parseItem(str));
            }

            ItemStack helmet = parseItem(section.getString("armor.helmet"));
            ItemStack chestplate = parseItem(section.getString("armor.chestplate"));
            ItemStack leggings = parseItem(section.getString("armor.leggings"));
            ItemStack boots = parseItem(section.getString("armor.boots"));

            Kit kit = new Kit(key, items, helmet, chestplate, leggings, boots);
            kits.put(key, kit);
        }
        plugin.getLogger().info("Loaded " + kits.size() + " kits.");
    }

    private ItemStack parseItem(String str) {
        if (str == null || str.isEmpty())
            return new ItemStack(Material.AIR);

        String[] split = str.split(":");
        Material material = Material.matchMaterial(split[0]);
        if (material == null) {
            plugin.getLogger().warning("Invalid material: " + split[0]);
            return new ItemStack(Material.AIR);
        }

        int amount = 1;
        if (split.length > 1) {
            try {
                amount = Integer.parseInt(split[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        return new ItemStack(material, amount);
    }

    public Kit getKit(String name) {
        return kits.get(name);
    }

    public void createKit(String name, org.bukkit.entity.Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }

        // Simple serialization (Material:Amount) for this example
        List<String> itemStrings = new ArrayList<>();
        // Main inventory items (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                itemStrings.add(item.getType().name() + ":" + item.getAmount());
            }
        }

        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        String path = "kits." + name;
        plugin.getConfig().set(path + ".items", itemStrings);
        plugin.getConfig().set(path + ".armor.helmet", serializeItem(helmet));
        plugin.getConfig().set(path + ".armor.chestplate", serializeItem(chest));
        plugin.getConfig().set(path + ".armor.leggings", serializeItem(legs));
        plugin.getConfig().set(path + ".armor.boots", serializeItem(boots));

        plugin.saveConfig();
        loadKits(); // Reload
    }

    private String serializeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return "";
        return item.getType().name() + ":" + item.getAmount();
    }
}

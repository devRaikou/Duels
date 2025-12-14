package me.raikou.duels.kit;

import lombok.Data;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@Data
public class Kit {
    private final String name;
    private final Material icon; // GUI display icon
    private final List<ItemStack> items;
    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;

    public void equip(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // Armor
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        // Items
        for (int i = 0; i < items.size(); i++) {
            if (i < 36) { // Main inventory limit
                player.getInventory().setItem(i, items.get(i));
            }
        }

        player.updateInventory();
    }
}

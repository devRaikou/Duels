package me.raikou.duels.duel;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.arena.Arena;
import me.raikou.duels.kit.Kit;
import org.bukkit.entity.Player;

import java.util.*;

public class DuelManager {

    private final DuelsPlugin plugin;
    private final Set<Duel> activeDuels = new HashSet<>();
    private final Map<UUID, Duel> playerDuelMap = new HashMap<>();

    public DuelManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void startDuel(Player p1, Player p2, String kitName) {
        Arena arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            p1.sendMessage("No arena available!");
            p2.sendMessage("No arena available!");
            return;
        }

        List<UUID> players = Arrays.asList(p1.getUniqueId(), p2.getUniqueId());

        // Give Kits with layout check
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit != null) {
            applyKit(p1, kit, kitName);
            applyKit(p2, kit, kitName);
        }

        Duel duel = new Duel(plugin, arena, players);

        activeDuels.add(duel);
        playerDuelMap.put(p1.getUniqueId(), duel);
        playerDuelMap.put(p2.getUniqueId(), duel);

        duel.start();
    }

    private void applyKit(Player player, Kit kit, String kitName) {
        // Load layout synchronously for valid start (or cache it? Storage is async.
        // For production, we should preload layouts on join or before matching.
        // For now, we will block or rely on fast SQLite.
        // Actually, CompletableFuture join() is okay here for simplicity in this turn,
        // but ideally we refactor startDuel to be async or callback based.
        // Let's use join() as we are already on main thread and logic is simple.
        String layoutData = plugin.getStorage().loadKitLayout(player.getUniqueId(), kitName).join();

        if (layoutData != null && !layoutData.isEmpty()) {
            // Helper method logic similar to KitEditorManager, but we need to centralize it
            // or duplicate for now.
            // Duplication is cleaner for this interaction step, but refactoring later is
            // good.
            // Wait, I can't easily access KitEditorManager's private method or logic if
            // it's not static.
            // I will duplicate the layout application logic here properly, as it's the game
            // logic version.

            player.getInventory().clear();
            kit.equip(player); // Default modify

            // Now rearrange
            org.bukkit.inventory.Inventory temp = org.bukkit.Bukkit.createInventory(null, 54);
            temp.setContents(player.getInventory().getContents());
            player.getInventory().clear();

            String[] entries = layoutData.split(",");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length != 2)
                    continue;
                try {
                    int slot = Integer.parseInt(parts[0]);
                    org.bukkit.Material mat = org.bukkit.Material.getMaterial(parts[1]);

                    for (int i = 0; i < temp.getSize(); i++) {
                        org.bukkit.inventory.ItemStack item = temp.getItem(i);
                        if (item != null && item.getType() == mat) {
                            player.getInventory().setItem(slot, item);
                            temp.setItem(i, null);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            for (org.bukkit.inventory.ItemStack remaining : temp.getContents()) {
                if (remaining != null) {
                    player.getInventory().addItem(remaining);
                }
            }

            // Restore armor
            player.getInventory().setHelmet(kit.getHelmet());
            player.getInventory().setChestplate(kit.getChestplate());
            player.getInventory().setLeggings(kit.getLeggings());
            player.getInventory().setBoots(kit.getBoots());

        } else {
            kit.equip(player);
        }
    }

    public void removeDuel(Duel duel) {
        activeDuels.remove(duel);
        for (UUID uuid : duel.getPlayers()) {
            playerDuelMap.remove(uuid);
        }
    }

    public Duel getDuel(Player player) {
        return playerDuelMap.get(player.getUniqueId());
    }

    public boolean isInDuel(Player player) {
        return playerDuelMap.containsKey(player.getUniqueId());
    }
}

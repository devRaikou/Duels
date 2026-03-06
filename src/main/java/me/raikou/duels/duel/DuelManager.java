package me.raikou.duels.duel;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.arena.Arena;
import me.raikou.duels.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DuelManager {

    private final DuelsPlugin plugin;
    private final Set<Duel> activeDuels = new HashSet<>();
    private final Map<UUID, Duel> playerDuelMap = new HashMap<>();

    public DuelManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void startDuel(Player p1, Player p2, String kitName, boolean isRanked) {
        Arena arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            p1.sendMessage("No arena available!");
            p2.sendMessage("No arena available!");
            return;
        }

        UUID p1Id = p1.getUniqueId();
        UUID p2Id = p2.getUniqueId();
        String templateWorldName = arena.getSpawn1().getWorld().getName();
        String instanceName = "duel_" + arena.getName() + "_" + UUID.randomUUID().toString().split("-")[0];
        Kit kit = plugin.getKitManager().getKit(kitName);

        CompletableFuture<String> p1LayoutFuture = kit == null
                ? CompletableFuture.completedFuture(null)
                : plugin.getStorage().loadKitLayout(p1Id, kitName);
        CompletableFuture<String> p2LayoutFuture = kit == null
                ? CompletableFuture.completedFuture(null)
                : plugin.getStorage().loadKitLayout(p2Id, kitName);
        CompletableFuture<Boolean> worldPreparation = plugin.getWorldManager()
                .prepareDuelWorldAsync(templateWorldName, instanceName)
                .orTimeout(20, TimeUnit.SECONDS);

        CompletableFuture.allOf(p1LayoutFuture, p2LayoutFuture, worldPreparation)
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player1 = Bukkit.getPlayer(p1Id);
                    Player player2 = Bukkit.getPlayer(p2Id);
                    if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
                        plugin.getWorldManager().cleanupPreparedWorld(instanceName);
                        return;
                    }

                    if (!worldPreparation.getNow(false)) {
                        player1.sendMessage("Failed to create duel world!");
                        player2.sendMessage("Failed to create duel world!");
                        plugin.getWorldManager().cleanupPreparedWorld(instanceName);
                        return;
                    }

                    World instanceWorld = plugin.getWorldManager().loadPreparedWorld(instanceName);
                    if (instanceWorld == null) {
                        player1.sendMessage("Failed to load duel world!");
                        player2.sendMessage("Failed to load duel world!");
                        plugin.getWorldManager().cleanupPreparedWorld(instanceName);
                        return;
                    }

                    if (kit != null) {
                        applyKit(player1, kit, p1LayoutFuture.getNow(null));
                        applyKit(player2, kit, p2LayoutFuture.getNow(null));
                    }

                    List<UUID> players = Arrays.asList(p1Id, p2Id);
                    Duel duel = new Duel(plugin, arena, players, kitName, instanceWorld, isRanked);

                    activeDuels.add(duel);
                    playerDuelMap.put(p1Id, duel);
                    playerDuelMap.put(p2Id, duel);

                    duel.start();
                    plugin.getDiscordManager().onDuelStart(duel, kitName);
                }))
                .exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player player1 = Bukkit.getPlayer(p1Id);
                        Player player2 = Bukkit.getPlayer(p2Id);
                        if (player1 != null) {
                            player1.sendMessage("Failed to start duel.");
                        }
                        if (player2 != null) {
                            player2.sendMessage("Failed to start duel.");
                        }
                        plugin.getWorldManager().cleanupPreparedWorld(instanceName);
                    });
                    plugin.getLogger().warning("startDuel failed: " + throwable.getMessage());
                    return null;
                });
    }

    private void applyKit(Player player, Kit kit, String layoutData) {
        if (layoutData != null && !layoutData.isEmpty()) {
            player.getInventory().clear();
            kit.equip(player);

            Inventory temp = Bukkit.createInventory(null, 54);
            temp.setContents(player.getInventory().getContents());
            player.getInventory().clear();

            String[] entries = layoutData.split(",");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length != 2) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(parts[0]);
                    if (slot < 0 || slot >= player.getInventory().getSize()) {
                        continue;
                    }
                    Material mat = Material.getMaterial(parts[1]);
                    if (mat == null) {
                        continue;
                    }

                    for (int i = 0; i < temp.getSize(); i++) {
                        ItemStack item = temp.getItem(i);
                        if (item != null && item.getType() == mat) {
                            player.getInventory().setItem(slot, item);
                            temp.setItem(i, null);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            for (ItemStack remaining : temp.getContents()) {
                if (remaining != null) {
                    player.getInventory().addItem(remaining);
                }
            }

            player.getInventory().setHelmet(kit.getHelmet());
            player.getInventory().setChestplate(kit.getChestplate());
            player.getInventory().setLeggings(kit.getLeggings());
            player.getInventory().setBoots(kit.getBoots());
            return;
        }
        kit.equip(player);
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

    public Set<Duel> getActiveDuels() {
        return Collections.unmodifiableSet(activeDuels);
    }
}

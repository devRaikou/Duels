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

        // Give Kits
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit != null) {
            kit.equip(p1);
            kit.equip(p2);
        }

        Duel duel = new Duel(plugin, arena, players);

        activeDuels.add(duel);
        playerDuelMap.put(p1.getUniqueId(), duel);
        playerDuelMap.put(p2.getUniqueId(), duel);

        duel.start();
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

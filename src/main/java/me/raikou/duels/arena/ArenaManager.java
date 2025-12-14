package me.raikou.duels.arena;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ArenaManager {

    private final DuelsPlugin plugin;
    private final Map<String, Arena> arenas = new HashMap<>();

    public ArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        loadArenas();
    }

    public void loadArenas() {
        arenas.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection arenaSection = config.getConfigurationSection("arenas");

        if (arenaSection == null)
            return;

        for (String name : arenaSection.getKeys(false)) {
            Arena arena = new Arena(name);
            String path = "arenas." + name + ".";

            arena.setSpawn1(getLocationFromConfig(config, path + "spawn1"));
            arena.setSpawn2(getLocationFromConfig(config, path + "spawn2"));
            arena.setSpectatorSpawn(getLocationFromConfig(config, path + "spectator"));

            if (arena.isSetup()) {
                arenas.put(name, arena);
            } else {
                plugin.getLogger().warning("Arena " + name + " is not fully setup!");
            }
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
    }

    private Location getLocationFromConfig(FileConfiguration config, String path) {
        if (!config.contains(path))
            return null;
        String worldName = config.getString(path + ".world");
        if (worldName == null)
            return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return null;

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public Arena getAvailableArena() {
        List<Arena> available = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.getState() == ArenaState.WAITING && arena.isSetup()) {
                available.add(arena);
            }
        }
        if (available.isEmpty())
            return null;
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    public Arena getArena(String name) {
        return arenas.get(name);
    }
}

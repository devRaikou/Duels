package me.raikou.duels.lobby;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class LobbyManager {

    private final DuelsPlugin plugin;
    private Location lobbyLocation;

    public LobbyManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        loadLobby();
    }

    public void loadLobby() {
        FileConfiguration config = plugin.getConfig();
        if (!config.contains("lobby.world")) {
            return;
        }

        String worldName = config.getString("lobby.world");
        if (worldName == null)
            return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger()
                    .warning("Lobby world '" + worldName + "' not found! valid worlds: " + Bukkit.getWorlds());
            return;
        }

        double x = config.getDouble("lobby.x");
        double y = config.getDouble("lobby.y");
        double z = config.getDouble("lobby.z");
        float yaw = (float) config.getDouble("lobby.yaw");
        float pitch = (float) config.getDouble("lobby.pitch");

        this.lobbyLocation = new Location(world, x, y, z, yaw, pitch);
    }

    public void setLobby(Location location) {
        this.lobbyLocation = location;
        FileConfiguration config = plugin.getConfig();
        config.set("lobby.world", location.getWorld().getName());
        config.set("lobby.x", location.getX());
        config.set("lobby.y", location.getY());
        config.set("lobby.z", location.getZ());
        config.set("lobby.yaw", location.getYaw());
        config.set("lobby.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public void teleportToLobby(Player player) {
        if (lobbyLocation != null && lobbyLocation.getWorld() != null) {
            player.teleport(lobbyLocation);
        } else {
            player.sendMessage("§cLobby not set!");
        }
    }

    public boolean isLobbySet() {
        return lobbyLocation != null && lobbyLocation.getWorld() != null;
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void giveLobbyItems(Player player) {
        player.getInventory().clear();

        org.bukkit.inventory.ItemStack compass = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
        org.bukkit.inventory.meta.ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    me.raikou.duels.util.MessageUtil.parse("<gold><bold>Play Duels</bold> <gray>(Right Click)"));
            compass.setItemMeta(meta);
        }

        org.bukkit.inventory.ItemStack editor = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK);
        org.bukkit.inventory.meta.ItemMeta editorMeta = editor.getItemMeta();
        if (editorMeta != null) {
            editorMeta.displayName(
                    me.raikou.duels.util.MessageUtil.parse("<aqua><bold>Kit Editor</bold> <gray>(Right Click)"));
            editor.setItemMeta(editorMeta);
        }

        player.getInventory().setItem(0, compass);
        player.getInventory().setItem(4, editor);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
    }
}

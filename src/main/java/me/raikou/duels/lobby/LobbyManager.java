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

        // Unranked - Iron Sword
        org.bukkit.inventory.ItemStack unrankedItem = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.IRON_SWORD);
        org.bukkit.inventory.meta.ItemMeta unrankedMeta = unrankedItem.getItemMeta();
        if (unrankedMeta != null) {
            unrankedMeta.displayName(
                    me.raikou.duels.util.MessageUtil
                            .parse("<gray><bold>Unranked</bold></gray> <dark_gray>(Right Click)"));
            unrankedItem.setItemMeta(unrankedMeta);
        }

        // Ranked - Diamond Sword
        org.bukkit.inventory.ItemStack rankedItem = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta rankedMeta = rankedItem.getItemMeta();
        if (rankedMeta != null) {
            rankedMeta.displayName(
                    me.raikou.duels.util.MessageUtil
                            .parse("<gold><bold>Ranked</bold></gold> <dark_gray>(Right Click)"));
            rankedItem.setItemMeta(rankedMeta);
        }

        // Kit Editor - Book
        org.bukkit.inventory.ItemStack editor = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOOK);
        org.bukkit.inventory.meta.ItemMeta editorMeta = editor.getItemMeta();
        if (editorMeta != null) {
            editorMeta.displayName(
                    me.raikou.duels.util.MessageUtil.parse("<aqua><bold>Kit Editor</bold></aqua> <gray>(Right Click)"));
            editor.setItemMeta(editorMeta);
        }

        // Leaderboard - Knowledge Book
        org.bukkit.inventory.ItemStack leaderboard = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.KNOWLEDGE_BOOK);
        org.bukkit.inventory.meta.ItemMeta lbMeta = leaderboard.getItemMeta();
        if (lbMeta != null) {
            lbMeta.displayName(
                    me.raikou.duels.util.MessageUtil
                            .parse("<gold><bold>Leaderboard</bold></gold> <gray>(Right Click)"));
            leaderboard.setItemMeta(lbMeta);
        }

        player.getInventory().setItem(0, unrankedItem);
        player.getInventory().setItem(1, rankedItem);
        player.getInventory().setItem(4, editor);
        player.getInventory().setItem(8, leaderboard);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
    }
}

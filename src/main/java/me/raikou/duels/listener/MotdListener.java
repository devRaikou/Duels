package me.raikou.duels.listener;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Enhanced MOTD listener with hover player sample and modern placeholders.
 */
public class MotdListener implements Listener {

    private final DuelsPlugin plugin;
    private CachedServerIcon cachedIcon;

    public MotdListener(DuelsPlugin plugin) {
        this.plugin = plugin;
        loadServerIcon();
    }

    /**
     * Load server icon if exists.
     */
    private void loadServerIcon() {
        try {
            java.io.File iconFile = new java.io.File("server-icon.png");
            if (iconFile.exists()) {
                cachedIcon = Bukkit.loadServerIcon(iconFile);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load server icon: " + e.getMessage());
        }
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.enabled", false)) {
            return;
        }

        List<String> lines = plugin.getConfig().getStringList("motd.lines");
        if (lines.isEmpty())
            return;

        // Build MOTD with placeholders
        String rawMotd = String.join("<newline>", lines);
        rawMotd = replacePlaceholders(rawMotd, event);

        // Parse MiniMessage
        Component motd = MessageUtil.parse(rawMotd);
        event.motd(motd);

        // Set custom max players if configured
        if (plugin.getConfig().contains("motd.max-players")) {
            int maxPlayers = plugin.getConfig().getInt("motd.max-players", 100);
            event.setMaxPlayers(maxPlayers);
        }

        // Set server icon
        if (cachedIcon != null) {
            event.setServerIcon(cachedIcon);
        }

        // Handle player sample (hover list)
        handlePlayerSample(event);
    }

    /**
     * Replace all placeholders in the MOTD string.
     */
    private String replacePlaceholders(String text, ServerListPingEvent event) {
        int online = event.getNumPlayers();
        int max = event.getMaxPlayers();
        int inDuel = countPlayersInDuel();
        int inQueue = countPlayersInQueue();
        int activeDuels = plugin.getDuelManager().getActiveDuels().size();

        // Date and time formatters
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // Runtime info
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;

        // TPS (approximate via Bukkit)
        double tps = 20.0; // Default, Bukkit doesn't expose TPS directly without Paper
        try {
            // Try Paper's TPS if available
            Object server = Bukkit.getServer();
            java.lang.reflect.Method getTPS = server.getClass().getMethod("getTPS");
            double[] tpsArray = (double[]) getTPS.invoke(server);
            tps = Math.min(20.0, tpsArray[0]);
        } catch (Exception ignored) {
            // Not Paper or method not available
        }

        return text
                // Player counts
                .replace("%online%", String.valueOf(online))
                .replace("%max%", String.valueOf(max))
                .replace("%in_duel%", String.valueOf(inDuel))
                .replace("%in_queue%", String.valueOf(inQueue))
                .replace("%active_duels%", String.valueOf(activeDuels))
                .replace("%spectators%", String.valueOf(countSpectators()))

                // Server info
                .replace("%version%", plugin.getPluginMeta().getVersion())
                .replace("%server_version%", Bukkit.getVersion())
                .replace("%bukkit_version%", Bukkit.getBukkitVersion())

                // Time
                .replace("%date%", today.format(dateFormatter))
                .replace("%time%", now.format(timeFormatter))
                .replace("%day%", today.getDayOfWeek().toString())

                // System
                .replace("%memory%", usedMemory + "/" + maxMemory + "MB")
                .replace("%tps%", String.format("%.1f", tps))

                // Misc
                .replace("%uptime%", getUptime());
    }

    /**
     * Handle player sample for hover effect.
     */
    private void handlePlayerSample(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.player-sample.enabled", true)) {
            return;
        }

        // Try Paper API
        try {
            if (!(event instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent paperEvent)) {
                return;
            }

            // Get sample configuration
            List<String> customLines = plugin.getConfig().getStringList("motd.player-sample.lines");
            boolean showPlayers = plugin.getConfig().getBoolean("motd.player-sample.show-players", true);
            int maxSamplePlayers = plugin.getConfig().getInt("motd.player-sample.max-players", 10);

            // Clear existing sample using getListedPlayers()
            paperEvent.getListedPlayers().clear();

            // Add custom lines first
            for (String line : customLines) {
                String processed = replacePlaceholders(line, event);
                // Strip MiniMessage tags for sample (plain text only)
                processed = processed.replaceAll("<[^>]+>", "");
                addSampleEntry(paperEvent, processed);
            }

            // Add actual players if enabled
            if (showPlayers) {
                int count = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (count >= maxSamplePlayers)
                        break;

                    String status = "";
                    if (plugin.getDuelManager().isInDuel(player)) {
                        status = " §c⚔";
                    } else if (plugin.getQueueManager().isInQueue(player)) {
                        status = " §e⏳";
                    } else if (plugin.getSpectatorManager().isSpectating(player)) {
                        status = " §b👁";
                    }

                    addSampleEntry(paperEvent, "§7" + player.getName() + status);
                    count++;
                }

                // Show remaining count if there are more players
                int remaining = Bukkit.getOnlinePlayers().size() - count;
                if (remaining > 0) {
                    addSampleEntry(paperEvent, "§8... and " + remaining + " more");
                }
            }
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            // Not Paper or API not available
        }
    }

    /**
     * Add an entry to the player sample using Paper's ListedPlayerInfo.
     * Note: ListedPlayerInfo allows longer text than player names.
     */
    private void addSampleEntry(com.destroystokyo.paper.event.server.PaperServerListPingEvent event, String text) {
        try {
            // Use ListedPlayerInfo directly with a random UUID
            // This avoids the 16 character limit from createProfile
            com.destroystokyo.paper.event.server.PaperServerListPingEvent.ListedPlayerInfo info = new com.destroystokyo.paper.event.server.PaperServerListPingEvent.ListedPlayerInfo(
                    text, UUID.randomUUID());

            event.getListedPlayers().add(info);
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            // API not available
        }
    }

    /**
     * Count players currently in duels.
     */
    private int countPlayersInDuel() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getDuelManager().isInDuel(player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count players currently in queue.
     */
    private int countPlayersInQueue() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getQueueManager().isInQueue(player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count current spectators.
     */
    private int countSpectators() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getSpectatorManager().isSpectating(player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get server uptime as formatted string.
     */
    private String getUptime() {
        long uptime = System.currentTimeMillis() - plugin.getEnableTime();
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else {
            return minutes + "m " + (seconds % 60) + "s";
        }
    }
}

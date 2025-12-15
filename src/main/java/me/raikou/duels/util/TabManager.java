package me.raikou.duels.util;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.duel.Duel;
import me.raikou.duels.stats.PlayerStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages TAB header, footer, player list names and sorting with LuckPerms
 * integration.
 */
public class TabManager implements Listener {

    private final DuelsPlugin plugin;
    private LuckPerms luckPerms;
    private boolean luckPermsEnabled = false;

    // Store each player's team name for proper cleanup
    private final Map<UUID, String> playerTeams = new HashMap<>();

    public TabManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Try to hook into LuckPerms
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
                luckPermsEnabled = true;
                plugin.getLogger().info("LuckPerms hooked for TAB formatting!");
            }
        } catch (Exception e) {
            plugin.getLogger().info("LuckPerms not found for TAB formatting.");
        }

        // Setup for existing players
        for (Player player : Bukkit.getOnlinePlayers()) {
            setupPlayer(player);
        }

        startUpdater();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Delay to ensure player is ready
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setupPlayer(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String teamName = playerTeams.remove(uuid);

        // Clean up from all player scoreboards
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(uuid))
                continue;
            Scoreboard board = online.getScoreboard();
            if (board != null && teamName != null) {
                Team team = board.getTeam(teamName);
                if (team != null && team.hasEntry(event.getPlayer().getName())) {
                    team.removeEntry(event.getPlayer().getName());
                }
            }
        }
    }

    private void setupPlayer(Player player) {
        updateTab(player);
        updatePlayerListName(player);
        updatePlayerSorting(player);
    }

    private void startUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateTab(player);
                    updatePlayerListName(player);
                    // Re-apply sorting periodically to fix any issues
                    updatePlayerSorting(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 60L); // Update every 3 seconds
    }

    public void updateTab(Player player) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        List<String> headerLines = plugin.getConfig().getStringList("tab.header");
        List<String> footerLines = plugin.getConfig().getStringList("tab.footer");

        // Build header
        StringBuilder headerBuilder = new StringBuilder();
        for (int i = 0; i < headerLines.size(); i++) {
            String line = applyPlaceholders(headerLines.get(i), player);
            headerBuilder.append(line);
            if (i < headerLines.size() - 1) {
                headerBuilder.append("\n");
            }
        }

        // Build footer
        StringBuilder footerBuilder = new StringBuilder();
        for (int i = 0; i < footerLines.size(); i++) {
            String line = applyPlaceholders(footerLines.get(i), player);
            footerBuilder.append(line);
            if (i < footerLines.size() - 1) {
                footerBuilder.append("\n");
            }
        }

        Component header = MessageUtil.parse(headerBuilder.toString());
        Component footer = MessageUtil.parse(footerBuilder.toString());

        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    /**
     * Updates player list name with LuckPerms prefix/suffix.
     */
    public void updatePlayerListName(Player player) {
        if (!plugin.getConfig().getBoolean("tab.player-list.enabled", true)) {
            return;
        }

        String format = plugin.getConfig().getString("tab.player-list.format",
                "%prefix%<white>%player%</white>%suffix%");

        String prefix = "";
        String suffix = "";
        String group = "";
        String groupDisplayName = "";

        if (luckPermsEnabled && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                CachedMetaData metaData = user.getCachedData().getMetaData();
                prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
                suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";

                String primaryGroup = user.getPrimaryGroup();
                group = primaryGroup != null ? primaryGroup : "";

                Group lpGroup = luckPerms.getGroupManager().getGroup(primaryGroup);
                if (lpGroup != null) {
                    String displayName = lpGroup.getDisplayName();
                    groupDisplayName = displayName != null ? displayName : group;
                } else {
                    groupDisplayName = group;
                }
            }
        }

        format = format.replace("%prefix%", prefix);
        format = format.replace("%suffix%", suffix);
        format = format.replace("%player%", player.getName());
        format = format.replace("%group%", group);
        format = format.replace("%group_display%", groupDisplayName);

        Component playerListName = MessageUtil.parse(format);
        player.playerListName(playerListName);
    }

    /**
     * Updates player sorting in TAB based on configured group order.
     * Uses per-player scoreboard teams for sorting.
     */
    public void updatePlayerSorting(Player player) {
        if (!plugin.getConfig().getBoolean("tab.sorting.enabled", true)) {
            return;
        }

        // Skip if player is in a duel (NametagManager handles them)
        if (plugin.getDuelManager().isInDuel(player)) {
            return;
        }

        List<String> groupOrder = plugin.getConfig().getStringList("tab.sorting.group-order");

        // Get player's primary group
        String playerGroup = "default";
        String prefix = "";
        String suffix = "";

        if (luckPermsEnabled && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                CachedMetaData metaData = user.getCachedData().getMetaData();
                if (user.getPrimaryGroup() != null) {
                    playerGroup = user.getPrimaryGroup();
                }
                prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
                suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";
            }
        }

        // Find group index (lower = higher priority in TAB)
        int priority = 99;
        if (!groupOrder.isEmpty()) {
            priority = groupOrder.size();
            for (int i = 0; i < groupOrder.size(); i++) {
                if (groupOrder.get(i).equalsIgnoreCase(playerGroup)) {
                    priority = i;
                    break;
                }
            }
        }

        // Team name format: priority_PlayerName (Unique per player to support
        // individual prefixes)
        // e.g. 00_Raikou
        String teamName = String.format("%02d_%s", priority, player.getName());
        if (teamName.length() > 64) {
            teamName = teamName.substring(0, 64);
        }

        String playerName = player.getName();
        final Component prefixComp = MessageUtil.parse(prefix);
        final Component suffixComp = MessageUtil.parse(suffix);

        // Store team for this player
        playerTeams.put(player.getUniqueId(), teamName);

        // Apply to all online players' scoreboards (including this player's)
        for (Player online : Bukkit.getOnlinePlayers()) {
            Scoreboard board = online.getScoreboard();

            // If player has main scoreboard, give them a new one
            if (board == null || board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                online.setScoreboard(board);
            }

            // Remove player from old teams on this board
            // We only need to check validation if they are in a different team
            Team existingTeam = board.getEntryTeam(playerName);
            if (existingTeam != null && !existingTeam.getName().equals(teamName)) {
                existingTeam.removeEntry(playerName);
                // If the old team was essentially just for this player (format 00_Name), we
                // could unregister it?
                // But for simplicity/stability, we leave it or rely on cleanup.
                if (existingTeam.getName().matches("\\d{2}_" + java.util.regex.Pattern.quote(playerName))) {
                    try {
                        existingTeam.unregister();
                    } catch (Exception ignored) {
                    }
                }
            }

            // Get or create team
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                // Collision rule never to prevent pushing in lobby
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            }

            // Determine Color from Meta "color" (e.g. red, dark_red, #ff0000)
            NamedTextColor nameColor = NamedTextColor.WHITE; // Default

            if (luckPermsEnabled && luckPerms != null) {
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    String colorName = user.getCachedData().getMetaData().getMetaValue("color");
                    if (colorName != null) {
                        NamedTextColor matched = NamedTextColor.NAMES.value(colorName.toLowerCase().replace(" ", "_"));
                        if (matched != null) {
                            nameColor = matched;
                        }
                    }
                }
            }

            // Set Color
            team.color(nameColor);

            // Update Prefix/Suffix
            // We set this every time because prefix might change even if team checks out
            team.prefix(prefixComp);
            team.suffix(suffixComp);

            // Add player to team
            if (!team.hasEntry(playerName)) {
                team.addEntry(playerName);
            }
        }
    }

    private String applyPlaceholders(String text, Player player) {
        // Player info
        text = text.replace("%player%", player.getName());
        text = text.replace("%ping%", String.valueOf(player.getPing()));
        text = text.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        text = text.replace("%date%", java.time.LocalDate.now().toString());
        text = text.replace("%time%", java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")));

        // Queue and Status
        Duel duel = plugin.getDuelManager().getDuel(player);
        if (plugin.getQueueManager().isInQueue(player)) {
            text = text.replace("%queue%", "Searching...");
            text = text.replace("%status%", "In Queue");
        } else if (duel != null) {
            text = text.replace("%queue%", "In Duel");
            text = text.replace("%status%", "In Duel");
        } else {
            text = text.replace("%queue%", "Not in queue");
            text = text.replace("%status%", "In Lobby");
        }

        // LuckPerms info
        if (luckPermsEnabled && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                CachedMetaData metaData = user.getCachedData().getMetaData();
                text = text.replace("%prefix%", metaData.getPrefix() != null ? metaData.getPrefix() : "");
                text = text.replace("%suffix%", metaData.getSuffix() != null ? metaData.getSuffix() : "");
                text = text.replace("%group%", user.getPrimaryGroup() != null ? user.getPrimaryGroup() : "");

                Group group = luckPerms.getGroupManager().getGroup(user.getPrimaryGroup());
                if (group != null && group.getDisplayName() != null) {
                    text = text.replace("%group_display%", group.getDisplayName());
                } else {
                    text = text.replace("%group_display%",
                            user.getPrimaryGroup() != null ? user.getPrimaryGroup() : "");
                }
            } else {
                text = text.replace("%prefix%", "");
                text = text.replace("%suffix%", "");
                text = text.replace("%group%", "");
                text = text.replace("%group_display%", "");
            }
        } else {
            text = text.replace("%prefix%", "");
            text = text.replace("%suffix%", "");
            text = text.replace("%group%", "");
            text = text.replace("%group_display%", "");
        }

        // Stats
        PlayerStats stats = plugin.getStatsManager().getStats(player);
        text = text.replace("%wins%", String.valueOf(stats.getWins()));
        text = text.replace("%losses%", String.valueOf(stats.getLosses()));
        text = text.replace("%kills%", String.valueOf(stats.getKills()));
        text = text.replace("%deaths%", String.valueOf(stats.getDeaths()));

        // Duel info
        if (duel != null) {
            text = text.replace("%kit%", duel.getKitName() != null ? duel.getKitName() : "None");

            for (UUID uuid : duel.getPlayers()) {
                if (!uuid.equals(player.getUniqueId())) {
                    Player opponent = Bukkit.getPlayer(uuid);
                    text = text.replace("%opponent%", opponent != null ? opponent.getName() : "Unknown");
                    break;
                }
            }
        } else {
            text = text.replace("%kit%", "None");
            text = text.replace("%opponent%", "None");
        }

        return text;
    }
}

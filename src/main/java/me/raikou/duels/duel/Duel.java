package me.raikou.duels.duel;

import lombok.Getter;
import lombok.Setter;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.arena.Arena;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
public class Duel {

    private final DuelsPlugin plugin;
    private final org.bukkit.World instanceWorld;
    private final Arena arena;
    private final List<UUID> players;
    private final List<UUID> alivePlayers;
    @Setter
    private DuelState state;
    @Getter
    private long startTime;
    private final String kitName;
    @Getter
    private final boolean ranked;
    @Getter
    private final Set<UUID> spectators = new HashSet<>();

    /**
     * Add a spectator to this duel.
     */
    public void addSpectator(UUID uuid) {
        spectators.add(uuid);
    }

    /**
     * Remove a spectator from this duel.
     */
    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
    }

    public Duel(DuelsPlugin plugin, Arena arena, List<UUID> players, String kitName, org.bukkit.World instanceWorld,
            boolean isRanked) {
        this.plugin = plugin;
        this.arena = arena;
        this.players = players;
        this.kitName = kitName;
        this.alivePlayers = new ArrayList<>(players);
        this.state = DuelState.STARTING;
        this.instanceWorld = instanceWorld;
        this.ranked = isRanked;
    }

    public void start() {
        // Teleport players to the instance world locations
        // We map the relative coordinates from template arena to instance world
        Player p1 = Bukkit.getPlayer(players.get(0));
        Player p2 = Bukkit.getPlayer(players.get(1));

        // Store original walk speeds for restoration
        java.util.Map<UUID, Float> originalWalkSpeeds = new java.util.HashMap<>();

        if (p1 != null) {
            Location loc1 = arena.getSpawn1().clone();
            loc1.setWorld(instanceWorld);
            p1.teleport(loc1);

            // Freeze player
            originalWalkSpeeds.put(p1.getUniqueId(), p1.getWalkSpeed());
            freezePlayer(p1);
        }
        if (p2 != null) {
            Location loc2 = arena.getSpawn2().clone();
            loc2.setWorld(instanceWorld);
            p2.teleport(loc2);

            // Freeze player
            originalWalkSpeeds.put(p2.getUniqueId(), p2.getWalkSpeed());
            freezePlayer(p2);
        }

        // Countdown with freeze
        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (count == 0) {
                    state = DuelState.FIGHTING;
                    startTime = System.currentTimeMillis();

                    // Unfreeze players and show titles
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            float originalSpeed = originalWalkSpeeds.getOrDefault(uuid, 0.2f);
                            unfreezePlayer(p, originalSpeed);

                            p.showTitle(net.kyori.adventure.title.Title.title(
                                    me.raikou.duels.util.MessageUtil.getRaw("titles.duel-start.title"),
                                    me.raikou.duels.util.MessageUtil.getRaw("titles.duel-start.subtitle")));
                            p.sendMessage(me.raikou.duels.util.MessageUtil.get("duel.start", "%opponent%",
                                    players.get(0).equals(uuid) ? Bukkit.getPlayer(players.get(1)).getName()
                                            : Bukkit.getPlayer(players.get(0)).getName(),
                                    "%kit%", kitName));
                        }
                    }
                    originalWalkSpeeds.clear();
                    cancel();
                    return;
                }

                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.showTitle(net.kyori.adventure.title.Title.title(
                                me.raikou.duels.util.MessageUtil.getRaw("titles.countdown.title", "%count%",
                                        String.valueOf(count)),
                                Component.empty()));
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Freeze a player during countdown.
     */
    private void freezePlayer(Player player) {
        // Set walk speed to 0
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);

        // Add slowness to prevent movement (high amplifier)
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, 200, 255, false, false, false));

        // Use JUMP_BOOST with amplifier 128+ to give negative jump (prevents jumping)
        // Amplifier 128 = -1 jump boost, 129 = -2, etc.
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.JUMP_BOOST, 200, 128, false, false, false));

        // Lock player in place by setting velocity to zero
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    /**
     * Unfreeze a player after countdown.
     */
    private void unfreezePlayer(Player player, float originalWalkSpeed) {
        // Restore original walk speed
        player.setWalkSpeed(originalWalkSpeed);
        player.setFlySpeed(0.1f);

        // Remove freeze effects
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);

        // Reset velocity
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    // reset logic
    public void reset() {
        // Teleport to spawn and reset players
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.setHealth(20);
                p.setFoodLevel(20);
                p.setSaturation(20);
                p.setFireTicks(0);
                p.setExp(0);
                p.setLevel(0);
                for (org.bukkit.potion.PotionEffect effect : p.getActivePotionEffects()) {
                    p.removePotionEffect(effect.getType());
                }

                if (plugin.getLobbyManager().isLobbySet()) {
                    plugin.getLobbyManager().teleportToLobby(p);
                    plugin.getLobbyManager().giveLobbyItems(p);
                } else {
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
            }
        }

        // Remove spectators
        if (plugin.getSpectatorManager() != null) {
            plugin.getSpectatorManager().removeAllFromDuel(this);
        }

        // Remove from manager
        plugin.getDuelManager().removeDuel(this);

        // Delete World
        if (instanceWorld != null) {
            plugin.getWorldManager().deleteDuelWorld(instanceWorld.getName());
        }
    }

    public void sendMessageToAll(Component message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null)
                p.sendMessage(message);
        }
    }

    public void end(UUID winner) {
        this.state = DuelState.ENDING;

        Player winnerPlayer = Bukkit.getPlayer(winner);
        String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";

        // Find loser
        UUID loser = null;
        for (UUID uuid : players) {
            if (!uuid.equals(winner)) {
                loser = uuid;
                break;
            }
        }
        Player loserPlayer = loser != null ? Bukkit.getPlayer(loser) : null;
        String loserName = loserPlayer != null ? loserPlayer.getName() : "Unknown";

        // Capture inventories before reset
        List<org.bukkit.inventory.ItemStack> winnerInventory = new ArrayList<>();
        org.bukkit.inventory.ItemStack[] winnerArmor = null;
        List<org.bukkit.inventory.ItemStack> loserInventory = new ArrayList<>();
        org.bukkit.inventory.ItemStack[] loserArmor = null;

        if (winnerPlayer != null) {
            for (org.bukkit.inventory.ItemStack item : winnerPlayer.getInventory().getContents()) {
                winnerInventory.add(item != null ? item.clone() : null);
            }
            winnerArmor = winnerPlayer.getInventory().getArmorContents().clone();
        }
        if (loserPlayer != null) {
            for (org.bukkit.inventory.ItemStack item : loserPlayer.getInventory().getContents()) {
                loserInventory.add(item != null ? item.clone() : null);
            }
            loserArmor = loserPlayer.getInventory().getArmorContents().clone();
        }

        // Calculate match duration
        long durationMs = System.currentTimeMillis() - startTime;

        // Generate unique match ID
        String matchId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Record stats using new methods (auto-saves and refreshes leaderboard)
        if (winnerPlayer != null) {
            plugin.getStatsManager().recordWin(winnerPlayer);
        }
        if (loserPlayer != null) {
            plugin.getStatsManager().recordLoss(loserPlayer);
        }

        plugin.getDiscordManager().onDuelEnd(this, winner);

        // Show titles
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.showTitle(net.kyori.adventure.title.Title.title(
                        me.raikou.duels.util.MessageUtil.getRaw("titles.duel-end.title"),
                        me.raikou.duels.util.MessageUtil.getRaw("titles.duel-end.subtitle", "%winner%", winnerName)));
            }
        }

        // Handle ELO for ranked duels and create match result
        final UUID finalLoser = loser;
        final List<org.bukkit.inventory.ItemStack> finalWinnerInventory = winnerInventory;
        final org.bukkit.inventory.ItemStack[] finalWinnerArmor = winnerArmor;
        final List<org.bukkit.inventory.ItemStack> finalLoserInventory = loserInventory;
        final org.bukkit.inventory.ItemStack[] finalLoserArmor = loserArmor;
        final double winnerHealth = winnerPlayer != null ? winnerPlayer.getHealth() : 0;

        if (ranked && loser != null) {
            int eloGain = plugin.getConfig().getInt("ranked.elo-gain-base", 25);
            int eloLoss = plugin.getConfig().getInt("ranked.elo-loss-base", 25);

            plugin.getStorage().loadElo(winner, kitName).thenAccept(winnerElo -> {
                plugin.getStorage().loadElo(finalLoser, kitName).thenAccept(loserElo -> {
                    int newWinnerElo = winnerElo + eloGain;
                    int newLoserElo = Math.max(100, loserElo - eloLoss);

                    plugin.getStorage().saveElo(winner, kitName, newWinnerElo);
                    plugin.getStorage().saveElo(finalLoser, kitName, newLoserElo);

                    // Create and store match result
                    me.raikou.duels.match.DuelResult result = me.raikou.duels.match.DuelResult.builder()
                            .matchId(matchId)
                            .timestamp(System.currentTimeMillis())
                            .kitName(kitName)
                            .ranked(true)
                            .winnerUuid(winner)
                            .winnerName(winnerName)
                            .winnerHealth(winnerHealth)
                            .winnerEloChange(eloGain)
                            .winnerNewElo(newWinnerElo)
                            .winnerInventory(finalWinnerInventory)
                            .winnerArmor(finalWinnerArmor)
                            .loserUuid(finalLoser)
                            .loserName(loserName)
                            .loserEloChange(-eloLoss)
                            .loserNewElo(newLoserElo)
                            .loserInventory(finalLoserInventory)
                            .loserArmor(finalLoserArmor)
                            .durationMs(durationMs)
                            .build();

                    plugin.getMatchHistoryManager().addMatch(result);

                    // Send clickable match result messages
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sendMatchResultMessage(result);
                    });
                });
            });
        } else {
            // Unranked duel - create match result without ELO
            me.raikou.duels.match.DuelResult result = me.raikou.duels.match.DuelResult.builder()
                    .matchId(matchId)
                    .timestamp(System.currentTimeMillis())
                    .kitName(kitName)
                    .ranked(false)
                    .winnerUuid(winner)
                    .winnerName(winnerName)
                    .winnerHealth(winnerHealth)
                    .winnerEloChange(0)
                    .winnerNewElo(0)
                    .winnerInventory(finalWinnerInventory)
                    .winnerArmor(finalWinnerArmor)
                    .loserUuid(finalLoser)
                    .loserName(loserName)
                    .loserEloChange(0)
                    .loserNewElo(0)
                    .loserInventory(finalLoserInventory)
                    .loserArmor(finalLoserArmor)
                    .durationMs(durationMs)
                    .build();

            plugin.getMatchHistoryManager().addMatch(result);
            sendMatchResultMessage(result);
        }

        // Cleanup after delay
        new BukkitRunnable() {
            @Override
            public void run() {
                reset();
            }
        }.runTaskLater(plugin, 60L); // 3 seconds
    }

    /**
     * Send the match result message with clickable GUI opener.
     */
    private void sendMatchResultMessage(me.raikou.duels.match.DuelResult result) {
        // Build kill message using localization
        String killMsgTemplate = me.raikou.duels.util.MessageUtil.getString("match-chat.kill-message");
        Component killMessage = me.raikou.duels.util.MessageUtil.parse(killMsgTemplate
                .replace("%loser%", result.getLoserName())
                .replace("%winner%", result.getWinnerName())
                .replace("%health%", result.getFormattedWinnerHealth()));

        // Build clickable match results header
        String headerText = me.raikou.duels.util.MessageUtil.getString("match-chat.header");
        String hoverText = me.raikou.duels.util.MessageUtil.getString("match-chat.hover-text");
        Component matchResultsHeader = net.kyori.adventure.text.Component.text("\n")
                .append(me.raikou.duels.util.MessageUtil.parse(headerText))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent
                        .runCommand("/duel matchresult " + result.getMatchId()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        me.raikou.duels.util.MessageUtil.parse(hoverText)));

        // Build winner/loser info lines
        Component winnerLine;
        Component loserLine;

        if (result.isRanked()) {
            String winnerTemplate = me.raikou.duels.util.MessageUtil.getString("match-chat.winner-line-ranked");
            winnerLine = me.raikou.duels.util.MessageUtil.parse(winnerTemplate
                    .replace("%winner%", result.getWinnerName())
                    .replace("%elo%", String.valueOf(result.getWinnerNewElo()))
                    .replace("%change%", String.valueOf(result.getWinnerEloChange())));

            String loserTemplate = me.raikou.duels.util.MessageUtil.getString("match-chat.loser-line-ranked");
            loserLine = me.raikou.duels.util.MessageUtil.parse(loserTemplate
                    .replace("%loser%", result.getLoserName())
                    .replace("%elo%", String.valueOf(result.getLoserNewElo()))
                    .replace("%change%", String.valueOf(result.getLoserEloChange())));
        } else {
            String winnerTemplate = me.raikou.duels.util.MessageUtil.getString("match-chat.winner-line");
            winnerLine = me.raikou.duels.util.MessageUtil.parse(winnerTemplate
                    .replace("%winner%", result.getWinnerName()));

            String loserTemplate = me.raikou.duels.util.MessageUtil.getString("match-chat.loser-line");
            loserLine = me.raikou.duels.util.MessageUtil.parse(loserTemplate
                    .replace("%loser%", result.getLoserName()));
        }

        // Combine all components
        Component fullMessage = killMessage
                .append(matchResultsHeader)
                .append(net.kyori.adventure.text.Component.text("\n"))
                .append(winnerLine)
                .append(net.kyori.adventure.text.Component.text("\n"))
                .append(loserLine);

        // Send to both players
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(fullMessage);
            }
        }

        // Send to spectators
        for (UUID uuid : spectators) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(fullMessage);
            }
        }
    }

}

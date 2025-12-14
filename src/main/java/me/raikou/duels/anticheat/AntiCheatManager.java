package me.raikou.duels.anticheat;

import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.anticheat.checks.FlightCheck;
import me.raikou.duels.anticheat.checks.KillAuraCheck;
import me.raikou.duels.anticheat.checks.ReachCheck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main anti-cheat manager that coordinates all checks.
 */
public class AntiCheatManager implements Listener {

    private final DuelsPlugin plugin;
    private final boolean enabled;
    private final List<Check> checks = new ArrayList<>();
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();
    private final String alertPermission = "duels.anticheat.alerts";

    public AntiCheatManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("anticheat.enabled", true);

        if (enabled) {
            registerChecks();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            startFlightChecker();
            plugin.getLogger().info("Anti-Cheat system enabled with " + checks.size() + " checks!");
        }
    }

    private void registerChecks() {
        checks.add(new FlightCheck(plugin));
        checks.add(new KillAuraCheck(plugin));
        checks.add(new ReachCheck(plugin));
    }

    /**
     * Run flight check periodically
     */
    private void startFlightChecker() {
        Check flightCheck = getCheck("Flight");
        if (flightCheck == null || !flightCheck.isEnabled())
            return;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (flightCheck.check(player)) {
                        flagPlayer(player, flightCheck, "Suspicious flight detected");
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 10L); // Every 0.5 seconds
    }

    /**
     * Check combat-related hacks on damage
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled)
            return;

        if (!(event.getDamager() instanceof Player attacker))
            return;
        if (!(event.getEntity() instanceof Player victim))
            return;

        // Run combat checks
        Check killAura = getCheck("KillAura");
        Check reach = getCheck("Reach");

        if (killAura != null && killAura.isEnabled() && killAura.check(attacker, victim)) {
            flagPlayer(attacker, killAura, "Attack angle: " +
                    String.format("%.1f°", getAttackAngle(attacker, victim)));
        }

        if (reach != null && reach.isEnabled() && reach.check(attacker, victim)) {
            flagPlayer(attacker, reach, "Distance: " +
                    String.format("%.2f blocks", attacker.getLocation().distance(victim.getLocation())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        violations.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Flag a player for a violation
     */
    public void flagPlayer(Player player, Check check, String details) {
        UUID uuid = player.getUniqueId();

        // Increment violations
        violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Map<String, Integer> playerViolations = violations.get(uuid);
        int vl = playerViolations.getOrDefault(check.getName(), 0) + 1;
        playerViolations.put(check.getName(), vl);

        // Alert staff
        alertStaff(player, check, vl, details);

        // Log to Discord
        logToDiscord(player, check, vl, details);

        // Take action if max violations reached
        if (vl >= check.getMaxViolations()) {
            executeAction(player, check, vl);
        }
    }

    /**
     * Alert online staff about violation
     */
    private void alertStaff(Player player, Check check, int vl, String details) {
        Component alert = Component.text()
                .append(Component.text("[AC] ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" failed ", NamedTextColor.GRAY))
                .append(Component.text(check.getName(), NamedTextColor.GOLD))
                .append(Component.text(" [VL:", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(vl), getViolationColor(vl, check.getMaxViolations())))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(details, NamedTextColor.DARK_GRAY))
                .build();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(alertPermission)) {
                staff.sendMessage(alert);
            }
        }

        // Also log to console
        plugin.getLogger().warning("[AC] " + player.getName() + " failed " + check.getName() +
                " [VL:" + vl + "] " + details);
    }

    /**
     * Log violation to Discord
     */
    private void logToDiscord(Player player, Check check, int vl, String details) {
        if (plugin.getDiscordManager() == null)
            return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Create embed for Discord
        String title = "⚠️ Anti-Cheat Alert";
        String description = String.format(
                "**Player:** %s\n**Check:** %s\n**Violations:** %d/%d\n**Details:** %s\n**Time:** %s",
                player.getName(),
                check.getName(),
                vl,
                check.getMaxViolations(),
                details,
                timestamp);

        // Use Discord webhook if available
        plugin.getDiscordManager().sendEmbed(title, description, vl >= check.getMaxViolations() ? 0xFF0000 : 0xFFAA00);
    }

    /**
     * Execute action when max violations reached
     */
    private void executeAction(Player player, Check check, int vl) {
        String action = plugin.getConfig().getString("anticheat.action", "kick");

        switch (action.toLowerCase()) {
            case "kick":
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.kick(Component.text()
                            .append(Component.text("Duels Anti-Cheat\n\n", NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text("You have been kicked for suspicious activity.\n",
                                    NamedTextColor.WHITE))
                            .append(Component.text("Detection: " + check.getName(), NamedTextColor.GRAY))
                            .build());
                });
                break;
            case "ban":
                // TODO: Implement ban action
                break;
            case "notify":
                // Just notify, no action
                break;
        }

        // Reset violations after action
        violations.get(player.getUniqueId()).put(check.getName(), 0);
    }

    private NamedTextColor getViolationColor(int vl, int max) {
        double ratio = (double) vl / max;
        if (ratio >= 0.8)
            return NamedTextColor.RED;
        if (ratio >= 0.5)
            return NamedTextColor.GOLD;
        if (ratio >= 0.3)
            return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    private double getAttackAngle(Player attacker, Player victim) {
        var toTarget = victim.getLocation().toVector().subtract(attacker.getEyeLocation().toVector()).normalize();
        var lookDir = attacker.getEyeLocation().getDirection().normalize();
        double dot = lookDir.dot(toTarget);
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }

    private Check getCheck(String name) {
        for (Check check : checks) {
            if (check.getName().equalsIgnoreCase(name)) {
                return check;
            }
        }
        return null;
    }

    public int getViolations(Player player, String checkName) {
        Map<String, Integer> playerVl = violations.get(player.getUniqueId());
        if (playerVl == null)
            return 0;
        return playerVl.getOrDefault(checkName, 0);
    }

    public boolean isEnabled() {
        return enabled;
    }
}

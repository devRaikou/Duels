package me.raikou.duels.punishment;

import me.raikou.duels.DuelsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class PunishmentManager {

    private final DuelsPlugin plugin;
    // Cache active punishments for quick lookup (Login/Chat)
    private final Map<UUID, List<Punishment>> activePunishments = new ConcurrentHashMap<>();

    public PunishmentManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadPunishments(UUID uuid) {
        plugin.getStorage().getActivePunishments(uuid).thenAccept(list -> {
            // Filter expired ones just in case
            list.removeIf(Punishment::isExpired);
            activePunishments.put(uuid, list);
        });
    }

    public void unloadPunishments(UUID uuid) {
        activePunishments.remove(uuid);
    }

    public boolean isBanned(UUID uuid) {
        if (!activePunishments.containsKey(uuid))
            return false;
        return activePunishments.get(uuid).stream()
                .anyMatch(p -> p.getType() == PunishmentType.BAN && !p.isExpired());
    }

    public Punishment getActiveBan(UUID uuid) {
        if (!activePunishments.containsKey(uuid))
            return null;
        return activePunishments.get(uuid).stream()
                .filter(p -> p.getType() == PunishmentType.BAN && !p.isExpired())
                .findFirst().orElse(null);
    }

    public boolean isMuted(UUID uuid) {
        if (!activePunishments.containsKey(uuid))
            return false;
        return activePunishments.get(uuid).stream()
                .anyMatch(p -> p.getType() == PunishmentType.MUTE && !p.isExpired());
    }

    public Punishment getActiveMute(UUID uuid) {
        if (!activePunishments.containsKey(uuid))
            return null;
        return activePunishments.get(uuid).stream()
                .filter(p -> p.getType() == PunishmentType.MUTE && !p.isExpired())
                .findFirst().orElse(null);
    }

    public CompletableFuture<Void> punish(UUID target, String targetName, String issuer, PunishmentType type,
            String reason, long duration) {
        Punishment punishment = new Punishment(
                0, // ID auto-generated
                target,
                targetName,
                issuer,
                type,
                reason,
                System.currentTimeMillis(),
                duration,
                true,
                false,
                null,
                null);

        return plugin.getStorage().savePunishment(punishment).thenRun(() -> {
            // Update cache if online
            if (Bukkit.getPlayer(target) != null) {
                loadPunishments(target);
            }

            // Execute immediate actions
            if (type == PunishmentType.KICK) {
                kickPlayer(target, reason, issuer);
            } else if (type == PunishmentType.BAN) {
                kickPlayer(target, getBanKickMessage(punishment), issuer);
            } else if (type == PunishmentType.MUTE) {
                Player p = Bukkit.getPlayer(target);
                if (p != null) {
                    p.sendMessage(
                            Component.text("You have been muted by " + issuer + " for: " + reason, NamedTextColor.RED));
                }
            }

            logToDiscord(punishment);
        });
    }

    public CompletableFuture<Boolean> pardon(UUID target, PunishmentType type, String removedBy, String reason) {
        return plugin.getStorage().getActivePunishments(target).thenApply(list -> {
            boolean found = false;
            for (Punishment p : list) {
                if (p.getType() == type && !p.isRemoved()) {
                    plugin.getStorage().expirePunishment(p.getId(), removedBy, reason);
                    found = true;
                }
            }
            // Refresh cache
            if (Bukkit.getPlayer(target) != null) {
                loadPunishments(target);
            }
            return found;
        });
    }

    private void kickPlayer(UUID uuid, String message, String issuer) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.kick(Component.text(message));
            }
        });
    }

    // Helper to format kick message
    public String getBanKickMessage(Punishment p) {
        StringBuilder sb = new StringBuilder();
        sb.append("§c§lYOU ARE BANNED\n\n");
        sb.append("§7Reason: §f").append(p.getReason()).append("\n");
        sb.append("§7Banned by: §f").append(p.getIssuerName()).append("\n");
        if (p.getDuration() > 0) {
            sb.append("§7Expires: §f").append(getDurationString(p.getExpirationTime() - System.currentTimeMillis()))
                    .append("\n");
        } else {
            sb.append("§7Expires: §cNEVER\n");
        }
        sb.append("\n§7Appeal at discord.gg/duels");
        return sb.toString();
    }

    private void logToDiscord(Punishment p) {
        if (plugin.getDiscordManager() == null)
            return;

        String title = "🔨 Punishment: " + p.getType().name();
        String desc = String.format("**Player:** %s\n**Issuer:** %s\n**Reason:** %s\n**Duration:** %s",
                p.getPlayerName(),
                p.getIssuerName(),
                p.getReason(),
                p.getDuration() > 0 ? getDurationString(p.getDuration()) : "Permanent");

        plugin.getDiscordManager().sendEmbed(title, desc, 0xFF0000);
    }

    public static String getDurationString(long millis) {
        if (millis <= 0)
            return "Permanent";

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0)
            return days + "d " + (hours % 24) + "h";
        if (hours > 0)
            return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0)
            return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    // Parse duration string like 1d12h to millis
    public static long parseDuration(String input) {
        long total = 0;
        StringBuilder number = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.length() == 0)
                    continue;
                int val = Integer.parseInt(number.toString());
                number = new StringBuilder();
                switch (Character.toLowerCase(c)) {
                    case 'd':
                        total += val * 86400000L;
                        break;
                    case 'h':
                        total += val * 3600000L;
                        break;
                    case 'm':
                        total += val * 60000L;
                        break;
                    case 's':
                        total += val * 1000L;
                        break;
                }
            }
        }
        return total;
    }
}

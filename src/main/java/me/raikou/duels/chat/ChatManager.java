package me.raikou.duels.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.raikou.duels.DuelsPlugin;
import me.raikou.duels.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Modern chat formatting system with LuckPerms integration.
 */
public class ChatManager implements Listener {

    private final DuelsPlugin plugin;
    private LuckPerms luckPerms;
    private boolean luckPermsEnabled = false;

    // Anti-spam tracking
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();

    public ChatManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Try to hook into LuckPerms
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
                luckPermsEnabled = true;
                plugin.getLogger().info("LuckPerms hooked successfully for chat formatting!");
            }
        } catch (Exception e) {
            plugin.getLogger().info("LuckPerms not found, using default chat format.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Anti-spam checks
        if (plugin.getPunishmentManager().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            me.raikou.duels.punishment.Punishment mute = plugin.getPunishmentManager()
                    .getActiveMute(player.getUniqueId());
            if (mute != null) {
                long remaining = mute.getExpirationTime() - System.currentTimeMillis();
                String durationStr = me.raikou.duels.punishment.PunishmentManager.getDurationString(remaining);
                me.raikou.duels.util.MessageUtil.sendError(player, "punishment.mute-chat", "%duration%", durationStr);
            }
            return;
        }

        if (!player.hasPermission(plugin.getConfig().getString("chat.bypass-cooldown", "duels.chat.bypass"))) {
            // Cooldown check
            double cooldown = plugin.getConfig().getDouble("chat.cooldown", 1.5);
            long now = System.currentTimeMillis();
            Long lastTime = lastMessageTime.get(player.getUniqueId());

            if (lastTime != null && (now - lastTime) < (cooldown * 1000)) {
                event.setCancelled(true);
                double remaining = (cooldown * 1000 - (now - lastTime)) / 1000.0;
                MessageUtil.sendError(player, "chat.cooldown", "%time%", String.format("%.1f", remaining));
                return;
            }

            // Duplicate message check
            if (plugin.getConfig().getBoolean("chat.anti-spam.duplicate-message-block", true)) {
                String last = lastMessage.get(player.getUniqueId());
                if (last != null && last.equalsIgnoreCase(rawMessage)) {
                    event.setCancelled(true);
                    MessageUtil.sendError(player, "chat.duplicate");
                    return;
                }
            }

            // Caps check
            if (plugin.getConfig().getBoolean("chat.anti-spam.enabled", true) && rawMessage.length() > 3) {
                int maxCapsPercent = plugin.getConfig().getInt("chat.anti-spam.max-caps-percent", 70);
                long capsCount = rawMessage.chars().filter(Character::isUpperCase).count();
                long letterCount = rawMessage.chars().filter(Character::isLetter).count();

                if (letterCount > 0 && ((double) capsCount / letterCount * 100) > maxCapsPercent) {
                    // Convert to lowercase instead of blocking
                    rawMessage = rawMessage.toLowerCase();
                }
            }

            lastMessageTime.put(player.getUniqueId(), now);
            lastMessage.put(player.getUniqueId(), rawMessage);
        }

        // Get format from config
        String format = plugin.getConfig().getString("chat.format",
                "%prefix%<white>%player%</white>%suffix% <dark_gray>»</dark_gray> <gray>%message%");

        // Get prefix and suffix from LuckPerms
        String prefix = "";
        String suffix = "";

        if (luckPermsEnabled && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                CachedMetaData metaData = user.getCachedData().getMetaData();
                prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
                suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";
            }
        }

        // Process message for colors (if player has permission)
        String finalMessage = rawMessage;
        if (player.hasPermission(plugin.getConfig().getString("chat.color-permission", "duels.chat.color"))) {
            // Allow MiniMessage formatting in chat
            finalMessage = rawMessage;
        } else {
            // Strip any formatting attempts
            finalMessage = rawMessage.replaceAll("<[^>]*>", "");
        }

        // Apply placeholders
        format = format.replace("%prefix%", prefix);
        format = format.replace("%suffix%", suffix);
        format = format.replace("%player%", player.getName());
        format = format.replace("%displayname%", PlainTextComponentSerializer.plainText()
                .serialize(player.displayName()));
        format = format.replace("%message%", finalMessage);

        // Parse and render
        Component formattedMessage = MessageUtil.parse(format);

        // Cancel original and broadcast custom format
        event.setCancelled(true);

        // Broadcast to all players
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(formattedMessage);
        }

        // Log to console
        Bukkit.getConsoleSender().sendMessage(formattedMessage);
    }

    public void cleanup(Player player) {
        lastMessageTime.remove(player.getUniqueId());
        lastMessage.remove(player.getUniqueId());
    }
}

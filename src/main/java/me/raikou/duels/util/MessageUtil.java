package me.raikou.duels.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class MessageUtil {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#FFD700:#FFA500><bold>Duels</bold></gradient> <dark_gray>»</dark_gray> ";

    public static Component parse(String text) {
        return mm.deserialize(text);
    }

    public static Component prefix(String text) {
        return mm.deserialize(PREFIX + text);
    }

    public static void send(CommandSender sender, String text) {
        sender.sendMessage(prefix(text));
    }

    public static void sendError(CommandSender sender, String text) {
        sender.sendMessage(prefix("<red>" + text));
    }

    public static void sendSuccess(CommandSender sender, String text) {
        sender.sendMessage(prefix("<green>" + text));
    }

    public static void sendInfo(CommandSender sender, String text) {
        sender.sendMessage(prefix("<gray>" + text));
    }
}

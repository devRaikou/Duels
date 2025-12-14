package me.raikou.duels.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class MessageUtil {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final String PREFIX_KEY = "prefix";

    public static Component parse(String text) {
        return mm.deserialize(text);
    }

    public static Component get(String key, String... replacements) {
        String msg = getMessageString(key, replacements);
        return mm.deserialize(
                me.raikou.duels.DuelsPlugin.getInstance().getLanguageManager().getMessage(PREFIX_KEY) + msg);
    }

    public static Component getRaw(String key, String... replacements) {
        String msg = getMessageString(key, replacements);
        return mm.deserialize(msg);
    }

    private static String getMessageString(String key, String... replacements) {
        String msg = me.raikou.duels.DuelsPlugin.getInstance().getLanguageManager().getMessage(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                msg = msg.replace(replacements[i], replacements[i + 1]);
            }
        }
        return msg;
    }

    public static void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(get(key, replacements));
    }

    public static void sendError(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(get(key, replacements));
    }

    public static void sendSuccess(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(get(key, replacements));
    }

    public static void sendInfo(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(get(key, replacements));
    }
}

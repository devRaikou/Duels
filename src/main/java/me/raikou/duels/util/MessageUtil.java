package me.raikou.duels.util;

import me.raikou.duels.DuelsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class MessageUtil {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static Component get(String path) {
        String msg = DuelsPlugin.getInstance().getConfig().getString(path);
        if (msg == null)
            return Component.text("Missing message: " + path);

        String prefix = DuelsPlugin.getInstance().getConfig().getString("prefix", "");
        return mm.deserialize(prefix + msg);
    }

    public static Component parse(String text) {
        return mm.deserialize(text);
    }

    public static void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public static Component replace(Component comp, String holder, String value) {
        return comp.replaceText(builder -> builder.matchLiteral("%" + holder + "%").replacement(value));
        // Note: Simple holder replacement. For MiniMessage, typically we resolve before
        // deserializing or use TagResolver.
        // But for this simple scope:
    }
}

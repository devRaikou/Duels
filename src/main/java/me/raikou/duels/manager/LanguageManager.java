package me.raikou.duels.manager;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LanguageManager {

    private final DuelsPlugin plugin;
    private FileConfiguration messages;
    private File messagesFile;

    public LanguageManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        loadLanguage();
    }

    public void loadLanguage() {
        // Ensure all language files exist
        saveLocale("messages_en.yml");
        saveLocale("messages_tr.yml");

        String lang = plugin.getConfig().getString("settings.language", "en");
        String fileName = "messages_" + lang + ".yml";

        messagesFile = new File(plugin.getDataFolder(), fileName);
        if (!messagesFile.exists()) {
            // Fallback to en if specified lang doesn't exist
            plugin.getLogger().warning("Language file " + fileName + " not found. Falling back to English.");
            messagesFile = new File(plugin.getDataFolder(), "messages_en.yml");
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Reload default to ensure we have all keys if file is old
        InputStream defConfigStream = plugin.getResource(fileName);
        if (defConfigStream != null) {
            messages.setDefaults(YamlConfiguration
                    .loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)));
        }
    }

    private void saveLocale(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
    }

    public String getMessage(String key) {
        if (!messages.contains(key))
            return "Missing key: " + key;
        return messages.getString(key);
    }

    public List<String> getList(String key) {
        return messages.getStringList(key);
    }
}

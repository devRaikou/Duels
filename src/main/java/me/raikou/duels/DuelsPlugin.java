package me.raikou.duels;

import lombok.Getter;
import me.raikou.duels.arena.ArenaManager;
import me.raikou.duels.duel.DuelManager;
import me.raikou.duels.manager.LanguageManager;
import me.raikou.duels.kit.KitManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DuelsPlugin extends JavaPlugin {

    @Getter
    private static DuelsPlugin instance;

    @Getter
    private ArenaManager arenaManager;
    @Getter
    private DuelManager duelManager;
    @Getter
    private KitManager kitManager;
    @Getter
    private me.raikou.duels.queue.QueueManager queueManager;
    @Getter
    private me.raikou.duels.storage.Storage storage;
    @Getter
    private me.raikou.duels.lobby.LobbyManager lobbyManager;
    @Getter
    private me.raikou.duels.gui.GuiManager guiManager;
    @Getter
    private me.raikou.duels.stats.StatsManager statsManager;
    @Getter
    private me.raikou.duels.editor.KitEditorManager kitEditorManager;
    @Getter
    private me.raikou.duels.world.WorldManager worldManager;
    @Getter
    private me.raikou.duels.discord.DiscordManager discordManager;
    @Getter
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        instance = this;

        // Load Config
        saveDefaultConfig();

        // Initialize Managers
        this.arenaManager = new ArenaManager(this);
        this.kitManager = new KitManager(this);
        this.duelManager = new DuelManager(this);
        this.queueManager = new me.raikou.duels.queue.QueueManager(this);
        this.queueManager = new me.raikou.duels.queue.QueueManager(this);
        this.lobbyManager = new me.raikou.duels.lobby.LobbyManager(this);
        this.guiManager = new me.raikou.duels.gui.GuiManager(this);
        this.statsManager = new me.raikou.duels.stats.StatsManager(this);
        this.kitEditorManager = new me.raikou.duels.editor.KitEditorManager(this);
        this.worldManager = new me.raikou.duels.world.WorldManager(this);
        this.worldManager = new me.raikou.duels.world.WorldManager(this);
        this.discordManager = new me.raikou.duels.discord.DiscordManager(this);
        this.languageManager = new LanguageManager(this);

        // Storage
        String type = getConfig().getString("storage.type", "sqlite");
        if (type.equalsIgnoreCase("mysql")) {
            this.storage = new me.raikou.duels.storage.MySQLStorage(this);
        } else {
            this.storage = new me.raikou.duels.storage.SQLiteStorage(this);
        }
        this.storage.connect();

        // Commands
        getCommand("duel").setExecutor(new me.raikou.duels.command.DuelCommand(this));
        getCommand("lobby").setExecutor(new me.raikou.duels.command.LobbyCommand(this));
        getCommand("stats").setExecutor(new me.raikou.duels.command.StatsCommand(this));

        // Listeners
        getServer().getPluginManager().registerEvents(new me.raikou.duels.listener.DuelListener(this), this);

        // Scoreboard
        new me.raikou.duels.util.BoardManager(this);

        getLogger().info("Duels Core Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (storage != null)
            storage.disconnect();
        getLogger().info("Duels Core Plugin has been disabled!");
    }
}

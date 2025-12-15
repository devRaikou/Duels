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
    @Getter
    private me.raikou.duels.manager.RequestManager requestManager;
    @Getter
    private me.raikou.duels.util.NametagManager nametagManager;
    @Getter
    private me.raikou.duels.util.CPSManager cpsManager;
    @Getter
    private me.raikou.duels.anticheat.AntiCheatManager antiCheatManager;
    @Getter
    private me.raikou.duels.leaderboard.LeaderboardManager leaderboardManager;
    @Getter
    private me.raikou.duels.command.LeaderboardCommand leaderboardCommand;
    @Getter
    private me.raikou.duels.util.BossBarManager bossBarManager;
    @Getter
    private me.raikou.duels.util.TabManager tabManager;
    @Getter
    private me.raikou.duels.chat.ChatManager chatManager;
    @Getter
    private me.raikou.duels.spectator.SpectatorManager spectatorManager;
    @Getter
    private me.raikou.duels.spectator.SpectatorGui spectatorGui;
    @Getter
    private me.raikou.duels.combat.CombatManager combatManager;
    @Getter
    private me.raikou.duels.match.MatchHistoryManager matchHistoryManager;
    @Getter
    private me.raikou.duels.match.MatchResultGui matchResultGui;
    @Getter
    private me.raikou.duels.gui.ProfileGui profileGui;
    @Getter
    private me.raikou.duels.punishment.PunishmentManager punishmentManager;
    @Getter
    private long enableTime;

    @Override
    public void onEnable() {
        instance = this;
        this.enableTime = System.currentTimeMillis();

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
        this.requestManager = new me.raikou.duels.manager.RequestManager(this);

        // Storage
        String type = getConfig().getString("storage.type", "sqlite");
        if (type.equalsIgnoreCase("mysql")) {
            this.storage = new me.raikou.duels.storage.MySQLStorage(this);
        } else {
            this.storage = new me.raikou.duels.storage.SQLiteStorage(this);
        }
        this.storage.connect();

        // Commands
        me.raikou.duels.command.DuelCommand duelCommand = new me.raikou.duels.command.DuelCommand(this);
        getCommand("duel").setExecutor(duelCommand);
        getCommand("duel").setTabCompleter(duelCommand);
        getCommand("lobby").setExecutor(new me.raikou.duels.command.LobbyCommand(this));
        getCommand("stats").setExecutor(new me.raikou.duels.command.StatsCommand(this));
        getCommand("ping").setExecutor(new me.raikou.duels.command.PingCommand(this));

        // Listeners
        getServer().getPluginManager().registerEvents(new me.raikou.duels.listener.DuelListener(this), this);
        getServer().getPluginManager().registerEvents(new me.raikou.duels.listener.MotdListener(this), this);
        getServer().getPluginManager().registerEvents(new me.raikou.duels.listener.WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new me.raikou.duels.listener.CombatListener(this), this);

        // Scoreboard & Nametags & CPS
        this.cpsManager = new me.raikou.duels.util.CPSManager(this);
        new me.raikou.duels.util.BoardManager(this);
        this.nametagManager = new me.raikou.duels.util.NametagManager(this);

        // Anti-Cheat
        this.antiCheatManager = new me.raikou.duels.anticheat.AntiCheatManager(this);

        // Leaderboard
        this.leaderboardManager = new me.raikou.duels.leaderboard.LeaderboardManager(this);
        this.leaderboardCommand = new me.raikou.duels.command.LeaderboardCommand(this);
        getCommand("leaderboard").setExecutor(leaderboardCommand);

        // PlaceholderAPI Hook
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new me.raikou.duels.placeholder.DuelsExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked successfully!");
        }

        // BossBar & TAB
        this.bossBarManager = new me.raikou.duels.util.BossBarManager(this);
        this.tabManager = new me.raikou.duels.util.TabManager(this);

        // Chat Formatter
        this.chatManager = new me.raikou.duels.chat.ChatManager(this);

        // Spectator System
        this.spectatorManager = new me.raikou.duels.spectator.SpectatorManager(this);
        this.spectatorGui = new me.raikou.duels.spectator.SpectatorGui(this);

        // Combat System (1.8 Legacy PvP)
        this.combatManager = new me.raikou.duels.combat.CombatManager(this);
        getServer().getPluginManager().registerEvents(combatManager, this);
        getServer().getPluginManager().registerEvents(combatManager.getDamageHandler(), this);
        getServer().getPluginManager().registerEvents(combatManager.getKnockbackHandler(), this);
        getServer().getPluginManager().registerEvents(combatManager.getBlockHandler(), this);
        getServer().getPluginManager().registerEvents(combatManager.getShieldHandler(), this);
        getServer().getPluginManager().registerEvents(combatManager.getGoldenAppleHandler(), this);

        // Match History & Result GUI
        this.matchHistoryManager = new me.raikou.duels.match.MatchHistoryManager(this);
        this.matchResultGui = new me.raikou.duels.match.MatchResultGui(this);
        this.profileGui = new me.raikou.duels.gui.ProfileGui(this);

        getCommand("profile").setExecutor(new me.raikou.duels.command.ProfileCommand(this));

        // Punishment System
        this.punishmentManager = new me.raikou.duels.punishment.PunishmentManager(this);
        getServer().getPluginManager().registerEvents(new me.raikou.duels.punishment.PunishmentListener(this), this);
        getCommand("punish").setExecutor(new me.raikou.duels.punishment.PunishmentCommand(this));

        // Lobby Double Jump
        new me.raikou.duels.lobby.DoubleJumpHandler(this);

        getLogger().info("Duels Core Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (storage != null)
            storage.disconnect();
        getLogger().info("Duels Core Plugin has been disabled!");
    }
}

package me.raikou.duels.world;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldManager {

    private static final List<String> IGNORE_FILES = Arrays.asList(
            "uid.dat", "session.lock", "playerdata", "stats", "poi", "advancements");

    private final DuelsPlugin plugin;
    private final ExecutorService ioExecutor;

    public WorldManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.ioExecutor = createIoExecutor();
    }

    private ExecutorService createIoExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "duels-world-io-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(2, factory);
    }

    /**
     * Backwards compatible entry point.
     * Prefer {@link #prepareDuelWorldAsync(String, String)} + {@link #loadPreparedWorld(String)}.
     */
    public World createDuelWorld(String templateWorldName, String instanceName) {
        boolean prepared = prepareDuelWorldSync(templateWorldName, instanceName);
        if (!prepared) {
            return null;
        }
        return loadPreparedWorld(instanceName);
    }

    public CompletableFuture<Boolean> prepareDuelWorldAsync(String templateWorldName, String instanceName) {
        return CompletableFuture.supplyAsync(() -> prepareDuelWorldSync(templateWorldName, instanceName), ioExecutor);
    }

    private boolean prepareDuelWorldSync(String templateWorldName, String instanceName) {
        File templateFolder = new File(Bukkit.getWorldContainer(), templateWorldName);
        File instanceFolder = new File(Bukkit.getWorldContainer(), instanceName);

        if (!templateFolder.exists()) {
            plugin.getLogger().severe("Template world folder not found: " + templateWorldName);
            return false;
        }

        if (instanceFolder.exists()) {
            deleteDirectory(instanceFolder);
        }

        try {
            copyDirectory(templateFolder, instanceFolder);

            File uidFile = new File(instanceFolder, "uid.dat");
            if (uidFile.exists()) {
                uidFile.delete();
            }

            File sessionLock = new File(instanceFolder, "session.lock");
            if (sessionLock.exists()) {
                sessionLock.delete();
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to prepare duel world '" + instanceName + "': " + e.getMessage());
            deleteDirectory(instanceFolder);
            return false;
        }
    }

    public World loadPreparedWorld(String instanceName) {
        World world = Bukkit.createWorld(new WorldCreator(instanceName));
        if (world != null) {
            world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        }
        return world;
    }

    public void cleanupPreparedWorld(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        deleteDirectory(worldFolder);
    }

    public void deleteDuelWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        cleanupPreparedWorld(worldName);
    }

    public void shutdown() {
        ioExecutor.shutdown();
    }

    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Could not create directory " + target.getAbsolutePath());
            }

            String[] files = source.list();
            if (files == null) {
                return;
            }

            for (String file : files) {
                if (IGNORE_FILES.contains(file)) {
                    continue;
                }

                File srcFile = new File(source, file);
                File destFile = new File(target, file);
                copyDirectory(srcFile, destFile);
            }
            return;
        }

        try (InputStream in = new FileInputStream(source);
                OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return !path.exists() || path.delete();
    }
}

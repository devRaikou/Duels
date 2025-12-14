package me.raikou.duels.world;

import me.raikou.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WorldManager {

    private final DuelsPlugin plugin;

    public WorldManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public World createDuelWorld(String templateWorldName, String instanceName) {
        File templateFolder = new File(Bukkit.getWorldContainer(), templateWorldName);
        File instanceFolder = new File(Bukkit.getWorldContainer(), instanceName);

        if (!templateFolder.exists()) {
            plugin.getLogger().severe("Template world folder not found: " + templateWorldName);
            return null;
        }

        if (instanceFolder.exists()) {
            // Should not happen if unique ID is good, but safety delete
            deleteDuelWorld(instanceName);
        }

        try {
            copyDirectory(templateFolder, instanceFolder);
            // Delete uid.dat so Bukkit treats it as a new world
            File uidFile = new File(instanceFolder, "uid.dat");
            if (uidFile.exists())
                uidFile.delete();

            // Delete session.lock to avoid conflicts
            File sessionLock = new File(instanceFolder, "session.lock");
            if (sessionLock.exists())
                sessionLock.delete();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return Bukkit.createWorld(new WorldCreator(instanceName));
    }

    public void deleteDuelWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Bukkit.unloadWorld(world, false); // Do not save on unload
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        deleteDirectory(worldFolder);
    }

    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }

            String[] files = source.list();
            if (files == null)
                return;

            // Filter out unnecessary files/folders to speed up copy (like playerdata)
            List<String> ignore = Arrays.asList("uid.dat", "session.lock", "playerdata", "stats", "poi",
                    "advancements");

            for (String file : files) {
                if (ignore.contains(file))
                    continue;

                File srcFile = new File(source, file);
                File destFile = new File(target, file);
                copyDirectory(srcFile, destFile);
            }
        } else {
            try (InputStream in = new FileInputStream(source);
                    OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
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
        return path.delete();
    }
}

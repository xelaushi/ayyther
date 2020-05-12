package net.lovector.ayyther;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.lovector.ayyther.generator.AyytherGenerator;

public final class Ayyther extends JavaPlugin {
    ArrayList<AyytherGenerator> generators = new ArrayList<>();

    @Override
    public void onEnable() {
        // TODO: check if any of the worlds in the Ayyther/worlds folder have been deleted

        getServer().getPluginManager().registerEvents(new EventListener(), this);
        getLogger().info("Ayyther enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving unpopulated chunks for all Ayyther worlds...");

        File worldsFolder = getWorldsFolder();
        if (worldsFolder.exists()) {
            for (AyytherGenerator generator : generators) {
                File worldFile = new File(worldsFolder, generator.worldName);

                try {
                    saveGeneratorData(worldFile, generator);
                    getLogger().info("Saved unpopulated chunks for '" + generator.worldName + "'.");
                } catch (IOException e) {
                    getLogger().severe("Failed to save unpopulated chunks for '" + generator.worldName + "'!");
                }
            }
        } else {
            getLogger().severe("Failed to get or create the worlds folder!");
        }

        getLogger().info("Ayyther disabled.");
    }

    private void saveGeneratorData(File worldFile, AyytherGenerator generator) throws IOException {
       try (FileOutputStream outputStream = new FileOutputStream(worldFile)) {
           outputStream.write(generator.getSaveBytes());
       } catch (IOException e) {
           throw e;
       }
    }

    private File getWorldsFolder() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists())
            dataFolder.mkdirs();

        File worldsFolder = new File(dataFolder, "worlds");
        if (!worldsFolder.exists())
            worldsFolder.mkdirs();
        
        return worldsFolder;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        getLogger().info("Loading generator for '" + worldName + "'...");

        for (AyytherGenerator generator : generators) {
            if (generator.worldName == worldName) {
                // Multiverse loads the generator again so we
                // have to make sure to give it the same one
                getLogger().info("Generator for '" + worldName + "' already loaded.");
                return generator;
            }
        }

        File worldFile = new File(getWorldsFolder(), worldName);
        AyytherGenerator generator = null;

        try {
            generator = loadGeneratorData(worldFile, worldName);
        } catch (IOException e) {
            getLogger().warning("Encountered an error while loading unpopulated chunks for '" + worldName + "'!");
        }

        if (generator == null) {
            generator = new AyytherGenerator(worldName);
            getLogger().info("No unpopulated chunks found for '" + worldName + "'.");
        } else {
            getLogger().info("Loaded unpopulated chunks for '" + worldName + "'.");
        }

        getLogger().info("Generator for '" + worldName + "' loaded.");

        generators.add(generator);
        return generator;
    }

    private AyytherGenerator loadGeneratorData(File worldFile, String worldName) throws IOException {
        if (worldFile.exists()) {
            try (DataInputStream inputStream = new DataInputStream(new FileInputStream(worldFile))) {
                return new AyytherGenerator(worldName, inputStream.readAllBytes());
            } catch (IOException e) {
                throw e;
            }
        }

        return null;
    }
}

class EventListener implements Listener {
    // Makes it so entites only spawn in darkness that is from <= 20 blocks above
    @EventHandler
    public void onCreatureSpawnEvent(CreatureSpawnEvent event) {
        if (isAyytherWorld(event.getLocation().getWorld()) && isDaytime(event.getLocation().getWorld().getTime()) && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && event.getEntity() instanceof Monster) {
            // Raytrace straight above the entity.
            // This is to check if they are spawning due to darkness.
            RayTraceResult result = event.getLocation().getWorld().rayTraceBlocks(event.getLocation(), new Vector(0.0, 1.0, 0.0), 256.0);

            // If the raytrace hit something (if spawn is due to darkness)
            if (result != null) {
                // If the length of the raytrace is greater than 20 blocks
                // (if the thing that's making it dark is > 20 blocks above)
                if (result.getHitPosition().subtract(event.getLocation().toVector()).length() > 20.0) {
                    // Don't spawn the entity
                    event.setCancelled(true);
                }
            }
        }
    }

    // Cancels all ender pearl damages in ayyther
    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof EnderPearl && isAyytherWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    private boolean isAyytherWorld(World world) {
        return world.getGenerator() instanceof AyytherGenerator;
    }

    private boolean isDaytime(long time) {
        return time < 12000;
    }
}
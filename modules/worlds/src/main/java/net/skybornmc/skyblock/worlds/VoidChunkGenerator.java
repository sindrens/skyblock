package net.skybornmc.skyblock.worlds;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Generates completely empty chunks (no terrain, caves, structures or mobs) with one biome everywhere.
 * Used for every Phoenix Skyblock world; all builds come from builders, schematics or the plugin.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    private final Biome biome;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;

    public VoidChunkGenerator(String biomeId, double spawnX, double spawnY, double spawnZ) {
        Registry<Biome> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        Biome resolved = biomes.get(NamespacedKey.minecraft(biomeId));
        this.biome = resolved != null ? resolved : biomes.getOrThrow(NamespacedKey.minecraft("the_void"));
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
    }

    @Override
    public boolean shouldGenerateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, spawnX, spawnY, spawnZ);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return biome;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(biome);
            }
        };
    }
}

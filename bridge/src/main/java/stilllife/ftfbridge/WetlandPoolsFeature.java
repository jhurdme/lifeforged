package stilllife.ftfbridge;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Shallow pools of standing water in Still Life's wetland biomes, at any height.
 *
 * <p>Still Life floods its swamps, marshes and bogs only at sea level (its surface rules put water and mud at Y 62-63), because
 * Lithosphere's terrain drops those biomes to sea level. FreeTerraForged's terrain does not, so above sea level they were dry.
 * This places noise-shaped pools (a few to twenty blocks across) in the top ground block, with a mud bed and a muddy rim.
 * A column only becomes water where all four neighbours reach at least as high, so a pool is always held by the ground (or by
 * more pool at the same level) and never spills down a slope; that decision depends only on the terrain, so pools also match
 * across chunk borders.
 */
final class WetlandPoolsFeature extends Feature<NoneFeatureConfiguration> {
   static final TagKey<Biome> POOL_BIOMES = TagKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath(StillLifeFtfBridge.MOD_ID, "has_wetland_pools")
   );
   /** Noise above this: pool (about 35% of the ground). */
   private static final double POOL = 0.1183;
   /** Noise between this and POOL: muddy rim (about another 10%). */
   private static final double RIM = 0.0386;
   /** Noise above this: the middle of a bigger pool is two blocks deep. */
   private static final double DEEP = 0.45;
   private static final int[] DX = {1, -1, 0, 0};
   private static final int[] DZ = {0, 0, 1, -1};
   private static volatile long noiseSeed;
   private static volatile NormalNoise noise;

   WetlandPoolsFeature(Codec<NoneFeatureConfiguration> codec) {
      super(codec);
   }

   private static NormalNoise noise(long seed) {
      NormalNoise n = noise;
      if (n == null || noiseSeed != seed) {
         n = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x3C6EF372FE94F82BL), -4, new double[]{1.0, 0.6});
         noiseSeed = seed;
         noise = n;
      }

      return n;
   }

   @Override
   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
      if (!BridgeConfig.enabled() || !WorldLock.activeSettings().wetlandPools()) {
         return false;
      }

      WorldGenLevel level = context.level();
      NormalNoise n = noise(level.getSeed());
      BlockPos origin = context.origin();
      int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4;
      int z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
      int minY = level.getMinBuildHeight();
      BlockState water = Blocks.WATER.defaultBlockState();
      BlockState mud = Blocks.MUD.defaultBlockState();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      boolean placed = false;

      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int z = z0 + lz;
            double v = n.getValue(x, 0.0, z);
            if (v < RIM) {
               continue;
            }

            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            // the top block must be solid ground with open air above (no water, no plants yet at this step)
            if (top - 3 <= minY || level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1 != top) {
               continue;
            }

            pos.set(x, top, z);
            BlockState ground = level.getBlockState(pos);
            if (!ground.is(BlockTags.DIRT) && !ground.is(Blocks.CLAY) || !level.getBiome(pos).is(POOL_BIOMES)) {
               continue;
            }

            if (v < POOL) {
               if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.PODZOL)) {
                  level.setBlock(pos, mud, 2);
                  placed = true;
               }

               continue;
            }

            if (!held(level, x, top, z)) {
               continue;
            }

            int depth = v >= DEEP ? 2 : 1;
            pos.set(x, top - depth, z);
            if (!level.getBlockState(pos).isSolid()) {
               depth = 1;
               pos.set(x, top - 1, z);
               if (!level.getBlockState(pos).isSolid()) {
                  continue;
               }
            }

            level.setBlock(pos, mud, 2);
            for (int d = 0; d < depth; d++) {
               pos.set(x, top - d, z);
               level.setBlock(pos, water, 2);
            }

            placed = true;
         }
      }

      return placed;
   }

   /** True when every horizontal neighbour's ground (or water) reaches at least as high as this column's top block. */
   private static boolean held(WorldGenLevel level, int x, int top, int z) {
      for (int i = 0; i < 4; i++) {
         if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + DX[i], z + DZ[i]) - 1 < top) {
            return false;
         }
      }

      return true;
   }
}

package stilllife.ftfbridge;

import etcodehome.freeterraforged.tags.FTFBlockTags;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * natural_snow_layers: replaces FreeTerraForged's smooth snow layers.
 *
 * <p>FTF's smooth decorator takes the decimal part of its own terrain height as the snow depth (height 143.6 over a block
 * at 143 = 5 layers). The blocks the game builds are interpolated between noise cells a few blocks apart, so they only
 * match FTF's height at those points: in between the real ground sits up to a few blocks above or below it. Where it is a
 * block or more lower FTF places a full snow block, where it is higher the thinnest layer, and where they differ by more
 * than 2 blocks it skips the column, which repeats every few blocks as a grid of stripes, lumps and gaps.
 *
 * <p>This pass works from the real ground only. Ground heights come from the chunk generator's terrain height map (the
 * same in every chunk, so the snow lines up across chunk borders), smoothed over a 5x5 area: where the area around is
 * higher than a spot (the foot of a step, a hollow, a gully) the snow is deeper, so it ramps up against every step and
 * fills dips; on ridges and tops it stays thin. FTF's slope thins it on steep ground and clears it off cliffs; it is a
 * little deeper on north faces and in colder (higher) places. Never more than 7 layers, so no lone snow blocks.
 */
final class NaturalSnow {
   private static final int R = 2;
   private static final int SIZE = 16 + 2 * R;
   private static final float[][] WEIGHT = new float[2 * R + 1][2 * R + 1];

   static {
      for (int dz = -R; dz <= R; dz++) {
         for (int dx = -R; dx <= R; dx++) {
            WEIGHT[dz + R][dx + R] = (float)Math.exp(-(dx * dx + dz * dz) / 2.5);
         }
      }
   }

   private NaturalSnow() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.naturalSnowLayers();
   }

   /** Runs after FTF's snow decorator (whose smoothing the mixin switched off) on one chunk. */
   static void decorate(WorldGenLevel level, BlockPos origin, boolean coverPlants, ScreeFeature.Config erodeConfig) {
      GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return;
      }
      Settings s = WorldLock.activeSettings();
      ChunkPos cp = new ChunkPos(origin);
      ChunkAccess chunk = level.getChunk(cp.x, cp.z);
      int x0 = cp.getMinBlockX();
      int z0 = cp.getMinBlockZ();

      // terrain height (top solid block before features) around the chunk
      int[] ground = new int[SIZE * SIZE];
      for (int dz = 0; dz < SIZE; dz++) {
         for (int dx = 0; dx < SIZE; dx++) {
            ground[dz * SIZE + dx] = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x0 + dx - R, z0 + dz - R) - 1;
         }
      }

      float cliff = SnowCaps.active(s) ? SnowCaps.cliffSteepness(s, erodeConfig) : erodeConfig.rockSteepness();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int z = z0 + lz;
            int g = ground[(lz + R) * SIZE + lx + R];
            int top = realGround(chunk, pos, x, z);
            // something stands on the terrain here (a boulder, a log), or the column is under a roof: leave it
            if (top < 0 || Math.abs(top - g) > 1 || chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) > top + 2) {
               continue;
            }
            pos.set(x, top + 1, z);
            BlockState above = chunk.getBlockState(pos);
            boolean hasSnow = above.is(Blocks.SNOW);
            boolean plant = !hasSnow && coverPlants && coverable(above);
            if (!hasSnow && !plant) {
               continue;
            }
            Biome biome = level.getBiome(pos).value();
            if (plant && !biome.coldEnoughToSnow(pos)) {
               continue;
            }

            Cell cell = FtfTerrain.cell(ctx, chunk, x, z);
            float slope = cell.gradient;
            if (slope > cliff) {
               if (hasSnow) {
                  clear(chunk, pos);
               }
               continue;
            }

            int layers = layers(ground, lx + R, lz + R, top, slope, cliff, temperature(biome, top));
            if (plant && layers < 2) {
               continue;
            }
            BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
            if (!snow.canSurvive(level, pos)) {
               continue;
            }
            chunk.setBlockState(pos, snow, false);
            pos.setY(top);
            BlockState below = chunk.getBlockState(pos);
            if (below.hasProperty(BlockStateProperties.SNOWY) && !below.getValue(BlockStateProperties.SNOWY)) {
               chunk.setBlockState(pos, below.setValue(BlockStateProperties.SNOWY, true), false);
            }
         }
      }
   }

   /** Snow layers (1-7) for the column at (cx, cz) of the ground array, whose real top block is at y top. */
   static int layers(int[] ground, int cx, int cz, int top, float slope, float cliff, float temperature) {
      float sum = 0.0F;
      float weights = 0.0F;
      for (int dz = -R; dz <= R; dz++) {
         for (int dx = -R; dx <= R; dx++) {
            int h = ground[(cz + dz) * SIZE + cx + dx];
            // a wall more than 3 blocks up counts as 3 (snow drifts against it, it doesn't bury it)
            h = Math.max(top - 3, Math.min(top + 3, h));
            float w = WEIGHT[dz + R][dx + R];
            sum += h * w;
            weights += w;
         }
      }
      float lift = sum / weights - top; // > 0: a hollow or the foot of a step; < 0: a ridge, a top, the edge of a step

      float depth;
      if (lift > 0.0F) {
         depth = 1.0F + lift * 7.0F;
      } else {
         // exposed: wind keeps it thin
         depth = 1.0F + lift * 2.0F;
      }

      // north faces (ground falling towards -z) keep more snow
      int north = ground[(cz - 1) * SIZE + cx];
      int south = ground[(cz + 1) * SIZE + cx];
      if (south - north >= 1) {
         depth += 1.0F;
      }

      // colder = deeper (Still Life's snowy biomes are well below 0.15; altitude lowers it further)
      depth += Math.max(0.0F, Math.min(1.0F, (0.15F - temperature) / 0.6F)) * 1.5F;

      // steep slopes shed snow
      if (cliff > 0.0F) {
         float steep = slope / cliff;
         if (steep > 0.6F) {
            depth = Math.min(depth, 1.0F + (1.0F - steep) * 5.0F);
         }
      }

      return Math.max(1, Math.min(7, Math.round(depth)));
   }

   /** Minecraft's temperature at a height: the biome's, cooling by 0.05 every 40 blocks above Y 80. */
   private static float temperature(Biome biome, int y) {
      return biome.getBaseTemperature() - Math.max(0, y - 80) * 0.05F / 40.0F;
   }

   /** The top solid block of the column (skipping snow layers and plants), or -1 when it is water or air. */
   private static int realGround(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z) {
      int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
      int min = chunk.getMinBuildHeight();
      for (; y >= min; y--) {
         BlockState state = chunk.getBlockState(pos.set(x, y, z));
         if (!state.getFluidState().isEmpty()) {
            return -1;
         }
         if (state.isAir() || state.is(Blocks.SNOW) || state.getCollisionShape(chunk, pos).isEmpty()) {
            continue;
         }
         return y;
      }
      return -1;
   }

   private static boolean coverable(BlockState state) {
      return state.is(FTFBlockTags.SNOW_COVERABLE)
         && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
         && !(state.getBlock() instanceof DoublePlantBlock)
         && !state.hasBlockEntity()
         && state.getFluidState().isEmpty();
   }

   private static void clear(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
      chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
      pos.setY(pos.getY() - 1);
      BlockState below = chunk.getBlockState(pos);
      if (below.hasProperty(BlockStateProperties.SNOWY) && below.getValue(BlockStateProperties.SNOWY)) {
         chunk.setBlockState(pos, below.setValue(BlockStateProperties.SNOWY, false), false);
      }
   }
}

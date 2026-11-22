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
 * same in every chunk, so the snow lines up across chunk borders). Like real drifts, snow fills the foot of every step up
 * to the level above and tapers away from it over 2-3 blocks (each column is raised to its highest neighbour, at most one
 * block, and that surface is smoothed), so a staircase of 1-block steps reads as a slope of snow; open flats and the tops
 * of steps get a thin cover, exposed edges stay thin. FTF's slope thins it on steep ground and clears it off cliffs; it is
 * a little deeper on north faces and in colder places; bare rock and gravel only hold it in hollows. Never more than 7
 * layers, so no lone snow blocks.
 */
final class NaturalSnow {
   /** Blur radius of the snow surface, and how far around the chunk the ground is read (blur + 1 for the drift fill). */
   private static final int B = 2;
   private static final int R = B + 1;
   private static final int SIZE = 16 + 2 * R;
   private static final float[][] WEIGHT = new float[2 * B + 1][2 * B + 1];

   static {
      for (int dz = -B; dz <= B; dz++) {
         for (int dx = -B; dx <= B; dx++) {
            WEIGHT[dz + B][dx + B] = (float)Math.exp(-(dx * dx + dz * dz) / 2.5);
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

      // drift surface: each column filled up to its highest neighbour (at most 1 block up), so snow fills the foot of
      // every step up to the level above; smoothed below, it ramps away from the step instead of ending in an edge
      int[] drift = new int[SIZE * SIZE];
      for (int dz = 1; dz < SIZE - 1; dz++) {
         for (int dx = 1; dx < SIZE - 1; dx++) {
            int h0 = ground[dz * SIZE + dx];
            int m = h0;
            for (int oz = -1; oz <= 1; oz++) {
               for (int ox = -1; ox <= 1; ox++) {
                  m = Math.max(m, ground[(dz + oz) * SIZE + dx + ox]);
               }
            }
            drift[dz * SIZE + dx] = Math.min(m, h0 + 1);
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
            net.minecraft.core.Holder<Biome> holder = level.getBiome(pos);
            Biome biome = holder.value();
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

            pos.setY(top);
            BlockState groundBlock = chunk.getBlockState(pos);
            boolean rock = ROCK.contains(groundBlock.getBlock());
            boolean snowGround = SnowCaps.isSnow(groundBlock);
            pos.setY(top + 1);
            boolean mountain = MountainSnow.active(s);
            int layers = layers(ground, drift, lx + R, lz + R, top, slope, cliff, temperature(biome, top), rock,
               mountain ? StillLifeClimateBridge.windFromWest(z) : 0, mountain ? MountainSnow.snowiness(holder) : 1.0F);
            // on snow ground a single layer adds nothing but a seam: only drifts (2+) go on it
            if (snowGround && layers < 2) {
               layers = 0;
            }
            if (layers == 0) {
               if (hasSnow) {
                  clear(chunk, pos);
               }
               continue;
            }
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

   /**
    * Bare rock and gravel: Still Life keeps snow off them (its snow_layers features, which the bridge switches off in its
    * place); here snow on them stays only where it collects, in hollows and against steps.
    */
   private static final java.util.Set<net.minecraft.world.level.block.Block> ROCK = java.util.Set.of(
      Blocks.GRAVEL, Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF, Blocks.CALCITE, Blocks.DEEPSLATE,
      Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SANDSTONE,
      Blocks.RED_SANDSTONE, Blocks.TERRACOTTA);

   /** Snow layers (0-7) for the column at (cx, cz) of the ground arrays, whose real top block is at y top. */
   static int layers(int[] ground, int[] drift, int cx, int cz, int top, float slope, float cliff, float temperature, boolean rock,
                     int wind, float snowy) {
      float sum = 0.0F;
      float weights = 0.0F;
      for (int dz = -B; dz <= B; dz++) {
         for (int dx = -B; dx <= B; dx++) {
            float w = WEIGHT[dz + B][dx + B];
            sum += drift[(cz + dz) * SIZE + cx + dx] * w;
            weights += w;
         }
      }
      // how far the smoothed drift surface lies above this column's ground: ~0.9 at the foot of a step, falling off over
      // 2-3 blocks; ~0 on open flats and the tops of steps; below 0 on ridges and edges
      float fill = sum / weights - top;
      if (rock && fill < 0.3F) {
         return 0;
      }

      float depth = 1.0F + Math.max(0.0F, fill) * 7.0F * snowy;
      if (fill < -0.3F) {
         depth = 1.0F; // exposed edge or ridge: wind keeps it thin
      }

      // prevailing wind (mountain_snow): it strips slopes facing into it and drops the snow on the sheltered side
      if (wind != 0) {
         int rise = ground[cz * SIZE + cx + 2] - ground[cz * SIZE + cx - 2]; // > 0: the ground rises towards the east
         if (rise * wind >= 2) {
            depth = Math.min(depth, 1.0F);
         } else if (rise * wind <= -2) {
            depth += 2.0F * snowy;
         }
      }

      // north faces (ground falling towards -z) keep a little more
      if (ground[(cz + 1) * SIZE + cx] - ground[(cz - 1) * SIZE + cx] >= 1) {
         depth += 0.75F;
      }
      // colder = a little deeper (altitude lowers it too)
      depth += Math.max(0.0F, Math.min(1.0F, (0.15F - temperature) / 0.6F));

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

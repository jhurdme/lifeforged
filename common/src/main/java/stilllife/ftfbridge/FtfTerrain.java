package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.FTFRandomState;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.IChunkCellsHolder;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Reads FreeTerraForged's per-column terrain data (its {@link Cell}) during feature placement: slope, sediment, terrain
 * type, river zone and so on. Used by the bridge's own surface passes and by the {@code stilllife_ftf_bridge:terrain}
 * placement filter that Still Life's features use.
 */
final class FtfTerrain {
   private static final ThreadLocal<Cell> SCRATCH = ThreadLocal.withInitial(Cell::new);

   private FtfTerrain() {
   }

   /** FreeTerraForged's generator context of this level, or null when the world is not a FreeTerraForged world. */
   static GeneratorContext context(WorldGenLevel level) {
      try {
         if ((Object)level.getLevel().getChunkSource().randomState() instanceof FTFRandomState state) {
            return state.generatorContext();
         }
      } catch (RuntimeException ignored) {
      }
      return null;
   }

   /**
    * The cell of the column at world x/z, with its slope (gradient) filled in. The returned cell is a per-thread scratch
    * copy: read what you need from it before the next call. Null when FreeTerraForged's data is not available.
    */
   static Cell cell(WorldGenLevel level, int x, int z) {
      GeneratorContext ctx = context(level);
      return ctx == null ? null : cell(ctx, level.getChunk(x >> 4, z >> 4), x, z);
   }

   // ---------------------------------------------------------------- shape of the ground around a column
   // These read the generator's terrain height map (top solid block before features), the same in every chunk, within
   // 2 blocks of the column, which stays inside the area a feature may read.

   /**
    * The top block of the terrain itself at x/z: the first solid block going down that is not a snow layer, plant, leaf,
    * log or other feature growth, past any water.
    *
    * <p>Minecraft's terrain height maps can't be used for neighbouring columns: in a chunk next door they hold the bare
    * terrain or the decorated surface (with snow layers, trees...) depending on how far that chunk has got, so every pass
    * that looked across a chunk border saw different ground there than inside, which drew lines along chunk borders. This
    * reads the blocks, the same whatever stage the chunk is at. Cached per thread for the chunk being decorated.
    */
   static int ground(WorldGenLevel level, int x, int z) {
      GroundCache c = GROUND.get();
      int cx = x >> 4;
      int cz = z >> 4;
      if (c.level != level || Math.abs(cx - c.cx) > 1 || Math.abs(cz - c.cz) > 1) {
         c.reset(level, cx, cz);
      }
      int i = ((z - (c.cz - 1) * 16) * 48) + (x - (c.cx - 1) * 16);
      int v = c.values[i];
      if (v == Integer.MIN_VALUE) {
         v = scanGround(level, x, z);
         c.values[i] = v;
      }
      return v;
   }

   /**
    * FreeTerraForged's own estimate of the top block at x/z (its terrain height model): available for every chunk at every
    * stage, so it is the same whatever order chunks are generated in. Within a block or two of the real top.
    */
   static int estimate(WorldGenLevel level, int x, int z) {
      GeneratorContext ctx = context(level);
      if (ctx == null) {
         return level.getMinBuildHeight();
      }
      // its own scratch cell, so callers holding the one cell() returns keep their values
      Cell out = ESTIMATE.get();
      net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
      Cell[] cells = chunk instanceof IChunkCellsHolder holder ? holder.freeterraforged$getOrCreateChunkCells(ctx) : null;
      if (cells != null) {
         out.copyFrom(cells[(z & 15) * 16 + (x & 15)]);
      } else {
         out.reset();
         ctx.lookup.applyCell(out, x, z, false, false);
      }
      return ctx.heightmap.levels().scale(out.height);
   }

   /**
    * The top water block over x/z by FreeTerraForged's terrain model (the sea, or a lake's or river's water level), or
    * Integer.MIN_VALUE when its ground is above water. The same in every chunk whatever its stage of generation.
    */
   static int water(WorldGenLevel level, int x, int z) {
      GeneratorContext ctx = context(level);
      if (ctx == null) {
         return Integer.MIN_VALUE;
      }
      Cell c = estimateCell(ctx, level, x, z);
      Levels levels = ctx.heightmap.levels();
      if ((c.terrain.isLake() || c.terrain.isRiver()) && c.riverWaterLevel > 0.0F && c.height < c.riverWaterLevel) {
         return levels.scale(c.riverWaterLevel);
      }
      if (c.height < levels.water) {
         return level.getSeaLevel() - 1;
      }
      return Integer.MIN_VALUE;
   }

   /** True when FreeTerraForged's model has lake water (not sea or river) over x/z. */
   static boolean lakeWater(WorldGenLevel level, int x, int z) {
      GeneratorContext ctx = context(level);
      if (ctx == null) {
         return false;
      }
      Cell c = estimateCell(ctx, level, x, z);
      return c.terrain.isLake() && c.riverWaterLevel > 0.0F && c.height < c.riverWaterLevel;
   }

   /** True when FreeTerraForged's model has river water (a river cell under its water level, not a lake or sea) over x/z. */
   static boolean riverWater(WorldGenLevel level, int x, int z) {
      GeneratorContext ctx = context(level);
      if (ctx == null) {
         return false;
      }
      Cell c = estimateCell(ctx, level, x, z);
      return c.terrain.isRiver() && !c.terrain.isLake() && c.riverWaterLevel > 0.0F && c.height < c.riverWaterLevel;
   }

   private static Cell estimateCell(GeneratorContext ctx, WorldGenLevel level, int x, int z) {
      Cell out = ESTIMATE.get();
      net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
      Cell[] cells = chunk instanceof IChunkCellsHolder holder ? holder.freeterraforged$getOrCreateChunkCells(ctx) : null;
      if (cells != null) {
         out.copyFrom(cells[(z & 15) * 16 + (x & 15)]);
      } else {
         out.reset();
         ctx.lookup.applyCell(out, x, z, false, false);
      }
      return out;
   }

   private static int scanGround(WorldGenLevel level, int x, int z) {
      // a neighbouring chunk that is still at an early stage of generation has no terrain blocks yet (every read there
      // found the bottom of the world, which drew bare lines along chunk borders, on the sides generated later): use
      // FreeTerraForged's height for it
      net.minecraft.world.level.chunk.ChunkAccess target = level.getChunk(x >> 4, z >> 4);
      if (!target.getPersistedStatus().isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.NOISE)) {
         return estimate(level, x, z);
      }
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int min = level.getMinBuildHeight();
      int max = level.getMaxBuildHeight() - 1;
      int y = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z));
      if (y <= min + 1) {
         // a neighbouring chunk whose terrain height maps are not filled in yet reports the bottom of the world: start
         // from the top of its highest filled section instead (climbing up from the bottom would stop in the first cave,
         // which is what drew bare lines along chunk borders)
         net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
         int index = chunk.getHighestFilledSectionIndex();
         if (index < 0) {
            return min;
         }
         y = Math.min(max, net.minecraft.core.SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index)) + 15);
      } else {
         // a height map can also sit a little below the real top: climb to it
         while (y < max && isTerrain(level.getBlockState(pos.set(x, y + 1, z)), level, pos)) {
            y++;
         }
      }
      for (; y >= min; y--) {
         net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos.set(x, y, z));
         if (isTerrain(state, level, pos)) {
            return y;
         }
      }
      return min;
   }

   static boolean isTerrain(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
      if (state.isAir() || !state.getFluidState().isEmpty() && !state.isSolid()) {
         return false;
      }
      if (state.is(net.minecraft.world.level.block.Blocks.SNOW) || state.is(net.minecraft.tags.BlockTags.LEAVES)
         || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
         || state.is(net.minecraft.world.level.block.Blocks.BAMBOO) || state.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM_BLOCK)
         || state.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.MUSHROOM_STEM)) {
         return false;
      }
      return !state.getCollisionShape(level, pos).isEmpty();
   }

   private static final ThreadLocal<GroundCache> GROUND = ThreadLocal.withInitial(GroundCache::new);

   /** Ground heights of a 3x3-chunk window around the chunk being decorated. */
   private static final class GroundCache {
      Object level;
      int cx;
      int cz;
      final int[] values = new int[48 * 48];

      void reset(Object level, int cx, int cz) {
         this.level = level;
         this.cx = cx;
         this.cz = cz;
         java.util.Arrays.fill(this.values, Integer.MIN_VALUE);
      }
   }

   /** Blocks by which the ground within 2 blocks (weighted towards the centre) lies above this column: + hollow, - ridge. */
   static float hollow(WorldGenLevel level, int x, int z) {
      int h0 = ground(level, x, z);
      float sum = 0.0F;
      float weights = 0.0F;
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            float w = (float)Math.exp(-(dx * dx + dz * dz) / 2.5);
            int h = Math.max(h0 - 4, Math.min(h0 + 4, ground(level, x + dx, z + dz)));
            sum += h * w;
            weights += w;
         }
      }
      return sum / weights - h0;
   }

   /** The way the ground falls here: north, south, east, west, or flat (less than 2 blocks over 4). */
   static String facing(WorldGenLevel level, int x, int z) {
      int dx = ground(level, x + 2, z) - ground(level, x - 2, z);
      int dz = ground(level, x, z + 2) - ground(level, x, z - 2);
      if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) {
         return "flat";
      }
      if (Math.abs(dz) >= Math.abs(dx)) {
         return dz > 0 ? "north" : "south";
      }
      return dx > 0 ? "west" : "east";
   }

   /**
    * Like hollow(), but over the shape of the land rather than single steps: the ground 3 and 6 blocks around (12 points)
    * against this column. 1-block terraces don't register; gullies, bowls and ridges do.
    */
   static float hollowWide(WorldGenLevel level, int x, int z) {
      // from FreeTerraForged's terrain model throughout, so it is the same in every chunk whatever its neighbours' stage
      int h0 = estimate(level, x, z);
      float sum = 0.0F;
      for (int i = 0; i < 8; i++) {
         int dx = i == 0 || i == 1 || i == 7 ? 6 : i == 3 || i == 4 || i == 5 ? -6 : 0;
         int dz = i == 1 || i == 2 || i == 3 ? 6 : i == 5 || i == 6 || i == 7 ? -6 : 0;
         sum += Math.max(h0 - 12, Math.min(h0 + 12, estimate(level, x + dx, z + dz)));
      }
      float near = 0.0F;
      near += estimate(level, x + 3, z) + estimate(level, x - 3, z) + estimate(level, x, z + 3) + estimate(level, x, z - 3);
      return (sum / 8.0F + near / 4.0F) / 2.0F - h0;
   }

   /** The way the land falls here over 8 blocks: north, south, east, west, or flat (less than 3 blocks). */
   static String facingWide(WorldGenLevel level, int x, int z) {
      int dx = estimate(level, x + 4, z) - estimate(level, x - 4, z);
      int dz = estimate(level, x, z + 4) - estimate(level, x, z - 4);
      if (Math.max(Math.abs(dx), Math.abs(dz)) < 3) {
         return "flat";
      }
      if (Math.abs(dz) >= Math.abs(dx)) {
         return dz > 0 ? "north" : "south";
      }
      return dx > 0 ? "west" : "east";
   }

   /** A steep gully: where avalanches and rockfall keep mountain forests open in strips. */
   static boolean isChute(float slope, float hollow) {
      return slope > 0.5F && hollow > 0.35F;
   }

   /** Blocks by which this column stands above the average of FTF's terrain 32 blocks around (from FTF's heightmap). */
   static float relief(GeneratorContext ctx, int x, int z) {
      // FTF's eroded height model (StillLifeClimateBridge.terrainHeight); applyCell answers with the eroded cell only when
      // that chunk's region happens to be cached, which made this depend on generation order
      int h0 = (int)StillLifeClimateBridge.terrainHeight(ctx, x, z);
      float sum = 0.0F;
      for (int i = 0; i < 8; i++) {
         double a = i * Math.PI / 4.0;
         sum += (int)StillLifeClimateBridge.terrainHeight(ctx, x + (int)Math.round(Math.cos(a) * 32.0), z + (int)Math.round(Math.sin(a) * 32.0));
      }
      return h0 - sum / 8.0F;
   }

   /** The biome's temperature at this position, cooled by 0.05 every 40 blocks above Y 80 like Minecraft's. */
   static float temperature(WorldGenLevel level, BlockPos pos) {
      return level.getBiome(pos).value().getBaseTemperature() - Math.max(0, pos.getY() - 80) * 0.05F / 40.0F;
   }

   private static final ThreadLocal<Cell> ESTIMATE = ThreadLocal.withInitial(Cell::new);

   static Cell cell(GeneratorContext ctx, ChunkAccess chunk, int x, int z) {
      Cell out = SCRATCH.get();
      Cell[] cells = chunk instanceof IChunkCellsHolder holder ? holder.freeterraforged$getOrCreateChunkCells(ctx) : null;
      if (cells != null) {
         out.copyFrom(cells[(z & 15) * 16 + (x & 15)]);
      } else {
         out.reset();
         ctx.lookup.applyCell(out, x, z, false, false);
      }
      if (out.gradient == 0.0F) {
         ctx.lookup.computeSteepness(out, x, z);
      }
      return out;
   }
}

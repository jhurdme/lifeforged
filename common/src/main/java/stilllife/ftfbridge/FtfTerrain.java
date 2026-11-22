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

   static int ground(WorldGenLevel level, int x, int z) {
      return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
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

   /** A steep gully: where avalanches and rockfall keep mountain forests open in strips. */
   static boolean isChute(float slope, float hollow) {
      return slope > 0.5F && hollow > 0.35F;
   }

   /** Blocks by which this column stands above the average of FTF's terrain 32 blocks around (from FTF's heightmap). */
   static float relief(GeneratorContext ctx, int x, int z) {
      Cell c = RING.get();
      Levels levels = ctx.heightmap.levels();
      ctx.lookup.applyCell(c.reset(), x, z, false, false);
      int h0 = levels.scale(c.height);
      float sum = 0.0F;
      for (int i = 0; i < 8; i++) {
         double a = i * Math.PI / 4.0;
         ctx.lookup.applyCell(c.reset(), x + (int)Math.round(Math.cos(a) * 32.0), z + (int)Math.round(Math.sin(a) * 32.0), false, false);
         sum += levels.scale(c.height);
      }
      return h0 - sum / 8.0F;
   }

   /** The biome's temperature at this position, cooled by 0.05 every 40 blocks above Y 80 like Minecraft's. */
   static float temperature(WorldGenLevel level, BlockPos pos) {
      return level.getBiome(pos).value().getBaseTemperature() - Math.max(0, pos.getY() - 80) * 0.05F / 40.0F;
   }

   private static final ThreadLocal<Cell> RING = ThreadLocal.withInitial(Cell::new);

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

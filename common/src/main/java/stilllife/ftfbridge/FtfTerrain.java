package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.FTFRandomState;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.IChunkCellsHolder;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import net.minecraft.world.level.WorldGenLevel;
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

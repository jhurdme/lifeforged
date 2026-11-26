package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;

/**
 * The valley floor around a place: the lowest ground within about 96 blocks, from FreeTerraForged's terrain model on a
 * 16-block lattice (each lattice node: the lowest of the 13 x 13 lattice heights around it), blended between the four nodes
 * around a column so it has no steps. For mountain_snow's patches below the snow line: how far a column stands above its
 * valley floor tells real mountain ground from gentle lowland.
 *
 * <p>The heights are FreeTerraForged's cell heights before erosion (its height model, ctx.heightmap.apply), a pure function of position
 * and seed, so the answer is the same in every chunk, on every thread, in any order. Memoised per thread.
 */
final class ValleyFloor {
   static final int STEP = 16;
   static final int RADIUS = 6; // lattice steps: 96 blocks

   private ValleyFloor() {
   }

   private static final class Memo {
      static final int SIZE = 4096;
      final Object[] ctx = new Object[SIZE];
      final long[] key = new long[SIZE];
      final float[] value = new float[SIZE];
      final Object[] ctxN = new Object[SIZE];
      final long[] keyN = new long[SIZE];
      final float[] valueN = new float[SIZE];
      final Cell cell = new Cell();
   }

   private static final ThreadLocal<Memo> MEMO = ThreadLocal.withInitial(Memo::new);

   /** Height (blocks) of the valley floor at x/z. */
   static double at(GeneratorContext ctx, int x, int z) {
      double fx = (double)x / STEP;
      double fz = (double)z / STEP;
      int ix = (int)Math.floor(fx);
      int iz = (int)Math.floor(fz);
      double tx = fx - ix;
      double tz = fz - iz;
      Memo m = MEMO.get();
      double a = node(m, ctx, ix, iz);
      double b = node(m, ctx, ix + 1, iz);
      double c = node(m, ctx, ix, iz + 1);
      double d = node(m, ctx, ix + 1, iz + 1);
      return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
   }

   private static long pack(int ix, int iz) {
      return (long)ix << 32 ^ (iz & 0xFFFFFFFFL);
   }

   private static int slot(int ix, int iz) {
      int h = ix * 0x9E3779B1 ^ iz * 0x85EBCA77;
      return (h ^ h >>> 15) & (Memo.SIZE - 1);
   }

   private static float node(Memo m, GeneratorContext ctx, int ix, int iz) {
      int s = slot(ix, iz);
      long k = pack(ix, iz);
      if (m.ctxN[s] == ctx && m.keyN[s] == k) {
         return m.valueN[s];
      }
      float lo = Float.MAX_VALUE;
      for (int dz = -RADIUS; dz <= RADIUS; dz++) {
         for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            lo = Math.min(lo, height(m, ctx, ix + dx, iz + dz));
         }
      }
      m.ctxN[s] = ctx;
      m.keyN[s] = k;
      m.valueN[s] = lo;
      return lo;
   }

   private static float height(Memo m, GeneratorContext ctx, int ix, int iz) {
      int s = slot(ix, iz);
      long k = pack(ix, iz);
      if (m.ctx[s] == ctx && m.key[s] == k) {
         return m.value[s];
      }
      // FTF's height model itself, not ctx.lookup.applyCell: that answers with the eroded cell when FTF happens to have
      // that chunk's region cached and with the uneroded one otherwise, so the valley floor (and with it the snow on high
      // ground) depended on which chunks were generated first (0.4% of the columns of a high massif changed between two
      // generations, along chunk edges)
      ctx.heightmap.apply(m.cell.reset(), ix * STEP, iz * STEP, false);
      // the sea floor counts as the sea's level: a coast is not a deep valley
      float h = Math.max(m.cell.height, ctx.levels.water) * ctx.levels.terrainScaleFactor;
      m.ctx[s] = ctx;
      m.key[s] = k;
      m.value[s] = h;
      return h;
   }
}

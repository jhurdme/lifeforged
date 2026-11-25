package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import net.minecraft.core.QuartPos;

/**
 * mountain_snow's snow field for one chunk at a time, worked out once per chunk and thread and shared by every pass that
 * asks (the erosion pass's snow ground, ground_cover's look at its neighbours, the snow layer pass, treetops, melted ice,
 * and the terrain filter / surface condition, which also read its sun, wind and shelter terms).
 *
 * <p>Everything in it is a pure function of the world position and seed, so it is the same whichever chunk asks and in
 * whatever order chunks are generated: the terrain shape comes from FreeTerraForged's eroded height model (the cells the
 * terrain itself is built from, read for any chunk without touching the world), the snow line, climate and latitude from
 * the climate bridge. Only the dependence on the column's own height (the snow line distance) is left to the caller.
 */
final class SnowField {
   private SnowField() {
   }

   /** The y-independent part of the snow model for the 16 x 16 columns of one chunk (index z * 16 + x). */
   static final class Chunk {
      java.lang.ref.WeakReference<Object> ctx = new java.lang.ref.WeakReference<>(null);
      long seed;
      int cx = Integer.MIN_VALUE;
      int cz = Integer.MIN_VALUE;
      long used;
      /** Shelter (0..1). */
      final float[] keep = new float[256];
      /** snowLineDistance at y 0 (the distance at y is this + y x inv, see dist). */
      final float[] line = new float[256];
      /**
       * 1 / the world's vertical scale (format 19; 1 on the reference preset and in older worlds): distances from the line,
       * heights above the sea and standing heights are in the reference preset's blocks.
       */
      double inv = 1.0;
      /** climateSnow. */
      final float[] snow = new float[256];
      /**
       * Still Life's temperature at sea level here (-1..1, the climate value its biome table is laid out on: blended
       * between the 4x4 columns around, so no 4-block steps). Its permanently snowy lowland begins at
       * {@link SnowModel#POLAR_T}.
       */
      final float[] temp = new float[256];
      /** Sea level (blocks). */
      float sea;
      final float[] hollow = new float[256];
      final float[] debris = new float[256];
      /** Sun on the slope, wind exposure and shelter by the land's shape (SnowModel.AUX_SUN, AUX_WIND, AUX_SHELTER). */
      final float[] sun = new float[256];
      final float[] wind = new float[256];
      final float[] shelter = new float[256];
      /** Relief (blocks): height above the lowest ground 12 and 32 blocks around (SnowModel.AUX_RELIEF). */
      final float[] relief = new float[256];
      /** FTF's terrain height of the column (blocks). */
      final float[] height = new float[256];
      /** How far the column stands above its valley floor (blocks; format 18 only, ValleyFloor). */
      final float[] standing = new float[256];
      /** True when the snow line comes from the snow-line curve (format 18: SnowModel.coverageCurve). */
      boolean curve;
      /** Slope over 8 blocks (format 18: tells gentle lowland from mountain ground, with standing). */
      final float[] slope8 = new float[256];

      /** Distance from the snow line at height y (blocks; format 19: the reference preset's blocks). */
      double dist(int i, int y) {
         // (written so a scale of 1 gives exactly the float sums of format 18)
         return this.inv == 1.0 ? line[i] + y : line[i] + y * this.inv;
      }

      /** Height above the sea at y (blocks; format 19: the reference preset's blocks). */
      double aboveSea(int y) {
         return this.inv == 1.0 ? y - sea : (y - sea) * this.inv;
      }

      double coverage(int i, int y) {
         return this.curve
            ? SnowModel.coverageCurve(dist(i, y) + SnowModel.aspectShift(sun[i], wind[i]), snow[i], temp[i], aboveSea(y), standing[i], slope8[i])
            : SnowModel.coverage(dist(i, y), snow[i], temp[i], aboveSea(y));
      }

      /** SnowModel.patch here (format 18: the polar patches of gentle ground). */
      final float[] patch = new float[256];

      /**
       * At least 0 where the column (index z * 16 + x, top at y) holds snow. With the snow-line curve (format 18) the light
       * patches of gentle ground near the arctic come from the patch noise (big, coherent patches like Still Life's own
       * arctic snow), the rest from the shelter.
       */
      double score(int i, int y) {
         double s = SnowModel.score(keep[i], coverage(i, y));
         if (this.curve) {
            double p = SnowModel.curvePatches(dist(i, y), snow[i], temp[i], aboveSea(y), standing[i], slope8[i]);
            if (p > 0.0) {
               // the patch noise is not uniform (most of it near 0.5): 0.6 x the share keeps the snowy part near the share
               s = Math.max(s, patch[i] - (1.0 - 0.6 * p));
            }
         }
         return s;
      }

      /** Snow depth in layers (8 = one block); 0 where bare. */
      float depth(int i, int y) {
         double score = score(i, y);
         float d = SnowModel.depth(score, snow[i], hollow[i], debris[i], -wind[i]);
         if (this.curve && score >= 0.0) {
            // format 18: where the zone is nearly full (high ground) the snow is deep right to its edges, so the stepped
            // slopes and open ground of a high massif are snowfields, not thin snow the step rule bares
            double full = SnowModel.smoothstep(SnowModel.DEEP_FROM, SnowModel.DEEP_TO, coverage(i, y));
            if (full > 0.0) {
               d = Math.max(d, (float)(full * (5.0 + 7.0 * Math.max(0.0, Math.min(1.0, (snow[i] - 0.4) / 1.1)))));
            }
         }
         return d;
      }

   }

   /**
    * Chunks kept per thread, least recently used replaced first: a chunk being worked on (asked about for every column)
    * stays while its 8 neighbours are looked at, so a Chunk a caller holds is not refilled under it.
    */
   private static final int SLOTS = 16;

   private static final class Cache {
      final Chunk[] chunks = new Chunk[SLOTS];
      long tick;
      final float[] window = new float[SnowModel.W * SnowModel.W];
      final float[] aux = new float[SnowModel.AUX_SIZE];
      final float[] temps = new float[36];
   }

   private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

   /** The field of chunk (cx, cz), or null when the climate bridge doesn't know the world yet. */
   static Chunk get(GeneratorContext ctx, long seed, int cx, int cz) {
      Cache c = CACHE.get();
      int oldest = 0;
      for (int s = 0; s < SLOTS; s++) {
         Chunk k = c.chunks[s];
         if (k == null) {
            oldest = s;
            break;
         }
         if (k.cx == cx && k.cz == cz && k.seed == seed && k.ctx.get() == ctx) {
            k.used = ++c.tick;
            return k;
         }
         if (k.used < c.chunks[oldest].used) {
            oldest = s;
         }
      }
      Chunk k = c.chunks[oldest];
      if (k == null) {
         k = new Chunk();
         c.chunks[oldest] = k;
      }
      k.cx = Integer.MIN_VALUE;
      if (!compute(k, c, ctx, seed, cx, cz)) {
         return null;
      }
      k.cx = cx;
      k.cz = cz;
      k.seed = seed;
      k.ctx = new java.lang.ref.WeakReference<>(ctx);
      k.used = ++c.tick;
      return k;
   }

   private static boolean compute(Chunk k, Cache c, GeneratorContext ctx, long seed, int cx, int cz) {
      int x0 = cx << 4;
      int z0 = cz << 4;
      int ox = x0 - SnowModel.R;
      int oz = z0 - SnowModel.R;
      // the snow line first: unknown before the climate bridge has the world
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            double d = StillLifeClimateBridge.snowLineDistance(x0 + lx, 0, z0 + lz);
            if (Double.isNaN(d)) {
               return false;
            }
            k.line[lz * 16 + lx] = (float)d;
         }
      }
      // FreeTerraForged's eroded terrain heights around the chunk, a chunk's cells at a time
      float[] h = c.window;
      float scale = ctx.heightmap.levels().terrainScaleFactor;
      Cell scratch = null;
      int w = SnowModel.W;
      for (int ccz = oz >> 4; ccz <= (oz + w - 1) >> 4; ccz++) {
         for (int ccx = ox >> 4; ccx <= (ox + w - 1) >> 4; ccx++) {
            Cell[] cells = ctx.filteredChunkCache.getFilteredChunk(ccx, ccz);
            int xa = Math.max(ox, ccx << 4);
            int xb = Math.min(ox + w - 1, (ccx << 4) + 15);
            int za = Math.max(oz, ccz << 4);
            int zb = Math.min(oz + w - 1, (ccz << 4) + 15);
            for (int z = za; z <= zb; z++) {
               for (int x = xa; x <= xb; x++) {
                  float v;
                  if (cells != null) {
                     v = cells[(z & 15) * 16 + (x & 15)].height;
                  } else {
                     if (scratch == null) {
                        scratch = new Cell();
                     }
                     ctx.lookup.applyCell(scratch.reset(), x, z, false, false);
                     v = scratch.height;
                  }
                  h[(z - oz) * w + x - ox] = v * scale;
               }
            }
         }
      }
      k.sea = ctx.heightmap.levels().water * scale;
      k.curve = StillLifeClimateBridge.snowCurve();
      k.inv = 1.0 / StillLifeClimateBridge.verticalScale();
      // Still Life's temperature at the 4x4 columns around the chunk (6 x 6 of them, for the blend)
      float[] tq = c.temps;
      int qx0 = Math.floorDiv(x0 - 2, 4);
      int qz0 = Math.floorDiv(z0 - 2, 4);
      for (int dz = 0; dz < 6; dz++) {
         for (int dx = 0; dx < 6; dx++) {
            // the biome lookup's own temperature at that 4x4 column (latitude and its regional variation)
            tq[dz * 6 + dx] = StillLifeClimateBridge.seaLevelTemperature(QuartPos.toBlock(qx0 + dx), QuartPos.toBlock(qz0 + dz));
         }
      }
      for (int lz = 0; lz < 16; lz++) {
         int z = z0 + lz;
         double latSigned = StillLifeClimateBridge.latitudeSigned(z);
         double fz = (z - 2) / 4.0;
         int qz = (int)Math.floor(fz);
         float tz = (float)(fz - qz);
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int i = lz * 16 + lx;
            float h0 = h[(lz + SnowModel.R) * w + lx + SnowModel.R];
            float snow = MountainSnow.climateSnow(x, (int)h0, z, seed);
            k.snow[i] = snow;
            double fx = (x - 2) / 4.0;
            int qx = (int)Math.floor(fx);
            float tx = (float)(fx - qx);
            int a = (qz - qz0) * 6 + qx - qx0;
            k.temp[i] = (tq[a] * (1 - tx) + tq[a + 1] * tx) * (1 - tz) + (tq[a + 6] * (1 - tx) + tq[a + 7] * tx) * tz;
            k.keep[i] = SnowModel.keep(h, lx + SnowModel.R, lz + SnowModel.R, x, z, seed, snow, latSigned, c.aux, k.curve);
            k.hollow[i] = c.aux[SnowModel.AUX_HOLLOW];
            k.debris[i] = c.aux[SnowModel.AUX_DEBRIS];
            k.sun[i] = c.aux[SnowModel.AUX_SUN];
            k.wind[i] = c.aux[SnowModel.AUX_WIND];
            k.shelter[i] = c.aux[SnowModel.AUX_SHELTER];
            k.relief[i] = c.aux[SnowModel.AUX_RELIEF];
            k.height[i] = h0;
            k.standing[i] = k.curve ? (float)((h0 - ValleyFloor.at(ctx, x, z)) * k.inv) : 0.0F;
            k.patch[i] = k.curve ? SnowModel.patch(seed, x, z) : 0.0F;
            if (k.curve) {
               int c0 = (lz + SnowModel.R) * w + lx + SnowModel.R;
               float sx = (h[c0 + 4] - h[c0 - 4]) / 8.0F;
               float sz = (h[c0 + 4 * w] - h[c0 - 4 * w]) / 8.0F;
               k.slope8[i] = (float)Math.sqrt(sx * sx + sz * sz);
            }
         }
      }
      return true;
   }
}

package stilllife.ftfbridge;

/**
 * Rock provinces: the land is divided into regions some 600-900 blocks across, each with its own character, so two ranges in
 * the same climate look different: which rock family its loose ground is (see GroundCover.Family), how much scree its
 * cliffs shed, how blocky or fine that scree is, how readily plants take it over, and how much bare rock shows through
 * thin soil.
 *
 * <p>The regions are Voronoi cells of a jittered 760-block grid, looked up through a two-scale domain warp, so their
 * borders wander organically instead of running straight. Across a border the two provinces' loose ground interlocks in
 * blobs over an ~80-block band (smooth noise, no per-block speckle), and their bedrock interfingers over ~150 blocks
 * ({@link #pair}, Strata). Rock character follows a smooth lithology field a few kilometres across ({@link #lightness}),
 * so neighbouring provinces have similar rock. Deterministic from seed + position.
 */
final class Provinces {
   static final float GRID = 760.0F;
   /** Half width of the band in which two provinces' loose ground interlocks. */
   static final float BAND = 40.0F;

   /**
    * One province's character (all 0..1 unless stated).
    *
    * @param id     province id (the same for every column of the province)
    * @param roll   picks the rock family (GroundCover.family)
    * @param debris how much scree the cliffs shed: scales the length of talus runs (0.55..1.55)
    * @param coarse blocky (1) or fine (0) scree
    * @param green  how readily soil and plants take over the toe of the scree (0.3..1.4)
    * @param bare   how much bare rock shows through thin soil on steep ground
    * @param lightness the regional lithology field at the province's centre, 0 (dark rock) to 1 (pale rock): a smooth field
    *                  a few kilometres across, so neighbouring provinces have similar rock and the palest and darkest
    *                  rarely meet
    * @param siteX  x of the province's centre
    * @param siteZ  z of the province's centre
    */
   record Province(int id, float roll, float debris, float coarse, float green, float bare, float lightness, float siteX, float siteZ) {
   }

   /**
    * The two provinces nearest a column and how far it lies into the transition between them.
    *
    * @param a    the nearest province
    * @param b    the second nearest
    * @param edge blocks from the border between them
    */
   record Pair(Province a, Province b, float edge) {
      /** The share of b in a transition zone of the given half width: 1/2 on the border, 0 at the zone's edge. */
      float share(float halfWidth) {
         return this.edge >= halfWidth ? 0.0F : 0.5F * (1.0F - this.edge / halfWidth);
      }
   }

   /** Half width (blocks) of the zone over which two provinces' bedrock interfingers. */
   static final float BLEND = 75.0F;

   private Provinces() {
   }

   /** The province at x/z: across a border the two (or three) provinces' loose ground interlocks in blobs. */
   static Province at(long seed, int x, int z) {
      Blend b = blend(seed, x, z, BAND);
      return b.pick(CoverNoise.value2(seed, x, z, 515, 9.0F));
   }

   /**
    * The provinces that share a column and their cumulative shares, in a fixed order (by id), so that picking by a noise
    * value is continuous across borders: no line where the nearest province changes.
    *
    * @param provinces the provinces with a share here (1 to 3)
    * @param upTo      cumulative shares, the last one 1
    */
   record Blend(Province[] provinces, float[] upTo) {
      /** The province a noise value 0..1 falls into. */
      Province pick(float f) {
         for (int i = 0; i < this.upTo.length - 1; i++) {
            if (f < this.upTo[i]) {
               return this.provinces[i];
            }
         }
         return this.provinces[this.provinces.length - 1];
      }
   }

   /**
    * The provinces sharing x/z within a transition of the given half width: each site's weight falls from 1 (as near as
    * the nearest site) to 0 (halfWidth blocks into the nearest province, measured as half the difference of the distances
    * to the two sites). Weights are continuous everywhere, also where three provinces meet.
    */
   static Blend blend(long seed, int x, int z, float halfWidth) {
      float px = warpX(seed, x, z) + x;
      float pz = warpZ(seed, x, z) + z;
      int gx = (int)Math.floor(px / GRID);
      int gz = (int)Math.floor(pz / GRID);
      float[] d = new float[9];
      float min = Float.MAX_VALUE;
      for (int k = 0; k < 9; k++) {
         int cx = gx + k % 3 - 1;
         int cz = gz + k / 3 - 1;
         float sx = (cx + 0.1F + 0.8F * CoverNoise.hash(seed, cx, cz, 511)) * GRID;
         float sz = (cz + 0.1F + 0.8F * CoverNoise.hash(seed, cx, cz, 513)) * GRID;
         d[k] = (float)Math.sqrt((sx - px) * (sx - px) + (sz - pz) * (sz - pz));
         min = Math.min(min, d[k]);
      }
      int n = 0;
      int[] idx = new int[9];
      float[] w = new float[9];
      float total = 0.0F;
      for (int k = 0; k < 9; k++) {
         float wk = 1.0F - (d[k] - min) / (2.0F * halfWidth);
         if (wk > 0.0F) {
            idx[n] = k;
            w[n] = wk;
            total += wk;
            n++;
         }
      }
      Province[] ps = new Province[n];
      for (int i = 0; i < n; i++) {
         int cx = gx + idx[i] % 3 - 1;
         int cz = gz + idx[i] / 3 - 1;
         float sx = (cx + 0.1F + 0.8F * CoverNoise.hash(seed, cx, cz, 511)) * GRID;
         float sz = (cz + 0.1F + 0.8F * CoverNoise.hash(seed, cx, cz, 513)) * GRID;
         ps[i] = of(seed, cx, cz, sx, sz);
      }
      // a fixed order (by id), so a share moving between provinces never jumps where the nearest one changes
      for (int i = 1; i < n; i++) {
         for (int j = i; j > 0 && ps[j].id() < ps[j - 1].id(); j--) {
            Province tp = ps[j];
            ps[j] = ps[j - 1];
            ps[j - 1] = tp;
            float tw = w[j];
            w[j] = w[j - 1];
            w[j - 1] = tw;
         }
      }
      float[] upTo = new float[n];
      float acc = 0.0F;
      for (int i = 0; i < n; i++) {
         acc += w[i] / total;
         upTo[i] = acc;
      }
      upTo[n - 1] = 1.0F;
      return new Blend(ps, upTo);
   }

   private static float warpX(long seed, int x, int z) {
      return (CoverNoise.value2(seed, x, z, 501, 520.0F) - 0.5F) * 340.0F + (CoverNoise.value(seed, x, z, 505, 95.0F) - 0.5F) * 70.0F;
   }

   private static float warpZ(long seed, int x, int z) {
      return (CoverNoise.value2(seed, x, z, 503, 520.0F) - 0.5F) * 340.0F + (CoverNoise.value(seed, x, z, 507, 95.0F) - 0.5F) * 70.0F;
   }

   /** The lithology field (0 dark .. 1 pale) at x/z: smooth over a few kilometres, stretched to use the whole range. */
   static float lightness(long seed, float x, float z) {
      return CoverNoise.clamp01(0.5F + (CoverNoise.value2(seed, x, z, 541, 3200.0F) - 0.5F) * 2.4F);
   }

   /** The two provinces nearest x/z (after the domain warp). */
   static Pair pair(long seed, int x, int z) {
      // domain warp: a broad one that bends the cell borders, and a finer one that makes them wander
      float w1x = (CoverNoise.value2(seed, x, z, 501, 520.0F) - 0.5F) * 340.0F;
      float w1z = (CoverNoise.value2(seed, x, z, 503, 520.0F) - 0.5F) * 340.0F;
      float w2x = (CoverNoise.value(seed, x, z, 505, 95.0F) - 0.5F) * 70.0F;
      float w2z = (CoverNoise.value(seed, x, z, 507, 95.0F) - 0.5F) * 70.0F;
      float px = x + w1x + w2x;
      float pz = z + w1z + w2z;
      int gx = (int)Math.floor(px / GRID);
      int gz = (int)Math.floor(pz / GRID);
      float best = Float.MAX_VALUE;
      float second = Float.MAX_VALUE;
      int bx = 0;
      int bz = 0;
      float bsx = 0.0F;
      float bsz = 0.0F;
      int sx2 = 0;
      int sz2 = 0;
      float ssx = 0.0F;
      float ssz = 0.0F;
      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            int cx = gx + dx;
            int cz = gz + dz;
            float sx = (cx + 0.1F + 0.8F * CoverNoise.hash(seed, cx, cz, 511)) * GRID;
            float sz = (cz + 0.1F + 0.8F * CoverNoise.hash(seed, cx, cz, 513)) * GRID;
            float d = (sx - px) * (sx - px) + (sz - pz) * (sz - pz);
            if (d < best) {
               second = best;
               sx2 = bx;
               sz2 = bz;
               ssx = bsx;
               ssz = bsz;
               best = d;
               bx = cx;
               bz = cz;
               bsx = sx;
               bsz = sz;
            } else if (d < second) {
               second = d;
               sx2 = cx;
               sz2 = cz;
               ssx = sx;
               ssz = sz;
            }
         }
      }
      // distance to the bisector between the nearest two sites
      float sep = (float)Math.sqrt((ssx - bsx) * (ssx - bsx) + (ssz - bsz) * (ssz - bsz));
      float edge = sep > 0.0F ? (second - best) / (2.0F * sep) : Float.MAX_VALUE;
      return new Pair(of(seed, bx, bz, bsx, bsz), of(seed, sx2, sz2, ssx, ssz), edge);
   }

   private static Province of(long seed, int cx, int cz, float sx, float sz) {
      int id = cx * 73856093 ^ cz * 19349663;
      float roll = CoverNoise.hash(seed, cx, cz, 521);
      float debris = 0.55F + 1.0F * push(CoverNoise.hash(seed, cx, cz, 523));
      float coarse = push(CoverNoise.hash(seed, cx, cz, 525));
      float green = 0.3F + 1.1F * push(CoverNoise.hash(seed, cx, cz, 527));
      float bare = push(CoverNoise.hash(seed, cx, cz, 529));
      return new Province(id, roll, debris, coarse, green, bare, lightness(seed, sx, sz), sx, sz);
   }

   /** Pushes a uniform 0..1 value towards its ends, so provinces differ clearly rather than mostly sitting in the middle. */
   private static float push(float v) {
      return v < 0.5F ? 0.5F * (float)Math.pow(2.0F * v, 1.6) : 1.0F - push(1.0F - v);
   }
}

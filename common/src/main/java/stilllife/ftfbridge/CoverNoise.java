package stilllife.ftfbridge;

/**
 * The ground cover's noise: a position hash and smooth value noise without per-block dither (so thresholds give clean
 * patch edges instead of a speckled fringe). Pure Java, deterministic from seed + position.
 */
final class CoverNoise {
   private CoverNoise() {
   }

   /** Hash of a position to 0..1 (the same as GroundCover.hash). */
   static float hash(long seed, int x, int z, int salt) {
      long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
      h ^= h >>> 33;
      h *= 0xFF51AFD7ED558CCDL;
      h ^= h >>> 33;
      h *= 0xC4CEB9FE1A85EC53L;
      h ^= h >>> 33;
      return (h >>> 40) / (float)(1L << 24);
   }

   /** Smooth value noise in 0..1 with features about `size` blocks across, at a fractional position. No dither. */
   static float value(long seed, float x, float z, int salt, float size) {
      float fx = x / size;
      float fz = z / size;
      int ix = (int)Math.floor(fx);
      int iz = (int)Math.floor(fz);
      float tx = smooth(fx - ix);
      float tz = smooth(fz - iz);
      int s = salt * 31 + 7;
      float a = hash(seed, ix, iz, s);
      float b = hash(seed, ix + 1, iz, s);
      float c = hash(seed, ix, iz + 1, s);
      float d = hash(seed, ix + 1, iz + 1, s);
      return a + (b - a) * tx + (c - a + (a - b - c + d) * tx) * tz;
   }

   /** Two octaves of value noise, re-stretched to roughly 0..1 (less blobby than one octave). */
   static float value2(long seed, float x, float z, int salt, float size) {
      float v = value(seed, x, z, salt, size) * 0.65F + value(seed, x + 37.3F, z - 11.9F, salt + 1, size * 0.45F) * 0.35F;
      return clamp01(0.5F + (v - 0.5F) * 1.35F);
   }

   /** Hash of a 3D lattice point to 0..1. */
   static float hash3(long seed, int x, int y, int z, int salt) {
      return hash(seed ^ (y * 0xD6E8FEB86659FD93L), x, z, salt);
   }

   /**
    * Smooth 3D noise in 0..1 at an already scaled position (features about 1 unit across), centred on 1/2 with about the
    * spread of {@link #value3}. Gradient noise: its contours have no preference for the lattice axes (value noise's
    * thresholded contours line up with the lattice planes, which on a rock face shows as boxes with straight vertical and
    * horizontal edges).
    */
   static float noise3(long seed, float fx, float fy, float fz, int salt) {
      int ix = (int)Math.floor(fx);
      int iy = (int)Math.floor(fy);
      int iz = (int)Math.floor(fz);
      float x = fx - ix;
      float y = fy - iy;
      float z = fz - iz;
      float u = fade(x);
      float v = fade(y);
      float w = fade(z);
      float n000 = grad(seed, ix, iy, iz, salt, x, y, z);
      float n100 = grad(seed, ix + 1, iy, iz, salt, x - 1, y, z);
      float n010 = grad(seed, ix, iy + 1, iz, salt, x, y - 1, z);
      float n110 = grad(seed, ix + 1, iy + 1, iz, salt, x - 1, y - 1, z);
      float n001 = grad(seed, ix, iy, iz + 1, salt, x, y, z - 1);
      float n101 = grad(seed, ix + 1, iy, iz + 1, salt, x - 1, y, z - 1);
      float n011 = grad(seed, ix, iy + 1, iz + 1, salt, x, y - 1, z - 1);
      float n111 = grad(seed, ix + 1, iy + 1, iz + 1, salt, x - 1, y - 1, z - 1);
      float n = lerp(lerp(lerp(n000, n100, u), lerp(n010, n110, u), v), lerp(lerp(n001, n101, u), lerp(n011, n111, u), v), w);
      // n is roughly -1..1 with most values near 0; scaled to the spread value noise has
      return clamp01(0.5F + n * NOISE3_SPREAD);
   }

   /**
    * The lattice cell last used by {@link #noise3(Cell3, long, float, float, float, int)}: its eight corner gradients, so a
    * run of calls inside one cell (a column read from the bottom up) hashes nothing. Results are bit-identical to
    * {@link #noise3(long, float, float, float, int)}.
    */
   static final class Cell3 {
      long seed;
      int salt;
      int ix = Integer.MIN_VALUE;
      int iy;
      int iz;
      final int[] g = new int[8];
   }

   /** {@link #noise3(long, float, float, float, int)} with the corner gradients of the last cell kept in c. */
   static float noise3(Cell3 c, long seed, float fx, float fy, float fz, int salt) {
      // floor without the double round trip (the same integer as Math.floor for these magnitudes)
      int ix = (int)fx;
      if (fx < ix) {
         ix--;
      }
      int iy = (int)fy;
      if (fy < iy) {
         iy--;
      }
      int iz = (int)fz;
      if (fz < iz) {
         iz--;
      }
      int[] g = c.g;
      if (c.ix == ix && c.iz == iz && c.iy + 1 == iy && c.seed == seed && c.salt == salt) {
         // one cell up (a column read upwards): the old top corners are the new bottom ones
         g[0] = g[2];
         g[1] = g[3];
         g[4] = g[6];
         g[5] = g[7];
         g[2] = (int)(hash3(seed, ix, iy + 1, iz, salt) * 12.0F);
         g[3] = (int)(hash3(seed, ix + 1, iy + 1, iz, salt) * 12.0F);
         g[6] = (int)(hash3(seed, ix, iy + 1, iz + 1, salt) * 12.0F);
         g[7] = (int)(hash3(seed, ix + 1, iy + 1, iz + 1, salt) * 12.0F);
         c.iy = iy;
      } else if (c.ix != ix || c.iy != iy || c.iz != iz || c.seed != seed || c.salt != salt) {
         g[0] = (int)(hash3(seed, ix, iy, iz, salt) * 12.0F);
         g[1] = (int)(hash3(seed, ix + 1, iy, iz, salt) * 12.0F);
         g[2] = (int)(hash3(seed, ix, iy + 1, iz, salt) * 12.0F);
         g[3] = (int)(hash3(seed, ix + 1, iy + 1, iz, salt) * 12.0F);
         g[4] = (int)(hash3(seed, ix, iy, iz + 1, salt) * 12.0F);
         g[5] = (int)(hash3(seed, ix + 1, iy, iz + 1, salt) * 12.0F);
         g[6] = (int)(hash3(seed, ix, iy + 1, iz + 1, salt) * 12.0F);
         g[7] = (int)(hash3(seed, ix + 1, iy + 1, iz + 1, salt) * 12.0F);
         c.ix = ix;
         c.iy = iy;
         c.iz = iz;
         c.seed = seed;
         c.salt = salt;
      }
      float x = fx - ix;
      float y = fy - iy;
      float z = fz - iz;
      float u = fade(x);
      float v = fade(y);
      float w = fade(z);
      float n000 = dot(g[0], x, y, z);
      float n100 = dot(g[1], x - 1, y, z);
      float n010 = dot(g[2], x, y - 1, z);
      float n110 = dot(g[3], x - 1, y - 1, z);
      float n001 = dot(g[4], x, y, z - 1);
      float n101 = dot(g[5], x - 1, y, z - 1);
      float n011 = dot(g[6], x, y - 1, z - 1);
      float n111 = dot(g[7], x - 1, y - 1, z - 1);
      float n = lerp(lerp(lerp(n000, n100, u), lerp(n010, n110, u), v), lerp(lerp(n001, n101, u), lerp(n011, n111, u), v), w);
      return clamp01(0.5F + n * NOISE3_SPREAD);
   }

   // grad's twelve directions as a table (no branches): the same sums, so the same floats (adding a zero term changes
   // nothing)
   private static final float[] GX = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0};
   private static final float[] GY = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1};
   private static final float[] GZ = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1};

   private static float dot(int h, float x, float y, float z) {
      return GX[h] * x + GY[h] * y + GZ[h] * z;
   }

   /** Scale of noise3 around 1/2, so its distribution matches value3's (measured: both have sd ~0.17). */
   static final float NOISE3_SPREAD = 0.687F;

   private static float fade(float t) {
      return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
   }

   /** Dot product with one of 12 gradient directions (the cube's edge midpoints), picked by hash. */
   private static float grad(long seed, int ix, int iy, int iz, int salt, float x, float y, float z) {
      int h = (int)(hash3(seed, ix, iy, iz, salt) * 12.0F);
      return switch (h) {
         case 0 -> x + y;
         case 1 -> -x + y;
         case 2 -> x - y;
         case 3 -> -x - y;
         case 4 -> x + z;
         case 5 -> -x + z;
         case 6 -> x - z;
         case 7 -> -x - z;
         case 8 -> y + z;
         case 9 -> -y + z;
         case 10 -> y - z;
         default -> -y - z;
      };
   }

   /** Smooth 3D value noise in 0..1 at an already scaled position (features about 1 unit across). */
   static float value3(long seed, float fx, float fy, float fz, int salt) {
      int ix = (int)Math.floor(fx);
      int iy = (int)Math.floor(fy);
      int iz = (int)Math.floor(fz);
      float tx = smooth(fx - ix);
      float ty = smooth(fy - iy);
      float tz = smooth(fz - iz);
      float a0 = lerp(hash3(seed, ix, iy, iz, salt), hash3(seed, ix + 1, iy, iz, salt), tx);
      float b0 = lerp(hash3(seed, ix, iy, iz + 1, salt), hash3(seed, ix + 1, iy, iz + 1, salt), tx);
      float a1 = lerp(hash3(seed, ix, iy + 1, iz, salt), hash3(seed, ix + 1, iy + 1, iz, salt), tx);
      float b1 = lerp(hash3(seed, ix, iy + 1, iz + 1, salt), hash3(seed, ix + 1, iy + 1, iz + 1, salt), tx);
      return lerp(lerp(a0, b0, tz), lerp(a1, b1, tz), ty);
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   static float clamp01(float v) {
      return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
   }
}

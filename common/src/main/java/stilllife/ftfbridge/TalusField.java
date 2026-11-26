package stilllife.ftfbridge;

/**
 * The scree (talus) deposit model of the ground cover, on a window of FreeTerraForged's terrain heights around the chunk
 * being decorated. Pure Java (no Minecraft types), so it can be run on any height field.
 *
 * <p>Talus is a deposit, not a kind of slope: rock breaks off cliffs and bare rock above the rock line, and runs down the
 * fall line until it stops. For every column this traces the fall line upwards (steepest ascent through the height
 * window) to the first rock face, and puts a deposit on the column when that face is near enough:
 * <ul>
 *   <li>how far the debris runs from a face depends on the face (steeper and gullied faces shed more, smooth noise along the
 *       cliff foot gives chutes of different lengths) and on the province's debris amount. Every column fed by the same
 *       spot on the face gets the same run length, so the deposit has clean, continuous outlines: tongues below chutes,
 *       cones below gullies (fall lines from below converge into a gully, so they spread out below it);</li>
 *   <li>the debris is sorted: fine at the apex below the face, blocks and boulders at the toe;</li>
 *   <li>soil and plants take the toe back in mild, wet climates (none in cold or dry ones);</li>
 *   <li>past the toe, fines run out a little further as a fan where the land has sediment (always in dry land);</li>
 *   <li>steep ground with no rock face above it gets thin rocky soil with outcrops instead of rubble.</li>
 * </ul>
 * Patterns inside a deposit are streaks along the fall line: the noise is averaged along the traced fall line (a line
 * integral convolution), so patches are long down the slope and narrow across it.
 *
 * <p>The window holds 48 x 48 columns (the chunk and 16 around it); every trace stays within 15 blocks of its column, and
 * every value it reads needs at most one more block, so a column's result depends only on terrain within 16 blocks of it
 * and is the same whichever chunk is being decorated. Not thread-safe: one per thread.
 *
 * <p>Windows are kept per thread ({@link #cached}, {@link #claim}): the erosion pass, and the terrain filter and surface
 * condition that ask about talus later, share them, so a chunk's window is built once per thread while it is in use.
 */
final class TalusField {
   static final int N = 48;
   static final int MARGIN = 16;
   /** How far (blocks) a trace may go from its column. */
   static final int LIMIT = 15;

   // kinds of ground a column gets
   static final int KEEP = 0;
   /** Talus fines at the apex (gravel and the family's fine rock). */
   static final int FINE = 1;
   /** Talus mid-size debris (broken rock: cobble, the source rock). */
   static final int MEDIUM = 2;
   /** Talus blocks and boulders at the toe (the source rock). */
   static final int COARSE = 3;
   /** Moss over talus blocks (wet climates). */
   static final int MOSSY = 4;
   /** The toe of the talus taken back by soil and plants. */
   static final int VEG = 5;
   /** Fines run out past the toe (fan). */
   static final int FAN = 6;
   /** Thin soil on steep ground without a rock face above (coarse dirt). */
   static final int THIN_SOIL = 7;
   /** Gravel streaks in thin soil on steep ground. */
   static final int THIN_GRAVEL = 8;
   /** Bare rock showing through steep thin soil. */
   static final int OUTCROP = 9;

   /** Surface height of each window column, in blocks (fractional). */
   final float[] h = new float[N * N];
   /** FTF-style steepness (the gradient FTF's scree thresholds compare against). */
   final float[] slope = new float[N * N];
   /** The height (blocks) above which FTF's rock line exposes rock; +inf where unknown. */
   final float[] rockLine = new float[N * N];
   /** River water surface (blocks) over each window column by FTF's model, or NaN where there is no river water. */
   final float[] riverWater = new float[N * N];

   /** Horizontal reach (blocks) and height above the water (blocks) of a river bank, which the ground passes leave alone. */
   static final int BANK_REACH = 8;
   static final float BANK_HEIGHT = 7.0F;

   /**
    * True when x/z (ground at y) is on a river bank: within {@link #BANK_REACH} blocks of river water and no more than
    * {@link #BANK_HEIGHT} blocks above it (the whole stepped bank up to the floodplain or forest floor). Window only.
    */
   boolean riverBank(int x, int z, float y) {
      for (int dz = -BANK_REACH; dz <= BANK_REACH; dz++) {
         int row = this.index(x - BANK_REACH, z + dz);
         for (int dx = 0; dx <= 2 * BANK_REACH; dx++) {
            float w = this.riverWater[row + dx];
            if (w == w && y >= w - 1.0F && y - w <= BANK_HEIGHT) {
               return true;
            }
         }
      }
      return false;
   }
   /** World x/z of window index 0. */
   int ox;
   int oz;
   long seed;
   float rockSteep;
   float screeSteep;
   /** FTF's rockVar / rockMin (0..1) the rock lines were worked out with: part of the window's identity. */
   float rockVar;
   float rockMin;
   /** Window identity, for reuse: the FreeTerraForged generator context it was read from (null while being filled). */
   Object owner;
   int chunkX;
   int chunkZ;
   private long used;

   // ---------------------------------------------------------------- results of the last trace
   /** Blocks along the fall line to the rock face feeding this column, or -1 if none within reach. */
   int dSrc;
   /** How far debris from that face runs (blocks). */
   float runout;
   /** Where the column lies along its deposit: 0 at the face, 1 at the toe (where the runout ends or the ground levels). */
   float sortR;
   /** Whether the face is real rock (cliff / above the rock line) rather than merely steep ground. */
   boolean strong;
   /** Fall-line streak noise, 0..1, for sorting and material (fine scale). */
   float streakA;
   /** Fall-line streak noise, 0..1, for vegetation (broader). */
   float streakB;
   /** Fall-line streak noise, 0..1, for the choice of material inside a size class (clusters drawn out downslope). */
   float streakC;
   /**
    * How strongly frost shatters steep ground in place here, 0..1 (set by the caller before {@link #classify}: high up near
    * and above the treeline, more in cold climates): steep ground with no cliff above becomes a scree field.
    */
   float frost;
   /**
    * When set, trace counts only cliffs (steeper than the rock steepness) as rock faces, not ground above FTF's rock line
    * (on high ground above the line every column would otherwise be an apron's apex).
    */
   boolean cliffSources;
   /** Direction the ground falls at the column (unit vector, 0/0 when flat). */
   float fallX;
   float fallZ;
   /** Blocks the ground falls per block at the column. */
   float fall;

   int index(int x, int z) {
      return (z - this.oz) * N + (x - this.ox);
   }

   /**
    * Sets up the window for the chunk at chunkX/chunkZ (heights and rock lines must then be filled for all 48 x 48 columns,
    * and {@link #computeSlopes} called).
    */
   void reset(Object owner, int chunkX, int chunkZ, long seed, float rockSteep, float screeSteep) {
      this.owner = owner;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      this.ox = chunkX * 16 - MARGIN;
      this.oz = chunkZ * 16 - MARGIN;
      this.seed = seed;
      this.rockSteep = rockSteep;
      this.screeSteep = screeSteep;
   }

   boolean covers(Object owner, int chunkX, int chunkZ) {
      return this.owner == owner && this.chunkX == chunkX && this.chunkZ == chunkZ;
   }

   // ---------------------------------------------------------------- windows kept per thread

   private static final int SLOTS = 4;

   private static final class Cache {
      final TalusField[] fields = new TalusField[SLOTS];
      long tick;
   }

   private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

   /**
    * This thread's filled window of chunk (chunkX, chunkZ) read from owner (the generator context) with these settings, or
    * null. A window is a pure function of these, so any pass may use one another pass built.
    */
   static TalusField cached(Object owner, int chunkX, int chunkZ, long seed, float rockSteep, float screeSteep, float rockVar, float rockMin) {
      Cache c = CACHE.get();
      for (TalusField f : c.fields) {
         if (f != null && f.owner != null && f.covers(owner, chunkX, chunkZ) && f.seed == seed && f.rockSteep == rockSteep
            && f.screeSteep == screeSteep && f.rockVar == rockVar && f.rockMin == rockMin) {
            f.used = ++c.tick;
            return f;
         }
      }
      return null;
   }

   /**
    * A window of this thread to fill for chunk (chunkX, chunkZ): the least recently used one, reset (owner null until the
    * caller has filled it and set {@link #owner}). Windows a caller got before stay valid unless they were the oldest.
    */
   static TalusField claim(int chunkX, int chunkZ, long seed, float rockSteep, float screeSteep, float rockVar, float rockMin) {
      Cache c = CACHE.get();
      int oldest = 0;
      for (int s = 0; s < SLOTS; s++) {
         TalusField f = c.fields[s];
         if (f == null) {
            oldest = s;
            break;
         }
         if (f.used < c.fields[oldest].used) {
            oldest = s;
         }
      }
      TalusField f = c.fields[oldest];
      if (f == null) {
         f = new TalusField();
         c.fields[oldest] = f;
      }
      f.reset(null, chunkX, chunkZ, seed, rockSteep, screeSteep);
      f.rockVar = rockVar;
      f.rockMin = rockMin;
      f.used = ++c.tick;
      return f;
   }

   /**
    * FTF's steepness from the heights: the summed height difference to the 8 neighbours (neighbours below the water level
    * count as at it), times 18.75 per terrain unit (FTF's WorldLookup.computeSteepness).
    *
    * @param water      water level in blocks
    * @param blocksUnit blocks per FTF height unit (Levels.terrainScaleFactor)
    */
   void computeSlopes(float water, float blocksUnit) {
      float k = 18.75F / blocksUnit;
      for (int z = 0; z < N; z++) {
         for (int x = 0; x < N; x++) {
            int i = z * N + x;
            if (x == 0 || z == 0 || x == N - 1 || z == N - 1) {
               this.slope[i] = 0.0F;
               continue;
            }
            float h0 = this.h[i];
            float sum = 0.0F;
            for (int dz = -1; dz <= 1; dz++) {
               for (int dx = -1; dx <= 1; dx++) {
                  if (dx != 0 || dz != 0) {
                     sum += Math.abs(h0 - Math.max(water, this.h[i + dz * N + dx]));
                  }
               }
            }
            this.slope[i] = sum * k;
         }
      }
   }

   /** Rise of the ground per block (Sobel) at window index i: x component. */
   private float gradX(int i) {
      float[] a = this.h;
      return (a[i - N + 1] + 2.0F * a[i + 1] + a[i + N + 1] - a[i - N - 1] - 2.0F * a[i - 1] - a[i + N - 1]) * 0.125F;
   }

   private float gradZ(int i) {
      float[] a = this.h;
      return (a[i + N - 1] + 2.0F * a[i + N] + a[i + N + 1] - a[i - N - 1] - 2.0F * a[i - N] - a[i - N + 1]) * 0.125F;
   }

   /** True when the column at window index i is bare rock by FTF's rules (cliff, or above the rock line). */
   boolean isRock(int i) {
      return this.slope[i] > this.rockSteep || this.h[i] > this.rockLine[i];
   }

   /**
    * Traces the fall line up from world x/z and fills in the trace results (dSrc, runout, strong, streaks, fall).
    *
    * @param debris the province's debris amount
    */
   void trace(int x, int z, float debris) {
      int c = this.index(x, z);
      float gx = this.gradX(c);
      float gz = this.gradZ(c);
      float mag = (float)Math.sqrt(gx * gx + gz * gz);
      this.fall = mag;
      this.fallX = mag > 1.0E-4F ? -gx / mag : 0.0F;
      this.fallZ = mag > 1.0E-4F ? -gz / mag : 0.0F;

      long seed = this.seed;
      float accA = plan(seed, x, z, 601, 4.5F);
      float accB = plan(seed, x, z, 603, 9.0F);
      float accC = plan(seed, x, z, 605, 5.0F);
      float wSum = 1.0F;
      this.dSrc = -1;
      this.strong = false;
      int weakStep = -1;
      int weakIdx = -1;
      int srcIdx = -1;
      boolean rockLineOnly = false;

      this.startPath(x, z);
      int i;
      for (int step = 1; step <= LIMIT; step++) {
         i = this.advance(x, z, 1.0F, 5);
         if (i < 0) {
            break;
         }
         float px = this.pX;
         float pz = this.pZ;
         if (step <= 10) {
            float w = 1.0F - step / 11.0F;
            accA += plan(seed, px, pz, 601, 4.5F) * w;
            accB += plan(seed, px, pz, 603, 9.0F) * w;
            accC += plan(seed, px, pz, 605, 5.0F) * w;
            wSum += w;
         }
         if (this.slope[i] > this.rockSteep) {
            this.dSrc = step;
            srcIdx = i;
            this.strong = true;
            break;
         }
         if (!this.cliffSources && this.h[i] > this.rockLine[i]) {
            this.dSrc = step;
            srcIdx = i;
            this.strong = true;
            rockLineOnly = this.slope[i] < this.screeSteep;
            break;
         }
         if (weakStep < 0 && this.slope[i] > this.screeSteep) {
            weakStep = step;
            weakIdx = i;
         }
      }
      // averaging lowers the spread: stretch back to about the full 0..1 range
      this.streakA = CoverNoise.clamp01(0.5F + (accA / wSum - 0.5F) * 1.9F);
      this.streakB = CoverNoise.clamp01(0.5F + (accB / wSum - 0.5F) * 1.7F);
      this.streakC = CoverNoise.clamp01(0.5F + (accC / wSum - 0.5F) * 2.0F);

      if (srcIdx < 0 && weakIdx >= 0) {
         this.dSrc = weakStep;
         srcIdx = weakIdx;
      }
      if (srcIdx < 0) {
         this.runout = 0.0F;
         this.sortR = 0.0F;
         return;
      }
      // the face: how much it sheds depends on smooth noise along it (chutes) and on whether it is a gully
      int sx = this.ox + srcIdx % N;
      int sz = this.oz + srcIdx / N;
      float feed = plan(seed, sx, sz, 607, 9.0F) * 0.7F + plan(seed, sx, sz, 609, 4.0F) * 0.3F;
      feed = CoverNoise.clamp01(0.5F + (feed - 0.5F) * 1.35F);
      float ring = 0.0F;
      int ring2 = 0;
      for (int k = 0; k < 8; k++) {
         int ax = k == 0 || k == 1 || k == 7 ? 2 : k == 3 || k == 4 || k == 5 ? -2 : 0;
         int az = k == 1 || k == 2 || k == 3 ? 2 : k == 5 || k == 6 || k == 7 ? -2 : 0;
         int rx = sx + ax;
         int rz = sz + az;
         if (Math.abs(rx - x) <= LIMIT + 1 && Math.abs(rz - z) <= LIMIT + 1) {
            ring += this.h[this.index(rx, rz)];
            ring2++;
         }
      }
      float gully = ring2 > 0 ? (ring / ring2 - this.h[srcIdx]) / 2.0F : 0.0F;
      // gullies funnel their rockfall: much more feed where the face is concave, so cones form at gully mouths
      feed = CoverNoise.clamp01(feed * 0.8F + 0.1F + Math.max(-0.2F, Math.min(0.6F, gully * 0.45F)));
      float strength = this.strong ? (rockLineOnly ? 0.75F : 1.0F) : 0.5F;
      // every rock face sheds a continuous apron (a base run length), chutes and gullies more; the province's debris
      // amount varies it within a narrower range, so an apron runs along the whole cliff base
      float amount = 0.75F + 0.4F * (debris - 0.55F);
      this.runout = amount * strength * (6.0F + 11.0F * feed);

      // where the deposit ends below this column: the runout, or sooner where the ground levels out
      int left = (int)Math.ceil(this.runout - this.dSrc);
      int toe = 0;
      if (left > 0) {
         this.startPath(x, z);
         for (int step = 1; step <= Math.min(left, LIMIT); step++) {
            int j = this.advance(x, z, -1.0F, 1);
            if (j < 0 || this.slope[j] > this.rockSteep) {
               break;
            }
            toe = step;
         }
      }
      this.sortR = this.dSrc / (float)(this.dSrc + Math.max(0, toe));
   }

   // ---------------------------------------------------------------- walking the fall line
   private float pX;
   private float pZ;
   private float dX;
   private float dZ;
   private int flatSteps;
   private int qX;
   private int qZ;
   private int at;

   private void startPath(int x, int z) {
      this.pX = x;
      this.pZ = z;
      this.dX = 0.0F;
      this.dZ = 0.0F;
      this.flatSteps = 0;
      this.qX = x;
      this.qZ = z;
      this.at = this.index(x, z);
   }

   /**
    * One block along the fall line from the current point, up (sign 1) or down (-1), staying within LIMIT of x/z. Where
    * the ground is too flat to tell, it looks at the height differences 3 and 6 blocks around (as far as the window allows),
    * then carries on in the same direction for up to maxFlat blocks. Returns the new window index, or -1 when the walk ends.
    */
   private int advance(int x, int z, float sign, int maxFlat) {
      int i = this.at;
      float ux = this.gradX(i);
      float uz = this.gradZ(i);
      float m = (float)Math.sqrt(ux * ux + uz * uz);
      if (m < 0.08F) {
         // too flat to tell at this scale (a cliff foot, a ledge): look wider, as far as the window allows
         int reach = LIMIT + 1 - Math.max(Math.abs(this.qX - x), Math.abs(this.qZ - z));
         for (int s = 3; s <= 6 && s <= reach; s += 3) {
            float inv = 0.5F / s;
            ux = (this.h[i + s] - this.h[i - s]) * inv;
            uz = (this.h[i + s * N] - this.h[i - s * N]) * inv;
            m = (float)Math.sqrt(ux * ux + uz * uz);
            if (m >= 0.06F) {
               break;
            }
         }
      }
      if (m < 0.06F) {
         // flat: keep going the same way for a few blocks (across a ledge or valley floor), or stop
         if (++this.flatSteps > maxFlat || this.dX == 0.0F && this.dZ == 0.0F) {
            return -1;
         }
      } else {
         ux = ux / m * sign;
         uz = uz / m * sign;
         if (this.dX != 0.0F || this.dZ != 0.0F) {
            // a little inertia, so the walk doesn't zigzag on the grid
            ux = ux * 0.75F + this.dX * 0.25F;
            uz = uz * 0.75F + this.dZ * 0.25F;
            float n = (float)Math.sqrt(ux * ux + uz * uz);
            if (n < 1.0E-4F) {
               return -1;
            }
            ux /= n;
            uz /= n;
         }
         this.dX = ux;
         this.dZ = uz;
      }
      this.pX += this.dX;
      this.pZ += this.dZ;
      this.qX = Math.round(this.pX);
      this.qZ = Math.round(this.pZ);
      if (Math.abs(this.qX - x) > LIMIT || Math.abs(this.qZ - z) > LIMIT) {
         return -1;
      }
      this.at = this.index(this.qX, this.qZ);
      return this.at;
   }

   /**
    * The ground a column gets, from the last {@link #trace}.
    *
    * @param steep    the column's own steepness (with FTF's noise) is above the scree steepness
    * @param coarse   the province's blockiness
    * @param green    the province's vegetation readiness
    * @param bare     the province's bare rock share
    * @param wet      how readily plants grow here: 0 cold or dry, 1 warm and wet
    * @param dry      dry land (fans always, no vegetation)
    * @param sediment FTF's sediment at the column, 0..1
    */
   int classify(boolean steep, float coarse, float green, float bare, float wet, boolean dry, float sediment) {
      float a = this.streakA;
      float b = this.streakB;
      // steep ground is only rubble when real rock above feeds it; below merely steep ground, a short apron
      if (this.dSrc > 0 && (this.strong || !steep)) {
         float r = this.dSrc / Math.max(0.01F, this.runout);
         // the angle of repose: debris runs out on slopes and stops sooner where the ground levels off (a fall under
         // ~0.35 blocks per block), so the apron thins out on flat ground instead of ending on a line
         if (this.fall < 0.35F) {
            r *= 1.0F + (0.35F - this.fall) * 1.6F;
         }
         // a ragged toe: tongues of debris reach further down the fall line, bays of grass reach up between them
         float toe = 1.0F + (b - 0.5F) * 0.35F;
         if (r <= toe) {
            r = Math.min(1.0F, r / toe);
            // vegetation takes the toe back from below (never the apex)
            if (!dry && wet > 0.0F) {
               if (veg(wet, green, this.sortR, b) > 0.32F) {
                  return VEG;
               }
            }
            // sorting: fine near the face, coarse at the toe, in bands with soft streaky edges (little noise, so the
            // grading reads)
            float size = 0.05F + 0.95F * Math.max(this.sortR, r * 0.8F) + (coarse - 0.5F) * 0.3F + (a - 0.5F) * 0.28F;
            int kind = size < 0.45F ? FINE : size < 0.75F ? MEDIUM : COARSE;
            if (kind != FINE && wet > 0.55F && veg(wet, green, this.sortR, b) > 0.12F) {
               return MOSSY;
            }
            return kind;
         }
         if (!steep && r <= 1.6F) {
            // the fan beyond the toe: fines where the land holds sediment
            float cover = (1.6F - r) / 0.6F * (dry ? 1.0F : CoverNoise.clamp01((sediment - 0.15F) * 3.0F + 0.25F));
            if (a < cover * 0.8F) {
               return FAN;
            }
            return KEEP;
         }
      }
      // frost-shattered scree fields: high up near and above the treeline, steep ground with no cliff above breaks up in
      // place into gravel and blocks (patches drawn out down the fall line, rock showing between them)
      if (this.frost > 0.0F && (steep || this.fall > 0.7F)) {
         float field = this.frost * (steep ? 1.0F : 0.7F) + (b - 0.5F) * 0.6F;
         if (field > 0.35F) {
            if (a > 0.78F) {
               return OUTCROP;
            }
            return this.streakC < 0.7F - this.frost * 0.15F ? FINE : MEDIUM;
         }
      }
      if (steep) {
         // steep ground with no rock face above: thin rocky soil, outcrops of the rock under it
         float rock = bare * 0.5F + (a - 0.5F) * 1.1F;
         if (rock > 0.38F) {
            return OUTCROP;
         }
         if (b < 0.3F + (1.0F - green) * 0.15F) {
            return THIN_GRAVEL;
         }
         if (b < 0.62F || dry || wet <= 0.0F) {
            return THIN_SOIL;
         }
         return KEEP;
      }
      return KEEP;
   }

   /**
    * Frost-shattered scree on high ground that FTF leaves as bare rock or that is rock already (above its rock line, or
    * the rock of a high peak's surface), where it is not a cliff: blockfields and scree fields of gravel and cobble in
    * patches drawn out down the fall line, with rock showing between them (KEEP) and as outcrops. Needs a trace and
    * {@link #frost}.
    */
   int screeField(int x, int z) {
      if (this.frost <= 0.0F) {
         return KEEP;
      }
      // scree lies in hollows and on the gentler ground; ribs, ridges and the steeper parts stay rock; the edges run
      // down the fall line (the streak noise is drawn out along it)
      float concave = CoverNoise.clamp01(this.hollowWide(x, z) / 3.0F + 0.5F);
      float gentle = 1.0F - CoverNoise.clamp01(this.fall / 1.4F);
      float f = this.frost * (0.25F + 0.45F * gentle + 0.45F * concave) + (this.streakB - 0.5F) * 0.45F;
      if (f < 0.5F) {
         return KEEP;
      }
      if (this.streakA > 0.8F) {
         return OUTCROP;
      }
      return this.streakC < 0.68F - this.frost * 0.15F ? FINE : MEDIUM;
   }

   /**
    * Format 18: frost-shattered scree on bare high-mountain ground that is not a cliff and has no cliff feeding it: fields
    * of gravel and cobble in large coherent areas (a field noise about 60 blocks across decides where, with soft
    * irregular edges drawn out down the fall line), more on gentle ground and in hollows, rock between the fields and as
    * ribs through them (convex ground). Needs a trace and {@link #frost}. FINE, MEDIUM, OUTCROP or KEEP (the rock).
    */
   int highScree(int x, int z) {
      if (this.frost <= 0.0F) {
         return KEEP;
      }
      float wide = this.hollowWide(x, z);
      float concave = CoverNoise.clamp01(wide / 3.0F + 0.5F);
      float gentle = 1.0F - CoverNoise.clamp01(this.fall / 1.2F);
      float big = 0.65F * plan(this.seed, x, z, 621, 60.0F) + 0.35F * plan(this.seed, x, z, 623, 20.0F);
      float f = this.frost * (0.1F + 0.35F * gentle + 0.3F * concave) + (big - 0.5F) * 1.6F + (this.streakB - 0.5F) * 0.15F;
      if (f < 0.42F) {
         return KEEP;
      }
      if (wide < -1.2F && this.streakA > 0.5F || this.streakA > 0.9F) {
         return OUTCROP;
      }
      return this.streakC < 0.62F ? FINE : MEDIUM;
   }

   /**
    * Smooth noise in plan view (0..1, features about size blocks across): gradient noise, so patches and streaks have no
    * preference for the axes (value noise gave square-cornered patches).
    */
   static float plan(long seed, float x, float z, int salt, float size) {
      return CoverNoise.noise3(seed, x / size, 0.43F, z / size, salt);
   }

   /** How far plants have taken the talus back here: from the toe up, by climate and province. */
   private static float veg(float wet, float green, float r, float b) {
      return wet * green * (r * 1.8F - 0.65F) + (b - 0.5F) * 0.55F;
   }

   /** Blocks by which the ground within 2 blocks (weighted towards the centre) lies above the column: + hollow, - ridge. */
   float hollow(int x, int z) {
      int c = this.index(x, z);
      float h0 = this.h[c];
      float sum = 0.0F;
      float weights = 0.0F;
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            float w = (float)Math.exp(-(dx * dx + dz * dz) / 2.5);
            float v = Math.max(h0 - 4.0F, Math.min(h0 + 4.0F, this.h[c + dz * N + dx]));
            sum += v * w;
            weights += w;
         }
      }
      return sum / weights - h0;
   }

   /** Blocks by which the ground 4 blocks around (8 points) lies above the column on average: + hollow, - rib. */
   float hollowWide(int x, int z) {
      int c = this.index(x, z);
      float sum = 0.0F;
      for (int k = 0; k < 8; k++) {
         int ax = k == 0 || k == 1 || k == 7 ? 3 : k == 3 || k == 4 || k == 5 ? -3 : 0;
         int az = k == 1 || k == 2 || k == 3 ? 3 : k == 5 || k == 6 || k == 7 ? -3 : 0;
         if (ax == 0 || az == 0) {
            ax = ax * 4 / 3;
            az = az * 4 / 3;
         }
         sum += this.h[c + az * N + ax];
      }
      return sum / 8.0F - this.h[c];
   }

   /** The lowest ground height (blocks) of the 8 columns around x/z. */
   float lowestNeighbour(int x, int z) {
      int c = this.index(x, z);
      float low = Float.MAX_VALUE;
      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            if (dx != 0 || dz != 0) {
               low = Math.min(low, this.h[c + dz * N + dx]);
            }
         }
      }
      return low;
   }

   /** True when the column's ground falls to the south (+z) by at least half a block per block, more than east or west. */
   boolean facesSouth() {
      return this.fall >= 0.5F && this.fallZ > 0.0F && Math.abs(this.fallZ) >= Math.abs(this.fallX);
   }
}

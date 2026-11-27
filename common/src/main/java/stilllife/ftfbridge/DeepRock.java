package stilllife.ftfbridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * rock_strata=deep: the regional rock through the whole body of the land, not only its bare skin. From format 20
 * rock_strata=bedded shows the same rock (this class's unit stacks, tilt and contacts) on bare ground, laid by the erosion
 * pass alone: everything below except the pass through the body.
 *
 * <ul>
 *   <li><b>Unit stacks</b>: each rock province is a stack of one to four rock units (suites): its usual suite as the main
 *       unit, in 40% of provinces a cap unit over its highest ground, and zero to two older units below (dolomite capped by
 *       limestone, over grey rock over dark rock, ...). The contacts are set relative to the regional ground level (the
 *       land smoothed over ~400 blocks, from FreeTerraForged's height model): the cap 50-130 blocks above it, the main
 *       unit's base 15-60 below it. They lie on the bed plane, so they tilt and fold with the beds, undulate ~30 blocks
 *       over a few hundred blocks, and interfinger as lenses over ~25 blocks: tall mountains change rock with height, deep
 *       valleys and caves cut into the older units, ordinary ground shows the main unit.</li>
 *   <li><b>Tilt by province</b>: flat-lying, dipping (a plane of 12-28 degrees about the province centre) or folded
 *       (folds 260-460 blocks across, 35-80 blocks high, along an axis), blended between provinces over ~440 blocks.</li>
 *   <li><b>Base</b>: below an undulating, interfingering contact some 15 blocks under the sea (+-30, tilted with half the
 *       beds' tilt up to +-40) the rock is plain stone again (the mining depths and the deepslate keep their vanilla look).</li>
 *   <li><b>The pass</b>: replaces minecraft:stone (nothing else) with the rock of the province at that spot, at the start of
 *       a chunk's LIGHT step. By then the chunk and all eight neighbours have finished their features (vanilla's LIGHT step
 *       needs its neighbours at INITIALIZE_LIGHT), so every carver, cave, ore, feature and structure that can touch the
 *       chunk has run against plain stone, and nothing writes into it afterwards: the result does not depend on the order
 *       chunks are made in, and ores are exactly where and what they would be. The top three blocks of a column whose
 *       ground is stone, or which lies under water, stay stone (Still Life's own rock surfaces, sea and river beds).</li>
 *   <li><b>Still Life's geology skin</b>: Still Life's surface rule (and Naturalized's version of it) lays ~20 blocks of
 *       andesite, tuff, basalt, granite, diorite (with Naturalized also chert, travertine, kaolins, white chalk) under the
 *       surface of every column. Below the top {@link #KEEP_SKIN} blocks under the ground the pass turns it into the
 *       regional rock too (not under water), so gentle ground keeps its look and cliffs show one rock, not a foreign band.</li>
 * </ul>
 * The skin laid by the erosion pass (GroundCover) uses the same function, so the two meet without a seam.
 */
final class DeepRock {
   private DeepRock() {
   }

   static final int FLAT = 0;
   static final int DIPPING = 1;
   static final int FOLDED = 2;

   /** Blocks of a stone-topped (or submerged) column's top that stay stone. */
   static final int KEEP_TOP = 3;

   /** At most this many provinces share a column (Provinces.blend gives 1-4 in practice). */
   static final int MAX_P = 9;

   /** Sea level and build top of the world (set by the first pass that sees the level; the same for the whole world). */
   static volatile int sea = 63;
   static volatile int top = 320;
   static volatile boolean known;

   static void world(int seaLevel, int maxBuildHeight) {
      if (!known || sea != seaLevel || top != maxBuildHeight) {
         sea = seaLevel;
         top = maxBuildHeight;
         known = true;
      }
   }

   /** Dev switch: -Dstilllife_ftf_bridge.strata_ns=false treats Nature's Spirit as absent for deep rock (comparisons). */
   static final boolean NS_ALLOWED = !"false".equals(System.getProperty("stilllife_ftf_bridge.strata_ns"));
   /** Dev switch: -Dstilllife_ftf_bridge.strata_create=false treats Create as absent for deep rock (comparisons). */
   static final boolean CREATE_ALLOWED = !"false".equals(System.getProperty("stilllife_ftf_bridge.strata_create"));
   static final boolean DEBUG = Boolean.getBoolean("stilllife_ftf_bridge.strata_debug");

   // ------------------------------------------------------------------ per province

   /** Tilt style of a province: flat 35%, dipping 30%, folded 35%. */
   static int style(Provinces.Province p, long seed) {
      float t = CoverNoise.hash(seed, p.id(), 7, 745);
      return t < 0.35F ? FLAT : t < 0.65F ? DIPPING : FOLDED;
   }

   /**
    * The province's rock units from the top down. The main unit is its usual suite (the rock most of its faces show),
    * except: pale provinces in drier country may be travertine (Nature's Spirit), some dark ones slate-like. Above it some
    * provinces have a cap unit on their highest ground (one step along the lightness ladder: tall mountains change rock with
    * height); below it come zero to two older units of similar value (deep valleys and caves cut into them); granitic and
    * andesitic (volcanic) bodies have no cap. Returns the units and whether the first is a cap.
    *
    * @param flags bit 0: kaolin clay rock may occur (dry, Nature's Spirit); bit 1: Nature's Spirit installed; bit 2: drier
    *              half of the climate (travertine); bit 3: Create installed (its limestone)
    */
   static Strata.Suite[] stack(Provinces.Province p, long seed, int flags) {
      boolean ns = (flags & 2) != 0;
      Strata.Suite t = Strata.suite(p, seed, (flags & 1) != 0);
      if ((t == Strata.Suite.LIMESTONE || t == Strata.Suite.DOLOMITE) && ns && (flags & 4) != 0
         && CoverNoise.hash(seed, p.id(), 5, 741) < 0.5F) {
         t = Strata.Suite.TRAVERTINE;
      } else if (t == Strata.Suite.DARK && CoverNoise.hash(seed, p.id(), 8, 749) < 0.35F) {
         t = Strata.Suite.SLATE;
      }
      Strata.Suite[] lower = switch (t) {
         case LIMESTONE -> new Strata.Suite[] {Strata.Suite.DOLOMITE, Strata.Suite.DARK};
         case TRAVERTINE -> new Strata.Suite[] {Strata.Suite.LIMESTONE, Strata.Suite.DOLOMITE};
         case DOLOMITE -> new Strata.Suite[] {Strata.Suite.GREY, Strata.Suite.DARK};
         case ANDESITIC -> new Strata.Suite[] {
            CoverNoise.hash(seed, p.id(), 9, 751) < 0.4F ? Strata.Suite.BASALT_FLOW : Strata.Suite.GREY, Strata.Suite.DARK};
         case GREY -> new Strata.Suite[] {Strata.Suite.DARK, Strata.Suite.SLATE};
         case DARK -> new Strata.Suite[] {Strata.Suite.SLATE};
         case SLATE -> new Strata.Suite[] {Strata.Suite.DARK, Strata.Suite.GREY};
         case KAOLIN_BUFF -> new Strata.Suite[] {ns ? Strata.Suite.TRAVERTINE : Strata.Suite.LIMESTONE, Strata.Suite.DOLOMITE};
         case KAOLIN_OCHRE -> new Strata.Suite[] {ns ? Strata.Suite.CHERT : Strata.Suite.DOLOMITE, Strata.Suite.DARK};
         default -> new Strata.Suite[0];
      };
      Strata.Suite cap = switch (t) {
         case GREY -> Strata.Suite.DOLOMITE;
         case DOLOMITE -> Strata.Suite.LIMESTONE;
         case LIMESTONE -> ns ? Strata.Suite.TRAVERTINE : null;
         case DARK -> Strata.Suite.GREY;
         case SLATE -> Strata.Suite.DARK;
         case KAOLIN_OCHRE -> Strata.Suite.KAOLIN_BUFF;
         default -> null;
      };
      if (CoverNoise.hash(seed, p.id(), 17, 771) >= 0.4F) {
         cap = null;
      }
      float n = CoverNoise.hash(seed, p.id(), 6, 743);
      int below = Math.min(lower.length, n < 0.25F ? 0 : n < 0.75F ? 1 : 2);
      Strata.Suite[] s = new Strata.Suite[(cap != null ? 1 : 0) + 1 + below];
      int k = 0;
      if (cap != null) {
         s[k++] = cap;
      }
      CAPPED.set(cap != null);
      s[k++] = t;
      System.arraycopy(lower, 0, s, k, below);
      if ((flags & 8) != 0) {
         // with Create installed, limestone units are Create's limestone (a calmer, layered pale rock than diorite)
         for (int i = 0; i < s.length; i++) {
            if (s[i] == Strata.Suite.LIMESTONE) {
               s[i] = Strata.Suite.LIMESTONE_C;
            }
         }
      }
      return s;
   }

   /** True when the province's first unit is a cap (see {@link #stack}: the same rule from the same hashes). */
   static boolean capped(Provinces.Province p, long seed, int flags) {
      CAPPED.set(Boolean.FALSE);
      stack(p, seed, flags);
      return CAPPED.get();
   }

   /** Set by {@link #stack} on the calling thread: whether the stack it returned starts with a cap. */
   private static final ThreadLocal<Boolean> CAPPED = ThreadLocal.withInitial(() -> Boolean.FALSE);

   /**
    * The contacts of a province, relative to the regional ground level R (the land height smoothed over ~400 blocks):
    * [0] the cap's base above R (blocks, 50-130), [1] the main unit's base below R (15-60), [2] the thickness of the first
    * older unit (50-120).
    */
   static float contact(Provinces.Province p, long seed, int which) {
      return switch (which) {
         case 0 -> 50.0F + 80.0F * CoverNoise.hash(seed, p.id(), 10, 757);
         case 1 -> 15.0F + 45.0F * CoverNoise.hash(seed, p.id(), 11, 759);
         default -> 50.0F + 70.0F * CoverNoise.hash(seed, p.id(), 18, 773);
      };
   }

   // ------------------------------------------------------------------ the regional ground level

   /** FreeTerraForged's generator context of the world (set by every pass that sees the level). */
   static volatile etcodehome.freeterraforged.world.worldgen.GeneratorContext ctx;
   private static final java.util.concurrent.ConcurrentHashMap<Long, Float> REGION = new java.util.concurrent.ConcurrentHashMap<>();
   private static final ThreadLocal<etcodehome.freeterraforged.world.worldgen.cell.Cell> CELL =
      ThreadLocal.withInitial(etcodehome.freeterraforged.world.worldgen.cell.Cell::new);
   static final float REGION_GRID = 192.0F;

   static void reset() {
      REGION.clear();
      ctx = null;
      known = false;
   }

   /**
    * The regional ground level at x/z: FreeTerraForged's terrain height (its model, before any blocks exist, the same in
    * every chunk and thread) averaged over 3 x 3 points 128 blocks apart around the nodes of a 192-block lattice, and
    * interpolated between them; never below the sea. Without FreeTerraForged: a third of the way from the sea to the top.
    */
   static float regional(int x, int z) {
      etcodehome.freeterraforged.world.worldgen.GeneratorContext c = ctx;
      if (c == null) {
         return sea + 0.3F * (top - sea);
      }
      float fx = x / REGION_GRID;
      float fz = z / REGION_GRID;
      int ix = (int)Math.floor(fx);
      int iz = (int)Math.floor(fz);
      float tx = fx - ix;
      float tz = fz - iz;
      float a = node(c, ix, iz);
      float b = node(c, ix + 1, iz);
      float cc = node(c, ix, iz + 1);
      float dd = node(c, ix + 1, iz + 1);
      return a + (b - a) * tx + (cc - a + (a - b - cc + dd) * tx) * tz;
   }

   private static float node(etcodehome.freeterraforged.world.worldgen.GeneratorContext c, int ix, int iz) {
      long key = (long)ix << 32 ^ iz & 0xFFFFFFFFL;
      Float v = REGION.get(key);
      if (v != null) {
         return v;
      }
      etcodehome.freeterraforged.world.worldgen.cell.Cell cell = CELL.get();
      float s = 0.0F;
      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            cell.reset();
            c.lookup.applyCell(cell, (int)(ix * REGION_GRID) + dx * 128, (int)(iz * REGION_GRID) + dz * 128, false, false);
            s += c.heightmap.levels().scale(cell.height);
         }
      }
      float r = Math.max(sea, s / 9.0F);
      REGION.put(key, r);
      return r;
   }
   /** The province's own tilt of the beds at x/z (blocks the bed plane is shifted by). */
   static float tilt(Provinces.Province p, long seed, int x, int z) {
      int st = style(p, seed);
      if (st == FLAT) {
         return 0.0F;
      }
      float th = CoverNoise.hash(seed, p.id(), 12, 753) * 6.2831855F;
      float cos = (float)Math.cos(th);
      float sin = (float)Math.sin(th);
      if (st == DIPPING) {
         // 12-28 degrees
         float s = 0.22F + 0.31F * CoverNoise.hash(seed, p.id(), 13, 761);
         return s * ((x - p.siteX()) * cos + (z - p.siteZ()) * sin);
      }
      float len = 260.0F + 200.0F * CoverNoise.hash(seed, p.id(), 14, 763);
      float amp = 35.0F + 45.0F * CoverNoise.hash(seed, p.id(), 15, 765);
      float across = (x * cos + z * sin) / len;
      float along = (-x * sin + z * cos) / (3.0F * len);
      return amp * 2.6F * (CoverNoise.noise3(seed, across, 0.5F + 37.0F * CoverNoise.hash(seed, p.id(), 16, 769), along, 767) - 0.5F);
   }

   // ------------------------------------------------------------------ per column

   /** What a column needs at every height (attached to Strata.Column with rock_strata=deep). */
   static final class Col {
      int n;
      Strata.Suite[][] stack = new Strata.Suite[MAX_P][];
      /** Per province: the unit contacts at this column, top down (bed-plane Y). */
      float[][] cut = new float[MAX_P][3];
      boolean[] capped = new boolean[MAX_P];
      /** The blended levels at this column (bed-plane Y): cap base, main unit base, first older unit base. */
      final float[] level = new float[3];
      float[][] bedded = new float[MAX_P][4];
      float[] bedScale = new float[MAX_P];
      boolean[] seams = new boolean[MAX_P];
      float[] upTo = new float[MAX_P];
      double offset;
      /** The regional ground level at the column (see {@link #regional}). */
      float region;
      float und;
      float base;
      int knot = Integer.MIN_VALUE;
      final float[] k0 = new float[5];
      final float[] k1 = new float[5];
      final CoverNoise.Cell3 bed = new CoverNoise.Cell3();
      final CoverNoise.Cell3 grain = new CoverNoise.Cell3();
      final CoverNoise.Cell3 lensP = new CoverNoise.Cell3();
      final CoverNoise.Cell3 lensP2 = new CoverNoise.Cell3();
      final CoverNoise.Cell3 lensU = new CoverNoise.Cell3();
      final CoverNoise.Cell3 lensB = new CoverNoise.Cell3();
      final CoverNoise.Cell3 seamA = new CoverNoise.Cell3();
      final CoverNoise.Cell3 seamB = new CoverNoise.Cell3();
      final CoverNoise.Cell3 dykeA = new CoverNoise.Cell3();
      final CoverNoise.Cell3 dykeB = new CoverNoise.Cell3();
      final CoverNoise.Cell3[] kn = {new CoverNoise.Cell3(), new CoverNoise.Cell3(), new CoverNoise.Cell3(), new CoverNoise.Cell3(), new CoverNoise.Cell3()};
   }

   private static final java.util.Set<Integer> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /**
    * Prepares c (already set by Strata.Column.set for the blend) for rock_strata=deep.
    *
    * @param flags per province of the blend, see {@link #stack}
    */
   static void setup(Strata.Column c, long seed, int x, int z, Provinces.Blend blend, int[] flags) {
      Col d = c.deepCache;
      if (d == null) {
         d = new Col();
         c.deepCache = d;
      }
      Provinces.Province[] ps = blend.provinces();
      // every province of the blend (where four meet, dropping one made a straight vertical step in the rock)
      int n = Math.min(MAX_P, ps.length);
      d.n = n;
      d.region = regional(x, z);
      for (int i = 0; i < n; i++) {
         Provinces.Province p = ps[i];
         Strata.Suite[] st = stack(p, seed, flags[i]);
         d.stack[i] = st;
         boolean capped = CAPPED.get();
         d.capped[i] = capped;
         float r = d.region;
         // this province's levels: cap base (far above the land where it has no cap), main unit base, first older unit base
         d.cut[i][0] = r + (capped ? contact(p, seed, 0) : 400.0F);
         d.cut[i][1] = r - contact(p, seed, 1);
         d.cut[i][2] = r - contact(p, seed, 1) - contact(p, seed, 2);
         boolean dipping = style(p, seed) == DIPPING;
         for (int u = 0; u < st.length; u++) {
            // steeply dipping beds read as stripes across stepped ground: fewer, thicker beds there
            d.bedded[i][u] = Strata.bedded(p, st[u], seed) * (dipping ? 0.75F : 1.0F);
         }
         d.bedScale[i] = (5.0F + 5.0F * CoverNoise.hash(seed, p.id(), 2, 735)) * (dipping ? 1.6F : 1.0F);
         d.seams[i] = CoverNoise.hash(seed, p.id(), 3, 737) < 0.6F;
         d.upTo[i] = blend.upTo()[i];
         if (DEBUG && LOGGED.add(p.id())) {
            StillLifeFtfBridge.LOG.info("deep rock province {} site {} {} style {} stack {} contacts at first column {} flags {}", p.id(),
               (int)p.siteX(), (int)p.siteZ(), style(p, seed), java.util.Arrays.toString(st), java.util.Arrays.toString(d.cut[i]), flags[i]);
         }
      }
      d.upTo[n - 1] = 1.0F;
      // the levels blended by the provinces' shares: continuous across borders, so where two provinces' rocks interfinger
      // both are at the same level of their stacks (no zebra of one province's older unit against the other's main unit)
      float prevW = 0.0F;
      d.level[0] = d.level[1] = d.level[2] = 0.0F;
      for (int i = 0; i < n; i++) {
         float w = d.upTo[i] - prevW;
         prevW = d.upTo[i];
         for (int k = 0; k < 3; k++) {
            d.level[k] += w * d.cut[i][k];
         }
      }
      // the tilt: the provinces' own, blended over a wide band so their beds bend into each other
      Provinces.Blend wide = Provinces.blend(seed, x, z, 220.0F);
      double off = 0.0;
      float prev = 0.0F;
      for (int i = 0; i < wide.provinces().length; i++) {
         float w = wide.upTo()[i] - prev;
         prev = wide.upTo()[i];
         off += w * tilt(wide.provinces()[i], seed, x, z);
      }
      float fold = (CoverNoise.noise3(seed, x / 130.0F, 0.71F, z / 130.0F, 723) - 0.5F) * 2.0F;
      d.offset = off + 9.0F * fold;
      d.und = (CoverNoise.value2(seed, x, z, 931, 420.0F) - 0.5F) * 56.0F;
      d.base = sea - 14 + (CoverNoise.value2(seed, x, z, 935, 260.0F) - 0.5F) * 60.0F;
      d.knot = Integer.MIN_VALUE;
      c.deep = d;
   }

   /** The smooth terms at knot height y: undulation, warp x, warp z, bed thickness factor, border tongue scale. */
   private static void knot(Strata.Column c, Col d, int y, float[] k) {
      long seed = c.seed;
      int x = c.x;
      int z = c.z;
      float und = (CoverNoise.noise3(d.kn[0], seed, x / 40.0F, y / 25.0F, z / 40.0F, 815) - 0.5F) * 6.0F;
      float ypf = (float)(y + d.offset + und);
      k[0] = und;
      k[1] = (CoverNoise.noise3(d.kn[1], seed, x / 110.0F, ypf / 40.0F, z / 110.0F, 851) - 0.5F) * 90.0F;
      k[2] = (CoverNoise.noise3(d.kn[2], seed, x / 110.0F, ypf / 40.0F, z / 110.0F, 853) - 0.5F) * 90.0F;
      k[3] = 0.55F + 0.9F * CoverNoise.noise3(d.kn[3], seed, x / 260.0F, ypf / 50.0F, z / 260.0F, 855);
      k[4] = 9.0F + 10.0F * CoverNoise.noise3(d.kn[4], seed, x / 300.0F, ypf / 80.0F, z / 300.0F, 835);
   }

   /**
    * The rock at height y of a prepared column (rock_strata=deep): Strata.STONE below the base. Smooth terms are linear
    * between knots every 4 blocks of height (a pure function of position, the same for the skin and the body).
    */
   static int rock(Strata.Column c, int y, float wet, float joint) {
      Col d = c.deep;
      long seed = c.seed;
      int x = c.x;
      int z = c.z;

      int y0 = Math.floorDiv(y, 4) * 4;
      if (d.knot != y0) {
         if (d.knot == y0 - 4) {
            System.arraycopy(d.k1, 0, d.k0, 0, 5);
         } else {
            knot(c, d, y0, d.k0);
         }
         knot(c, d, y0 + 4, d.k1);
         d.knot = y0;
      }
      float t = (y - y0) * 0.25F;
      float[] a = d.k0;
      float[] b = d.k1;
      float ypf = (float)(y + d.offset + (a[0] + (b[0] - a[0]) * t));
      float wx = x + a[1] + (b[1] - a[1]) * t;
      float wz = z + a[2] + (b[2] - a[2]) * t;
      float thick = a[3] + (b[3] - a[3]) * t;
      // base: plain stone below an undulating contact (an unconformity: it follows the tilt of the beds by half, wanders
      // ~30 blocks over a few hundred (by half, at most 40 blocks), and the two rocks interfinger as lenses over ~25 blocks)
      float tiltB = Math.max(-40.0F, Math.min(40.0F, 0.5F * (float)d.offset));
      if (y < d.base + tiltB + 16.0F) {
         float yb = y - tiltB
            + (CoverNoise.noise3(d.lensB, seed, wx / 120.0F, ypf / 6.0F, wz / 120.0F, 937) - 0.5F) * 32.0F;
         if (yb < d.base) {
            return Strata.STONE;
         }
      }
      // the level (0 cap, 1 main, 2 first older, 3 second older unit) from the blended levels: unit contacts on the bed
      // plane, undulating, interfingering as lenses near the contact
      float uy = ypf - d.und;
      float[] lv = d.level;
      for (int k = 0; k < 3; k++) {
         if (Math.abs(uy - lv[k]) < 11.0F) {
            uy += (CoverNoise.noise3(d.lensU, seed, wx / 180.0F, ypf / 10.0F, wz / 180.0F, 933) - 0.5F) * 20.0F;
            break;
         }
      }
      int level = uy > lv[0] ? 0 : uy > lv[1] ? 1 : uy > lv[2] ? 2 : 3;
      int i = 0;
      if (d.n > 1) {
         float f;
         if (level >= 2) {
            // the older units: a steep, wandering border between the provinces' bodies (like a fault or an intrusion's
            // edge), not tongues along the beds; tongues of one province's older unit in another's made zebra stripes
            // (a granitic province's stone against a grey one's slate-like rock) through every cave near a border
            // (a second octave wavy in height, so the border is not a straight vertical sheet)
            f = CoverNoise.noise3(d.lensP, seed, wx / 200.0F, ypf / 400.0F, wz / 200.0F, 839) * 0.65F
               + CoverNoise.noise3(d.lensP2, seed, wx / 50.0F, ypf / 45.0F, wz / 50.0F, 841) * 0.35F;
         } else {
            // the main unit and the cap: thick uneven tongues along the bed plane, as on the skin
            float tv = a[4] + (b[4] - a[4]) * t;
            f = CoverNoise.noise3(d.lensP, seed, wx / 200.0F, ypf / tv, wz / 200.0F, 831) * 0.8F
               + CoverNoise.noise3(d.lensP2, seed, wx / 70.0F, ypf / 5.0F, wz / 70.0F, 833) * 0.2F;
         }
         f = CoverNoise.clamp01(0.5F + (f - 0.5F) * 2.4F);
         i = d.n - 1;
         for (int k = 0; k < d.n - 1; k++) {
            if (f < d.upTo[k]) {
               i = k;
               break;
            }
         }
      }
      Strata.Suite[] st = d.stack[i];
      int unit = Math.min(d.capped[i] ? level : Math.max(0, level - 1), st.length - 1);
      Strata.Suite s = st[unit];
      float bedded = d.bedded[i][unit];
      int rock = s.main;
      if (bedded > 0.0F) {
         float tt = d.bedScale[i] * thick;
         float bb = CoverNoise.noise3(d.bed, seed, wx / 170.0F, ypf / tt, wz / 170.0F, 857);
         if (bb > 0.7F + 0.1F * (1.0F - bedded)) {
            rock = s.companion;
         }
         // (the broad lens test first: it is rarely passed, and the thin seam field changes cell almost every block)
         if (d.seams[i] && CoverNoise.noise3(d.seamB, seed, x / 80.0F, ypf / 15.0F, z / 80.0F, 861) > 0.6F
            && CoverNoise.noise3(d.seamA, seed, wx / 230.0F, ypf / 1.3F, wz / 230.0F, 859) > 0.84F) {
            rock = s.seam;
         }
      }
      if (s == Strata.Suite.GRANITIC
         // (straighter, rarer sheets than the skin-only rule: through the whole body the skin's dykes read as worm tracks)
         && Math.abs(CoverNoise.noise3(d.dykeA, seed, x / 110.0F, y / 220.0F, z / 110.0F, 863) - 0.5F) < 0.008F
         && CoverNoise.noise3(d.dykeB, seed, x / 150.0F, y / 80.0F, z / 150.0F, 865) > 0.66F) {
         rock = s.seam;
      }
      if (CoverNoise.noise3(d.grain, seed, x / 1.7F, y / 1.7F, z / 1.7F, 825) > 0.72F) {
         rock = rock == s.main ? s.second : rock == s.companion ? s.main : rock;
      }
      if (joint > 0.0F) {
         rock = Strata.joint(c, s, rock, y, wet, joint);
      }
      return rock;
   }

   // ------------------------------------------------------------------ the chunk pass

   private static final java.util.concurrent.atomic.AtomicLong NANOS = new java.util.concurrent.atomic.AtomicLong();
   private static final java.util.concurrent.atomic.AtomicLong CHUNKS = new java.util.concurrent.atomic.AtomicLong();
   private static final java.util.concurrent.atomic.AtomicLong BLOCKS = new java.util.concurrent.atomic.AtomicLong();

   /** Blocks under the ground surface that keep Still Life's geology skin (see the pass). */
   static final int KEEP_SKIN = 4;
   /** How far below the ground the pass looks for Still Life's geology skin (its rule reaches ~20 + surface depth). */
   static final int SKIN_DEPTH = 40;
   private static final java.util.concurrent.atomic.AtomicLong SKIN = new java.util.concurrent.atomic.AtomicLong();
   private static volatile java.util.Set<net.minecraft.world.level.block.Block> skinBlocks;

   /**
    * True for a block Still Life's or Naturalized's geology-skin rule lays (stone_depth floor offset 20: intrusion, volcanic
    * and sedimentary zones): andesite, diorite, granite, tuff, basalt, smooth basalt, and Nature's Spirit's chert,
    * travertine, kaolins and white chalk. Vanilla's own andesite/diorite/granite/tuff blobs within reach are taken too.
    */
   static boolean skin(BlockState s) {
      java.util.Set<net.minecraft.world.level.block.Block> set = skinBlocks;
      if (set == null) {
         set = new java.util.HashSet<>(java.util.List.of(Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF, Blocks.BASALT,
            Blocks.SMOOTH_BASALT));
         for (String n : new String[] {"chert", "travertine", "kaolin", "light_gray_kaolin", "gray_kaolin", "orange_kaolin",
            "brown_kaolin", "white_kaolin", "yellow_kaolin", "red_kaolin", "white_chalk"}) {
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
               .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("natures_spirit", n)).ifPresent(set::add);
         }
         skinBlocks = set;
      }
      return set.contains(s.getBlock());
   }

   /** At the start of a chunk's LIGHT step (see the class comment). */   static void lightStep(ServerLevel level, ChunkAccess chunk) {
      if (!(chunk instanceof ProtoChunk) || chunk instanceof ImposterProtoChunk || level == null) {
         return;
      }
      if (!GroundCover.deep() || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
         return;
      }
      long t0 = System.nanoTime();
      world(level.getSeaLevel(), level.getMaxBuildHeight());
      if (ctx == null) {
         ctx = FtfTerrain.context(level);
      }
      long seed = level.getSeed();
      int minY = chunk.getMinBuildHeight();
      LevelChunkSection[] sections = chunk.getSections();
      int lowest = sea - 14 - 30 - 40 - 16 - 1;
      int bx = chunk.getPos().getMinBlockX();
      int bz = chunk.getPos().getMinBlockZ();
      BlockState stone = Blocks.STONE.defaultBlockState();
      boolean[] hasStone = new boolean[sections.length];
      boolean any = false;
      for (int s = 0; s < sections.length; s++) {
         LevelChunkSection sec = sections[s];
         hasStone[s] = sec != null && !sec.hasOnlyAir() && sec.maybeHas(st -> st == stone);
         any |= hasStone[s];
      }
      if (!any) {
         return;
      }
      long changed = 0;
      long skinChanged = 0;
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = bx + lx;
            int z = bz + lz;
            // the ground: first solid block from the top that is not a tree, plant, snow or fluid
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            boolean wet = false;
            BlockState g = null;
            for (; y >= minY; y--) {
               BlockState st = sections[(y - minY) >> 4].getBlockState(lx, y & 15, lz);
               if (st.isAir()) {
                  continue;
               }
               if (!st.getFluidState().isEmpty()) {
                  wet = true;
                  if (st.is(Blocks.WATER) || st.is(Blocks.LAVA) || !st.blocksMotion()) {
                     continue;
                  }
               }
               if (!st.blocksMotion() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.MINEABLE_WITH_AXE)
                  || st.is(Blocks.SNOW_BLOCK)
                  || st.is(Blocks.POWDER_SNOW) || st.is(Blocks.ICE) || st.is(Blocks.PACKED_ICE)) {
                  continue;
               }
               g = st;
               break;
            }
            if (g == null) {
               continue;
            }
            int keepAbove = g == stone || wet ? y - KEEP_TOP : Integer.MAX_VALUE;
            Strata.Column col = null;
            int from = Math.max(minY, lowest);
            for (int yy = from; yy <= y; yy++) {
               int si = (yy - minY) >> 4;
               if (!hasStone[si]) {
                  yy = ((si + 1) << 4) + minY - 1;
                  continue;
               }
               if (yy > keepAbove) {
                  break;
               }
               LevelChunkSection sec = sections[si];
               if (sec.getBlockState(lx, yy & 15, lz) != stone) {
                  continue;
               }
               if (col == null) {
                  col = GroundCover.deepColumn(seed, x, z);
                  if (col == null) {
                     return;
                  }
               }
               int r = Strata.rock(col, yy, 0.0F, 0.0F);
               if (r != Strata.STONE) {
                  sec.setBlockState(lx, yy & 15, lz, GroundCover.rockBlock(r));
                  changed++;
               }
            }
            // Still Life's geology skin (its surface rule lays ~20 blocks of andesite, tuff, basalt, granite, diorite, and
            // with Naturalized chert, travertine, kaolins, white chalk, following the surface): kept in the top KEEP_SKIN
            // blocks under the ground (what gentle ground and small steps show), the regional rock below, so cliffs and
            // cuts show one rock instead of a foreign band. Not under water (sea and river beds keep their look).
            if (!wet) {
               for (int yy = y - KEEP_SKIN; yy >= Math.max(minY, y - SKIN_DEPTH); yy--) {
                  LevelChunkSection sec = sections[(yy - minY) >> 4];
                  BlockState st = sec.getBlockState(lx, yy & 15, lz);
                  if (!skin(st)) {
                     continue;
                  }
                  if (col == null) {
                     col = GroundCover.deepColumn(seed, x, z);
                     if (col == null) {
                        return;
                     }
                  }
                  BlockState rb = GroundCover.rockBlock(Strata.rock(col, yy, 0.0F, 0.0F));
                  if (rb != st) {
                     sec.setBlockState(lx, yy & 15, lz, rb);
                     changed++;
                     skinChanged++;
                  }
               }
            }         }
      }
      if (changed > 0) {
         chunk.setUnsaved(true);
      }
      long dt = System.nanoTime() - t0;
      long n = CHUNKS.incrementAndGet();
      NANOS.addAndGet(dt);
      BLOCKS.addAndGet(changed);
      SKIN.addAndGet(skinChanged);
      if (n == 1 || n % 2000 == 0) {
         StillLifeFtfBridge.LOG.info("deep rock: {} chunks, {} ms/chunk on average, {} blocks/chunk replaced (thread {})", n,
            String.format(java.util.Locale.ROOT, "%.2f", NANOS.get() / 1.0E6 / n), BLOCKS.get() / n, Thread.currentThread().getName());
      }
   }

   /** Totals so far (chunks, nanoseconds, blocks), logged when the server stops. */
   static void report() {
      long n = CHUNKS.get();
      if (n > 0) {
         StillLifeFtfBridge.LOG.info("deep rock total: {} chunks, {} ms/chunk on average, {} blocks/chunk replaced ({} of them Still Life skin rock)", n,
            String.format(java.util.Locale.ROOT, "%.2f", NANOS.get() / 1.0E6 / n), BLOCKS.get() / n, SKIN.get() / n);
      }
   }
}

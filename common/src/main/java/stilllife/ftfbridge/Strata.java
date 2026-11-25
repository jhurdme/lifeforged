package stilllife.ftfbridge;

/**
 * Bedrock with regional character: which rock shows where the ground is bare (cliffs, peaks above the rock line, the
 * risers of steep slopes). Pure Java (rocks are small ints; GroundCover maps them to blocks).
 *
 * <p>Real rock faces at this scale are either layered (beds long along the face and thin across it, gently dipping and
 * undulating, uneven, pinching out, low contrast between neighbouring beds, an occasional marker bed) or massive (one rock
 * with fine grain and joints). Neither has isolated blobs of another colour. So:
 * <ul>
 *   <li><b>Suites</b> ({@link Suite}): per {@link Provinces rock province} a main rock, its fine-grain variant, the rock of
 *       ordinary beds and a marker rock, chosen by measured colour distance (CIE Lab of the average texture colour): the
 *       grain variant within ~8, ordinary beds within ~13, markers (thin, uncommon) up to ~20.</li>
 *   <li><b>Massive</b> (andesitic, granitic; and every suite with rock_strata=regional): the main rock, its grain variant
 *       mixed in at a grain of 1-3 blocks, vertical joints.</li>
 *   <li><b>Beds</b> (rock_strata=bedded; sedimentary suites clearly, grey and dark weakly in half their provinces): a
 *       gradient-noise field long along the bed plane (~170 blocks) and thin across it (5-10 blocks, swelling and
 *       thinning over the face), on a bed plane that is tilted and folded (offset), undulates a few blocks, and wanders
 *       laterally (domain warp). Where the field is high the bed rock shows (~10-15%): beds of uneven thickness that
 *       pinch out. Markers: a much thinner field (1-2 blocks), rare, only in long lenses. Granitic rock gets rare thin
 *       steep dykes instead.</li>
 *   <li><b>Province borders</b>: the provinces' suites interfinger as thick uneven tongues along the bed plane over the
 *       transition zone.</li>
 * </ul>
 * Gradient noise throughout, so nothing lines up with a lattice. Everything is a function of seed and position: the same
 * in every chunk and thread.
 */
final class Strata {
   static final int STONE = 0;
   static final int ANDESITE = 1;
   static final int TUFF = 2;
   static final int DEEPSLATE = 3;
   static final int DIORITE = 4;
   static final int CALCITE = 5;
   static final int GRANITE = 6;
   static final int DRIPSTONE = 7;
   static final int BASALT = 8;
   /** Nature's Spirit travertine (diorite without it). */
   static final int TRAVERTINE = 9;
   static final int COBBLE = 10;
   static final int MOSSY_COBBLE = 11;
   static final int COBBLED_DEEPSLATE = 12;
   // Nature's Spirit kaolins (clay rock of dry regions; only chosen when Nature's Spirit is installed)
   static final int K_LIGHT_GRAY = 13;
   static final int K_GRAY = 14;
   static final int K_PLAIN = 15;
   static final int K_ORANGE = 16;
   static final int K_BROWN = 17;
   static final int K_WHITE = 18;
   /** Nature's Spirit chert (rock_strata=deep only; andesite without it). */
   static final int CHERT = 19;
   /** Nature's Spirit yellow kaolin (rock_strata=deep only; calcite without it). */
   static final int K_YELLOW = 20;
   /** Create's limestone (rock_strata=deep only; diorite without it). */
   static final int C_LIMESTONE = 21;
   static final int COUNT = 22;

   // how a suite's rock is built
   /** Massive: one rock, fine grain and joints only. */
   static final int MASSIVE = 0;
   /** Weakly bedded: some provinces show soft beds (only with rock_strata=bedded). */
   static final int WEAK = 1;
   /** Sedimentary: clear but soft beds (only with rock_strata=bedded). */
   static final int BEDDED = 2;

   /**
    * A bedrock suite. Colour distances are CIE Lab delta E of the blocks' average texture colours:
    * <ul>
    *   <li>{@code main}: the rock itself;</li>
    *   <li>{@code second}: its fine-grain variant, mixed in at a grain of 1-3 blocks (delta E under 8: the same rock up
    *       close);</li>
    *   <li>{@code companion}: the rock of ordinary beds, long and thin (delta E 4-13: shades of one rock at a distance);</li>
    *   <li>{@code seam}: marker beds, 1-2 blocks thick and uncommon (delta E up to ~20).</li>
    * </ul>
    */
   enum Suite {
      /** Grey (L 52): stone; grain andesite (4); beds andesite (4); tuff markers (8). */
      GREY(STONE, ANDESITE, ANDESITE, TUFF, WEAK),
      /** Andesite (L 57): andesite; grain stone (4); beds stone (4); tuff markers (12). Massive. */
      ANDESITIC(ANDESITE, STONE, STONE, TUFF, MASSIVE),
      /** The darkest (L 46): tuff; grain stone (8); beds stone (8); deepslate markers (13). */
      DARK(TUFF, STONE, STONE, DEEPSLATE, WEAK),
      /** Pale limestone (L 76): diorite; grain calcite (13); beds calcite (13); andesite markers (20). Bedded. */
      LIMESTONE(DIORITE, CALCITE, CALCITE, ANDESITE, BEDDED),
      /** Bedded grey (L 57): andesite; grain stone (4); beds tuff (12); diorite markers (20). */
      DOLOMITE(ANDESITE, TUFF, STONE, DIORITE, BEDDED),
      /** Granitic (L 52): stone; grain andesite (4); granite only as rare thin dykes (22). Massive. */
      GRANITIC(STONE, ANDESITE, ANDESITE, GRANITE, MASSIVE),
      /** Buff clay rock (L 58): light grey kaolin; beds grey kaolin (12, darker, same hue); white kaolin markers (17). */
      KAOLIN_BUFF(K_LIGHT_GRAY, K_GRAY, K_LIGHT_GRAY, K_WHITE, BEDDED),
      /** Ochre clay rock (L 51): kaolin; grain and beds orange kaolin (7); brown kaolin markers (20). */
      KAOLIN_OCHRE(K_PLAIN, K_ORANGE, K_ORANGE, K_BROWN, BEDDED),
      // ---- rock_strata=deep only (never chosen by the other modes)
      /** Warm limestone (L 63, Nature's Spirit): travertine; beds light grey kaolin (7); white kaolin markers (12). */
      TRAVERTINE(Strata.TRAVERTINE, K_LIGHT_GRAY, Strata.TRAVERTINE, K_WHITE, BEDDED),
      /** Chert (L 57, Nature's Spirit): chert; grain orange kaolin (4); beds kaolin (7); yellow kaolin markers (14). */
      CHERT(Strata.CHERT, K_PLAIN, K_ORANGE, K_YELLOW, BEDDED),
      /** Slate-like dark rock (L 34): deepslate; grain smooth basalt (4). Massive (tuff beds read as zebra stripes). */
      SLATE(DEEPSLATE, TUFF, BASALT, STONE, MASSIVE),
      /** Flood basalt (L 31): smooth basalt; grain deepslate (4). Massive. */
      BASALT_FLOW(BASALT, TUFF, DEEPSLATE, TUFF, MASSIVE),
      /** Limestone with Create (L 75): Create's limestone; grain diorite (7); beds calcite (15); andesite markers (20). */
      LIMESTONE_C(C_LIMESTONE, CALCITE, DIORITE, ANDESITE, BEDDED);

      final int main;
      /** The rock of ordinary beds. */
      final int companion;
      /** The fine-grain variant. */
      final int second;
      /** Marker beds (thin, uncommon). */
      final int seam;
      final int style;

      Suite(int main, int companion, int second, int seam, int style) {
         this.main = main;
         this.companion = companion;
         this.second = second;
         this.seam = seam;
         this.style = style;
      }
   }

   /**
    * Where a province stands on the lightness ladder, 0 (darkest) to 4 (palest): the regional lithology field at its
    * centre, nudged a little by its own roll, so neighbours are usually the same or one step apart.
    */
   static int level(Provinces.Province p) {
      float l = p.lightness() + (p.roll() - 0.5F) * 0.12F;
      return l < 0.2F ? 0 : l < 0.4F ? 1 : l < 0.6F ? 2 : l < 0.8F ? 3 : 4;
   }

   /**
    * The suite of a province, from its step on the lightness ladder: DARK, GREY or GRANITIC, ANDESITIC, DOLOMITE,
    * LIMESTONE. In dry regions with Nature's Spirit, about half are clay rock (buff on the lighter steps, ochre on the
    * darker ones, so their brightness still fits their neighbours).
    */
   static Suite suite(Provinces.Province p, long seed, boolean dryKaolin) {
      int level = level(p);
      if (dryKaolin && CoverNoise.hash(seed, p.id(), 4, 739) < 0.5F) {
         return level >= 2 ? Suite.KAOLIN_BUFF : Suite.KAOLIN_OCHRE;
      }
      return switch (level) {
         case 0 -> Suite.DARK;
         case 1 -> CoverNoise.hash(seed, p.id(), 0, 731) < 0.3F ? Suite.GRANITIC : Suite.GREY;
         case 2 -> Suite.ANDESITIC;
         case 3 -> Suite.DOLOMITE;
         default -> Suite.LIMESTONE;
      };
   }

   /**
    * How bedded a province is with rock_strata=bedded: sedimentary suites 0.65-1 (clear but soft beds), weakly bedded
    * suites 0 for half of their provinces and 0.25-0.5 for the rest, massive suites 0.
    */
   static float bedded(Provinces.Province p, Suite s, long seed) {
      float h = CoverNoise.hash(seed, p.id(), 1, 733);
      return switch (s.style) {
         case BEDDED -> 0.65F + 0.35F * h;
         case WEAK -> h < 0.5F ? 0.0F : 0.25F + 0.5F * (h - 0.5F);
         default -> 0.0F;
      };
   }

   /** The weathered (broken / mossy) form of a rock, for wet joints and coarse scree. */
   static int broken(int rock, boolean mossy) {
      return switch (rock) {
         case STONE, ANDESITE -> mossy ? MOSSY_COBBLE : COBBLE;
         case DEEPSLATE, BASALT -> COBBLED_DEEPSLATE;
         default -> rock;
      };
   }

   /** What a column needs for its rocks at every height: computed once per column. */
   static final class Column {
      long seed;
      int x;
      int z;
      boolean set;
      Suite suite;
      float bedded;
      /** Bed thickness scale of the province (blocks). */
      float bedScale;
      boolean seams;
      /** Tilt, folds and fault offsets of the beds at this column (blocks). */
      double offset;
      float joint;
      int colHash;

      int key;
      /** rock_strata=deep: the column's unit stacks, tilt and caches (null in every other mode). */
      DeepRock.Col deep;
      /** The thread's DeepRock.Col, reused from column to column. */
      DeepRock.Col deepCache;

      boolean is(long seed, int x, int z, int key) {
         return this.set && this.seed == seed && this.x == x && this.z == z && this.key == key;
      }

      /**
       * @param bedding   bedding lenses and seams ("bedded"); false = one mottled rock per province ("regional")
       * @param dryKaolin a dry climate with Nature's Spirit installed: the province may be kaolin clay rock
       */
      void set(long seed, int x, int z, Provinces.Province p, boolean bedding, boolean dryKaolin) {
         this.seed = seed;
         this.x = x;
         this.z = z;
         this.key = (bedding ? 1 : 0) | (dryKaolin ? 2 : 0);
         this.suite = suite(p, seed, dryKaolin);
         this.bedded = bedding ? bedded(p, this.suite, seed) : 0.0F;
         this.bedScale = 5.0F + 5.0F * CoverNoise.hash(seed, p.id(), 2, 735);
         this.seams = bedding && this.bedded > 0.0F && CoverNoise.hash(seed, p.id(), 3, 737) < 0.6F;
         this.offset = offset(seed, x, z);
         // gradient noise in plan view, so joint streaks are not square prisms lined up with the axes
         this.joint = CoverNoise.noise3(seed, x / 2.6F, 0.37F, z / 2.6F, 711);
         this.colHash = (int)(CoverNoise.hash(seed, x, z, 713) * 0x7FFFFF);
         this.suites = null;
         this.upTo = null;
         this.seamShare = 1.0F;
         this.deep = null;
         this.set = true;
      }

      /** The share of the column's provinces that have seams (fades seams out across a border). */
      float seamShare = 1.0F;

      /** Near a province border: the suites sharing the column, and their cumulative shares (null: one suite). */
      Suite[] suites;
      float[] upTo;

      /**
       * A column that may lie in the transition between provinces: their rocks interfinger over it.
       *
       * @param blend  the provinces sharing the column (Provinces.blend with Provinces.BLEND)
       * @param kaolin per province of the blend: a dry region with Nature's Spirit installed (may be clay rock)
       */
      void set(long seed, int x, int z, Provinces.Blend blend, boolean bedding, boolean[] kaolin, int key) {
         Provinces.Province[] ps = blend.provinces();
         // the main province (largest share) sets the column's own character
         int main = 0;
         for (int i = 1; i < ps.length; i++) {
            float wi = blend.upTo()[i] - blend.upTo()[i - 1];
            float wm = main == 0 ? blend.upTo()[0] : blend.upTo()[main] - blend.upTo()[main - 1];
            if (wi > wm) {
               main = i;
            }
         }
         this.set(seed, x, z, ps[main], bedding, kaolin[main]);
         this.key = key;
         if (ps.length > 1) {
            // everything blended by share, so nothing jumps where the main province changes
            Suite[] ss = new Suite[ps.length];
            boolean mixed = false;
            float bed = 0.0F;
            float scale = 0.0F;
            float seamShare = 0.0F;
            float prev = 0.0F;
            for (int i = 0; i < ps.length; i++) {
               float w = blend.upTo()[i] - prev;
               prev = blend.upTo()[i];
               ss[i] = suite(ps[i], seed, kaolin[i]);
               mixed |= ss[i] != this.suite;
               float bi = bedded(ps[i], ss[i], seed);
               bed += bi * w;
               scale += (5.0F + 5.0F * CoverNoise.hash(seed, ps[i].id(), 2, 735)) * w;
               seamShare += bi > 0.0F && CoverNoise.hash(seed, ps[i].id(), 3, 737) < 0.6F ? w : 0.0F;
            }
            if (mixed) {
               this.suites = ss;
               this.upTo = blend.upTo();
            }
            this.bedScale = scale;
            if (bedding) {
               this.bedded = bed;
               // seams of the provinces that have them, fading across the border with their share
               this.seams = seamShare > 0.0F;
               this.seamShare = seamShare;
            }
         }
      }
   }

   /**
    * The height the beds are shifted by at a column: a broad tilt (its slope is the dip: mostly gentle, steeper in
    * places, varying regionally) and gentle folds. Gradient noise only, so nothing lines up with a lattice; no faults.
    */
   static double offset(long seed, int x, int z) {
      float amp = CoverNoise.noise3(seed, x / 1400.0F, 0.31F, z / 1400.0F, 725);
      amp = 20.0F + 160.0F * amp * amp;
      float broad = (CoverNoise.noise3(seed, x / 480.0F, 0.53F, z / 480.0F, 721) - 0.5F) * 2.0F;
      float fold = (CoverNoise.noise3(seed, x / 130.0F, 0.71F, z / 130.0F, 723) - 0.5F) * 2.0F;
      return amp * broad + 9.0F * fold;
   }

   /**
    * The rock at height y of a prepared column.
    *
    * @param wet   0..1, wet joints weather to cobble and mossy cobble
    * @param joint how much jointing shows (1 on steep faces, 0 on gentle ground)
    */
   static int rock(Column c, int y, float wet, float joint) {
      if (c.deep != null) {
         return DeepRock.rock(c, y, wet, joint);
      }
      long seed = c.seed;
      int x = c.x;
      int z = c.z;
      // the bed plane: tilted and folded (offset), undulating a few blocks (smooth 3D noise)
      double yp = y + c.offset + (CoverNoise.noise3(seed, x / 40.0F, y / 25.0F, z / 40.0F, 815) - 0.5F) * 6.0F;
      float ypf = (float)yp;
      // along the bed plane the position wanders (domain warp), so beds curve, swell and pinch out instead of running
      // as straight sheets
      float wx = x + (CoverNoise.noise3(seed, x / 110.0F, ypf / 40.0F, z / 110.0F, 851) - 0.5F) * 90.0F;
      float wz = z + (CoverNoise.noise3(seed, x / 110.0F, ypf / 40.0F, z / 110.0F, 853) - 0.5F) * 90.0F;
      Suite s = c.suite;
      if (c.suites != null) {
         // across a province border: the other province's rock comes in as beds and lenses along the bed plane
         // (long and thin), thickening towards the border and thinning out away from it
         // thick, uneven tongues (their vertical scale swells and thins), not a fine alternation
         float tv = 9.0F + 10.0F * CoverNoise.noise3(seed, x / 300.0F, ypf / 80.0F, z / 300.0F, 835);
         float f = CoverNoise.noise3(seed, wx / 200.0F, ypf / tv, wz / 200.0F, 831) * 0.8F
            + CoverNoise.noise3(seed, wx / 70.0F, ypf / 5.0F, wz / 70.0F, 833) * 0.2F;
         f = CoverNoise.clamp01(0.5F + (f - 0.5F) * 2.4F);
         s = c.suites[c.suites.length - 1];
         for (int i = 0; i < c.suites.length - 1; i++) {
            if (f < c.upTo[i]) {
               s = c.suites[i];
               break;
            }
         }
      }
      int rock = s.main;
      if (c.bedded > 0.0F) {
         // beds: a field long along the bed plane (~170 blocks) and thin across it; its vertical scale swells and thins
         // over the face, so beds are uneven in thickness; where the field dips below the threshold a bed pinches out
         float t = c.bedScale * (0.55F + 0.9F * CoverNoise.noise3(seed, x / 260.0F, ypf / 50.0F, z / 260.0F, 855));
         float b = CoverNoise.noise3(seed, wx / 170.0F, ypf / t, wz / 170.0F, 857);
         if (b > 0.7F + 0.1F * (1.0F - c.bedded)) {
            rock = s.companion;
         }
         // marker beds: thin (1-2 blocks), uncommon, and only in long lenses
         if (c.seams && CoverNoise.noise3(seed, wx / 230.0F, ypf / 1.3F, wz / 230.0F, 859) > 0.84F
            && CoverNoise.noise3(seed, x / 80.0F, ypf / 15.0F, z / 80.0F, 861) > 1.0F - 0.4F * c.seamShare) {
            rock = s.seam;
         }
      }
      // granitic rock (rock_strata=bedded): rare thin steep dykes instead of beds
      if (s == Suite.GRANITIC && (c.key & 1) != 0
         && Math.abs(CoverNoise.noise3(seed, x / 45.0F, y / 90.0F, z / 45.0F, 863) - 0.5F) < 0.015F
         && CoverNoise.noise3(seed, x / 150.0F, y / 80.0F, z / 150.0F, 865) > 0.62F) {
         rock = s.seam;
      }
      // fine grain within the rock: its close variant at a grain of 1-3 blocks
      if (CoverNoise.noise3(seed, x / 1.7F, y / 1.7F, z / 1.7F, 825) > 0.72F) {
         rock = rock == s.main ? s.second : rock == s.companion ? s.main : rock;
      }
      // subtle vertical joints: the close variant, or cobble and mossy cobble where it is wet
      if (joint > 0.0F) {
         float along = value1(seed, c.colHash, y / 11.0F);
         float j = c.joint * 0.65F + along * 0.35F;
         if (j > 1.0F - joint * 0.2F) {
            boolean weather = wet > 0.0F && value1(seed, c.colHash ^ 0x5BD1E995, y / 5.0F) < wet * 0.6F;
            rock = weather ? broken(rock, wet > 0.5F) : rock == s.second ? s.main : s.second;
         }
      }
      return rock;
   }

   /** rock()'s vertical joints, for DeepRock (the same rule). */
   static int joint(Column c, Suite s, int rock, int y, float wet, float joint) {
      long seed = c.seed;
      float along = value1(seed, c.colHash, y / 11.0F);
      float j = c.joint * 0.65F + along * 0.35F;
      if (j > 1.0F - joint * 0.2F) {
         boolean weather = wet > 0.0F && value1(seed, c.colHash ^ 0x5BD1E995, y / 5.0F) < wet * 0.6F;
         rock = weather ? broken(rock, wet > 0.5F) : rock == s.second ? s.main : s.second;
      }
      return rock;
   }

   /** One-dimensional value noise along t, for line key. */
   private static float value1(long seed, int key, float t) {
      int i = (int)Math.floor(t);
      float f = CoverNoise.smooth(t - i);
      float a = CoverNoise.hash(seed, key, i, 717);
      float b = CoverNoise.hash(seed, key, i + 1, 717);
      return a + (b - a) * f;
   }

   private Strata() {
   }
}

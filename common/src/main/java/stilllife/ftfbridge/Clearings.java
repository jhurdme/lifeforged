package stilllife.ftfbridge;

/**
 * forest_variety's natural clearings (format 20): patches of land the tree features skip.
 *
 * <p>6.0.0 thresholded one octave of value noise on a 40-block lattice at {@code 1 - share}. Value noise is not spread
 * evenly over 0..1 and peaks on its lattice points, so a share of 0.1 gave about 3% of the land, in holes 10-18 blocks
 * across that sat on a 40-block grid. This is gradient noise in two sizes (no lattice preference; a slice of
 * {@link CoverNoise#noise3}), with a slightly ragged edge, and the threshold comes from the measured spread of the
 * field, so the share asked for is the share of the land: at 0.1, clearings mostly about 40 blocks across, up to about
 * 95 (measured over 5.8 km2 on three seeds; the thresholds agree within 0.002 between seeds).
 */
final class Clearings {
   private static final float SIZE = 48.0F;
   private static final float DETAIL = 20.0F;
   // the share of the field above each threshold (measured)
   private static final float[] SHARE = {0.0F, 0.02F, 0.05F, 0.1F, 0.15F, 0.2F, 0.3F, 0.4F, 0.5F};
   private static final float[] THRESHOLD = {1.0F, 0.793F, 0.7426F, 0.6938F, 0.6591F, 0.6306F, 0.5824F, 0.54F, 0.5F};

   private Clearings() {
   }

   /** True when this column lies in a clearing; `share` of the land does. */
   static boolean at(long seed, int x, int z, float share) {
      return field(seed, x, z) > threshold(share);
   }

   private static float field(long seed, int x, int z) {
      float broad = CoverNoise.noise3(seed, x / SIZE, 0.37F, z / SIZE, 171);
      float detail = CoverNoise.noise3(seed, x / DETAIL + 17.3F, 0.71F, z / DETAIL - 5.1F, 172);
      return 0.7F * broad + 0.3F * detail + (CoverNoise.hash(seed, x, z, 173) - 0.5F) * 0.03F;
   }

   /** The field is symmetric around 1/2, so shares above a half mirror the table. */
   static float threshold(float share) {
      if (share <= 0.0F) {
         return 2.0F;
      }
      if (share > 0.5F) {
         return 1.0F - threshold(1.0F - Math.min(share, 1.0F));
      }
      for (int i = 1; i < SHARE.length; i++) {
         if (share <= SHARE[i]) {
            return THRESHOLD[i - 1] + (THRESHOLD[i] - THRESHOLD[i - 1]) * (share - SHARE[i - 1]) / (SHARE[i] - SHARE[i - 1]);
         }
      }
      return 0.5F;
   }
}

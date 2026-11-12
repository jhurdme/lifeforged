package stilllife.ftfbridge;

/**
 * FreeTerraForged's continental uplift without its coastal cliffs.
 *
 * <p>On UPLIFT continents FTF raises the land (and its water table) towards plate borders, but within a band along every coast
 * it forces the uplift down to almost nothing (a "custom peak" taken from the distance to the coast). Where raised land meets
 * that band the uplift drops at once from its full value to nearly zero, so a cliff of 40-100 blocks runs parallel to the coast
 * for kilometres (the long "plateau ridges"). Here the cap rises gradually instead: from the band's edge it climbs back to full
 * uplift over uplift_smoothing blocks, so the cliff becomes a slope. Land seaward of the old cliff and everything farther inland
 * is unchanged; plates and coastlines are untouched.
 */
public final class UpliftSmoothing {
   /** FTF's coastal band: custom peak values below this replace the uplift. */
   private static final float BAND = 0.05F;

   private UpliftSmoothing() {
   }

   /**
    * The water-table value to store, given FTF's value and its coastal custom peak.
    *
    * @param frequency the continent generator's frequency (1 / tectonic scale)
    * @param water     sea level as a height fraction (levels.water)
    */
   public static float waterTable(float value, float customPeak, float frequency, float water) {
      if (!(customPeak >= BAND) || !BridgeConfig.enabled()) {
         return value;
      }

      double ramp = WorldLock.activeSettings().upliftSmoothing();
      if (!(ramp > 0.0)) {
         return value;
      }

      // custom peak per block: the coast distance (in continent cells) maps 0.05..0.25 onto edge 0..1, then above sea level onto 0..1
      float perBlock = 5.0F * frequency / (1.0F - water);
      float cap = BAND + (customPeak - BAND) * (float)((1.0F - BAND) / (perBlock * ramp));
      return Math.min(value, cap);
   }
}

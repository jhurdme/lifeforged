package stilllife.ftfbridge;

/**
 * Settings a world is generated with. Saved per world in stilllife_ftf_bridge.json, so editing the
 * properties file never changes chunks of an existing world.
 *
 * @param stillLifeLatitude   true = Still Life's latitude temperature, false = FreeTerraForged's temperature
 * @param poleDistance        blocks from the equator to the coldest band (still_life latitude only)
 * @param americanShare       share of FTF continents that get Still Life's American biome set
 * @param humidSwamps         very humid, low, flat land gets Still Life's swamp/marsh climate even outside FTF wetlands
 * @param swampHumidity       FTF humidity (-1..1) at and above which humid lowland counts as swamp land
 * @param swampMaxHeight      highest ground, on Lithosphere's height scale (sea = 63), that can become swamp land
 * @param snowCaps            keep Still Life's snow blocks on peaks at every altitude (FTF erosion only bares cliffs)
 * @param snowCliffSteepness  steepness above which snow is eroded off; 0 or less = FTF's own rock steepness
 * @param paleGarden          VanillaBackport Pale Garden prevalence (1 = standard layout, 0 = none, up to 2)
 * @param dappledForest       VanillaBackport Dappled Forest prevalence
 * @param sulfurCaves         VanillaBackport Sulfur Caves prevalence
 * @param aspenSwamps         Still Life aspen swamps on cool, fairly dry lowland (American continents): 0 = none, 1 = about
 *                            a quarter of that land, up to 2
 * @param saltLakes           Still Life salt lakes on warm-to-hot dry lowland far inland (American continents), same scale
 * @param wetlandPools        shallow pools of standing water in Still Life's swamps, marshes, bogs and other wetlands at any
 *                            height (Still Life only floods them at sea level, where Lithosphere's terrain puts them)
 * @param upliftSmoothing     blocks over which continental uplift climbs back from FTF's coastal band (instead of a cliff); 0 = FTF's cliff
 * @param wetlandMaxHeight    FTF wetlands leave ground more than this many blocks above their water alone (and fade out above half
 *                            of it), so they never dig pits into hillsides; 0 = FTF's own wetlands
 * @param keepWetlandBiomes   wetland water keeps a Still Life wetland biome FTF 1.0.0027+ does not recognise as a wetland
 * @param sulfurRock          sulfur and cinnabar rock in VanillaBackport's Sulfur Caves
 * @param beachMaxHeight      coastal land more than this many blocks above the sea is never a beach biome; 0 = no limit
 * @param beachShallows       sea up to this many blocks deep (not deep ocean) gets beach biomes; 0 = ocean biomes everywhere
 * @param saltLakeWater       share of a salt lake's flat ground covered by one block of water over calcite (wetland_pools only)
 * @param saltLakeMaxRelief   salt lakes only on ground that varies by at most this many blocks over 32 (aspen swamps: 4;
 *                            5.1 test builds and older worlds: 4)
 * @param saltLakeSlopePools  on salt-lake ground that is not one level for 6 blocks around, small round pools instead of
 *                            wide sheets (which would only lie in bands along each 1-block step)
 * @param biomeSetBlend       blocks over which the Still Life biome set (American / Afro-Eurasian) blends across a border between
 *                            two FTF continents, so the switch is an irregular, dithered edge; 0 = a sharp switch on the border
 * @param peakLineVariation   blocks by which the height where the high-mountain biome bands start (alpine, then snowy peaks)
 *                            wanders: up and down across a range, lower in gullies, higher on ridges; 0 = a level line
 * @param snowCapFade         lowest this many blocks of a snowy peaks cap get snow only in patches that thin out downwards,
 *                            bare rock between (snow_caps only); 0 = solid snow down to the cap's edge
 * @param biomeSets           how Still Life's two biome sets are laid out: "continents" (one per FTF continent),
 *                            "regions" (regions of biomeSetRegionSize, several per continent) or "halves" (each continent
 *                            American in the west, Afro-Eurasian in the east)
 * @param biomeSetRegionSize  regions mode: size of one region, in percent of the land of an average FTF continent
 */
record Settings(
   boolean stillLifeLatitude,
   double poleDistance,
   double americanShare,
   boolean humidSwamps,
   double swampHumidity,
   double swampMaxHeight,
   boolean snowCaps,
   double snowCliffSteepness,
   double paleGarden,
   double dappledForest,
   double sulfurCaves,
   double aspenSwamps,
   double saltLakes,
   boolean wetlandPools,
   double upliftSmoothing,
   double wetlandMaxHeight,
   boolean keepWetlandBiomes,
   boolean sulfurRock,
   double beachMaxHeight,
   double beachShallows,
   double saltLakeWater,
   double saltLakeMaxRelief,
   boolean saltLakeSlopePools,
   double biomeSetBlend,
   double peakLineVariation,
   double snowCapFade,
   String biomeSets,
   double biomeSetRegionSize
) {
   static final String SETS_CONTINENTS = "continents";
   static final String SETS_REGIONS = "regions";
   static final String SETS_HALVES = "halves";

   /** A biome_sets value as written in a file: continents, regions or halves (anything else: continents). */
   static String biomeSetsMode(String value) {
      if (value == null) {
         return SETS_CONTINENTS;
      }
      String v = value.trim().toLowerCase(java.util.Locale.ROOT);
      return v.equals(SETS_REGIONS) || v.equals(SETS_HALVES) ? v : SETS_CONTINENTS;
   }

   /** Defaults for a fresh properties file: every v4 improvement on. */
   static final Settings DEFAULT = new Settings(true, 40000.0, 0.5, true, 0.7, 80.0, true, 0.0, 0.18, 0.5, 0.5, 1.0, 1.0, true, 1600.0, 48.0, true, true, 8.0, 4.0, 0.35, 2.0, true, 384.0, 40.0, 24.0, SETS_CONTINENTS, 25.0);

   /** What a world saved by bridge 3.x was generated with: none of the v4 changes. */
   static Settings legacy(boolean stillLifeLatitude, double poleDistance, double americanShare) {
      return new Settings(stillLifeLatitude, poleDistance, americanShare, false, 0.5, 84.0, false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, 0.0, 0.0, true, true, 0.0, 0.0, 0.0, 4.0, false, 0.0, 0.0, 0.0, SETS_CONTINENTS, 25.0);
   }
}

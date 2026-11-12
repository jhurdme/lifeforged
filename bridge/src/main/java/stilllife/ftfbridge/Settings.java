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
 * @param riverHeadwaters     rivers start below mountains instead of cutting through them
 * @param riverMountainHeight blocks above a river's water level that count as a mountain on its course
 * @param riverFadeIn         blocks over which a shortened river fades in below the mountains
 * @param riverFlankMaxCut    most blocks a river valley may lower land away from the river (and how high above the water a
 *                            wetland may reach); 0 = unlimited (FTF default)
 * @param riverFlankSlope     beside the river the limit relaxes along this slope (blocks up per block out from the bank),
 *                            so valley sides become an even slope instead of a wall
 * @param paleGarden          VanillaBackport Pale Garden prevalence (1 = standard layout, 0 = none, up to 2)
 * @param dappledForest       VanillaBackport Dappled Forest prevalence
 * @param sulfurCaves         VanillaBackport Sulfur Caves prevalence
 * @param aspenSwamps         Still Life aspen swamps on cool, fairly dry lowland (American continents): 0 = none, 1 = about
 *                            a quarter of that land, up to 2
 * @param saltLakes           Still Life salt lakes on warm-to-hot dry lowland far inland (American continents), same scale
 * @param saltLakesOnLakes    FreeTerraForged lakes (and their shores) in the salt-lake climate become salt lakes, on both
 *                            continent sets (Still Life itself only has salt lakes in its American set)
 * @param wetlandPools        shallow pools of standing water in Still Life's swamps, marshes, bogs and other wetlands at any
 *                            height (Still Life only floods them at sea level, where Lithosphere's terrain puts them)
 * @param lakeFill            FreeTerraForged lake beds are shaped to the full width FTF clears for the lake, so the water reaches
 *                            the foot of the bank instead of leaving a dry shelf at water level around it
 * @param lakeMinDepth        least water depth (blocks) over a filled lake's bed, right up to the bank (lake_fill only)
 * @param upliftSmoothing     blocks over which continental uplift climbs back from FTF's coastal band (instead of a cliff); 0 = FTF's cliff
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
   boolean riverHeadwaters,
   double riverMountainHeight,
   double riverFadeIn,
   double riverFlankMaxCut,
   double riverFlankSlope,
   double paleGarden,
   double dappledForest,
   double sulfurCaves,
   double aspenSwamps,
   double saltLakes,
   boolean saltLakesOnLakes,
   boolean wetlandPools,
   boolean lakeFill,
   double lakeMinDepth,
   double upliftSmoothing
) {
   /** Defaults for a fresh properties file: every v4 improvement on. */
   static final Settings DEFAULT = new Settings(true, 40000.0, 0.5, true, 0.7, 80.0, true, 0.0, true, 160.0, 192.0, 48.0, 1.0, 0.18, 0.5, 0.5, 1.0, 1.0, true, true, true, 2.0, 1600.0);

   /** What a world saved by bridge 3.x was generated with: none of the v4 changes. */
   static Settings legacy(boolean stillLifeLatitude, double poleDistance, double americanShare) {
      return new Settings(stillLifeLatitude, poleDistance, americanShare, false, 0.5, 84.0, false, 0.0, false, 160.0, 192.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, false, false, 1.0, 0.0);
   }
}

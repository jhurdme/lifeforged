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
 * @param aspenSwamps         Still Life aspen swamps on cool, fairly dry lowland (American continents): 0 = none, 1 = about
 *                            a quarter of that land, up to 2
 * @param saltLakes           Still Life salt lakes on warm-to-hot dry lowland far inland (American continents), same scale
 * @param wetlandPools        shallow pools of standing water in Still Life's swamps, marshes, bogs and other wetlands at any
 *                            height (Still Life only floods them at sea level, where Lithosphere's terrain puts them)
 * @param upliftSmoothing     blocks over which continental uplift climbs back from FTF's coastal band (instead of a cliff); 0 = FTF's cliff
 * @param wetlandMaxHeight    FTF wetlands leave ground more than this many blocks above their water alone (and fade out above half
 *                            of it), so they never dig pits into hillsides; 0 = FTF's own wetlands
 * @param keepWetlandBiomes   wetland water keeps a Still Life wetland biome FTF 1.0.0027+ does not recognise as a wetland
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
 * @param biomeSets           how Still Life's two biome sets are laid out: "continents" (one per landmass: FTF continents
 *                            joined by continent bridges count as one), "cells" (one per FTF continent cell, the layout
 *                            before format 17),
 *                            "regions" (regions of biomeSetRegionSize, several per continent) or "halves" (each continent
 *                            American in the west, Afro-Eurasian in the east)
 * @param biomeSetRegionSize  regions mode: size of one region, in percent of the land of an average FTF continent
 * @param caveFloorGuard      where Still Life's ground blocks (snow blocks, sand, mud ...) may go, for caves FreeTerraForged
 *                            leaves in a surface biome: "exact" (only ground open to the sky or under standing water),
 *                            "vanilla" (Minecraft's above_preliminary_surface check: misses pockets near the surface) or
 *                            "off" (Still Life's own rules: cave floors get them too)
 * @param columnDepth         cave biome depth as the share of the column below the surface (mapped onto the Lithosphere
 *                            column of the same Still Life height); false = FTF's depth read as 1/128 per block (5.0-5.4)
 * @param mountainCooling     how much cooler (Still Life temperature) the climate is for choosing the biome of the alpine band.
 *                            Format 18 (snowCurve): the whole alpine band at once, and only under a snow cap and only into a
 *                            cold alpine biome (tag stilllife_ftf_bridge:cold_alpine); formats 15-17: ramped up from the
 *                            alpine band to the snowy peaks band (5.6 worlds: from inside the highland band); 0 = Still
 *                            Life's own bands
 * @param naturalSnowLayers   with FTF's smooth snow layers on: the bridge sets each spot's snow depth from the real ground
 *                            (drifts against steps and in hollows, thin on ridges and steep slopes, none on cliffs)
 * @param mountainSnow        snow ground shaped by wind (by latitude), sun (south faces bare higher) and gullies / hollows
 *                            (couloirs and late patches below the snow line), more or less by the biome's climate
 * @param snowLineClimate     worlds before format 18, latitude=still_life: blocks by which the high-mountain bands (alpine,
 *                            snowy peaks) rise at the equator and drop at the cold end; 0 = the same height everywhere
 *                            (format 18 worlds use snowLineEquator / snowLineTundra instead, and keep this 0)
 * @param forestVariety       natural clearings in forests (6.0.0 worlds: also regional tree mixes; 6.0.0 had belts at
 *                            forest edges too)
 * @param shores              rocky shores, mud flats, lake shores with reeds and lily pads, dune grass
 * @param groundCover         the bridge picks FTF's scree, talus and thin-soil blocks to suit the climate, and lays
 *                            rockfall gravel and boulders at the foot of cliffs, instead of FTF's one generic mix
 * @param rockStrata          ground_cover only: what bare rock is made of: "off" (the stone the world has), "bedded" (the
 *                            rock of the column's rock province, in beds, where rock is bare) or "deep" (the same rock
 *                            through the body of the land too). From format 20 bedded and deep use the same rocks
 *                            (DeepRock's unit stacks). "regional" (6.0.0: one mottled rock per province, no beds) only
 *                            exists in worlds saved with it
 * @param snowLineEquator     latitude=still_life (format 18): the snow line at the equator, in blocks (Y). With
 *                            snowLineTundra it gives each place its snow line from Still Life's temperature there: linear
 *                            from the warm edge of the tundra (temperature -0.56) to the equator (1), then falling to the sea
 *                            at the cold edge of the tundra (-0.8). The mountain biome bands are placed under it (highlands
 *                            stretched, alpine band under the line, snowy or bare peaks above). 0 = the bands of format
 *                            17 and older (snowLineClimate, the ramped mountainCooling)
 * @param snowLineTundra      latitude=still_life (format 18): the snow line at the warm edge of the tundra, in blocks (Y)
 *                            (both: AUTO = worked out from the world's measured land heights when the world is created,
 *                            format 19)
 * @param ftfCurve            latitude=ftf worlds made with format 19 or later: the snow-line curve, its bands and snow zone
 *                            from FreeTerraForged's own temperature at sea level (format 18 and older ftf worlds: false)
 * @param paleGarden          VanillaBackport Pale Garden amount (1 = the standard band, 0 = none, up to 2; 5.x default 0.18)
 * @param dappledForest       VanillaBackport Dappled Forest amount (5.x default 0.5)
 * @param sulfurCaves         VanillaBackport Sulfur Caves amount (5.x default 0.5)
 * @param sulfurRock          sulfur and cinnabar rock in VanillaBackport's Sulfur Caves
 * @param coastalMoistureReach blocks from the sea over which FreeTerraForged's Coastal Moisture Boost fades out (format
 *                            20, CoastalMoisture); 0 = FTF's own boost, which wets all low land at any distance
 * @param coastalBiomes       low coastal land past the beach gets Still Life's coast-only biomes (format 20, CoastalBiomes)
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
   double aspenSwamps,
   double saltLakes,
   boolean wetlandPools,
   double upliftSmoothing,
   double wetlandMaxHeight,
   boolean keepWetlandBiomes,
   double beachMaxHeight,
   double beachShallows,
   double saltLakeWater,
   double saltLakeMaxRelief,
   boolean saltLakeSlopePools,
   double biomeSetBlend,
   double peakLineVariation,
   double snowCapFade,
   String biomeSets,
   double biomeSetRegionSize,
   String caveFloorGuard,
   boolean columnDepth,
   double mountainCooling,
   boolean naturalSnowLayers,
   boolean groundCover,
   boolean mountainSnow,
   double snowLineClimate,
   boolean shores,
   boolean forestVariety,
   String rockStrata,
   double snowLineEquator,
   double snowLineTundra,
   boolean ftfCurve,
   double paleGarden,
   double dappledForest,
   double sulfurCaves,
   boolean sulfurRock,
   double coastalMoistureReach,
   boolean coastalBiomes
) {
   /** snow_line_equator / snow_line_tundra "auto": resolved from the world's measured land heights (format 19). */
   static final double AUTO = -1.0;

   /**
    * True when this world places its mountain bands and snow line from the snow-line curve (format 18, latitude=still_life):
    * Variant S of the 6.0 mountain rework. False: the bands of format 17 and older.
    */
   boolean snowCurve() {
      return (this.stillLifeLatitude || this.ftfCurve) && (this.snowLineEquator > 0.0 || this.snowLineEquator == AUTO)
         && (this.snowLineTundra > 0.0 || this.snowLineTundra == AUTO);
   }

   /** One set per landmass: FTF continent cells joined by FTF's continent bridges share one set (bridge 6.0, format 17). */
   static final String SETS_CONTINENTS = "continents";
   /** One set per FTF continent cell, even where bridges join cells into one landmass (bridge 3.x-6.0 "continents"). */
   static final String SETS_CELLS = "cells";
   static final String SETS_REGIONS = "regions";
   static final String SETS_HALVES = "halves";
   static final String GUARD_EXACT = "exact";
   static final String GUARD_VANILLA = "vanilla";
   static final String GUARD_OFF = "off";
   static final String STRATA_OFF = "off";
   static final String STRATA_REGIONAL = "regional";
   static final String STRATA_BEDDED = "bedded";
   /** bedded, plus the regional rock through the whole body of the land (DeepRock). */
   static final String STRATA_DEEP = "deep";

   /** A rock_strata value as written in a file: off, bedded, deep or (saved worlds) regional; anything else, or missing: off. */
   static String rockStrataMode(String value) {
      if (value == null) {
         return STRATA_OFF;
      }
      String v = value.trim().toLowerCase(java.util.Locale.ROOT);
      return v.equals(STRATA_REGIONAL) || v.equals(STRATA_BEDDED) || v.equals(STRATA_DEEP) ? v : STRATA_OFF;
   }

   /** A cave_floor_guard value as written in a file: exact, vanilla or off (true = exact, false = off; anything else: exact). */
   static String caveFloorGuardMode(String value) {
      if (value == null) {
         return GUARD_EXACT;
      }
      String v = value.trim().toLowerCase(java.util.Locale.ROOT);
      return v.equals(GUARD_VANILLA) ? GUARD_VANILLA : v.equals(GUARD_OFF) || v.equals("false") ? GUARD_OFF : GUARD_EXACT;
   }

   /** A biome_sets value as written in a file: continents, cells, regions or halves (anything else: continents). */
   static String biomeSetsMode(String value) {
      if (value == null) {
         return SETS_CONTINENTS;
      }
      String v = value.trim().toLowerCase(java.util.Locale.ROOT);
      return v.equals(SETS_REGIONS) || v.equals(SETS_HALVES) || v.equals(SETS_CELLS) ? v : SETS_CONTINENTS;
   }

   /** Defaults for a fresh properties file (the VanillaBackport biomes off: 0, 0, 0). */
   static final Settings DEFAULT = new Settings(true, 50000.0, 0.5, true, 0.7, 80.0, true, 0.0, 1.0, 1.0, true, 1600.0, 48.0, true, 8.0, 4.0, 0.35, 2.0, true, 384.0, 40.0, 24.0, SETS_CONTINENTS, 25.0, GUARD_EXACT, true, 0.25, true, true, true, 0.0, true, true, STRATA_DEEP, AUTO, AUTO, false, 0.0, 0.0, 0.0, true, 700.0, true);

   /** What a world saved by bridge 3.x was generated with: none of the v4 changes. */
   static Settings legacy(boolean stillLifeLatitude, double poleDistance, double americanShare) {
      return new Settings(stillLifeLatitude, poleDistance, americanShare, false, 0.5, 84.0, false, 0.0, 0.0, 0.0, false, 0.0, 0.0, true, 0.0, 0.0, 0.0, 4.0, false, 0.0, 0.0, 0.0, SETS_CELLS, 25.0, GUARD_OFF, false, 0.0, false, false, false, 0.0, false, false, STRATA_OFF, 0.0, 0.0, false, 0.0, 0.0, 0.0, false, 0.0, false);
   }
}

package stilllife.ftfbridge;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

final class BridgeConfig {
   static final String FILE_NAME = "stilllife_ftf_bridge.properties";
   private static volatile boolean enabled = true;
   private static volatile Settings settings = Settings.DEFAULT;

   private BridgeConfig() {
   }

   static boolean enabled() {
      return enabled;
   }

   static Settings settings() {
      return settings;
   }

   static void load(Path configDir) {
      Path file = configDir.resolve(FILE_NAME);
      Properties p = new Properties();

      try {
         if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
               p.load(r);
            }
         }
      } catch (Exception e) {
         StillLifeFtfBridge.LOG.error("[{}] could not read {}; using defaults", StillLifeFtfBridge.MOD_ID, file, e);
      }

      Settings d = Settings.DEFAULT;
      LIMITS.clear();
      enabled = bool(p, "enabled", true);
      LIMITS.put("latitude", "Default: " + (d.stillLifeLatitude() ? "still_life" : "ftf") + ". Allowed: still_life or ftf.");
      String latitude = p.getProperty("latitude", p.getProperty("temperature_mode", d.stillLifeLatitude() ? "still_life" : "ftf"));
      boolean stillLife = switch (latitude.trim().toLowerCase(Locale.ROOT)) {
         case "still_life" -> true;
         case "ftf" -> false;
         default -> {
            invalid("latitude", latitude, "still_life or ftf");
            yield d.stillLifeLatitude();
         }
      };
      double pole = number(p, "pole_distance", d.poleDistance(), 1000.0, 1.0E7);
      double share = number(p, "american_share", d.americanShare(), 0.0, 1.0);
      boolean swamps = bool(p, "humid_swamps", d.humidSwamps());
      double swampHumidity = number(p, "swamp_humidity", d.swampHumidity(), -1.0, 1.0);
      double swampMaxHeight = number(p, "swamp_max_height", d.swampMaxHeight(), 63.0, 400.0);
      boolean snowCaps = bool(p, "snow_caps", d.snowCaps());
      double snowCliff = number(p, "snow_cliff_steepness", d.snowCliffSteepness(), 0.0, 10.0);
      double aspen = number(p, "aspen_swamps", d.aspenSwamps(), 0.0, 2.0);
      double salt = number(p, "salt_lakes", d.saltLakes(), 0.0, 2.0);
      boolean pools = bool(p, "wetland_pools", d.wetlandPools());
      double upliftSmoothing = number(p, "uplift_smoothing", d.upliftSmoothing(), 0.0, 4000.0);
      double wetlandMaxHeight = number(p, "wetland_max_height", d.wetlandMaxHeight(), 0.0, 2048.0);
      boolean keepWetlandBiomes = bool(p, "keep_wetland_biomes", d.keepWetlandBiomes());
      double beachMaxHeight = number(p, "beach_max_height", d.beachMaxHeight(), 0.0, 256.0);
      double beachShallows = number(p, "beach_shallows", d.beachShallows(), 0.0, 32.0);
      double saltLakeWater = number(p, "salt_lake_water", d.saltLakeWater(), 0.0, 0.8);
      double saltLakeMaxRelief = number(p, "salt_lake_max_relief", d.saltLakeMaxRelief(), 1.0, 16.0);
      boolean saltLakeSlopePools = bool(p, "salt_lake_slope_pools", d.saltLakeSlopePools());
      double biomeSetBlend = number(p, "biome_set_blend", d.biomeSetBlend(), 0.0, 2048.0);
      LIMITS.put("biome_sets", "Default: " + d.biomeSets() + ". Allowed: continents, regions or halves.");
      String biomeSetsRaw = p.getProperty("biome_sets", d.biomeSets());
      String biomeSets = Settings.biomeSetsMode(biomeSetsRaw);
      if (!biomeSets.equals(biomeSetsRaw.trim().toLowerCase(Locale.ROOT))) {
         invalid("biome_sets", biomeSetsRaw, "continents, regions or halves");
      }
      double biomeSetRegionSize = number(p, "biome_set_region_size", d.biomeSetRegionSize(), 1.0, 100.0);
      double peakLineVariation = number(p, "peak_line_variation", d.peakLineVariation(), 0.0, 160.0);
      double snowCapFade = number(p, "snow_cap_fade", d.snowCapFade(), 0.0, 64.0);
      boolean columnDepth = bool(p, "column_depth", d.columnDepth());
      boolean naturalSnow = bool(p, "natural_snow_layers", d.naturalSnowLayers());
      boolean groundCover = bool(p, "ground_cover", d.groundCover());
      boolean mountainSnow = bool(p, "mountain_snow", d.mountainSnow());
      double altitudeCooling = number(p, "altitude_cooling", d.altitudeCooling(), 0.0, 2.0);
      double snowLineClimate = number(p, "snow_line_climate", d.snowLineClimate(), 0.0, 200.0);
      LIMITS.put("cave_floor_guard", "Default: " + d.caveFloorGuard() + ". Allowed: exact, vanilla or off.");
      String caveFloorGuardRaw = p.getProperty("cave_floor_guard", d.caveFloorGuard());
      String caveFloorGuard = Settings.caveFloorGuardMode(caveFloorGuardRaw);
      String guardWritten = caveFloorGuardRaw.trim().toLowerCase(Locale.ROOT);
      if (!guardWritten.equals(caveFloorGuard) && !guardWritten.equals("true") && !guardWritten.equals("false")) {
         invalid("cave_floor_guard", caveFloorGuardRaw, "exact, vanilla or off");
      }
      settings = new Settings(
         stillLife, pole, share, swamps, swampHumidity, swampMaxHeight, snowCaps, snowCliff,
         aspen, salt, pools, upliftSmoothing, wetlandMaxHeight, keepWetlandBiomes, beachMaxHeight, beachShallows, saltLakeWater,
         saltLakeMaxRelief, saltLakeSlopePools, biomeSetBlend, peakLineVariation, snowCapFade, biomeSets, biomeSetRegionSize,
         caveFloorGuard, columnDepth, naturalSnow, groundCover, mountainSnow, altitudeCooling, snowLineClimate
      );

      String text = String.format(
         Locale.ROOT,
         """
         # =====================================================================================================
         #  Still Life x FreeTerraForged bridge
         # =====================================================================================================
         # These settings are used when a NEW world is created. Each world then keeps its own copy in
         # <world>/%s, so editing this file never changes a world you already have.
         # To apply new settings to an existing world, delete that file (chunks made after that may not line up
         # with older ones). Worlds made with bridge 3.x keep their 3.x behaviour until that file is deleted.
         #
         # Every setting shows its default and the values it accepts on the line above it.

         # -----------------------------------------------------------------------------------------------------
         #  General
         # -----------------------------------------------------------------------------------------------------

         # Turns the whole bridge on or off. Off = Still Life's biomes end up in the wrong places on FTF terrain.
         enabled=%s

         # Where hot and cold climates go.
         #   still_life = Still Life's own layout: one warm equator, getting colder towards the poles.
         #   ftf        = FreeTerraForged's layout: repeating warm and cold bands, set in FTF's climate settings.
         latitude=%s

         # Only for latitude=still_life: how many blocks it is from the equator to the coldest climate.
         # Smaller = climates change faster as you travel north or south. Still Life itself uses 40000.
         pole_distance=%s

         # Only for latitude=still_life (FTF's own temperature has its "Altitude Cooling Strength" slider instead).
         # How much colder it gets going up, from the sea to the highest peaks, on Still Life's temperature scale
         # (-1 coldest to 1 hottest). Forests give way to colder forests on the way up, and warm lands get snowy rather
         # than barren peaks on their highest mountains. 0 = no cooling (Still Life's own: only its height bands change).
         altitude_cooling=%s

         # Only for latitude=still_life. The high-mountain bands (alpine, then snowy peaks) rise by up to this many blocks
         # towards the warm equator and drop by as much towards the cold end, like real snow lines. Everything tied to
         # the snow line (the alpine belt, the treeline, mountain snow) follows. 0 = the same height everywhere.
         snow_line_climate=%s

         # -----------------------------------------------------------------------------------------------------
         #  American and Afro-Eurasian biomes
         # -----------------------------------------------------------------------------------------------------
         # Still Life has two sets of biomes: American and Afro-Eurasian. These settings decide where each set goes.

         # How the two sets are spread out:
         #   continents = each FTF continent gets one set.
         #   regions    = each continent is split into several irregular regions, each with one set (closest to
         #                Still Life's own worlds; also works with FTF's single-continent worlds).
         #   halves     = the west of every continent is American, the east Afro-Eurasian.
         biome_sets=%s

         # Only for biome_sets=regions: how big one region is, as a percentage of an average continent's land.
         # 25 = about four regions per continent, 50 = about two, 10 = about ten.
         biome_set_region_size=%s

         # How much of the world is American, from 0 (none) to 1 (all): the share of continents, of regions, or of
         # each continent's width counted from the west, depending on biome_sets.
         american_share=%s

         # Where the two sets meet, they mix over about this many blocks in a ragged edge instead of a straight
         # line. 0 = a sharp line.
         biome_set_blend=%s

         # -----------------------------------------------------------------------------------------------------
         #  Beaches
         # -----------------------------------------------------------------------------------------------------

         # Coast more than this many blocks above sea level never becomes a beach. Still Life only puts sand or
         # gravel close to the sea, so higher "beaches" would just be grassland. 0 = no limit.
         beach_max_height=%s

         # Sea up to this many blocks deep counts as beach (sandy or gravelly sea floor near the shore), like in
         # Still Life's own worlds. 0 = ocean right up to the shore.
         beach_shallows=%s

         # -----------------------------------------------------------------------------------------------------
         #  Swamps and wetlands
         # -----------------------------------------------------------------------------------------------------

         # true = very humid, low land can become a swamp, marsh or fen, not only FTF's river wetlands.
         humid_swamps=%s

         # How humid land must be to become a swamp, from -1 (driest) to 1 (most humid). Higher = fewer swamps.
         swamp_humidity=%s

         # The highest land that can become a swamp. This uses Still Life's height scale, where 63 is sea level,
         # 74 is the lowest quarter of all land and 84 is the middle. Coasts never become swamps.
         swamp_max_height=%s

         # true = shallow pools of water in swamps, marshes, bogs, fens, muskegs, salt lakes and other wetlands,
         # at any height (Still Life only floods them at sea level). Water only sits on flat ground, so it never
         # spills down a slope; the sloped parts become mud.
         wetland_pools=%s

         # FTF flattens the ground around a wetland down to its water level, which can dig deep pits into hills.
         # Ground more than this many blocks above the water is left alone, and the wetland fades out between
         # half that height and the full height. 0 = FTF's own behaviour.
         wetland_max_height=%s

         # true = water in FTF's wetlands keeps the Still Life wetland biome picked there (muskeg, floodplain,
         # flooded prairie, salt lake...). false = FTF replaces the ones it does not recognise with bog, fen or river.
         keep_wetland_biomes=%s

         # -----------------------------------------------------------------------------------------------------
         #  Dry wetlands: aspen swamps and salt lakes
         # -----------------------------------------------------------------------------------------------------
         # Only on continents with American biomes (the Afro-Eurasian set has none). They come in patches a few
         # hundred blocks across, on flat ground only.
         #   aspen swamps: cool, fairly dry, flat lowland.
         #   salt lakes:   warm to hot, dry, flat land at least 350 blocks from the sea; white salt flats with
         #                 shallow water.
         # How common they are: 0 = only where an FTF river wetland happens to be, 1 = about a quarter of the
         # land that suits them, 2 = about half.
         aspen_swamps=%s
         salt_lakes=%s

         # How much of a salt lake is covered by a thin sheet of water (the rest is white salt). Still Life's own
         # salt lakes are about a fifth water. 0 = dry salt flats. Needs wetland_pools=true.
         salt_lake_water=%s

         # Salt lakes only form where the ground rises or falls by at most this many blocks over 32 blocks.
         # FTF's "flat" land is often a gentle slope of 1-block steps, where water only lies in thin strips, so
         # the default 2 keeps salt lakes on truly flat ground. Higher = more salt lakes, with stepped water.
         salt_lake_max_relief=%s

         # true = on stepped salt lake ground, the water forms small round pools. false = only wide sheets of
         # water on level ground, so stepped ground stays dry.
         salt_lake_slope_pools=%s

         # -----------------------------------------------------------------------------------------------------
         #  Snow and mountains
         # -----------------------------------------------------------------------------------------------------

         # true = Still Life's snow stays on mountain tops at every height. Without this, FTF's erosion turns it
         # into stone, tuff or moss, and its snow decorator strips snow off most mountain slopes.
         snow_caps=%s

         # How steep a slope must be before its snow is cleared off (bare cliffs). Higher = snow on steeper slopes.
         # 0 = use the rock steepness from your FTF preset.
         snow_cliff_steepness=%s

         # Still Life starts its high mountain biomes (alpine, then snowy peaks) at a fixed height, so every snow
         # line is perfectly level. This makes the snow line wander up and down by up to this many blocks: lower
         # in gullies, higher on ridges. 0 = a level line.
         peak_line_variation=%s

         # The bottom this many blocks of a snowy peak get snow in patches that thin out downwards, with bare rock
         # between, so the snow fades in instead of starting at a hard edge. Needs snow_caps=true. 0 = no fade.
         snow_cap_fade=%s

         # Only when "Smooth Layer Decorator" is on in your FTF preset. FTF works out snow depth from its own terrain
         # height, which often doesn't match the blocks the game actually builds, so the snow comes out in grids and
         # stripes with odd full snow blocks. true = the bridge sets the depth from the real ground instead: snow drifts
         # against steps and fills hollows, stays thin on ridges and steep slopes, is a little deeper on cold north
         # faces and higher up, and is never thicker than 7 layers. false = FTF's own smooth layers.
         natural_snow_layers=%s

         # true = mountain snow follows wind, sun and the shape of the ground, with real snow ground and bare rock:
         #   - a prevailing wind (with latitude=still_life: easterly near the equator and the poles, westerly in between;
         #     otherwise westerly) scours slopes facing into it to bare rock in patches; the sheltered side gets deeper drifts
         #   - sunny south faces go bare up to 30 blocks above where the snowy peaks begin
         #   - below that line, leftover snow that grows going up: only in the most sheltered spots (hollows, gullies,
         #     north faces, out of the wind) low down, more and more towards the line; cold climates bring it further
         #     down, so lower mountains get some too
         #   - dry biomes get less snow and more bare rock, wet ones more snow, warm ones only snowy gullies
         mountain_snow=%s

         # -----------------------------------------------------------------------------------------------------
         #  Ground cover
         # -----------------------------------------------------------------------------------------------------

         # true = the ground on slopes suits the place, in one family of colours after FTF's own mix: grey talus of
         # gravel, tuff, andesite and stone on steep slopes (with moss where it is wet), coarse dirt, moss and gravel where
         # soil thins out, sandstone and red sandstone in deserts; rockfall gravel and the odd boulder at the foot of
         # cliffs, and fans of gravel fading to finer ground below steep slopes where erosion left sediment. Along
         # rivers: gravel and sand bars (mud where warm and wet) at the water's edge, and in dry lands a green strip of
         # grass on the banks and valley floor. Below the snow line, an alpine belt where grass breaks up into coarse
         # dirt, gravel, moss and stone. false = FTF's own mix everywhere.
         ground_cover=%s

         # -----------------------------------------------------------------------------------------------------
         #  Caves
         # -----------------------------------------------------------------------------------------------------
         # FTF often leaves caves in the biome of the ground above them, so Still Life's rules would put that
         # biome's ground (snow, sand, mud...) on cave floors. This decides where that ground may go:
         #   exact   = only on ground open to the sky or under water: never on cave floors, pockets or overhangs.
         #   vanilla = Minecraft's own check: keeps deep caves clean, but small pockets near the surface still
         #             get it.
         #   off     = Still Life's own rules: caves under snowy peaks get snow floors, desert caves get sand.
         cave_floor_guard=%s

         # true = Still Life's cave biomes are stacked from the surface down to bedrock in every column, including
         # the deepest ones (Haunted Depths), however tall the world is. false = the old behaviour (bridge 5.0-5.4):
         # cave biomes start deeper under high ground and the deepest ones never appear.
         column_depth=%s

         # -----------------------------------------------------------------------------------------------------
         #  Terrain
         # -----------------------------------------------------------------------------------------------------

         # Only for FTF's UPLIFT continent type. FTF raises land towards plate borders but drops that lift near
         # the coast, which leaves a long cliff (often 40-100 blocks) running along the coast. This spreads the
         # rise over this many blocks instead, turning the cliff into a slope. 0 = FTF's cliffs.
         uplift_smoothing=%s
         """,
         WorldLock.FILE_NAME,
         enabled,
         stillLife ? "still_life" : "ftf",
         plain(pole),
         plain(altitudeCooling),
         plain(snowLineClimate),
         biomeSets,
         plain(biomeSetRegionSize),
         plain(share),
         plain(biomeSetBlend),
         plain(beachMaxHeight),
         plain(beachShallows),
         swamps,
         plain(swampHumidity),
         plain(swampMaxHeight),
         pools,
         plain(wetlandMaxHeight),
         keepWetlandBiomes,
         plain(aspen),
         plain(salt),
         plain(saltLakeWater),
         plain(saltLakeMaxRelief),
         saltLakeSlopePools,
         snowCaps,
         plain(snowCliff),
         plain(peakLineVariation),
         plain(snowCapFade),
         naturalSnow,
         mountainSnow,
         groundCover,
         caveFloorGuard,
         columnDepth,
         plain(upliftSmoothing)
      );

      text = withLimits(text);
      try {
         String old = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
         if (!old.equals(text)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
         }
      } catch (Exception e) {
         StillLifeFtfBridge.LOG.warn("[{}] could not write {}", StillLifeFtfBridge.MOD_ID, file, e);
      }
   }

   /** Each setting's default (for new worlds) and allowed values, written above it in the file. */
   private static final java.util.Map<String, String> LIMITS = new java.util.LinkedHashMap<>();

   /** The file's text with a "Default ... Allowed ..." line above every setting. */
   private static String withLimits(String text) {
      StringBuilder out = new StringBuilder(text.length() + LIMITS.size() * 48);
      for (String line : text.split("\n", -1)) {
         int eq = line.indexOf('=');
         String note = !line.startsWith("#") && eq > 0 ? LIMITS.get(line.substring(0, eq)) : null;
         if (note != null) {
            out.append("# ").append(note).append('\n');
         }
         out.append(line).append('\n');
      }
      out.setLength(out.length() - 1);
      return out.toString();
   }

   static boolean bool(Properties p, String key, boolean def) {
      LIMITS.put(key, "Default: " + def + ". Allowed: true or false.");
      String v = p.getProperty(key);
      if (v == null) {
         return def;
      }

      if (v.trim().equalsIgnoreCase("true")) {
         return true;
      }

      if (v.trim().equalsIgnoreCase("false")) {
         return false;
      }

      invalid(key, v, "true or false");
      return def;
   }

   static double number(Properties p, String key, double def, double min, double max) {
      LIMITS.put(key, "Default: " + plain(def) + ". Allowed: " + plain(min) + " to " + plain(max) + ".");
      String v = p.getProperty(key);
      if (v == null) {
         return def;
      }

      try {
         double d = Double.parseDouble(v.trim());
         if (d >= min && d <= max) {
            return d;
         }
      } catch (NumberFormatException ignored) {
      }

      invalid(key, v, "a number from " + plain(min) + " to " + plain(max));
      return def;
   }

   private static void invalid(String key, String value, String expected) {
      StillLifeFtfBridge.LOG.error("[{}] config: {}={} is invalid (expected {}); using the default", StillLifeFtfBridge.MOD_ID, key, value, expected);
   }

   static String plain(double d) {
      return d == Math.rint(d) ? Long.toString((long)d) : Double.toString(d);
   }
}

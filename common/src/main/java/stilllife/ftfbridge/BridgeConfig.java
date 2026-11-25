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
      LIMITS.put("biome_sets", "Default: " + d.biomeSets() + ". Allowed: continents, cells, regions or halves.");
      String biomeSetsRaw = p.getProperty("biome_sets", d.biomeSets());
      String biomeSets = Settings.biomeSetsMode(biomeSetsRaw);
      if (!biomeSets.equals(biomeSetsRaw.trim().toLowerCase(Locale.ROOT))) {
         invalid("biome_sets", biomeSetsRaw, "continents, cells, regions or halves");
      }
      double biomeSetRegionSize = number(p, "biome_set_region_size", d.biomeSetRegionSize(), 1.0, 100.0);
      double peakLineVariation = number(p, "peak_line_variation", d.peakLineVariation(), 0.0, 160.0);
      double snowCapFade = number(p, "snow_cap_fade", d.snowCapFade(), 0.0, 64.0);
      double mountainCooling = number(p, "mountain_cooling", d.mountainCooling(), 0.0, 1.0);
      boolean columnDepth = bool(p, "column_depth", d.columnDepth());
      boolean naturalSnow = bool(p, "natural_snow_layers", d.naturalSnowLayers());
      boolean groundCover = bool(p, "ground_cover", d.groundCover());
      boolean mountainSnow = bool(p, "mountain_snow", d.mountainSnow());
      double snowLineEquator = autoNumber(p, "snow_line_equator", 64.0, 4064.0);
      double snowLineTundra = autoNumber(p, "snow_line_tundra", 64.0, 4064.0);
      if (snowLineTundra > 0.0 && snowLineEquator > 0.0 && snowLineTundra > snowLineEquator) {
         invalid("snow_line_tundra", plain(snowLineTundra), "a number no higher than snow_line_equator (" + plain(snowLineEquator) + ")");
         snowLineEquator = d.snowLineEquator();
         snowLineTundra = d.snowLineTundra();
      }
      boolean shores = bool(p, "shores", d.shores());
      boolean forestVariety = bool(p, "forest_variety", d.forestVariety());
      LIMITS.put("rock_strata", "Default: " + d.rockStrata() + ". Allowed: off, regional, bedded or deep (experimental).");
      String rockStrataRaw = p.getProperty("rock_strata", d.rockStrata());
      String rockStrata = Settings.rockStrataMode(rockStrataRaw);
      if (!rockStrata.equals(rockStrataRaw.trim().toLowerCase(Locale.ROOT))) {
         invalid("rock_strata", rockStrataRaw, "off, regional, bedded or deep");
      }
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
         caveFloorGuard, columnDepth, mountainCooling, naturalSnow, groundCover, mountainSnow, d.snowLineClimate(), shores, forestVariety,
         rockStrata, snowLineEquator, snowLineTundra, !stillLife
      );

      String text = text(enabled, settings);
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

   /**
    * The text of the properties file for these values: a short header, then the options in sections by topic. Each option
    * gets a short explanation ("Advanced:" / "Experimental:" marked), its "Default ... Allowed ..." line (LIMITS, filled
    * while reading) and its current value. Only the text: which keys are read, and how, is load().
    */
   static String text(boolean enabled, Settings s) {
      StringBuilder t = new StringBuilder(16384);
      t.append("""
         # ==================================================================================================
         #  Lifeforged (Still Life x FreeTerraForged bridge): settings for NEW worlds
         # ==================================================================================================
         # A new world copies these values into <world folder>/%s and from then on uses
         # only that copy. Editing this file never changes an existing world.
         # To change an existing world, edit its %s (or delete it to take the values
         # below) and restart. Only chunks generated after that use the new values, so a seam can show
         # where old and new chunks meet.
         #
         # Each option shows what it does, its default and the values it accepts.
         # "Advanced:" = fine-tuning most people never need. "Experimental:" = not tested in game.
         # A value that is not accepted is reported in logs/latest.log and the default is used.
         # The game rewrites this file at start-up: your own comments and unknown keys are not kept.

         """.formatted(WorldLock.FILE_NAME, WorldLock.FILE_NAME));

      section(t, "1. Basics", null);
      option(t, "enabled", enabled, """
         Turns the whole mod on or off. Off: Still Life's biomes land in the wrong places.
         Not saved per world: it applies to every world from the next game start.""");

      section(t, "2. Climate", """
         Humidity always comes from FreeTerraForged. These options decide where it is warm or cold.""");
      option(t, "latitude", s.stillLifeLatitude() ? "still_life" : "ftf", """
         How warm and cold climates are laid out.
           still_life = one warm equator running east-west, colder towards the north and the south, like
                        Still Life's own worlds. The log says where the equator is (usually 10000 to 32000
                        blocks south of the world centre).
           ftf        = FreeTerraForged's own warm and cold bands, set in your FreeTerraForged preset.""");
      option(t, "pole_distance", plain(s.poleDistance()), """
         Only with latitude=still_life. Blocks from the equator to the coldest climate (polar ice).
         Smaller = climates change faster as you travel.""");

      section(t, "3. Biome sets", """
         Still Life has two biome sets for the same climates: American and Afro-Eurasian (different
         deserts, steppes, forests...). These options decide where each set is used.""");
      option(t, "biome_sets", s.biomeSets(), """
         How the two sets are spread out.
           continents = one set per landmass. Continents joined by land bridges count as one; a very large
                        landmass (more than 64 continents) is split into parts.
           cells      = one set per FreeTerraForged continent cell, even across land bridges (the layout of
                        worlds made before 6.0).
           regions    = every continent is split into irregular regions with one set each. Use this if
                        your preset makes one single continent.
           halves     = every continent is American in the west and Afro-Eurasian in the east.""");
      option(t, "biome_set_region_size", plain(s.biomeSetRegionSize()), """
         Only with biome_sets=regions. Size of one region in percent of an average continent's land:
         25 = about four regions per continent, 50 = about two.""");
      option(t, "american_share", plain(s.americanShare()), """
         How much is American, from 0 (none) to 1 (all): the share of landmasses (continents), of
         continent cells (cells), or of each continent's land (regions, halves).""");
      option(t, "biome_set_blend", plain(s.biomeSetBlend()), """
         Advanced: where the two sets meet, they mix in a ragged band about this many blocks wide.
         0 = a sharp line.""");

      section(t, "4. Mountains and snow", """
         The snow line is the height above which snow lies all year. It is highest at the equator, falls
         towards the poles and reaches the sea at the cold edge of the tundra. Still Life's mountain
         biomes are placed under it, and the treeline follows it.
         auto = worked out from this world's mountain heights when the world is created, then saved with
         the world: Y 650 and Y 250 where the high mountains stand about 330 blocks above the sea, lower
         on lower presets. A number sets the line at that Y.""");
      option(t, "snow_line_equator", s.snowLineEquator() == Settings.AUTO ? "auto" : plain(s.snowLineEquator()), """
         Snow line at the equator (Y). Lower tropical mountains get no snow cap.""");
      option(t, "snow_line_tundra", s.snowLineTundra() == Settings.AUTO ? "auto" : plain(s.snowLineTundra()), """
         Snow line at the warm edge of the tundra (Y). Not higher than snow_line_equator.""");
      option(t, "mountain_cooling", plain(s.mountainCooling()), """
         Only with latitude=still_life. Under a snow cap where Still Life would put desert scrub, the
         alpine band is chosen as if this much colder (Still Life's temperature scale), but only when
         that gives a cold alpine biome (biome tag stilllife_ftf_bridge:cold_alpine).
         0 = Still Life's own biomes everywhere.""");
      option(t, "peak_line_variation", plain(s.peakLineVariation()), """
         Blocks by which the snow line and the bands under it wander up and down: lower in gullies,
         higher on ridges. 0 = a level line.""");
      option(t, "mountain_snow", s.mountainSnow(), """
         true = a snow model decides where snow lies on mountains: in hollows, gullies and shade first;
         sunny faces, windy ridges and cliffs stay bare longest. Patches below the snow line, mostly
         white high above it. false = Still Life's snow: snowy peak biomes are solid white.
         Runs in the preset's Erosion Decorator. With that off, it needs natural_snow_layers and the
         Smooth Layer Decorator instead, and lays no scree.""");
      option(t, "natural_snow_layers", s.naturalSnowLayers(), """
         Needs the Smooth Layer Decorator in your FreeTerraForged preset. true = thin snow takes its
         depth from the real ground (drifts against steps, thin on ridges, no stripes); with
         mountain_snow, leaves keep snow only inside snowfields. false = FreeTerraForged's layers.""");
      option(t, "snow_caps", s.snowCaps(), """
         Advanced: true = FreeTerraForged's erosion does not turn mountain snow into stone, tuff or moss;
         only cliffs lose their snow. Leave it on.""");
      option(t, "snow_cliff_steepness", plain(s.snowCliffSteepness()), """
         Advanced: only with snow_caps=true. How steep a slope must be to lose its snow. Higher = snow
         stays on steeper slopes. 0 = your preset's rock steepness.""");
      option(t, "snow_cap_fade", plain(s.snowCapFade()), """
         Advanced: only with mountain_snow=false and snow_caps=true. The lowest this many blocks of a
         snowy peak have patchy snow. 0 = a hard edge.""");

      section(t, "5. Ground and rock", null);
      option(t, "ground_cover", s.groundCover(), """
         true = the ground suits the place: scree below cliffs (gravel to cobblestone, overgrown where
         mild and wet), frost scree on bare high ground, thin rocky soil on steep slopes. Sand only where
         the ground is sand already. River banks are left alone.
         false = FreeTerraForged's one mix of tuff, andesite, moss and coarse dirt.
         Needs the Erosion Decorator in your FreeTerraForged preset.""");
      option(t, "rock_strata", s.rockStrata(), """
         Only with ground_cover=true. What bare rock (cliffs, peaks, the rock in scree) is made of.
           off      = the world's own stone.
           regional = each mountain region has its own rock (stone, andesite, tuff, diorite with
                      calcite...), softly mottled. With Nature's Spirit: kaolin clay rock in some dry
                      regions.
           bedded   = regional, plus tilted and folded rock layers and rare thin seams.
           deep     = Experimental: bedded, plus the regional rock through the whole body of the land,
                      in layers that tilt and fold by region. Costs about 5-17% more generation time.
                      With Worldgen Accelerator's gpuLight on, the part below the surface is skipped.""");

      section(t, "6. Forests", """
         The treeline, the small trees above it and the plants on cliff ledges have no option: they come
         with the mod and the Still Life FTF edition.""");
      option(t, "forest_variety", s.forestVariety(), """
         true = a belt of bushes and young trees at forest edges, natural clearings, and tree mixes that
         change from region to region. Clearings and mixes need Still Life FTF edition 0.3.7-ftf.""");

      section(t, "7. Coasts, lakes and rivers", """
         River banks have no option: they are left as Still Life and FreeTerraForged make them.""");
      option(t, "beach_max_height", plain(s.beachMaxHeight()), """
         Coast more than this many blocks above the sea never becomes a beach biome. 0 = no limit.""");
      option(t, "beach_shallows", plain(s.beachShallows()), """
         Sea up to this many blocks deep next to the shore also gets a beach biome (a sandy or gravelly
         sea floor). 0 = ocean biomes up to the shore.""");
      option(t, "shores", s.shores(), """
         true = gravel and stone below steep coasts, mud flats in warm shallow sea, gravel, mud and clay
         on lake shores, reeds and lily pads on warm lakes. Most FreeTerraForged lakes are part of a
         river and are left alone.""");

      section(t, "8. Swamps and wetlands", null);
      option(t, "humid_swamps", s.humidSwamps(), """
         true = very humid lowland away from the coast can become swamp, marsh or fen, not only the small
         wetlands FreeTerraForged puts along its rivers.""");
      option(t, "swamp_humidity", plain(s.swampHumidity()), """
         Advanced: only with humid_swamps=true. How humid land must be to become swamp, from -1 (driest)
         to 1 (wettest). Higher = fewer swamps.""");
      option(t, "swamp_max_height", plain(s.swampMaxHeight()), """
         Advanced: the highest land that can become swamp or aspen swamp (salt lakes: 25 higher), on
         Still Life's height scale: 63 = sea level, 84 = the middle of all land, whatever your world's
         real heights.""");
      option(t, "wetland_pools", s.wetlandPools(), """
         true = shallow pools of water in swamps, marshes, bogs, fens and salt lakes at any height, only
         on level ground (sloping parts become mud).""");
      option(t, "aspen_swamps", plain(s.aspenSwamps()), """
         How common aspen swamps are (cool, fairly dry, flat lowland; American set only).
         0 = only in FreeTerraForged wetlands, 1 = about a quarter of the land that suits them, 2 = half.""");
      option(t, "salt_lakes", plain(s.saltLakes()), """
         How common salt lakes are (hot, dry, flat land far inland; American set only). Same scale.""");
      option(t, "salt_lake_water", plain(s.saltLakeWater()), """
         Only with wetland_pools=true. Share of a salt lake under a thin sheet of water; the rest is white
         salt. 0 = dry salt flats.""");
      option(t, "salt_lake_max_relief", plain(s.saltLakeMaxRelief()), """
         Advanced: salt lakes only where the ground rises or falls by at most this many blocks over 32
         blocks. Higher = more salt lakes, also on stepped ground.""");
      option(t, "salt_lake_slope_pools", s.saltLakeSlopePools(), """
         Advanced: only with wetland_pools=true. true = small round pools on stepped salt lake ground.
         false = stepped ground stays dry.""");
      option(t, "keep_wetland_biomes", s.keepWetlandBiomes(), """
         Advanced: true = water in FreeTerraForged's wetlands keeps the Still Life wetland biome chosen
         there (muskeg, floodplain, salt lake...). false = FreeTerraForged turns the ones it does not
         know into bog, fen or marsh.""");

      section(t, "9. Land shape", null);
      option(t, "uplift_smoothing", plain(s.upliftSmoothing()), """
         Only for presets with continent type UPLIFT. FreeTerraForged leaves a cliff of 40 to 100 blocks
         along its coasts; this spreads it over this many blocks as gentle terraces.
         0 = FreeTerraForged's cliff.""");
      option(t, "wetland_max_height", plain(s.wetlandMaxHeight()), """
         FreeTerraForged's wetlands leave ground more than this many blocks above their water alone, so
         they do not dig pits into hills. 0 = FreeTerraForged's own wetlands.""");

      section(t, "10. Caves", null);
      option(t, "cave_floor_guard", s.caveFloorGuard(), """
         Where Still Life's ground blocks (snow blocks, sand, mud...) may go in caves that belong to the
         biome above them.
           exact   = only on ground open to the sky or under water, never on cave floors.
           vanilla = Minecraft's own check: small pockets near the surface still get them.
           off     = Still Life's own rules: snow floors under snowy peaks, sand under deserts.""");
      option(t, "column_depth", s.columnDepth(), """
         Advanced: true = Still Life's cave biomes reach from the surface down to bedrock in every column,
         the deepest (Haunted Depths) included. false = as in bridge 5.0-5.4 (the deepest never appear).""");
      t.setLength(t.length() - 1);
      return t.toString();
   }

   private static void section(StringBuilder t, String title, String intro) {
      t.append("# --------------------------------------------------------------------------------------------------\n")
         .append("#  ").append(title).append('\n')
         .append("# --------------------------------------------------------------------------------------------------\n");
      if (intro != null) {
         comment(t, intro);
      }
      t.append('\n');
   }

   /** An option: its explanation (null for none), its "Default ... Allowed ..." line and key=value. */
   private static void option(StringBuilder t, String key, Object value, String explanation) {
      if (explanation != null) {
         comment(t, explanation);
      }
      t.append("# ").append(LIMITS.get(key)).append('\n').append(key).append('=').append(value).append("\n\n");
   }

   private static void comment(StringBuilder t, String text) {
      for (String line : text.stripTrailing().split("\n")) {
         t.append(line.isEmpty() ? "#" : "# " + line).append('\n');
      }
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

   /** A number in min..max, or "auto" (the default): Settings.AUTO. */
   static double autoNumber(Properties p, String key, double min, double max) {
      LIMITS.put(key, "Default: auto. Allowed: auto, or " + plain(min) + " to " + plain(max) + ".");
      String v = p.getProperty(key);
      if (v == null || v.trim().equalsIgnoreCase("auto")) {
         return Settings.AUTO;
      }
      try {
         double d = Double.parseDouble(v.trim());
         if (d >= min && d <= max) {
            return d;
         }
      } catch (NumberFormatException ignored) {
      }
      invalid(key, v, "auto, or a number from " + plain(min) + " to " + plain(max));
      return Settings.AUTO;
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

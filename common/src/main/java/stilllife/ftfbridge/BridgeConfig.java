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
      double paleGarden = number(p, "pale_garden", d.paleGarden(), 0.0, 2.0);
      double dappledForest = number(p, "dappled_forest", d.dappledForest(), 0.0, 2.0);
      double sulfurCaves = number(p, "sulfur_caves", d.sulfurCaves(), 0.0, 2.0);
      boolean sulfurRock = bool(p, "sulfur_rock", d.sulfurRock());
      boolean shores = bool(p, "shores", d.shores());
      double coastalMoistureReach = number(p, "coastal_moisture_reach", d.coastalMoistureReach(), 0.0, 1300.0);
      boolean coastalBiomes = bool(p, "coastal_biomes", d.coastalBiomes());
      boolean forestVariety = bool(p, "forest_variety", d.forestVariety());
      LIMITS.put("rock_strata", "Default: " + d.rockStrata() + ". Allowed: off, bedded or deep.");
      String rockStrataRaw = p.getProperty("rock_strata", d.rockStrata());
      String rockStrata = Settings.rockStrataMode(rockStrataRaw);
      if (rockStrata.equals(Settings.STRATA_REGIONAL)) {
         // 6.0.0's "regional" (one rock per region, no layers) is gone for new worlds; worlds saved with it keep it
         StillLifeFtfBridge.LOG.warn("[{}] config: rock_strata=regional no longer exists for new worlds; using bedded", StillLifeFtfBridge.MOD_ID);
         rockStrata = Settings.STRATA_BEDDED;
      } else if (!rockStrata.equals(rockStrataRaw.trim().toLowerCase(Locale.ROOT))) {
         invalid("rock_strata", rockStrataRaw, "off, bedded or deep");
         rockStrata = d.rockStrata();
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
         rockStrata, snowLineEquator, snowLineTundra, !stillLife, paleGarden, dappledForest, sulfurCaves, sulfurRock,
         coastalMoistureReach, coastalBiomes
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
    * The text of the properties file for these values: a short header, then the options in sections by topic (the same
    * groups as the guide), fine-tuning last under "Advanced". Each option gets a short explanation, its "Default ...
    * Allowed ..." line (LIMITS, filled while reading) and its current value. Only the text: which keys are read, and how,
    * is load(); Properties is order-independent, so files written in any older order load the same.
    */
   static String text(boolean enabled, Settings s) {
      StringBuilder t = new StringBuilder(16384);
      t.append("""
         # ==================================================================================================
         #  Lifeforged settings for NEW worlds
         # ==================================================================================================
         # A new world copies these values into <world folder>/%s and then uses only that
         # copy, so this file never changes an existing world. To change a world, edit or delete its copy
         # and restart: only chunks generated afterwards change.
         # Each option: what it does, then its default and allowed values. A value that is not allowed is
         # logged in logs/latest.log and the default is used. This file is rewritten at every start, so
         # your own comments are not kept. Guide: docs/guide.md in the Lifeforged repository.

         """.formatted(WorldLock.FILE_NAME));

      section(t, "Biomes and climate", null);
      option(t, "latitude", s.stillLifeLatitude() ? "still_life" : "ftf", """
         Where it is warm and where it is cold.
           still_life = one warm equator running east-west, colder to the north and south (the log says
                        where the equator is).
           ftf        = FreeTerraForged's warm and cold bands from your preset. The snow line uses their
                        sea-level temperature, so mountains are not cooled twice.""");
      option(t, "pole_distance", plain(s.poleDistance()), """
         Only with latitude=still_life: blocks from the equator to the coldest climate.
         Smaller = climates change faster as you travel.""");
      option(t, "biome_sets", s.biomeSets(), """
         How Still Life's two biome sets (American and Afro-Eurasian) are spread out.
           continents = one biome set per landmass (land joined by land bridges counts as one).
           cells      = one biome set per FreeTerraForged continent cell (worlds made before 6.0).
           regions    = irregular regions on every continent; use this if your preset makes one continent.
           halves     = every continent American in the west, Afro-Eurasian in the east.""");
      option(t, "american_share", plain(s.americanShare()), """
         Share in the American biome set, 0 to 1: of the landmasses (continents, cells) or of each
         continent's land (regions, halves).""");
      option(t, "biome_set_region_size", plain(s.biomeSetRegionSize()), """
         Only with biome_sets=regions: region size in percent of an average continent's land
         (25 = about four regions per continent).""");
      option(t, "pale_garden", plain(s.paleGarden()), """
         VanillaBackport's Pale Garden, in the most humid old temperate forests. 0 = off (the
         default). 0.18 = the amount of Lifeforged 5.x, 1 = a full band, 2 = about double.""");
      option(t, "dappled_forest", plain(s.dappledForest()), """
         VanillaBackport's Dappled Forest, in the drier cool temperate forests, same scale. 0 = off
         (the default), 0.5 = the 5.x amount. Its ground needs Still Life FTF edition 0.3.8-ftf.""");
      option(t, "sulfur_caves", plain(s.sulfurCaves()), """
         VanillaBackport's Sulfur Caves, in part of Still Life's barren caves, same scale. 0 = off
         (the default), 0.5 = the 5.x amount. A biome switched off in VanillaBackport's own config
         (vanillabackport-common.toml) stays off whatever this file says.""");

      option(t, "coastal_moisture_reach", plain(s.coastalMoistureReach()), """
         FreeTerraForged's Coastal Moisture Boost (climate page of the preset) wets all low land, however
         far from the sea. With this, Lifeforged applies it in full at the shore and fades it out over
         this many blocks inland; snow then also ignores the preset's Rain Shadow, so tall peaks do
         not get less snow than their foothills. 0 = FreeTerraForged's own boost.""");

      option(t, "coastal_biomes", s.coastalBiomes(), """
         true = Still Life's coast-only biomes (coastal temperate rainforest, pine woods, coastal
         shrubland ...) form a belt along the whole coast, up to about 370 blocks inland. false = only
         where the land rises steeply out of the sea.""");

      section(t, "Mountains and snow", null);
      option(t, "snow_line_equator", s.snowLineEquator() == Settings.AUTO ? "auto" : plain(s.snowLineEquator()), """
         The snow line (the height above which snow lies all year) at the equator, as a Y level.
         auto = worked out from this world's mountains and saved with it (Y 650 on tall presets).""");
      option(t, "snow_line_tundra", s.snowLineTundra() == Settings.AUTO ? "auto" : plain(s.snowLineTundra()), """
         The snow line at the warm edge of the tundra, as a Y level; not above snow_line_equator.
         auto = worked out like snow_line_equator (Y 250 on tall presets).""");
      option(t, "mountain_snow", s.mountainSnow(), """
         true = snow lies where weather would put it: in gullies, hollows and shade, not on cliffs.
         false = Still Life's solid white snowy peaks. Needs the preset's Erosion Decorator, or else
         natural_snow_layers and the Smooth Layer Decorator.""");
      option(t, "natural_snow_layers", s.naturalSnowLayers(), """
         Needs the preset's Smooth Layer Decorator. true = thin snow follows the real ground, and leaves
         keep snow only inside snowfields. false = FreeTerraForged's snow layers.""");
      option(t, "mountain_cooling", plain(s.mountainCooling()), """
         Only with latitude=still_life: under snow caps, a cold alpine biome instead of desert scrub
         (this much colder, only into the biome tag stilllife_ftf_bridge:cold_alpine). 0 = off.""");
      option(t, "peak_line_variation", plain(s.peakLineVariation()), """
         Blocks by which the snow line and the mountain biomes wander up and down (lower in gullies,
         higher on ridges). 0 = a level line.""");

      section(t, "Ground and rock", null);
      option(t, "ground_cover", s.groundCover(), """
         true = scree below cliffs and on bare high ground, thin rocky soil on steep slopes. false =
         FreeTerraForged's one mix of tuff, andesite and moss. Needs the preset's Erosion Decorator.""");
      option(t, "rock_strata", s.rockStrata(), """
         Only with ground_cover=true: what bare rock is made of.
           off    = the world's own stone.
           bedded = each mountain region has its own rock, in tilted and folded layers, where rock
                    is bare (cliffs, peaks, steep slopes).
           deep   = the same rocks through the whole land, also underground (the default). Costs
                    about 5-17% more generation time, most of it in mountains; set bedded to save
                    that.
         Both use the same rocks. Nature's Spirit adds kaolin, travertine and chert; Create adds its
         limestone to the pale limestone regions.""");
      option(t, "sulfur_rock", s.sulfurRock(), """
         true = Sulfur Caves (sulfur_caves above 0) get their yellow sulfur and red cinnabar rock.
         false = plain stone there.""");
      option(t, "cave_floor_guard", s.caveFloorGuard(), """
         Where Still Life's surface blocks (snow, sand, mud) may go in caves.
           exact   = only on ground open to the sky or under water, never on cave floors.
           vanilla = Minecraft's own check: small pockets near the surface still get them.
           off     = Still Life's own rules: snow floors under snowy peaks, sand under deserts.""");

      section(t, "Plants", """
         The treeline and the plants above it have no option: they come with the Still Life FTF edition.""");
      option(t, "forest_variety", s.forestVariety(), """
         true = natural clearings in forests, about a tenth of the land, mostly 40 blocks across.""");

      section(t, "Water and wetlands", null);
      option(t, "shores", s.shores(), """
         true = gravel and stone below steep coasts, mud flats in warm shallow sea, reeds and lily pads
         on warm lakes. River banks are always left as they are.""");
      option(t, "beach_max_height", plain(s.beachMaxHeight()), """
         Coast more than this many blocks above the sea is never a beach biome. 0 = no limit.""");
      option(t, "beach_shallows", plain(s.beachShallows()), """
         Sea up to this many blocks deep next to the shore is a beach biome. 0 = ocean up to the shore.""");
      option(t, "uplift_smoothing", plain(s.upliftSmoothing()), """
         Only for presets with continent type UPLIFT: spreads FreeTerraForged's 40-100 block coastal
         cliff over this many blocks as gentle terraces. 0 = keep the cliff.""");
      option(t, "humid_swamps", s.humidSwamps(), """
         true = very humid lowland away from the coast can become swamp, marsh or fen, not only the
         small wetlands along rivers.""");
      option(t, "wetland_pools", s.wetlandPools(), """
         true = shallow pools of water in swamps, bogs, fens and salt lakes at any height.""");
      option(t, "aspen_swamps", plain(s.aspenSwamps()), """
         How common aspen swamps are (American biome set): 0 = only in river wetlands, 1 = about a
         quarter of the cool, dry, flat land that suits them, 2 = about half.""");
      option(t, "salt_lakes", plain(s.saltLakes()), """
         How common salt lakes are (American biome set, hot dry flat land), on the same scale.""");
      option(t, "salt_lake_water", plain(s.saltLakeWater()), """
         Only with wetland_pools=true: share of a salt lake under a thin sheet of water. 0 = dry.""");
      option(t, "wetland_max_height", plain(s.wetlandMaxHeight()), """
         Wetlands leave ground more than this many blocks above their water alone, so they do not dig
         pits into hills. 0 = FreeTerraForged's own wetlands.""");

      section(t, "Advanced", """
         Fine-tuning that most people never change.""");
      option(t, "enabled", enabled, """
         Turns the whole mod on or off (off: Still Life's biomes land in the wrong places). Not saved
         per world: it applies to every world from the next start.""");
      option(t, "biome_set_blend", plain(s.biomeSetBlend()), """
         Width in blocks of the ragged band where the two biome sets meet. 0 = a sharp line.""");
      option(t, "column_depth", s.columnDepth(), """
         true = Still Life's cave biomes reach from the surface down to bedrock in every column.
         false = as in bridge 5.0-5.4 (the deepest cave biomes never appear).""");
      option(t, "snow_caps", s.snowCaps(), """
         true = FreeTerraForged's erosion does not turn mountain snow into stone; only cliffs lose it.""");
      option(t, "snow_cliff_steepness", plain(s.snowCliffSteepness()), """
         Only with snow_caps=true: how steep a slope must be to lose its snow. 0 = the preset's rock
         steepness.""");
      option(t, "snow_cap_fade", plain(s.snowCapFade()), """
         Only with mountain_snow=false: the lowest this many blocks of a snowy peak have patchy snow.
         0 = a hard edge.""");
      option(t, "swamp_humidity", plain(s.swampHumidity()), """
         Only with humid_swamps=true: how humid land must be to become swamp, -1 (dry) to 1 (wet).""");
      option(t, "swamp_max_height", plain(s.swampMaxHeight()), """
         Highest land for swamps and aspen swamps (salt lakes: 25 higher), on Still Life's height scale
         (63 = sea level, 84 = the middle of all land).""");
      option(t, "salt_lake_max_relief", plain(s.saltLakeMaxRelief()), """
         Salt lakes only where the ground rises or falls by at most this many blocks over 32 blocks.""");
      option(t, "salt_lake_slope_pools", s.saltLakeSlopePools(), """
         Only with wetland_pools=true: small round pools on stepped salt lake ground (false: dry).""");
      option(t, "keep_wetland_biomes", s.keepWetlandBiomes(), """
         true = Still Life's wetland biomes keep their water instead of becoming FreeTerraForged's bog,
         fen or marsh.""");
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

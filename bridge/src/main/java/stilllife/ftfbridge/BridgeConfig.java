package stilllife.ftfbridge;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

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

   static void load() {
      Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
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
      enabled = bool(p, "enabled", true);
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
      boolean headwaters = bool(p, "river_headwaters", d.riverHeadwaters());
      double mountain = number(p, "river_mountain_height", d.riverMountainHeight(), 16.0, 2048.0);
      double fadeIn = number(p, "river_fade_in", d.riverFadeIn(), 0.0, 4096.0);
      double flank = number(p, "river_flank_max_cut", d.riverFlankMaxCut(), 0.0, 2048.0);
      double flankSlope = number(p, "river_flank_slope", d.riverFlankSlope(), 0.1, 10.0);
      double pale = number(p, "pale_garden", d.paleGarden(), 0.0, 2.0);
      double dappled = number(p, "dappled_forest", d.dappledForest(), 0.0, 2.0);
      double sulfur = number(p, "sulfur_caves", d.sulfurCaves(), 0.0, 2.0);
      double aspen = number(p, "aspen_swamps", d.aspenSwamps(), 0.0, 2.0);
      double salt = number(p, "salt_lakes", d.saltLakes(), 0.0, 2.0);
      boolean saltOnLakes = bool(p, "salt_lakes_on_lakes", d.saltLakesOnLakes());
      boolean pools = bool(p, "wetland_pools", d.wetlandPools());
      boolean lakeFill = bool(p, "lake_fill", d.lakeFill());
      double lakeMinDepth = number(p, "lake_min_depth", d.lakeMinDepth(), 1.0, 8.0);
      double upliftSmoothing = number(p, "uplift_smoothing", d.upliftSmoothing(), 0.0, 4000.0);
      settings = new Settings(
         stillLife, pole, share, swamps, swampHumidity, swampMaxHeight, snowCaps, snowCliff, headwaters, mountain, fadeIn, flank, flankSlope, pale, dappled, sulfur,
         aspen, salt, saltOnLakes, pools, lakeFill, lakeMinDepth, upliftSmoothing
      );

      String text = String.format(
         Locale.ROOT,
         """
         # Still Life x FreeTerraForged bridge
         # Settings for NEW worlds. Every world saves the settings it was created with in <world>/%s,
         # so changing this file never changes an existing world (delete that file to re-apply; new chunks may then not match old ones).
         # Worlds made with bridge 3.x keep their 3.x behaviour (all options below 'american_share' off) until that file is deleted.

         # false = turn the bridge off (Still Life biomes will be misplaced on FreeTerraForged terrain)
         enabled=%s

         # still_life = Still Life's own latitude: one equator, colder towards the poles, cold beyond pole_distance
         # ftf        = FreeTerraForged's temperature (repeating bands from its climate settings, altitude cooling)
         latitude=%s

         # blocks from the equator to the coldest band (latitude=still_life only). Still Life uses 40000.
         pole_distance=%s

         # share of FreeTerraForged continents that get Still Life's American biomes (the rest get Afro-Eurasian ones), 0 to 1
         american_share=%s

         # --- Swamps -------------------------------------------------------------------------------------------
         # true = very humid, low land gets Still Life's swamp/marsh/fen biomes even outside FreeTerraForged wetlands
         humid_swamps=%s
         # FreeTerraForged humidity (-1 to 1) from which humid lowland counts as swamp land (higher = rarer swamps)
         swamp_humidity=%s
         # highest land that can become swamp, on Lithosphere's height scale (63 = sea, 74 = lowest quarter of land, 84 = median land). Coasts never become swamp.
         swamp_max_height=%s
         # Still Life's dry-climate wetlands, on continents with Still Life's American biomes (the Afro-Eurasian set has none):
         # aspen swamps on cool, fairly dry flat lowland; salt lakes (white salt flats) on warm-to-hot, dry flat land 350+ blocks
         # inland (up to a little above swamp_max_height), plus every FreeTerraForged lake in that climate and its shore.
         # They come in patches a few hundred blocks across, on flat ground only.
         # 0 = only where a FreeTerraForged river wetland happens to fall there, 1 = about a quarter of that land, up to 2 = about half
         aspen_swamps=%s
         salt_lakes=%s
         # true = FreeTerraForged lakes (the widened stretches of its rivers) and their shores in the salt-lake climate become
         #        salt lakes, on both continent sets (Still Life itself only has salt lakes in its American set). Needs salt_lakes above 0.
         salt_lakes_on_lakes=%s
         # true = shallow pools of standing water in Still Life's swamps, marshes, bogs, fens, muskegs and other wetlands at any
         #        height (Still Life only floods them at sea level, where Lithosphere's terrain puts them). Pools only form where
         #        the ground holds them, so water never spills down slopes.
         wetland_pools=%s

         # --- Snow ---------------------------------------------------------------------------------------------
         # true = Still Life's snow on peaks is kept at every altitude: FreeTerraForged's erosion no longer turns it
         #        into stone/tuff/moss above its rock line, and its snow decorator only clears snow off cliffs
         snow_caps=%s
         # slope above which snow is cleared (bare cliffs). 0 = use FreeTerraForged's rock steepness from the preset
         snow_cliff_steepness=%s

         # --- Rivers -------------------------------------------------------------------------------------------
         # true = rivers no longer cut through mountains: the part of a river's course that crosses mountains is
         #        dropped, so the river starts (fades in) below them. Branches that would join a dropped part are dropped too.
         river_headwaters=%s
         # ground at least this many blocks above the river's water level counts as a mountain on its course
         river_mountain_height=%s
         # blocks over which a shortened river fades in from a dry valley to a full river
         river_fade_in=%s
         # most blocks a river valley may lower the land away from the river (keeps mountain sides from being sheared off).
         # Wetlands also stay off ground more than half this height above their water. 0 = unlimited (FreeTerraForged's own behaviour)
         river_flank_max_cut=%s
         # next to the river the limit relaxes along this slope, so valley sides are an even slope, never a wall:
         # blocks up per block out from the riverbank (1 = 45 degrees, 0.5 = gentler and wider, 2 = steeper)
         river_flank_slope=%s
         # true = FreeTerraForged lakes are filled to their banks: the lake bed is shaped to the full width FreeTerraForged clears
         #        for the lake, so the water reaches the foot of the bank. false = FreeTerraForged's own lakes, which leave a
         #        dry shelf at water level (often 20-40 blocks wide) between the water and the bank.
         lake_fill=%s
         # least water depth in blocks over a filled lake's bed, right up to the bank (lake_fill only). 1 = very shallow, pale margins
         # (white over salt crust), 2 = shallow but clearly water
         lake_min_depth=%s

         # --- Terrain ------------------------------------------------------------------------------------------
         # On UPLIFT continents FreeTerraForged lifts the land towards plate borders but drops that lift to almost nothing in a band
         # along every coast, so raised land ends in a long cliff (often 40-100 blocks) running parallel to the coast. This makes the
         # lift climb back over this many blocks from that band instead, turning the cliff into a slope. Coastlines, the land seaward
         # of the old cliff and everything farther inland are unchanged. 0 = FreeTerraForged's cliffs.
         uplift_smoothing=%s

         # --- VanillaBackport biomes -----------------------------------------------------------------------------
         # How common each one is in Still Life's layout: 1 = the standard amount, 0 = none, up to 2 = about double.
         # (Pale Garden replaces the most humid temperate old-growth / mixed-wood forest, Dappled Forest the drier cool
         # temperate forests, Sulfur Caves part of Still Life's barren caves.) Needs Still Life 0.3.2-ftf or later.
         pale_garden=%s
         dappled_forest=%s
         sulfur_caves=%s
         """,
         WorldLock.FILE_NAME,
         enabled,
         stillLife ? "still_life" : "ftf",
         plain(pole),
         plain(share),
         swamps,
         plain(swampHumidity),
         plain(swampMaxHeight),
         plain(aspen),
         plain(salt),
         saltOnLakes,
         pools,
         snowCaps,
         plain(snowCliff),
         headwaters,
         plain(mountain),
         plain(fadeIn),
         plain(flank),
         plain(flankSlope),
         lakeFill,
         plain(lakeMinDepth),
         plain(upliftSmoothing),
         plain(pale),
         plain(dappled),
         plain(sulfur)
      );

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

   static boolean bool(Properties p, String key, boolean def) {
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

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
      double pale = number(p, "pale_garden", d.paleGarden(), 0.0, 2.0);
      double dappled = number(p, "dappled_forest", d.dappledForest(), 0.0, 2.0);
      double sulfur = number(p, "sulfur_caves", d.sulfurCaves(), 0.0, 2.0);
      double aspen = number(p, "aspen_swamps", d.aspenSwamps(), 0.0, 2.0);
      double salt = number(p, "salt_lakes", d.saltLakes(), 0.0, 2.0);
      boolean pools = bool(p, "wetland_pools", d.wetlandPools());
      double upliftSmoothing = number(p, "uplift_smoothing", d.upliftSmoothing(), 0.0, 4000.0);
      double wetlandMaxHeight = number(p, "wetland_max_height", d.wetlandMaxHeight(), 0.0, 2048.0);
      boolean keepWetlandBiomes = bool(p, "keep_wetland_biomes", d.keepWetlandBiomes());
      boolean sulfurRock = bool(p, "sulfur_rock", d.sulfurRock());
      double beachMaxHeight = number(p, "beach_max_height", d.beachMaxHeight(), 0.0, 256.0);
      double beachShallows = number(p, "beach_shallows", d.beachShallows(), 0.0, 32.0);
      double saltLakeWater = number(p, "salt_lake_water", d.saltLakeWater(), 0.0, 0.8);
      double saltLakeMaxRelief = number(p, "salt_lake_max_relief", d.saltLakeMaxRelief(), 1.0, 16.0);
      boolean saltLakeSlopePools = bool(p, "salt_lake_slope_pools", d.saltLakeSlopePools());
      double biomeSetBlend = number(p, "biome_set_blend", d.biomeSetBlend(), 0.0, 2048.0);
      settings = new Settings(
         stillLife, pole, share, swamps, swampHumidity, swampMaxHeight, snowCaps, snowCliff, pale, dappled, sulfur,
         aspen, salt, pools, upliftSmoothing, wetlandMaxHeight, keepWetlandBiomes, sulfurRock, beachMaxHeight, beachShallows, saltLakeWater,
         saltLakeMaxRelief, saltLakeSlopePools, biomeSetBlend
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
         # where two continents with different biome sets share land, the switch is spread over about this many blocks as an
         # irregular, dithered edge (like other biome edges) instead of a straight line along the continent border. 0 = sharp
         biome_set_blend=%s

         # --- Beaches -------------------------------------------------------------------------------------------
         # coastal land more than this many blocks above the sea never becomes a beach biome (Still Life only lays sand or gravel
         # below Y 65, so higher "beaches" are grassland; in Still Life's own worlds 90%% of beach land is within 9 blocks of the
         # sea). 0 = no limit (beach biomes up whole coastal slopes, as before bridge 5.0)
         beach_max_height=%s
         # shallow sea up to this many blocks deep gets beach biomes (sand or gravel floor), as in Still Life's own worlds, where
         # most shallow coastal water is beach. 0 = ocean biomes right up to the shore (as before bridge 5.0)
         beach_shallows=%s

         # --- Swamps -------------------------------------------------------------------------------------------
         # true = very humid, low land gets Still Life's swamp/marsh/fen biomes even outside FreeTerraForged wetlands
         humid_swamps=%s
         # FreeTerraForged humidity (-1 to 1) from which humid lowland counts as swamp land (higher = rarer swamps)
         swamp_humidity=%s
         # highest land that can become swamp, on Lithosphere's height scale (63 = sea, 74 = lowest quarter of land, 84 = median land). Coasts never become swamp.
         swamp_max_height=%s
         # Still Life's dry-climate wetlands, on continents with Still Life's American biomes (the Afro-Eurasian set has none):
         # aspen swamps on cool, fairly dry flat lowland; salt lakes (white salt flats with shallow pools of water) on warm-to-hot,
         # dry flat land 350+ blocks inland (up to a little above swamp_max_height).
         # They come in patches a few hundred blocks across, on flat ground only.
         # 0 = only where a FreeTerraForged river wetland happens to fall there, 1 = about a quarter of that land, up to 2 = about half
         aspen_swamps=%s
         salt_lakes=%s
         # true = shallow pools of standing water in Still Life's swamps, marshes, bogs, fens, muskegs, salt lakes and other
         #        wetlands at any height (Still Life only floods them at sea level, where Lithosphere's terrain puts them). Water only
         #        sits on flat ground (level for 2 blocks around), so it never spills or lines the steps of a slope; the sloped parts
         #        of a pool patch become mud.
         wetland_pools=%s
         # share of a salt lake's flat ground under one block of water over white calcite, in wide irregular sheets with calcite
         # islands (Still Life's own salt lakes: about a fifth). Water only sits on flat ground, so on stepped flats it comes out a
         # little lower. 0 = dry salt flats. Needs wetland_pools=true.
         salt_lake_water=%s
         # salt lakes only form where the ground varies by at most this many blocks over 32 (1 to 16). FreeTerraForged's "flat"
         # land is often a gentle slope of 1-block terraces, where water can only lie in narrow bands along each step, so the
         # default 2 keeps salt lakes to properly flat ground (wide sheets of water); 4 = more, terraced salt lakes (as in 5.0).
         # Aspen swamps always use 4.
         salt_lake_max_relief=%s
         # true = on stepped salt-lake ground (not one level for 6 blocks around) the water comes as small round pools; the wide
         #        sheets with calcite islands stay on wide level ground. false = sheets only, so stepped ground stays dry
         salt_lake_slope_pools=%s
         # FreeTerraForged flattens everything within a wetland's radius down to its water, which digs pits (up to hundreds of
         # blocks deep) where a wetland touches a hill or mountain. Ground more than this many blocks above the wetland's water is
         # left alone, ground up to half of it is flooded normally, and in between the wetland fades out. 0 = FreeTerraForged's own
         wetland_max_height=%s
         # true = water in FreeTerraForged's wetlands keeps the Still Life wetland biome chosen there (muskegs, floodplain, flooded
         #        prairie, salt lake...). FreeTerraForged 1.0.0027+ replaces wetland-water biomes it does not recognise as wetlands,
         #        which leaves patches of bog, fen or river inside them.
         keep_wetland_biomes=%s

         # --- Snow ---------------------------------------------------------------------------------------------
         # true = Still Life's snow on peaks is kept at every altitude: FreeTerraForged's erosion no longer turns it
         #        into stone/tuff/moss above its rock line, and its snow decorator only clears snow off cliffs
         snow_caps=%s
         # slope above which snow is cleared (bare cliffs). 0 = use FreeTerraForged's rock steepness from the preset
         snow_cliff_steepness=%s

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
         # true = VanillaBackport's Sulfur Caves get their sulfur and cinnabar rock (FreeTerraForged skips the surface rule that
         #        paints it, so without this they are plain stone)
         sulfur_rock=%s
         """,
         WorldLock.FILE_NAME,
         enabled,
         stillLife ? "still_life" : "ftf",
         plain(pole),
         plain(share),
         plain(biomeSetBlend),
         plain(beachMaxHeight),
         plain(beachShallows),
         swamps,
         plain(swampHumidity),
         plain(swampMaxHeight),
         plain(aspen),
         plain(salt),
         pools,
         plain(saltLakeWater),
         plain(saltLakeMaxRelief),
         saltLakeSlopePools,
         plain(wetlandMaxHeight),
         keepWetlandBiomes,
         snowCaps,
         plain(snowCliff),
         plain(upliftSmoothing),
         plain(pale),
         plain(dappled),
         plain(sulfur),
         sulfurRock
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

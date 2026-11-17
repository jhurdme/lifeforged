package stilllife.ftfbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** The settings a world was created with, saved next to its level.dat. */
final class WorldLock {
   static final String FILE_NAME = "stilllife_ftf_bridge.json";
   private static final int FORMAT = 11;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static volatile WorldLock.Lock current;

   private WorldLock() {
   }

   static WorldLock.Lock current() {
      return current;
   }

   /** Settings of the running world, or of the properties file when no world is running (world-creation preview). */
   static Settings activeSettings() {
      WorldLock.Lock lock = current;
      return lock != null ? lock.settings : BridgeConfig.settings();
   }

   static void open(MinecraftServer server) {
      Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME).normalize();
      WorldLock.Lock lock;
      if (Files.exists(file)) {
         try {
            JsonObject o = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            boolean stillLife = "still_life".equals(o.get("latitude").getAsString());
            double pole = o.get("pole_distance").getAsDouble();
            double share = o.get("american_share").getAsDouble();
            int format = o.has("format") ? o.get("format").getAsInt() : 1;
            Settings s;
            if (format < 2) {
               s = Settings.legacy(stillLife, pole, share);
               StillLifeFtfBridge.LOG.info(
                  "[{}] this world was made with bridge 3.x: keeping its 3.x terrain (no swamp/snow/river changes). Delete {} to apply the new options to new chunks.",
                  StillLifeFtfBridge.MOD_ID, file.getFileName()
               );
            } else {
               Settings d = Settings.DEFAULT;
               s = new Settings(
                  stillLife,
                  pole,
                  share,
                  bool(o, "humid_swamps", false),
                  num(o, "swamp_humidity", d.swampHumidity()),
                  num(o, "swamp_max_height", d.swampMaxHeight()),
                  bool(o, "snow_caps", false),
                  num(o, "snow_cliff_steepness", d.snowCliffSteepness()),
                  // 4.0 / 4.1 worlds (format 2) were made with Still Life 0.3.1, whose saved layout already has these biomes
                  num(o, "pale_garden", 1.0),
                  num(o, "dappled_forest", 1.0),
                  num(o, "sulfur_caves", 1.0),
                  // worlds from before bridge 4.4 (format 2/3) keep generating without the dry wetlands
                  num(o, "aspen_swamps", 0.0),
                  num(o, "salt_lakes", 0.0),
                  // and without wetland pools (format 2-4)
                  bool(o, "wetland_pools", false),
                  // uplift smoothing came with bridge 4.8 (format 9)
                  num(o, "uplift_smoothing", 0.0),
                  // bridge 4.x tied the wetland hillside guard to river_flank_max_cut (0 in 3.x / 4.0 worlds = no guard)
                  num(o, "wetland_max_height", num(o, "river_flank_max_cut", format >= 10 ? d.wetlandMaxHeight() : 0.0)),
                  // FTF up to 1.0.0025 kept every wetland biome; 5.0 keeps them by default
                  bool(o, "keep_wetland_biomes", true),
                  // sulfur rock came with bridge 4.3 for every world
                  bool(o, "sulfur_rock", true),
                  // beach limits came with bridge 5.0 (format 10)
                  num(o, "beach_max_height", format >= 10 ? d.beachMaxHeight() : 0.0),
                  num(o, "beach_shallows", format >= 10 ? d.beachShallows() : 0.0),
                  // water on salt lakes came with bridge 5.1 (format 10 worlds made with 5.0 get it in new chunks)
                  // 0.2 in bridge 5.1 test builds (format 10), 0.35 from format 11
                  num(o, "salt_lake_water", format >= 11 ? d.saltLakeWater() : format >= 10 ? 0.2 : 0.0),
                  num(o, "salt_lake_max_relief", 4.0),
                  bool(o, "salt_lake_slope_pools", false),
                  // blended biome-set borders came with format 11; older worlds keep the sharp switch
                  num(o, "biome_set_blend", format >= 11 ? d.biomeSetBlend() : 0.0)
               );
            }

            lock = new WorldLock.Lock(file, s, doubles(o, "heights"), doubles(o, "coast_edge"), o.has("terrain") ? o.get("terrain").getAsString() : null, format);
            StillLifeFtfBridge.LOG.info("[{}] using this world's saved settings: {}", StillLifeFtfBridge.MOD_ID, s);
         } catch (Exception e) {
            throw new IllegalStateException(
               "[" + StillLifeFtfBridge.MOD_ID + "] " + file + " is unreadable. Fix it, or delete it to re-apply the current config (new chunks may then not match old ones).",
               e
            );
         }
      } else {
         lock = new WorldLock.Lock(file, BridgeConfig.settings(), null, null, null, FORMAT);
         save(lock);
         StillLifeFtfBridge.LOG.info("[{}] saved settings for this world: {}", StillLifeFtfBridge.MOD_ID, lock.settings);
      }

      current = lock;
   }

   static void close() {
      current = null;
   }

   static synchronized void storeSurvey(WorldLock.Lock lock, StillLifeClimateBridge.Survey s, String terrain) {
      lock.heights = s.heights();
      lock.edge = s.edge();
      lock.terrain = terrain;
      save(lock);
   }

   private static synchronized void save(WorldLock.Lock lock) {
      Settings s = lock.settings;
      JsonObject o = new JsonObject();
      o.addProperty("format", lock.format);
      o.addProperty("latitude", s.stillLifeLatitude() ? "still_life" : "ftf");
      o.addProperty("pole_distance", s.poleDistance());
      o.addProperty("american_share", s.americanShare());
      if (lock.format >= 2) {
         o.addProperty("humid_swamps", s.humidSwamps());
         o.addProperty("swamp_humidity", s.swampHumidity());
         o.addProperty("swamp_max_height", s.swampMaxHeight());
         o.addProperty("snow_caps", s.snowCaps());
         o.addProperty("snow_cliff_steepness", s.snowCliffSteepness());
         if (lock.format >= 3) {
            o.addProperty("pale_garden", s.paleGarden());
            o.addProperty("dappled_forest", s.dappledForest());
            o.addProperty("sulfur_caves", s.sulfurCaves());
         }

         if (lock.format >= 4) {
            o.addProperty("aspen_swamps", s.aspenSwamps());
            o.addProperty("salt_lakes", s.saltLakes());
         }

         if (lock.format >= 5) {
            o.addProperty("wetland_pools", s.wetlandPools());
         }

         if (lock.format >= 9) {
            o.addProperty("uplift_smoothing", s.upliftSmoothing());
         }

         // older worlds keep their 4.x value in river_flank_max_cut, which is read back as wetland_max_height
         if (lock.format >= 10) {
            o.addProperty("wetland_max_height", s.wetlandMaxHeight());
            o.addProperty("keep_wetland_biomes", s.keepWetlandBiomes());
            o.addProperty("sulfur_rock", s.sulfurRock());
            o.addProperty("beach_max_height", s.beachMaxHeight());
            o.addProperty("beach_shallows", s.beachShallows());
            o.addProperty("salt_lake_water", s.saltLakeWater());
         }

         if (lock.format >= 11) {
            o.addProperty("salt_lake_max_relief", s.saltLakeMaxRelief());
            o.addProperty("salt_lake_slope_pools", s.saltLakeSlopePools());
            o.addProperty("biome_set_blend", s.biomeSetBlend());
         }
      }

      if (lock.heights != null) {
         o.add("heights", array(lock.heights));
      }

      if (lock.edge != null) {
         o.add("coast_edge", array(lock.edge));
      }

      if (lock.terrain != null) {
         o.addProperty("terrain", lock.terrain);
      }

      try {
         Path tmp = lock.file.resolveSibling(FILE_NAME + ".tmp");
         Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
         Files.move(tmp, lock.file, StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         StillLifeFtfBridge.LOG.error("[{}] could not save {}", StillLifeFtfBridge.MOD_ID, lock.file, e);
      }
   }

   private static boolean bool(JsonObject o, String key, boolean def) {
      return o.has(key) ? o.get(key).getAsBoolean() : def;
   }

   private static double num(JsonObject o, String key, double def) {
      return o.has(key) ? o.get(key).getAsDouble() : def;
   }

   private static JsonArray array(double[] values) {
      JsonArray a = new JsonArray();
      for (double v : values) {
         a.add(v);
      }

      return a;
   }

   private static double[] doubles(JsonObject o, String key) {
      if (!o.has(key)) {
         return null;
      }

      JsonArray a = o.getAsJsonArray(key);
      double[] out = new double[a.size()];
      for (int i = 0; i < out.length; i++) {
         out[i] = a.get(i).getAsDouble();
      }

      return out;
   }

   static final class Lock {
      final Path file;
      final Settings settings;
      final int format;
      volatile double[] heights;
      volatile double[] edge;
      volatile String terrain;

      Lock(Path file, Settings settings, double[] heights, double[] edge, String terrain, int format) {
         this.file = file;
         this.settings = settings;
         this.heights = heights;
         this.edge = edge;
         this.terrain = terrain;
         this.format = format;
      }
   }
}

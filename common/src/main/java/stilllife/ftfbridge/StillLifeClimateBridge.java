package stilllife.ftfbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import etcodehome.freeterraforged.data.worldgen.preset.settings.Preset;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.biome.ClimateQuerySemantics;
import etcodehome.freeterraforged.world.worldgen.biome.FTFClimateSampler;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.terrain.Terrain;
import etcodehome.freeterraforged.world.worldgen.cell.terrain.TerrainCategory;
import etcodehome.freeterraforged.world.worldgen.cell.terrain.TerrainType;
import etcodehome.freeterraforged.world.worldgen.densityfunction.CellSampler;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.Climate.TargetPoint;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class StillLifeClimateBridge {
   /** Still Life's beach entries: weirdness magnitude 0.05-0.08 and continentalness up to 0.02. */
   private static final double BEACH_W_MIN = 0.05;
   private static final double BEACH_W_MAX = 0.08;
   private static final float BEACH_C_MAX = 0.02F;
   /** Inside Still Life's next weirdness band (0.08-0.267), clear of the beach band's edge. */
   private static final double ABOVE_BEACH_W = 0.09;
   /** The middle of the beach band. */
   private static final double SHORE_W = 0.065;
   static final double[] Y_LITHO = new double[]{63.0, 66.0, 74.0, 84.0, 109.0, 142.0, 172.0, 201.0, 223.0, 256.0, 292.0, 349.0};
   private static final double[] LITHO_LAND_PERCENTILES = new double[]{0.0, 9.3, 26.3, 50.7, 75.3, 90.0, 95.0, 97.0, 98.0, 99.0, 99.5, 99.9};
   private static final double[] DEFAULT_HEIGHTS = new double[]{63.0, 67.0, 80.0, 106.0, 140.0, 166.0, 182.0, 194.0, 202.0, 218.0, 236.0, 275.0};
   private static final double[] CONT_DISTANCES = new double[]{-1.0, 0.0, 50.0, 110.0, 170.0, 320.0, 690.0, 1180.0, 1380.0};
   private static final double[] CONT = new double[]{-0.15, -0.109, 0.02, 0.141, 0.158, 0.167, 0.189, 0.364, 0.418};
   private static final double[] DEFAULT_EDGE = new double[]{0.2, 0.235, 0.255, 0.269, 0.299, 0.367, 0.525, 0.708, 0.793};
   private static final float FTF_DEPTH_PER_BLOCK = 0.0078125F;
   private static final double LITHO_BOTTOM = -64.0;
   private static final double[] LITHO_DEPTH = new double[]{
      0.0, 0.08, 0.153, 0.226, 0.296, 0.363, 0.426, 0.488, 0.546, 0.606, 0.665, 0.724, 0.78, 0.842, 0.903, 0.959, 1.035, 1.09, 1.174
   };
   private static final float[] W_LOC = new float[]{0.025F, 0.0375F, 0.75F, 0.2F, 0.95F, 0.98F};
   private static final float[] W_VAL = new float[]{0.0F, 0.05F, 0.1F, 0.15F, 0.62F, 0.63F};
   private static final float[] W_DER = new float[]{0.0F, 0.43F, 0.0F, 0.35F, 1.1673189F, 0.0F};
   // Climate boxes {Tmin, Tmax, Hmin, Hmax, Cmin, Cmax, Wmin, Wmax} of Still Life's aspen_swamp and salt_lake in its American
   // wetland band (erosion -0.225..-0.005, depth 0), from Still Life 0.3.x's layout. A point inside one gets that biome.
   private static final float[][] ASPEN_SWAMP = {
      {-0.435F, -0.345F, -0.6F, -0.105F, -1.2F, 1.0F, -0.26666668F, -0.08F},
      {-0.435F, -0.345F, -0.6F, -0.105F, -1.05F, 1.0F, -0.08F, -0.05F},
      {-0.435F, -0.4F, -0.6F, -0.105F, -0.19F, 1.0F, -0.05F, 0.26666668F},
      {-0.4F, -0.345F, -0.6F, -0.105F, -0.19F, 1.0F, -0.05F, 0.05F},
      {-0.4F, -0.345F, -0.405F, -0.105F, -1.05F, 1.0F, 0.05F, 0.26666668F}
   };
   private static final float[][] SALT_LAKE = {
      {0.17F, 0.85F, -1.0F, -0.6F, 0.17F, 1.0F, -0.26666668F, 0.26666668F},
      {0.205F, 0.825F, -0.6F, -0.405F, 0.17F, 1.0F, -0.26666668F, 0.26666668F},
      {0.23F, 0.57F, -0.405F, -0.105F, 0.17F, 1.0F, -0.26666668F, 0.26666668F},
      {0.26F, 0.52F, -0.105F, 0.18F, 0.17F, 1.0F, -0.26666668F, 0.26666668F},
      {0.23F, 0.52F, -0.105F, 0.035F, 0.17F, 1.0F, 0.05F, 0.26666668F},
      {0.3F, 0.52F, 0.18F, 0.235F, 0.17F, 1.0F, -0.26666668F, 0.26666668F},
      {0.345F, 0.485F, 0.235F, 0.33F, 0.17F, 1.0F, -0.26666668F, 0.26666668F},
      {0.485F, 0.52F, 0.235F, 0.33F, 0.17F, 1.0F, -0.26666668F, 0.05F},
      {0.57F, 0.74F, -0.405F, -0.275F, 0.17F, 1.0F, -0.26666668F, 0.26666668F}
   };
   // flat ground for dry wetlands: at most FLAT_RELIEF blocks between the column and the ground 16 blocks away in 4 directions
   private static final int[] FLAT_DX = {16, -16, 0, 0};
   private static final int[] FLAT_DZ = {0, 0, 16, -16};
   private static final double FLAT_RELIEF = 4.0;
   /** Salt lakes may sit this much higher than swamp_max_height (dry inland basins are often raised). */
   private static final double SALT_LAKE_EXTRA_HEIGHT = 25.0;
   // share of the patch noise above each threshold (measured over several seeds)
   private static final double[] MASK_COVER = new double[]{0.0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6};
   private static final double[] MASK_THRESHOLD = new double[]{
      1.1767, 0.4938, 0.389, 0.3166, 0.2567, 0.2069, 0.1609, 0.1181, 0.0773, 0.0371, -0.0019, -0.0409, -0.081
   };
   // Still Life heights (Lithosphere's scale) where its alpine bands (weirdness 0.4) and its snowy peaks cap (0.5667) start
   static final double YL_ALPINE = bandStart(0.4);
   static final double YL_PEAKS = bandStart(0.56666666);
   // peak_line_variation acts from here up (full from PEAK_SHIFT_FULL), so lowland bands, beaches and wetlands never move
   private static final double PEAK_SHIFT_FROM = 140.0;
   private static final double PEAK_SHIFT_FULL = 175.0;
   // 5-95% range of 0.7 * big + 0.3 * mid (measured)
   private static final double PEAK_NOISE_RANGE = 0.375;
   // standard deviation of the dither noise (Still Life's surface_dithered settings; measured)
   private static final double CAP_DITHER_STD = 0.365;
   private static volatile Sampler lastSampler;
   private static final Map<GeneratorContext, StillLifeClimateBridge.World> WORLDS = new WeakHashMap<>();
   private static volatile StillLifeClimateBridge.World last;
   private static final Map<String, CompletableFuture<StillLifeClimateBridge.Survey>> SURVEYS = new ConcurrentHashMap<>();

   private StillLifeClimateBridge() {
   }

   private static StillLifeClimateBridge.World world(long seed, Object preset, GeneratorContext ctx) {
      WorldLock.Lock lock = WorldLock.current();
      StillLifeClimateBridge.World w = last;
      if (w != null && w.ctx == ctx && w.lock == lock && w.seed == seed) {
         return w;
      }

      synchronized (WORLDS) {
         w = WORLDS.get(ctx);
      }

      if (w == null || w.lock != lock || w.seed != seed) {
         w = build(seed, preset, ctx, lock);
         synchronized (WORLDS) {
            WORLDS.put(ctx, w);
         }
      }

      last = w;
      return w;
   }

   private static StillLifeClimateBridge.World build(long seed, Object preset, GeneratorContext ctx, WorldLock.Lock lock) {
      String terrain = terrainFingerprint(preset, ctx.levels);
      StillLifeClimateBridge.Survey s;
      if (lock != null && valid(lock.heights, Y_LITHO.length) && valid(lock.edge, CONT.length) && (lock.terrain == null || lock.terrain.equals(terrain))) {
         s = new StillLifeClimateBridge.Survey(lock.heights, lock.edge);
         if (lock.terrain == null) {
            WorldLock.storeSurvey(lock, s, terrain);
         }
      } else {
         if (lock != null && lock.heights != null) {
            StillLifeFtfBridge.LOG
               .warn("[{}] this world's FreeTerraForged terrain settings changed since it was last measured; measuring again", "stilllife_ftf_bridge");
         }

         s = survey(seed + "|" + terrain, ctx);
         if (lock != null) {
            WorldLock.storeSurvey(lock, s, terrain);
         }
      }

      Settings settings = lock != null ? lock.settings : BridgeConfig.settings();
      NormalNoise variant = NormalNoise.create(new XoroshiroRandomSource(seed ^ 1460736510L), -9, new double[]{1.4, 1.2, 1.3, 1.3, 0.2});
      NormalNoise temperature = NormalNoise.create(
         new XoroshiroRandomSource(seed ^ 2117770257L), -13, new double[]{1.25, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5}
      );
      double equatorZ = (temperature.getValue(0.0, 0.0, 0.0) * 0.25 - 0.5) * 43000.0;
      NormalNoise dryWetlands = NormalNoise.create(new XoroshiroRandomSource(seed ^ 7640891576956012809L), -9, new double[]{1.0, 1.0, 0.5});
      NormalNoise setWarpX = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x2545F4914F6CDD1DL), -8, new double[]{1.0, 0.6, 0.35});
      NormalNoise setWarpZ = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x9E3779B97F4A7C15L), -8, new double[]{1.0, 0.6, 0.35});
      // biome_sets=regions/halves: the size of an average continent's land, measured once and kept in the world's settings
      double landArea = 0.0;
      double continentSpacing = 0.0;
      float landControlPoint = 1.0F;
      try {
         continentSpacing = 4.0 * ctx.preset.world().continent.continentScale;
         landControlPoint = ctx.preset.world().controlPoints.beach;
      } catch (Throwable t) {
         StillLifeFtfBridge.LOG.warn("[{}] could not read FreeTerraForged's continent settings; biome sets by regions/halves use averages", StillLifeFtfBridge.MOD_ID);
      }
      if (!settings.biomeSets().equals(Settings.SETS_CONTINENTS)) {
         landArea = lock != null && lock.landArea > 0.0 ? lock.landArea : measureLandArea(ctx);
         if (lock != null && !(lock.landArea > 0.0)) {
            WorldLock.storeLandArea(lock, landArea);
         }
         StillLifeFtfBridge.LOG.info(
            "[{}] biome sets by {}: average continent land {} km2{}", StillLifeFtfBridge.MOD_ID, settings.biomeSets(),
            Math.round(landArea / 1.0E6), settings.biomeSets().equals(Settings.SETS_REGIONS)
               ? ", regions about " + Math.round(Math.sqrt(settings.biomeSetRegionSize() / 100.0 * landArea)) + " blocks across (" + BridgeConfig.plain(settings.biomeSetRegionSize()) + "% each)"
               : ""
         );
      } else {
         landArea = 1.0;
      }
      NormalNoise peakBig = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x5DEECE66DL), -10, new double[]{1.0, 1.0});
      NormalNoise peakMid = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x632BE59BD9B4E019L), -8, new double[]{1.0, 0.5});
      NormalNoise capDither = NormalNoise.create(
         new XoroshiroRandomSource(seed ^ 0x2F7A3C5B1D9E4F61L), -6, new double[]{1.0, 1.25, 1.5, 1.5, 1.5, 1.75, 1.75, 1.75, 2.0, 1.75, 1.5}
      );
      return new StillLifeClimateBridge.World(
         ctx, seed, lock, settings, variant, temperature, equatorZ, s.heights, s.edge, dryWetlands,
         maskThreshold(settings.aspenSwamps()), maskThreshold(settings.saltLakes()), setWarpX, setWarpZ,
         peakBig, peakMid, capDither, fromLitho(s.heights, YL_PEAKS), fromLitho(s.heights, YL_ALPINE),
         Math.sqrt(settings.biomeSetRegionSize() / 100.0 * landArea), Math.sqrt(landArea), continentSpacing, landControlPoint,
         NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x3C6EF372FE94F82BL), -10, new double[]{1.0, 0.5}),
         new ConcurrentHashMap<>()
      );
   }

   private static StillLifeClimateBridge.Survey survey(String key, GeneratorContext ctx) {
      CompletableFuture<StillLifeClimateBridge.Survey> f = SURVEYS.get(key);
      if (f == null) {
         if (SURVEYS.size() > 64) {
            SURVEYS.clear();
         }

         CompletableFuture<StillLifeClimateBridge.Survey> mine = new CompletableFuture<>();
         f = SURVEYS.putIfAbsent(key, mine);
         if (f == null) {
            f = mine;

            try {
               mine.complete(new StillLifeClimateBridge.Survey(calibrateHeights(ctx), calibrateCoast(ctx)));
            } catch (Throwable t) {
               SURVEYS.remove(key, mine);
               mine.completeExceptionally(t);
            }
         }
      }

      return f.join();
   }

   private static boolean valid(double[] values, int length) {
      if (values != null && values.length == length) {
         for (int i = 1; i < length; i++) {
            if (!(values[i] > values[i - 1])) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static TargetPoint apply(Sampler sampler, TargetPoint target, int qx, int qy, int qz) {
      if (BridgeConfig.enabled() && ((Object)sampler) instanceof FTFClimateSampler ftf) {
         ClimateQuerySemantics semantics = ftf.climateQuerySemantics();
         GeneratorContext ctx = semantics.surfaceContext();
         if (ctx == null) {
            return target;
         }

         StillLifeClimateBridge.World w = world(semantics.seed(), semantics.preset(), ctx);
         int bx = QuartPos.toBlock(qx);
         int bz = QuartPos.toBlock(qz);
         Cell cell = CellSampler.getCachedOrLookup(ctx.lookup, bx, bz, false);
         if (lastSampler != sampler) {
            lastSampler = sampler;
         }
         Levels levels = ctx.levels;
         double[] heights = w.heights;
         Terrain terrain = cell.terrain;
         boolean ocean = isSea(cell, levels);
         boolean freshWater = terrain.isRiver() || terrain.isLake();
         boolean submerged = ocean || freshWater || cell.height <= levels.water;
         double y = cell.height * levels.terrainScaleFactor;
         long continentalness = !ocean && terrain != TerrainType.MUSHROOM_FIELDS
            ? Climate.quantizeCoord((float)lerp(w.edge, CONT, cell.controlPoint))
            : target.continentalness();
         double yL = toLitho(heights, y);
         // the cell's values are read before the neighbour lookups below, which may reuse it
         final float cellHeight = cell.height;
         final float cellContinent = cell.continentId;
         final float cellControl = cell.controlPoint;
         // peak_line_variation: the high-mountain bands start at a wandering height instead of a level line
         double yBand = yL;
         if (!submerged && w.settings.peakLineVariation() > 0.0 && yL > PEAK_SHIFT_FROM) {
            double ramp = clamp((yL - PEAK_SHIFT_FROM) / (PEAK_SHIFT_FULL - PEAK_SHIFT_FROM), 0.0, 1.0);
            yBand = toLitho(heights, y + ramp * peakShift(w, ctx, qx, qz));
         }
         double magnitude = submerged ? 0.0 : ridgeMagnitude(yBand);
         // Beaches. Still Life's beach biomes are coastal points (continentalness up to 0.02) in the weirdness band 0.05-0.08. On
         // Lithosphere that band is land within ~12 blocks of the sea plus the shallow sea in front of it, and Still Life's beach
         // surface rule only lays sand or gravel below Y 65. The height mapping above squeezes FreeTerraForged's much taller
         // coastal land into that band, so beach biomes climbed 30-50 blocks up grassy slopes, while the sea stayed ocean.
         Settings bs = w.settings;
         if (!ocean && bs.beachMaxHeight() > 0.0 && magnitude >= BEACH_W_MIN && magnitude <= BEACH_W_MAX
            && Climate.unquantizeCoord(continentalness) < BEACH_C_MAX && (cellHeight - levels.water) / levels.unit > bs.beachMaxHeight()) {
            // coastal land too high above the sea for a beach: the band just above (the land's inland biome)
            magnitude = ABOVE_BEACH_W;
         } else if (ocean && bs.beachShallows() > 0.0 && !terrain.isDeepOcean() && (levels.water - cellHeight) / levels.unit <= bs.beachShallows()) {
            // shallow sea along the shore: beach biome, as in Still Life's own worlds (sand or gravel floor)
            magnitude = SHORE_W;
         }
         long weirdness = Climate.quantizeCoord((float)(w.variant.getValue(bx, 0.0, bz) >= 0.0 ? magnitude : -magnitude));
         double group = biomeSetGroup(w, ctx, cellContinent, bx, bz);
         double topo = (yL - 136.16) / 144.0;
         double aboveLowland = topo > -0.3 ? 1.0 : 0.0;
         boolean highland = terrain.getDelegate() == TerrainCategory.HIGHLAND;
         double orogeny = highland ? -0.747 : 0.198;
         // FTF wetlands always get Still Life's wetland climate band. With humid_swamps, very humid low land does too,
         // so Still Life's swamps/marshes/fens also appear in moisture-rich lowland away from FTF's river wetlands.
         // inland only (about 110+ blocks from the sea), so coasts keep their beaches
         double inland = lerp(w.edge, CONT, cellControl);
         float humidity = Climate.unquantizeCoord(target.humidity());
         boolean humidSwamp = w.settings.humidSwamps()
            && !submerged
            && !highland
            && !ocean
            && inland >= CONT[3]
            && yL <= w.settings.swampMaxHeight()
            && humidity >= w.settings.swampHumidity();
         long temperature = target.temperature();
         if (w.settings.stillLifeLatitude()) {
            double t = 0.2 * w.temperature.getValue(bx * 1.1, 0.0, bz * 1.1) + 1.0 - 2.0 * Math.abs(bz + w.equatorZ) / w.settings.poleDistance();
            temperature = Climate.quantizeCoord((float)t);
         }

         // Dry wetlands: Still Life's aspen swamps and salt lakes need its wetland climate together with fairly dry air, which
         // the humid-swamp rule never gives. On American-set continents, inland FLAT low ground whose climate falls in one of
         // their niches gets the wetland climate in patches (a few hundred blocks across) of the configured share.
         boolean dryWetland = false;
         if (!ocean && !highland && !submerged && !freshWater && inland >= CONT[3] && !terrain.isWetland() && !humidSwamp && group < 0.0) {
            float t = Climate.unquantizeCoord(temperature);
            float c = Climate.unquantizeCoord(continentalness);
            float wv = Climate.unquantizeCoord(weirdness);
            boolean aspen = yL <= w.settings.swampMaxHeight() && inside(ASPEN_SWAMP, t, humidity, c, wv);
            boolean saltFlat = !aspen && w.settings.saltLakes() > 0.0 && inside(SALT_LAKE, t, humidity, c, wv)
               && yL <= w.settings.swampMaxHeight() + SALT_LAKE_EXTRA_HEIGHT;
            double threshold = aspen ? w.aspenThreshold : saltFlat ? w.saltThreshold : Double.POSITIVE_INFINITY;
            if (threshold != Double.POSITIVE_INFINITY && w.dryWetlands.getValue(bx, 0.0, bz) > threshold) {
               // reads the neighbouring cells; `cell` may be reused by these lookups, so it is not used after this
               double lo = y;
               double hi = y;
               for (int k = 0; k < 4; k++) {
                  Cell n = CellSampler.getCachedOrLookup(ctx.lookup, bx + FLAT_DX[k], bz + FLAT_DZ[k], false);
                  double ny = n.height * levels.terrainScaleFactor;
                  lo = Math.min(lo, ny);
                  hi = Math.max(hi, ny);
               }

               dryWetland = hi - lo <= (saltFlat ? w.settings.saltLakeMaxRelief() : FLAT_RELIEF);
            }
         }

         double wetland = terrain.isWetland() || humidSwamp || dryWetland ? 1.0 : 0.0;
         double erosion = group + 0.49 * clamp(aboveLowland * orogeny + 0.06 + wetland, -1.0, 1.0);
         long depth = target.depth();
         float ftfDepth = Climate.unquantizeCoord(depth);
         if (ftfDepth > 0.0F) {
            // FTF's depth (1.0.0023+) is the share of the column below the surface, 1.5 x (surface - y) / (surface + world
            // depth). column_depth: the same share of the Lithosphere column (down to Y -64) under this column's Still Life
            // height, so cave biomes keep Still Life's order from the surface to bedrock in every column. Bridge 5.0-5.4 read
            // it as 1/128 per block, which is far too little under high ground and never reached Still Life's deepest band.
            double blocks = w.settings.columnDepth()
               ? Math.min(ftfDepth / 1.5, 1.0) * Math.max(yL - LITHO_BOTTOM, 1.0)
               : ftfDepth / FTF_DEPTH_PER_BLOCK;
            double i = blocks / 16.0;
            int lo = (int)Math.floor(i);
            double lithoDepth = lo >= LITHO_DEPTH.length - 1
               ? LITHO_DEPTH[LITHO_DEPTH.length - 1] + (blocks - 16.0 * (LITHO_DEPTH.length - 1)) / 200.0
               : LITHO_DEPTH[lo] + (LITHO_DEPTH[lo + 1] - LITHO_DEPTH[lo]) * (i - lo);
            depth = Climate.quantizeCoord((float)lithoDepth);
         }

         return new TargetPoint(temperature, target.humidity(), continentalness, Climate.quantizeCoord((float)erosion), depth, weirdness);
      } else {
         return target;
      }
   }

   private static boolean isSea(Cell cell, Levels levels) {
      Terrain t = cell.terrain;
      return t.isDeepOcean() || t.isShallowOcean() || t.getDelegate() == TerrainCategory.COAST && cell.height <= levels.water;
   }

   static double[] calibrateHeights(GeneratorContext ctx) {
      long t0 = System.currentTimeMillis();
      int half = 16384;
      int step = 192;
      Levels levels = ctx.levels;
      Cell cell = new Cell();
      double[] samples = new double[(2 * half / step + 1) * (2 * half / step + 1)];
      int count = 0;

      for (int z = -half; z <= half; z += step) {
         for (int x = -half; x <= half; x += step) {
            ctx.lookup.applyCell(cell.reset(), x, z, false, false);
            Terrain t = cell.terrain;
            if (!isSea(cell, levels) && !t.isRiver() && !t.isLake()) {
               double y = cell.height * levels.terrainScaleFactor;
               if (!(y <= levels.waterY + 0.5)) {
                  samples[count++] = y;
               }
            }
         }
      }

      double sea = levels.waterY + 1;
      double[] out = new double[Y_LITHO.length];
      if (count < 500) {
         StillLifeFtfBridge.LOG.warn("[{}] only {} land samples; using the default height table", "stilllife_ftf_bridge", count);

         for (int i = 0; i < out.length; i++) {
            out[i] = DEFAULT_HEIGHTS[i] + sea - DEFAULT_HEIGHTS[0];
         }

         return out;
      } else {
         Arrays.sort(samples, 0, count);

         for (int i = 0; i < out.length; i++) {
            out[i] = samples[(int)Math.min(count - 1, Math.round(LITHO_LAND_PERCENTILES[i] / 100.0 * (count - 1)))];
         }

         out[0] = sea;

         for (int i = 1; i < out.length; i++) {
            out[i] = Math.max(Math.round(out[i]), out[i - 1] + 1.0);
         }

         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < out.length; i++) {
            sb.append(i == 0 ? "" : ",").append((long)out[i]);
         }

         StillLifeFtfBridge.LOG
            .info("[{}] measured land heights from {} samples in {} ms: {}", new Object[]{"stilllife_ftf_bridge", count, System.currentTimeMillis() - t0, sb});
         return out;
      }
   }

   static double[] calibrateCoast(GeneratorContext ctx) {
      long t0 = System.currentTimeMillis();
      int step = 48;
      int cells = 160;
      int size = step * cells;
      int[][] centres = new int[][]{{0, 0}, {12000, 12000}, {-12000, 12000}, {12000, -12000}, {-12000, -12000}};
      Levels levels = ctx.levels;
      Cell cell = new Cell();
      int buckets = CONT_DISTANCES.length;
      FloatArrayList[] byDist = new FloatArrayList[buckets];

      for (int i = 0; i < buckets; i++) {
         byDist[i] = new FloatArrayList();
      }

      FloatArrayList nearShore = new FloatArrayList();
      float[] edge = new float[cells * cells];
      boolean[] sea = new boolean[cells * cells];
      double[] dist = new double[cells * cells];

      for (int[] c : centres) {
         int x0 = c[0] - size / 2;
         int z0 = c[1] - size / 2;
         boolean anySea = false;
         boolean anyLand = false;

         for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
               ctx.lookup.applyCell(cell.reset(), x0 + i * step, z0 + j * step, false, false);
               int k = j * cells + i;
               sea[k] = isSea(cell, levels);
               edge[k] = cell.controlPoint;
               anySea |= sea[k];
               anyLand |= !sea[k];
            }
         }

         if (anySea && anyLand) {
            chamferDistance(sea, cells, step, dist);

            for (int j = 0; j < cells; j++) {
               for (int i = 0; i < cells; i++) {
                  int k = j * cells + i;
                  if (!sea[k]) {
                     double border = (double)Math.min(Math.min(i, cells - 1 - i), Math.min(j, cells - 1 - j)) * step;
                     if (!(dist[k] > border)) {
                        if (dist[k] <= 64.0) {
                           nearShore.add(edge[k]);
                        }

                        for (int b = 1; b < buckets; b++) {
                           double target = CONT_DISTANCES[b];
                           double tol = Math.max(step, target * 0.1);
                           if (Math.abs(dist[k] - target) <= tol) {
                              byDist[b].add(edge[k]);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      double[] out = new double[buckets];

      for (int b = 1; b < buckets; b++) {
         if (byDist[b].size() < 30) {
            StillLifeFtfBridge.LOG
               .warn(
                  "[{}] not enough coast samples to measure coast distances ({} at {} blocks); using defaults",
                  new Object[]{"stilllife_ftf_bridge", byDist[b].size(), (int)CONT_DISTANCES[b]}
               );
            return (double[])DEFAULT_EDGE.clone();
         }

         float[] a = byDist[b].toFloatArray();
         Arrays.sort(a);
         out[b] = a[a.length / 2];
      }

      float[] ns = nearShore.toFloatArray();
      Arrays.sort(ns);
      out[0] = Math.min(ns[(int)(ns.length * 0.05)], out[1] - 0.01);

      for (int b = 1; b < buckets; b++) {
         out[b] = Math.max(out[b], out[b - 1] + 0.002);
      }

      StringBuilder sb = new StringBuilder();

      for (int b = 0; b < buckets; b++) {
         sb.append(b == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.3f", out[b]));
      }

      StillLifeFtfBridge.LOG.info("[{}] measured coast distances in {} ms: {}", new Object[]{"stilllife_ftf_bridge", System.currentTimeMillis() - t0, sb});
      return out;
   }

   private static void chamferDistance(boolean[] sea, int n, int step, double[] d) {
      double inf = 1.0E9;
      double a = step;
      double diag = step * Math.sqrt(2.0);

      for (int k = 0; k < n * n; k++) {
         d[k] = sea[k] ? 0.0 : inf;
      }

      for (int j = 0; j < n; j++) {
         for (int i = 0; i < n; i++) {
            int k = j * n + i;
            double v = d[k];
            if (i > 0) {
               v = Math.min(v, d[k - 1] + a);
            }

            if (j > 0) {
               v = Math.min(v, d[k - n] + a);
               if (i > 0) {
                  v = Math.min(v, d[k - n - 1] + diag);
               }

               if (i < n - 1) {
                  v = Math.min(v, d[k - n + 1] + diag);
               }
            }

            d[k] = v;
         }
      }

      for (int j = n - 1; j >= 0; j--) {
         for (int i = n - 1; i >= 0; i--) {
            int k = j * n + i;
            double v = d[k];
            if (i < n - 1) {
               v = Math.min(v, d[k + 1] + a);
            }

            if (j < n - 1) {
               v = Math.min(v, d[k + n] + a);
               if (i < n - 1) {
                  v = Math.min(v, d[k + n + 1] + diag);
               }

               if (i > 0) {
                  v = Math.min(v, d[k + n - 1] + diag);
               }
            }

            d[k] = v;
         }
      }

      for (int k = 0; k < n * n; k++) {
         if (!sea[k]) {
            d[k] -= step / 2.0;
         }
      }
   }

   private static String terrainFingerprint(Object preset, Levels levels) {
      String id = "levels:" + levels.worldHeight + "/" + levels.waterLevel + "/" + levels.terrainScaleFactor;

      try {
         JsonObject o = ((JsonElement)Preset.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, (Preset)preset).getOrThrow()).getAsJsonObject();

         for (String k : new String[]{"climate", "caves", "surface", "miscellaneous", "presentation"}) {
            o.remove(k);
         }

         if (o.get("world") instanceof JsonObject world && world.get("properties") instanceof JsonObject props) {
            for (String k : new String[]{"spawnX", "spawnZ", "spawnType", "lavaLevel"}) {
               props.remove(k);
            }
         }

         id = id + "|" + o;
      } catch (Exception | LinkageError var11) {
      }

      try {
         byte[] h = MessageDigest.getInstance("SHA-1").digest(id.getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(h, 0, 8);
      } catch (Exception e) {
         return Integer.toHexString(id.hashCode());
      }
   }

   static double ridgeMagnitude(double y) {
      double topo = (y - 136.16) / 144.0;
      float c = (float)((topo + 0.6) * 0.9 * 0.75);
      int count = W_LOC.length;
      int min = 0;
      int len = count;

      while (len > 0) {
         int half = len / 2;
         int k = min + half;
         if (c < W_LOC[k]) {
            len = half;
         } else {
            min = k + 1;
            len -= half + 1;
         }
      }

      int i = min - 1;
      if (i < 0) {
         return W_VAL[0] + W_DER[0] * (c - W_LOC[0]);
      }

      if (i == count - 1) {
         return W_VAL[i] + W_DER[i] * (c - W_LOC[i]);
      }

      float g = W_LOC[i];
      float h = W_LOC[i + 1];
      float k = (c - g) / (h - g);
      float a = W_VAL[i];
      float b = W_VAL[i + 1];
      float p = W_DER[i] * (h - g) - (b - a);
      float q = -W_DER[i + 1] * (h - g) + (b - a);
      return a + k * (b - a) + k * (1.0F - k) * (p + k * (q - p));
   }

   static double lerp(double[] xs, double[] ys, double x) {
      if (x <= xs[0]) {
         return ys[0];
      }

      for (int i = 1; i < xs.length; i++) {
         if (x <= xs[i]) {
            return ys[i - 1] + (ys[i] - ys[i - 1]) * (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
         }
      }

      return ys[ys.length - 1];
   }

   /**
    * Still Life's biome set at a point: -0.5 = American, 0.5 = Afro-Eurasian (the sign of Still Life's erosion number).
    * <ul>
    * <li>continents: each FTF continent rolls one set. Where two continents share land the biomes used to switch along
    * their (straight) border; with biome_set_blend the set is read at a point displaced by a smooth warp (up to about
    * biome_set_blend blocks) plus a small per-cell jitter, so the switch becomes an irregular, dithered edge spread over a
    * few hundred blocks, like FTF's other biome edges. Far from a border the warped point is on the same continent.</li>
    * <li>regions: irregular regions (a jittered grid of cells biome_set_region_size percent of an average continent's land
    * in area) each roll a set, read at the same warped point, so their borders fray the same way. Both sets on one
    * continent, as in Still Life's own worlds (Lithosphere picks the set by region).</li>
    * <li>halves: west of a line through the continent's centre is American, east Afro-Eurasian; american_share moves the
    * line (0.5 = the centre, 1 = all American).</li>
    * </ul>
    */
   private static double biomeSetGroup(StillLifeClimateBridge.World w, GeneratorContext ctx, float own, int bx, int bz) {
      Settings s = w.settings;
      double blend = s.biomeSetBlend();
      double wx = bx;
      double wz = bz;
      if (blend > 0.0) {
         long h = (bx >> 2) * 0x9E3779B97F4A7C15L ^ (bz >> 2) * 0xC2B2AE3D27D4EB4FL ^ w.seed;
         h ^= h >>> 29;
         h *= 0xBF58476D1CE4E5B9L;
         h ^= h >>> 32;
         double jitter = Math.min(32.0, blend / 8.0);
         double jx = ((h & 0xFFFF) / 65535.0 * 2.0 - 1.0) * jitter;
         double jz = (((h >>> 16) & 0xFFFF) / 65535.0 * 2.0 - 1.0) * jitter;
         wx = bx + blend * w.setWarpX.getValue(bx, 0.0, bz) + jx;
         wz = bz + blend * w.setWarpZ.getValue(bx, 0.0, bz) + jz;
      }

      switch (s.biomeSets()) {
         case Settings.SETS_REGIONS: {
            Cell scratch = SET_SCRATCH.get();
            ctx.heightmap.continent().apply(scratch, (float)wx, (float)wz);
            LandSets land = landSets(w, ctx, scratch);
            return regionRoll(w, wx, wz) < (land != null ? land.threshold() : s.americanShare()) ? -0.5 : 0.5;
         }
         case Settings.SETS_HALVES: {
            Cell scratch = SET_SCRATCH.get();
            ctx.heightmap.continent().apply(scratch, (float)wx, (float)wz);
            LandSets land = landSets(w, ctx, scratch);
            double line = land != null ? land.line() : scratch.continentX + (s.americanShare() - 0.5) * w.landWidth;
            double width = land != null ? land.width() : w.landWidth;
            // the line wanders by up to about a sixth of the continent's width, so it is not a straight north-south cut
            line += 0.25 * width * w.halfWander.getValue(0.0, 0.0, wz / Math.max(1.0, width) * 1024.0);
            return wx < line ? -0.5 : 0.5;
         }
         default: {
            float id = own;
            if (blend > 0.0) {
               Cell scratch = SET_SCRATCH.get();
               ctx.heightmap.continent().apply(scratch, (float)wx, (float)wz);
               id = scratch.continentId;
            }
            return continentRoll(id) < s.americanShare() ? -0.5 : 0.5;
         }
      }
   }

   /**
    * Per FTF continent (regions and halves): the region-roll threshold below which a region is American, and the x where
    * the halves split, both chosen so that american_share of that continent's own land is American; and its land width.
    */
   record LandSets(double threshold, double line, double width) {
   }

   private static LandSets landSets(StillLifeClimateBridge.World w, GeneratorContext ctx, Cell at) {
      if (at.continentId == 0.0F || w.continentSpacing <= 0.0) {
         return null;
      }
      final int cx = at.continentX;
      final int cz = at.continentZ;
      return w.landSets.computeIfAbsent(at.continentId, id -> measureLandSets(w, ctx, id, cx, cz));
   }

   /** Samples one continent's cell (96 x 96 points, 1.5 continent cells each way from its centre) for that continent's land. */
   private static LandSets measureLandSets(StillLifeClimateBridge.World w, GeneratorContext ctx, float id, int cx, int cz) {
      double share = w.settings.americanShare();
      double spacing = w.continentSpacing;
      int n = 96;
      double step = 3.0 * spacing / n;
      double[] rolls = new double[n * n];
      double[] xs = new double[n * n];
      int count = 0;
      Cell c = new Cell();
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            double x = cx - 1.5 * spacing + (i + 0.5) * step;
            double z = cz - 1.5 * spacing + (j + 0.5) * step;
            ctx.heightmap.continent().apply(c.reset(), (float)x, (float)z);
            if (c.continentId == id && c.controlPoint >= w.landControlPoint) {
               rolls[count] = regionRoll(w, x, z);
               xs[count] = x;
               count++;
            }
         }
      }
      if (count == 0) {
         return new LandSets(share, cx + (share - 0.5) * w.landWidth, w.landWidth);
      }
      double[] r = java.util.Arrays.copyOf(rolls, count);
      double[] x = java.util.Arrays.copyOf(xs, count);
      java.util.Arrays.sort(r);
      java.util.Arrays.sort(x);
      int k = (int)Math.round(share * count);
      double line = k <= 0 ? Double.NEGATIVE_INFINITY : k >= count ? Double.POSITIVE_INFINITY : x[k];
      // regions are whole: cut between two regions' rolls where the American part of the land comes closest to the share
      double threshold = -1.0;
      double bestError = share;
      for (int i = 1; i <= count; i++) {
         if (i < count && r[i] == r[i - 1]) {
            continue;
         }
         double error = Math.abs((double)i / count - share);
         if (error < bestError) {
            bestError = error;
            threshold = i < count ? r[i] : 2.0;
         }
      }
      return new LandSets(threshold, line, Math.sqrt(count * step * step));
   }

   /** A roll in [0, 1) per biome-set region: the region is the nearest of the jittered points of a grid of regionSize cells. */
   static double regionRoll(StillLifeClimateBridge.World w, double x, double z) {
      double gx = x / w.regionSize;
      double gz = z / w.regionSize;
      int ix = (int)Math.floor(gx);
      int iz = (int)Math.floor(gz);
      double best = Double.MAX_VALUE;
      long bestHash = 0L;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            long h = mix((ix + dx) * 0x9E3779B97F4A7C15L ^ (iz + dz) * 0xC2B2AE3D27D4EB4FL ^ w.seed ^ 0x51ED270B27E1F2A3L);
            double px = ix + dx + 0.15 + 0.7 * ((h & 0xFFFF) / 65536.0);
            double pz = iz + dz + 0.15 + 0.7 * (((h >>> 16) & 0xFFFF) / 65536.0);
            double d = (gx - px) * (gx - px) + (gz - pz) * (gz - pz);
            if (d < best) {
               best = d;
               bestHash = h;
            }
         }
      }
      return (bestHash >>> 40) / (double)(1L << 24);
   }

   private static long mix(long h) {
      h ^= h >>> 29;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 32;
      h *= 0x94D049BB133111EBL;
      return h ^ h >>> 29;
   }

   /**
    * The land area (blocks squared) of an average FTF continent: FTF's continent map is sampled on a grid over five continent
    * cells each way around the origin, and the land (control point at or above the preset's beach value) of every continent
    * whose centre lies in the middle three is counted. With continent type SINGLE that is the one continent.
    */
   static double measureLandArea(GeneratorContext ctx) {
      double spacing = 4.0 * ctx.preset.world().continent.continentScale;
      float beach = ctx.preset.world().controlPoints.beach;
      int n = 100;
      double half = 2.5 * spacing;
      double step = 2.0 * half / n;
      java.util.Map<Float, long[]> found = new java.util.HashMap<>();
      Cell c = new Cell();
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            ctx.heightmap.continent().apply(c.reset(), (float)(-half + (i + 0.5) * step), (float)(-half + (j + 0.5) * step));
            if (c.continentId != 0.0F && c.controlPoint >= beach) {
               long[] e = found.computeIfAbsent(c.continentId, k -> new long[3]);
               e[0]++;
               e[1] = c.continentX;
               e[2] = c.continentZ;
            }
         }
      }
      double sum = 0.0;
      int count = 0;
      for (long[] e : found.values()) {
         if (Math.abs(e[1]) <= 1.5 * spacing && Math.abs(e[2]) <= 1.5 * spacing) {
            sum += e[0] * step * step;
            count++;
         }
      }
      if (count == 0) {
         for (long[] e : found.values()) {
            sum += e[0] * step * step;
            count++;
         }
      }
      return count > 0 ? sum / count : 0.25 * spacing * spacing;
   }

   private static final ThreadLocal<Cell> SET_SCRATCH = ThreadLocal.withInitial(Cell::new);

   /** FTF height (blocks) to Still Life's height scale, by the world's measured height table. */
   static double toLitho(double[] heights, double y) {
      return y <= heights[heights.length - 1] ? lerp(heights, Y_LITHO, y) : Y_LITHO[Y_LITHO.length - 1] + (y - heights[heights.length - 1]);
   }

   /** Still Life height scale back to FTF blocks. */
   static double fromLitho(double[] heights, double yl) {
      return yl <= Y_LITHO[Y_LITHO.length - 1] ? lerp(Y_LITHO, heights, yl) : heights[heights.length - 1] + (yl - Y_LITHO[Y_LITHO.length - 1]);
   }

   /** Lowest Still Life height whose band value reaches m. */
   private static double bandStart(double m) {
      double lo = 63.0, hi = 349.0;
      for (int i = 0; i < 60; i++) {
         double mid = 0.5 * (lo + hi);
         if (ridgeMagnitude(mid) >= m) {
            hi = mid;
         } else {
            lo = mid;
         }
      }
      return hi;
   }

   /**
    * peak_line_variation: blocks by which the ground counts as higher (+) or lower (-) for the high-mountain bands, per biome
    * cell. A slow wander across ranges (about +-variation), plus the terrain: gullies (ground below its surroundings 24 blocks
    * away) count as higher, so snow and alpine biomes reach further down them, while ridges and spurs count as lower; plus a
    * small per-cell jitter, so the edge is dithered like FTF's other biome edges.
    */
   static double peakShift(StillLifeClimateBridge.World w, GeneratorContext ctx, int qx, int qz) {
      double v = w.settings.peakLineVariation();
      if (v <= 0.0) {
         return 0.0;
      }

      ShiftCache c = SHIFT_CACHE.get();
      int slot = (qx * 31 + qz) & (ShiftCache.SIZE - 1);
      if (c.world[slot] == w && c.qx[slot] == qx && c.qz[slot] == qz) {
         return c.value[slot];
      }

      int bx = QuartPos.toBlock(qx);
      int bz = QuartPos.toBlock(qz);
      double n = (0.7 * w.peakBig.getValue(bx, 0.0, bz) + 0.3 * w.peakMid.getValue(bx, 0.0, bz)) / PEAK_NOISE_RANGE;
      double shift = v * clamp(n, -1.3, 1.3);
      float scale = ctx.levels.terrainScaleFactor;
      double h0 = CellSampler.getCachedOrLookup(ctx.lookup, bx, bz, false).height * scale;
      double around = 0.0;
      for (int k = 0; k < 4; k++) {
         around += CellSampler.getCachedOrLookup(ctx.lookup, bx + RELIEF_DX[k], bz + RELIEF_DZ[k], false).height * scale;
      }
      shift += clamp(around / 4.0 - h0, -v, v);
      long h = qx * 0x9E3779B97F4A7C15L ^ qz * 0xC2B2AE3D27D4EB4FL ^ w.seed ^ 0x7F4A7C15L;
      h ^= h >>> 29;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 32;
      shift += ((h & 0xFFFF) / 65535.0 * 2.0 - 1.0) * Math.min(8.0, v / 4.0);
      c.world[slot] = w;
      c.qx[slot] = qx;
      c.qz[slot] = qz;
      c.value[slot] = shift;
      return shift;
   }

   private static final int[] RELIEF_DX = {24, -24, 0, 0};
   private static final int[] RELIEF_DZ = {0, 0, 24, -24};
   private static final ThreadLocal<ShiftCache> SHIFT_CACHE = ThreadLocal.withInitial(ShiftCache::new);

   private static final class ShiftCache {
      static final int SIZE = 256;
      final Object[] world = new Object[SIZE];
      final int[] qx = new int[SIZE];
      final int[] qz = new int[SIZE];
      final double[] value = new double[SIZE];
   }

   /**
    * snow_cap_fade: share of the ground (0..1) that keeps its snow at block (x, y, z) of a snowy peaks cap, or 1 where the
    * fade does not apply (more than snow_cap_fade blocks above the cap's local edge, or a polar cap, which starts lower).
    */
   static double capSnowShare(int x, int y, int z, float slope) {
      StillLifeClimateBridge.World w = last;
      if (w == null) {
         return 1.0;
      }
      Settings s = w.settings;
      double fade = s.snowCapFade();
      if (fade <= 0.0) {
         return 1.0;
      }
      int qx = QuartPos.fromBlock(x);
      int qz = QuartPos.fromBlock(z);
      double d = y - (w.peaksHeight - peakShift(w, w.ctx, qx, qz));
      if (d >= fade) {
         return 1.0;
      }
      // polar caps (Still Life temperature below -0.56) start at the alpine band, well below this edge
      Sampler sampler = lastSampler;
      if (sampler != null && Climate.unquantizeCoord(sampler.sample(qx, QuartPos.fromBlock(y), qz).temperature()) < -0.56F) {
         return 1.0;
      }
      double t = clamp(d / fade, 0.0, 1.0);
      double share = 0.1 + 0.9 * t * t * (3.0 - 2.0 * t);
      // snow lies on gentle ground first: steep spots go bare first
      share -= 0.3 * (1.0 - t) * (clamp(slope, 0.0, 1.0) - 0.4);
      return share;
   }

   /** Blocks above (+) or below (-) the line where snowy peaks begin at this column, or NaN before the world is known. */
   static double capLineDistance(int x, int y, int z) {
      StillLifeClimateBridge.World w = last;
      if (w == null) {
         return Double.NaN;
      }
      return y - (w.peaksHeight - peakShift(w, w.ctx, QuartPos.fromBlock(x), QuartPos.fromBlock(z)));
   }

   /**
    * The prevailing wind at this z: +1 = from the west (blowing east), -1 = from the east. With Still Life's latitude climate
    * it follows Earth's belts: easterly trade winds near the equator, westerlies in the middle latitudes, polar easterlies
    * towards the cold end. With FTF's temperature bands there are no latitudes: westerly everywhere.
    */
   static int windFromWest(int z) {
      StillLifeClimateBridge.World w = last;
      if (w == null || !w.settings.stillLifeLatitude()) {
         return 1;
      }
      double lat = Math.abs(z + w.equatorZ) / w.settings.poleDistance();
      return lat < 0.3 || lat > 0.65 ? -1 : 1;
   }

   /** Roughly uniform 0..1 dither value for the cap fade (Still Life's arctic-desert noise, through its normal distribution). */
   static double capDither(int x, int z) {
      StillLifeClimateBridge.World w = last;
      return w == null ? 0.5 : phi(w.capDither.getValue(x, 0.0, z) / CAP_DITHER_STD);
   }

   private static double phi(double v) {
      double t = 1.0 / (1.0 + 0.2316419 * Math.abs(v));
      double d = 0.3989422804014327 * Math.exp(-v * v / 2.0);
      double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
      return v >= 0.0 ? 1.0 - p : p;
   }

   static double continentRoll(float continentId) {
      long h = Float.floatToIntBits(continentId) * -7046029254386353131L;
      h ^= h >>> 31;
      h *= -4658895280553007687L;
      h ^= h >>> 29;
      return (h >>> 11) * 1.110223E-16F;
   }

   /** True when the climate point lies inside one of the boxes (with a small margin, so the biome is not a tie). */
   static boolean inside(float[][] boxes, float t, float h, float c, float w) {
      final float m = 0.004F;
      for (float[] b : boxes) {
         if (t >= b[0] + m && t <= b[1] - m && h >= b[2] + m && h <= b[3] - m && c >= b[4] + m && c <= b[5] && w >= b[6] + m && w <= b[7] - m) {
            return true;
         }
      }

      return false;
   }

   /** Patch-noise threshold for a dry-wetland setting: 1 = about a quarter of the qualifying land, 2 = about half, 0 = none. */
   static double maskThreshold(double prevalence) {
      if (!(prevalence > 0.0)) {
         return Double.POSITIVE_INFINITY;
      }

      return lerp(MASK_COVER, MASK_THRESHOLD, Math.min(0.6, 0.25 * prevalence));
   }

   private static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : Math.min(v, hi);
   }

   record Survey(double[] heights, double[] edge) {
   }

   private record World(
      GeneratorContext ctx,
      long seed,
      WorldLock.Lock lock,
      Settings settings,
      NormalNoise variant,
      NormalNoise temperature,
      double equatorZ,
      double[] heights,
      double[] edge,
      NormalNoise dryWetlands,
      double aspenThreshold,
      double saltThreshold,
      NormalNoise setWarpX,
      NormalNoise setWarpZ,
      NormalNoise peakBig,
      NormalNoise peakMid,
      NormalNoise capDither,
      double peaksHeight,
      double alpineHeight,
      double regionSize,
      double landWidth,
      double continentSpacing,
      float landControlPoint,
      NormalNoise halfWander,
      Map<Float, LandSets> landSets
   ) {
   }
}

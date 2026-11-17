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
      return new StillLifeClimateBridge.World(
         ctx, seed, lock, settings, variant, temperature, equatorZ, s.heights, s.edge, dryWetlands,
         maskThreshold(settings.aspenSwamps()), maskThreshold(settings.saltLakes())
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
         double yL = y <= heights[heights.length - 1] ? lerp(heights, Y_LITHO, y) : Y_LITHO[Y_LITHO.length - 1] + (y - heights[heights.length - 1]);
         double magnitude = submerged ? 0.0 : ridgeMagnitude(yL);
         // Beaches. Still Life's beach biomes are coastal points (continentalness up to 0.02) in the weirdness band 0.05-0.08. On
         // Lithosphere that band is land within ~12 blocks of the sea plus the shallow sea in front of it, and Still Life's beach
         // surface rule only lays sand or gravel below Y 65. The height mapping above squeezes FreeTerraForged's much taller
         // coastal land into that band, so beach biomes climbed 30-50 blocks up grassy slopes, while the sea stayed ocean.
         Settings bs = w.settings;
         if (!ocean && bs.beachMaxHeight() > 0.0 && magnitude >= BEACH_W_MIN && magnitude <= BEACH_W_MAX
            && Climate.unquantizeCoord(continentalness) < BEACH_C_MAX && (cell.height - levels.water) / levels.unit > bs.beachMaxHeight()) {
            // coastal land too high above the sea for a beach: the band just above (the land's inland biome)
            magnitude = ABOVE_BEACH_W;
         } else if (ocean && bs.beachShallows() > 0.0 && !terrain.isDeepOcean() && (levels.water - cell.height) / levels.unit <= bs.beachShallows()) {
            // shallow sea along the shore: beach biome, as in Still Life's own worlds (sand or gravel floor)
            magnitude = SHORE_W;
         }
         long weirdness = Climate.quantizeCoord((float)(w.variant.getValue(bx, 0.0, bz) >= 0.0 ? magnitude : -magnitude));
         double group = continentRoll(cell.continentId) < w.settings.americanShare() ? -0.5 : 0.5;
         double topo = (yL - 136.16) / 144.0;
         double aboveLowland = topo > -0.3 ? 1.0 : 0.0;
         boolean highland = terrain.getDelegate() == TerrainCategory.HIGHLAND;
         double orogeny = highland ? -0.747 : 0.198;
         // FTF wetlands always get Still Life's wetland climate band. With humid_swamps, very humid low land does too,
         // so Still Life's swamps/marshes/fens also appear in moisture-rich lowland away from FTF's river wetlands.
         // inland only (about 110+ blocks from the sea), so coasts keep their beaches
         double inland = lerp(w.edge, CONT, cell.controlPoint);
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
         // Salt lakes on FreeTerraForged's lakes: lake water (the widened stretches of its rivers, marked by the bridge) and the
         // ground within 16 blocks of it get the wetland climate in the salt-lake niche on either continent set; the Afro-Eurasian
         // set has no salt lake of its own, so those cells use the American wetland climate (only ever salt lake there).
         boolean dryWetland = false;
         boolean saltLakeWater = false;
         if (!ocean && !highland && inland >= CONT[3] && !terrain.isWetland() && !humidSwamp) {
            float t = Climate.unquantizeCoord(temperature);
            float c = Climate.unquantizeCoord(continentalness);
            float wv = Climate.unquantizeCoord(weirdness);
            boolean american = group < 0.0;
            boolean saltClimate = w.settings.saltLakes() > 0.0 && inside(SALT_LAKE, t, humidity, c, wv);
            boolean onLakes = saltClimate && w.settings.saltLakesOnLakes();
            if (terrain.isRiver() || terrain.isLake()) {
               saltLakeWater = onLakes && (terrain.isLake() || LakeCell.isLake(cell));
            } else if (!submerged) {
               boolean aspen = american && yL <= w.settings.swampMaxHeight() && inside(ASPEN_SWAMP, t, humidity, c, wv);
               boolean saltFlat = american && !aspen && saltClimate && yL <= w.settings.swampMaxHeight() + SALT_LAKE_EXTRA_HEIGHT;
               double threshold = aspen ? w.aspenThreshold : saltFlat ? w.saltThreshold : Double.POSITIVE_INFINITY;
               boolean patch = threshold != Double.POSITIVE_INFINITY && w.dryWetlands.getValue(bx, 0.0, bz) > threshold;
               if (patch || onLakes && !aspen) {
                  // reads the neighbouring cells; `cell` may be reused by these lookups, so it is not used after this
                  double lo = y;
                  double hi = y;
                  boolean lakeNearby = false;
                  for (int k = 0; k < 4; k++) {
                     Cell n = CellSampler.getCachedOrLookup(ctx.lookup, bx + FLAT_DX[k], bz + FLAT_DZ[k], false);
                     double ny = n.height * levels.terrainScaleFactor;
                     lakeNearby |= n.terrain.isLake() || n.terrain.isRiver() && LakeCell.isLake(n);
                     lo = Math.min(lo, ny);
                     hi = Math.max(hi, ny);
                  }

                  saltLakeWater = onLakes && !aspen && lakeNearby;
                  dryWetland = patch && hi - lo <= FLAT_RELIEF;
               }
            }
         }

         if (saltLakeWater) {
            dryWetland = true;
            group = -0.5;
         }

         double wetland = terrain.isWetland() || humidSwamp || dryWetland ? 1.0 : 0.0;
         double erosion = group + 0.49 * clamp(aboveLowland * orogeny + 0.06 + wetland, -1.0, 1.0);
         long depth = target.depth();
         float ftfDepth = Climate.unquantizeCoord(depth);
         if (ftfDepth > 0.0F) {
            double blocks = ftfDepth / 0.0078125F;
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
      double saltThreshold
   ) {
   }
}

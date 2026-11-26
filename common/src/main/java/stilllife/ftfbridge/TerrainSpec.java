package stilllife.ftfbridge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The parsed fields of the terrain filter ({@link TerrainFilter}, placement modifier) and the terrain surface condition
 * ({@link TerrainCondition}): one JSON vocabulary, one codec, one evaluation. The field reference is in TerrainFilter's
 * javadoc and in docs/bridge.md ("Terrain data for datapacks").
 *
 * <p>Evaluation order: cheap tests first (the column's FTF cell, options), then the per-column facts kept per chunk and
 * thread ({@link TerrainFacts}: province, climate, talus), the per-chunk snow field ({@link SnowField}), the real ground's
 * shape (blocks, placement only), and the probability tests last. Fields that only read something the world doesn't have
 * (an option off, the climate bridge not knowing the world) are ignored: they let everything through.
 */
final class TerrainSpec {
   // ---------------------------------------------------------------- names

   static final Map<String, Integer> TALUS = new LinkedHashMap<>();
   static final Map<String, Integer> FACES = new LinkedHashMap<>();
   static final Map<String, Integer> FAMILIES = new LinkedHashMap<>();
   static final Map<String, Integer> SUITES = new LinkedHashMap<>();
   static final Map<String, Integer> CLIMATES = new LinkedHashMap<>();

   static {
      TALUS.put("none", 1 << TalusField.KEEP);
      TALUS.put("fine", 1 << TalusField.FINE);
      TALUS.put("medium", 1 << TalusField.MEDIUM);
      TALUS.put("coarse", 1 << TalusField.COARSE);
      TALUS.put("mossy", 1 << TalusField.MOSSY);
      TALUS.put("overgrown", 1 << TalusField.VEG);
      TALUS.put("fan", 1 << TalusField.FAN);
      TALUS.put("thin_soil", 1 << TalusField.THIN_SOIL);
      TALUS.put("thin_gravel", 1 << TalusField.THIN_GRAVEL);
      TALUS.put("outcrop", 1 << TalusField.OUTCROP);
      TALUS.put("rock", 1 << GroundCover.BARE_ROCK);
      // groups
      TALUS.put("fresh", TALUS.get("fine") | TALUS.get("medium") | TALUS.get("coarse"));
      TALUS.put("old", TALUS.get("mossy") | TALUS.get("overgrown"));
      TALUS.put("deposit", TALUS.get("fresh") | TALUS.get("old"));
      FACES.put("open", 1 << SnowModel.OPEN);
      FACES.put("ledge", 1 << SnowModel.LEDGE);
      FACES.put("face", 1 << SnowModel.FACE);
      FACES.put("cliff", 1 << SnowModel.CLIFF);
      for (GroundCover.Family f : new GroundCover.Family[]{GroundCover.Family.GREY, GroundCover.Family.DARK, GroundCover.Family.PALE}) {
         FAMILIES.put(f.name().toLowerCase(Locale.ROOT), 1 << f.ordinal());
      }
      for (Strata.Suite s : Strata.Suite.values()) {
         SUITES.put(s.name().toLowerCase(Locale.ROOT), 1 << s.ordinal());
      }
      SUITES.put("kaolin", SUITES.get("kaolin_buff") | SUITES.get("kaolin_ochre"));
      CLIMATES.put("cold", 1 << GroundCover.Climate.COLD.ordinal());
      CLIMATES.put("temperate", 1 << GroundCover.Climate.TEMPERATE.ordinal());
      CLIMATES.put("humid", 1 << GroundCover.Climate.HUMID.ordinal());
      CLIMATES.put("arid", 1 << GroundCover.Climate.ARID.ordinal() | 1 << GroundCover.Climate.RED_ARID.ordinal());
   }

   /** A list of names from a table (any case); unknown names are a decoding error naming the allowed ones. */
   private static Codec<List<String>> names(String field, Map<String, Integer> table) {
      return Codec.STRING.validate(s -> table.containsKey(s.toLowerCase(Locale.ROOT))
         ? DataResult.success(s)
         : DataResult.error(() -> field + ": unknown name '" + s + "' (allowed: " + String.join(", ", table.keySet()) + ")")).listOf();
   }

   private static int mask(List<String> names, Map<String, Integer> table) {
      int m = 0;
      for (String n : names) {
         m |= table.getOrDefault(n.toLowerCase(Locale.ROOT), 0);
      }
      return m;
   }

   private static final float NEG = Float.NEGATIVE_INFINITY;
   private static final float POS = Float.POSITIVE_INFINITY;

   private static MapCodec<Float> min(String name) {
      return Codec.FLOAT.optionalFieldOf(name, NEG);
   }

   private static MapCodec<Float> max(String name) {
      return Codec.FLOAT.optionalFieldOf(name, POS);
   }

   private static boolean set(float min, float max) {
      return min != NEG || max != POS;
   }

   // ---------------------------------------------------------------- the fields (groups of at most 16)

   record Ranges(float minSlope, float maxSlope, float minSediment, float maxSediment, float minHollow, float maxHollow,
                 float minTemperature, float maxTemperature, float minSnowLine, float maxSnowLine, float minClimate, float maxClimate) {
      static final MapCodec<Ranges> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            min("min_slope").forGetter(Ranges::minSlope),
            max("max_slope").forGetter(Ranges::maxSlope),
            min("min_sediment").forGetter(Ranges::minSediment),
            max("max_sediment").forGetter(Ranges::maxSediment),
            min("min_hollow").forGetter(Ranges::minHollow),
            max("max_hollow").forGetter(Ranges::maxHollow),
            min("min_temperature").forGetter(Ranges::minTemperature),
            max("max_temperature").forGetter(Ranges::maxTemperature),
            min("min_snow_line").forGetter(Ranges::minSnowLine),
            max("max_snow_line").forGetter(Ranges::maxSnowLine),
            // Still Life's latitude temperature of the column (-1..1, not lowered by height), format 18 worlds only:
            // spruce treeline features stay out of hot country, its own treeline bushes stay in it
            min("min_climate").forGetter(Ranges::minClimate),
            max("max_climate").forGetter(Ranges::maxClimate)
         ).apply(i, Ranges::new));
   }

   record Forest(int edge, float clearings, float regionalMix, int mixSalt, float facingMaxSnowLine) {
      static final MapCodec<Forest> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("forest_edge", 0).forGetter(Forest::edge),
            Codec.FLOAT.optionalFieldOf("clearings", 0.0F).forGetter(Forest::clearings),
            Codec.FLOAT.optionalFieldOf("regional_mix", 0.0F).forGetter(Forest::regionalMix),
            Codec.INT.optionalFieldOf("mix_salt", 0).forGetter(Forest::mixSalt),
            // prefer_facing only below this snow-line distance (fading out over the 30 blocks above it)
            Codec.FLOAT.optionalFieldOf("facing_max_snow_line", POS).forGetter(Forest::facingMaxSnowLine)
         ).apply(i, Forest::new));
   }

   /** Sun, wind and shelter (SnowField), their thinning, and the treeline's shift by them. */
   record Exposure(float minInsolation, float maxInsolation, float minWind, float maxWind, float minShelter, float maxShelter,
                   float windThinning, float shadeThinning, float treelineShelter, float treelineSun, float treelineWind,
                   float treelineRagged, float treelineRaggedSize) {
      static final MapCodec<Exposure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            min("min_insolation").forGetter(Exposure::minInsolation),
            max("max_insolation").forGetter(Exposure::maxInsolation),
            min("min_wind").forGetter(Exposure::minWind),
            max("max_wind").forGetter(Exposure::maxWind),
            min("min_shelter").forGetter(Exposure::minShelter),
            max("max_shelter").forGetter(Exposure::maxShelter),
            Codec.FLOAT.optionalFieldOf("wind_thinning", 0.0F).forGetter(Exposure::windThinning),
            Codec.FLOAT.optionalFieldOf("shade_thinning", 0.0F).forGetter(Exposure::shadeThinning),
            Codec.FLOAT.optionalFieldOf("treeline_shelter", 0.0F).forGetter(Exposure::treelineShelter),
            Codec.FLOAT.optionalFieldOf("treeline_sun", 0.0F).forGetter(Exposure::treelineSun),
            Codec.FLOAT.optionalFieldOf("treeline_wind", 0.0F).forGetter(Exposure::treelineWind),
            Codec.FLOAT.optionalFieldOf("treeline_ragged", 0.0F).forGetter(Exposure::treelineRagged),
            Codec.FLOAT.optionalFieldOf("treeline_ragged_size", 24.0F).forGetter(Exposure::treelineRaggedSize)
         ).apply(i, Exposure::new));
   }

   /** Talus, the real ground's face class, and the rock province. */
   record Ground(List<String> talus, List<String> avoidTalus, float minTalusPosition, float maxTalusPosition, float minFaceDistance,
                 float maxFaceDistance, List<String> faces, List<String> rockFamilies, List<String> rockSuites, float minGreenness,
                 float maxGreenness, float minBareRock, float maxBareRock) {
      static final MapCodec<Ground> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            names("talus", TALUS).optionalFieldOf("talus", List.of()).forGetter(Ground::talus),
            names("avoid_talus", TALUS).optionalFieldOf("avoid_talus", List.of()).forGetter(Ground::avoidTalus),
            min("min_talus_position").forGetter(Ground::minTalusPosition),
            max("max_talus_position").forGetter(Ground::maxTalusPosition),
            min("min_face_distance").forGetter(Ground::minFaceDistance),
            max("max_face_distance").forGetter(Ground::maxFaceDistance),
            names("faces", FACES).optionalFieldOf("faces", List.of()).forGetter(Ground::faces),
            names("rock_families", FAMILIES).optionalFieldOf("rock_families", List.of()).forGetter(Ground::rockFamilies),
            names("rock_suites", SUITES).optionalFieldOf("rock_suites", List.of()).forGetter(Ground::rockSuites),
            min("min_greenness").forGetter(Ground::minGreenness),
            max("max_greenness").forGetter(Ground::maxGreenness),
            min("min_bare_rock").forGetter(Ground::minBareRock),
            max("max_bare_rock").forGetter(Ground::maxBareRock)
         ).apply(i, Ground::new));
   }

   /** Snow (mountain_snow's model) and moisture (Still Life's humidity, ground_cover's climate class and wetness). */
   record Moisture(Optional<Boolean> snow, float minSnowDepth, float maxSnowDepth, float minSnowMargin, float maxSnowMargin,
                   float minHumidity, float maxHumidity, List<String> climates, float minWetness, float maxWetness) {
      static final MapCodec<Moisture> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("snow").forGetter(Moisture::snow),
            min("min_snow_depth").forGetter(Moisture::minSnowDepth),
            max("max_snow_depth").forGetter(Moisture::maxSnowDepth),
            min("min_snow_margin").forGetter(Moisture::minSnowMargin),
            max("max_snow_margin").forGetter(Moisture::maxSnowMargin),
            min("min_humidity").forGetter(Moisture::minHumidity),
            max("max_humidity").forGetter(Moisture::maxHumidity),
            names("climates", CLIMATES).optionalFieldOf("climates", List.of()).forGetter(Moisture::climates),
            min("min_wetness").forGetter(Moisture::minWetness),
            max("max_wetness").forGetter(Moisture::maxWetness)
         ).apply(i, Moisture::new));
   }

   record Model(Exposure exposure, Ground ground, Moisture moisture) {
      static final MapCodec<Model> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Exposure.CODEC.forGetter(Model::exposure),
            Ground.CODEC.forGetter(Model::ground),
            Moisture.CODEC.forGetter(Model::moisture)
         ).apply(i, Model::new));
   }

   static final MapCodec<TerrainSpec> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
         Ranges.CODEC.forGetter(f -> f.ranges),
         Codec.STRING.listOf().optionalFieldOf("river_zones", List.of()).forGetter(f -> f.riverZonesRaw),
         Codec.STRING.optionalFieldOf("prefer_facing").forGetter(f -> f.preferFacingRaw),
         Codec.FLOAT.optionalFieldOf("facing_bias", 0.4F).forGetter(f -> f.facingBias),
         Codec.FLOAT.optionalFieldOf("ridge_thinning", 0.0F).forGetter(f -> f.ridgeThinning),
         Codec.BOOL.optionalFieldOf("avoid_chutes", false).forGetter(f -> f.avoidChutes),
         Codec.BOOL.optionalFieldOf("skip_with_natural_snow", false).forGetter(f -> f.skipWithNaturalSnow),
         Codec.INT.optionalFieldOf("max_above_water", Integer.MAX_VALUE).forGetter(f -> f.maxAboveWater),
         Codec.BOOL.optionalFieldOf("requires_rain", false).forGetter(f -> f.requiresRain),
         Codec.FLOAT.listOf().optionalFieldOf("treeline", List.of()).forGetter(f -> f.treeline),
         Codec.STRING.listOf().optionalFieldOf("skip_if_loaded", List.of()).forGetter(f -> f.skipIfLoaded),
         Codec.STRING.listOf().optionalFieldOf("terrain_categories", List.of()).forGetter(f -> f.terrainCategoriesRaw),
         Codec.STRING.optionalFieldOf("requires_option", "").forGetter(f -> f.requiresOption),
         Codec.FLOAT.listOf().optionalFieldOf("fade_in", List.of()).forGetter(f -> f.fadeIn),
         Forest.CODEC.forGetter(f -> f.forest),
         Model.CODEC.forGetter(f -> f.model)
      ).apply(i, TerrainSpec::new));

   // ---------------------------------------------------------------- parsed

   final Ranges ranges;
   private final List<String> riverZonesRaw;
   final List<String> riverZones;
   private final Optional<String> preferFacingRaw;
   final Optional<String> preferFacing;
   final float facingBias;
   final float ridgeThinning;
   final boolean avoidChutes;
   final boolean skipWithNaturalSnow;
   final int maxAboveWater;
   final boolean requiresRain;
   final List<Float> treeline;
   final List<String> skipIfLoaded;
   final boolean skipped;
   private final List<String> terrainCategoriesRaw;
   final List<String> terrainCategories;
   final String requiresOption;
   /** [from, to] snow-line distance: never below from, more and more from from to to (the treeline turned upside down). */
   final List<Float> fadeIn;
   final Forest forest;
   final Model model;

   private final int talusAllow;
   private final int talusAvoid;
   private final int faces;
   private final int families;
   private final int suites;
   private final int climates;
   private final boolean needTalus;
   private final boolean needProvince;
   private final boolean needMoisture;
   private final boolean needSnow;
   private final boolean needExposure;
   private final boolean treelineShift;

   private TerrainSpec(Ranges ranges, List<String> riverZones, Optional<String> preferFacing, float facingBias, float ridgeThinning,
                       boolean avoidChutes, boolean skipWithNaturalSnow, int maxAboveWater, boolean requiresRain, List<Float> treeline,
                       List<String> skipIfLoaded, List<String> terrainCategories, String requiresOption, List<Float> fadeIn, Forest forest,
                       Model model) {
      this.ranges = ranges;
      this.riverZonesRaw = riverZones;
      this.riverZones = riverZones.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
      this.preferFacingRaw = preferFacing;
      this.preferFacing = preferFacing.map(s -> s.toLowerCase(Locale.ROOT));
      this.facingBias = facingBias;
      this.ridgeThinning = ridgeThinning;
      this.avoidChutes = avoidChutes;
      this.skipWithNaturalSnow = skipWithNaturalSnow;
      this.maxAboveWater = maxAboveWater;
      this.requiresRain = requiresRain;
      this.treeline = treeline;
      this.skipIfLoaded = skipIfLoaded;
      this.skipped = skipIfLoaded.stream().anyMatch(StillLifeFtfBridge::isLoaded);
      this.terrainCategoriesRaw = terrainCategories;
      this.terrainCategories = terrainCategories.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
      this.requiresOption = requiresOption;
      this.fadeIn = fadeIn;
      this.forest = forest;
      this.model = model;

      Ground g = model.ground;
      Exposure e = model.exposure;
      Moisture m = model.moisture;
      this.talusAllow = mask(g.talus, TALUS);
      this.talusAvoid = mask(g.avoidTalus, TALUS);
      this.faces = mask(g.faces, FACES);
      this.families = mask(g.rockFamilies, FAMILIES);
      this.suites = mask(g.rockSuites, SUITES);
      this.climates = mask(m.climates, CLIMATES);
      this.needTalus = this.talusAllow != 0 || this.talusAvoid != 0 || set(g.minTalusPosition, g.maxTalusPosition)
         || set(g.minFaceDistance, g.maxFaceDistance);
      this.needProvince = this.families != 0 || this.suites != 0 || set(g.minGreenness, g.maxGreenness) || set(g.minBareRock, g.maxBareRock);
      this.needMoisture = this.climates != 0 || set(m.minHumidity, m.maxHumidity) || set(m.minWetness, m.maxWetness);
      this.needSnow = m.snow.isPresent() || set(m.minSnowDepth, m.maxSnowDepth) || set(m.minSnowMargin, m.maxSnowMargin);
      this.treelineShift = (treeline.size() == 2 || fadeIn.size() == 2) && (e.treelineShelter != 0.0F || e.treelineSun != 0.0F || e.treelineWind != 0.0F);
      this.needExposure = set(e.minInsolation, e.maxInsolation) || set(e.minWind, e.maxWind) || set(e.minShelter, e.maxShelter)
         || e.windThinning > 0.0F || e.shadeThinning > 0.0F || this.treelineShift;
   }

   /** Fields that read real blocks (feature placement only), set in this spec; empty when there are none. */
   String blockFields() {
      List<String> out = new java.util.ArrayList<>();
      if (this.faces != 0) {
         out.add("faces");
      }
      if (this.forest.edge > 0) {
         out.add("forest_edge");
      }
      return String.join(", ", out);
   }

   // ---------------------------------------------------------------- evaluation

   /**
    * Everything before the column is looked at: false when the feature / rule is off here whatever the terrain (a mod in
    * skip_if_loaded, natural snow layers with skip_with_natural_snow, requires_option off). Call after the non-FTF check.
    */
   boolean enabledHere() {
      if (this.skipWithNaturalSnow && NaturalSnow.active(WorldLock.activeSettings())) {
         return false;
      }
      if (this.requiresOption.startsWith("!")) {
         // "!option": only when that option is off (for features the option does better, e.g. Still Life's stone_cliff)
         return !optionOn(this.requiresOption.substring(1));
      }
      return this.requiresOption.isEmpty() || optionOn(this.requiresOption);
   }

   /** A hash of the column with this salt: the same for every block of the column and independent of other placements. */
   private static float roll(RandomSource random, long seed, int x, int z, int salt) {
      // a hash of the position, never the placement's random: a test that reads blocks around the column (faces, hollow,
      // facing, forest_edge, the snow fields' ground) can come out differently while a neighbouring chunk is still being
      // decorated, and when that decided whether a later roll drew from the shared random, every later placement of the
      // feature in the chunk moved (two worlds of one seed differed in about 2% of their trees, and the ground under them)
      return CoverNoise.hash(seed, x, z, 7300 + salt);
   }

   /** The facts that need the per-chunk caches, looked up once per test. */
   private static final class Probe {
      TerrainFacts.Chunk facts;
      SnowField.Chunk snow;
      boolean snowLooked;
   }

   private static final ThreadLocal<Probe> PROBE = ThreadLocal.withInitial(Probe::new);

   /**
    * The column tests, in the order the placement filter always ran them (so old JSON draws the same random numbers),
    * with the new tests where they are cheapest.
    *
    * @param level  the world, during feature placement; null in surface rules (then the model ground replaces the blocks
    *               and position hashes replace random draws)
    * @param pos    the feature's position (placement); null in surface rules
    * @param random placement's random; null in surface rules
    * @param chunk  the chunk holding the column (its biomes)
    * @param cell   the column's FTF cell, slope filled in (FtfTerrain.cell)
    * @param y      the feature's y (placement); the column's model ground + 1 (surface rules)
    */
   boolean test(WorldGenLevel level, BlockPos pos, RandomSource random, GeneratorContext ctx, long seed, ChunkAccess chunk, Cell cell,
                int x, int y, int z) {
      Ranges r = this.ranges;
      float slope = cell.gradient;
      if (slope < r.minSlope || slope > r.maxSlope) {
         return false;
      }
      // steep-slope painters (Still Life's stone_cliff and steep_slope_cold disks, min_slope 0.45+) would turn the snow
      // ground mountain_snow keeps on steep slopes into rock; they stay out of its snowfields (rock faces it leaves bare)
      if (r.minSlope >= 0.45F && level != null && MountainSnow.deepSnow(level, x, z)) {
         return false;
      }
      // snow_curve worlds: Still Life's snow disks only where its own surface is kept (keeps_own_surface: arctic and frozen
      // biomes); elsewhere mountain_snow decides the snow ground, and a disk reaching into a neighbouring chunk after
      // that chunk's snow pass (or before it) made the snow depend on the order chunks were generated in
      if (OWN_SURFACE.equals(this.requiresOption) && optionOn("snow_curve")) {
         net.minecraft.core.Holder<Biome> b = level != null ? level.getBiome(pos)
            : chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
         if (!MountainSnow.keepsOwnSurface(b)) {
            return false;
         }
      }
      float sediment = TerrainFilter.sediment(cell);
      if (sediment < r.minSediment || sediment > r.maxSediment) {
         return false;
      }
      if (!this.riverZones.isEmpty() && !this.riverZones.contains(TerrainFilter.riverZone(cell))) {
         return false;
      }
      if (!this.terrainCategories.isEmpty() && !this.terrainCategories.contains(TerrainFilter.category(cell))) {
         return false;
      }
      if (this.maxAboveWater != Integer.MAX_VALUE) {
         if (cell.riverWaterLevel <= 0.0F || y - ctx.heightmap.levels().scale(cell.riverWaterLevel) > this.maxAboveWater) {
            return false;
         }
      }
      if (this.requiresRain && !biome(level, pos, chunk, x, y - 1, z).hasPrecipitation()) {
         return false;
      }

      Probe probe = PROBE.get();
      probe.facts = null;
      probe.snow = null;
      probe.snowLooked = false;
      if (!this.modelTests(probe, level, ctx, seed, chunk, cell, x, y, z)) {
         return false;
      }

      boolean lineRange = r.minSnowLine != NEG || r.maxSnowLine != POS;
      boolean fade = this.fadeIn.size() == 2;
      if (lineRange || this.treeline.size() == 2 || fade) {
         double d = StillLifeClimateBridge.capLineDistance(x, y, z);
         if (!Double.isNaN(d)) {
            if (d < r.minSnowLine || d > r.maxSnowLine) {
               return false;
            }
            if (this.treeline.size() == 2 || fade) {
               double shifted = d - this.treelineShift(probe, ctx, seed, x, z);
               if (this.treeline.size() == 2) {
                  float a = this.treeline.get(0);
                  float b = this.treeline.get(1);
                  double t = (shifted - a) / Math.max(1.0F, b - a);
                  if (t >= 1.0 || t > 0.0 && roll(random, seed, x, z, 1) < t) {
                     return false;
                  }
               }
               if (fade) {
                  // the same shifted line, the other way: none below from, all of it from to up
                  float a = this.fadeIn.get(0);
                  float b = this.fadeIn.get(1);
                  double t = (b - shifted) / Math.max(1.0F, b - a);
                  if (t >= 1.0 || t > 0.0 && roll(random, seed, x, z, 8) < t) {
                     return false;
                  }
               }
            }
         }
      }

      boolean needHollow = r.minHollow != NEG || r.maxHollow != POS || this.avoidChutes;
      if (needHollow) {
         float hollow = level != null ? FtfTerrain.hollow(level, x, z) : TerrainFacts.modelHollow(ctx, x, z);
         if (hollow < r.minHollow || hollow > r.maxHollow) {
            return false;
         }
         if (this.avoidChutes && FtfTerrain.isChute(slope, hollow)) {
            return false;
         }
      }

      if (r.minClimate != NEG || r.maxClimate != POS) {
         if (StillLifeClimateBridge.snowCurve()) {
            float climate = StillLifeClimateBridge.seaLevelTemperature(x, z);
            if (!Float.isNaN(climate) && (climate < r.minClimate || climate > r.maxClimate)) {
               return false;
            }
         } else if (r.minClimate != NEG) {
            // features for hot country came with format 18: older worlds keep generating without them
            return false;
         }
      }

      if (r.minTemperature != NEG || r.maxTemperature != POS) {
         float t = level != null ? FtfTerrain.temperature(level, pos)
            : biome(null, null, chunk, x, y - 1, z).getBaseTemperature() - Math.max(0, y - 80) * 0.05F / 40.0F;
         if (t < r.minTemperature || t > r.maxTemperature) {
            return false;
         }
      }

      if (this.preferFacing.isPresent() && this.facingBias > 0.0F) {
         float bias = this.facingBias;
         if (this.forest.facingMaxSnowLine != POS) {
            // e.g. conifers denser on shady faces lower down, but not holding the treeline down on sunny ones
            double d = StillLifeClimateBridge.capLineDistance(x, y, z);
            if (!Double.isNaN(d)) {
               bias *= CoverNoise.clamp01((float)((this.forest.facingMaxSnowLine + 30.0 - d) / 30.0));
            }
         }
         String facing = bias <= 0.0F ? "" : level != null ? FtfTerrain.facing(level, x, z) : TerrainFacts.modelFacing(ctx, x, z);
         if (facing.equals(opposite(this.preferFacing.get())) && roll(random, seed, x, z, 2) < bias) {
            return false;
         }
      }


      if (this.ridgeThinning > 0.0F) {
         float relief = FtfTerrain.relief(ctx, x, z);
         float exposure = Math.max(0.0F, Math.min(1.0F, (relief - 16.0F) / 32.0F));
         if (exposure > 0.0F && roll(random, seed, x, z, 3) < exposure * this.ridgeThinning) {
            return false;
         }
      }
      Forest f = this.forest;
      if (f.edge > 0 && level != null && !ForestEdges.nearEdge(level, x, y, z, f.edge)) {
         return false;
      }
      if ((f.clearings > 0.0F || f.regionalMix > 0.0F) && optionOn("forest_variety")) {
         if (f.clearings > 0.0F && GroundCover.patch(seed, x, z, 171, 40.0F) > 1.0F - f.clearings) {
            return false;
         }
         if (f.regionalMix > 0.0F && roll(random, seed, x, z, 4 + f.mixSalt) < f.regionalMix * GroundCover.patch(seed, x, z, 200 + f.mixSalt, 350.0F)) {
            return false;
         }
      }

      // the new probability fields, after every old one
      Exposure e = this.model.exposure;
      if (e.windThinning > 0.0F || e.shadeThinning > 0.0F) {
         SnowField.Chunk sf = this.snow(probe, ctx, seed, x, z);
         if (sf != null) {
            int i = TerrainFacts.index(x, z);
            if (e.windThinning > 0.0F && sf.wind[i] > 0.0F && roll(random, seed, x, z, 5) < e.windThinning * sf.wind[i]) {
               return false;
            }
            float shade = CoverNoise.clamp01(1.0F - 2.0F * sf.sun[i]);
            if (e.shadeThinning > 0.0F && shade > 0.0F && roll(random, seed, x, z, 6) < e.shadeThinning * shade) {
               return false;
            }
         }
      }
      return true;
   }

   /** Province, moisture, snow, exposure, talus and the real ground's face class. */
   private boolean modelTests(Probe probe, WorldGenLevel level, GeneratorContext ctx, long seed, ChunkAccess chunk, Cell cell, int x,
                              int y, int z) {
      Settings s = WorldLock.activeSettings();
      Ground g = this.model.ground;
      Moisture m = this.model.moisture;
      boolean groundCover = GroundCover.active(s);
      boolean known = StillLifeClimateBridge.known();
      boolean suites = this.suites != 0 && groundCover && !GroundCover.rockStrata().equals(Settings.STRATA_OFF);

      if (this.needProvince && groundCover) {
         TerrainFacts.Chunk k = this.facts(probe, ctx, seed, x, z);
         Provinces.Province p = TerrainFacts.province(k, x, z);
         if (this.families != 0 && (this.families & 1 << GroundCover.natural(p).ordinal()) == 0) {
            return false;
         }
         if (p.green() < g.minGreenness || p.green() > g.maxGreenness || p.bare() < g.minBareRock || p.bare() > g.maxBareRock) {
            return false;
         }
         if (suites) {
            TerrainFacts.climate(k, cell, ctx.heightmap.levels(), chunk, x, z);
            GroundCover.Climate c = TerrainFacts.climateOf(k, x, z);
            boolean dryKaolin = (c == GroundCover.Climate.ARID || c == GroundCover.Climate.RED_ARID) && GroundCover.kaolinRock();
            if ((this.suites & 1 << Strata.suite(p, seed, dryKaolin).ordinal()) == 0) {
               return false;
            }
         }
      }

      if (this.needMoisture && known) {
         TerrainFacts.Chunk k = this.facts(probe, ctx, seed, x, z);
         TerrainFacts.climate(k, cell, ctx.heightmap.levels(), chunk, x, z);
         int i = TerrainFacts.index(x, z);
         if (this.climates != 0 && (this.climates & 1 << k.climate[i]) == 0) {
            return false;
         }
         if (k.humidity[i] < m.minHumidity || k.humidity[i] > m.maxHumidity || k.wetness[i] < m.minWetness || k.wetness[i] > m.maxWetness) {
            return false;
         }
      }

      Exposure e = this.model.exposure;
      boolean exposureRanges = set(e.minInsolation, e.maxInsolation) || set(e.minWind, e.maxWind) || set(e.minShelter, e.maxShelter);
      boolean snowFields = this.needSnow && MountainSnow.active(s);
      if (exposureRanges || snowFields) {
         SnowField.Chunk sf = this.snow(probe, ctx, seed, x, z);
         if (sf != null) {
            int i = TerrainFacts.index(x, z);
            if (sf.sun[i] < e.minInsolation || sf.sun[i] > e.maxInsolation || sf.wind[i] < e.minWind || sf.wind[i] > e.maxWind
               || sf.shelter[i] < e.minShelter || sf.shelter[i] > e.maxShelter) {
               return false;
            }
            if (snowFields) {
               // the snow passes judge the real top block; surface rules (before them) the model ground
               int ground = level != null ? FtfTerrain.ground(level, x, z) : y - 1;
               double score = sf.score(i, ground);
               if (m.snow.isPresent() && score >= 0.0 != m.snow.get()) {
                  return false;
               }
               if (score < m.minSnowMargin || score > m.maxSnowMargin) {
                  return false;
               }
               if (set(m.minSnowDepth, m.maxSnowDepth)) {
                  float depth = sf.depth(i, ground);
                  if (depth < m.minSnowDepth || depth > m.maxSnowDepth) {
                     return false;
                  }
               }
            }
         }
      }

      if (this.needTalus && groundCover) {
         TerrainFacts.Chunk k = this.facts(probe, ctx, seed, x, z);
         TerrainFacts.talus(k, ctx, cell, chunk, x, z);
         int i = TerrainFacts.index(x, z);
         int kind = k.talus[i];
         if (kind >= 0) {
            if (this.talusAllow != 0 && (this.talusAllow & 1 << kind) == 0 || (this.talusAvoid & 1 << kind) != 0) {
               return false;
            }
            if (set(g.minTalusPosition, g.maxTalusPosition)) {
               float p = k.position[i];
               if (Float.isNaN(p) || p < g.minTalusPosition || p > g.maxTalusPosition) {
                  return false;
               }
            }
            float d = k.faceDistance[i];
            if (d < g.minFaceDistance || d > g.maxFaceDistance) {
               return false;
            }
         }
      }

      if (this.faces != 0 && level != null && (this.faces & 1 << TerrainFacts.face(level, x, z)) == 0) {
         return false;
      }
      return true;
   }

   /** Blocks by which the treeline is raised at this column (treeline_shelter / _sun / _wind / _ragged). */
   private float treelineShift(Probe probe, GeneratorContext ctx, long seed, int x, int z) {
      Exposure e = this.model.exposure;
      float shift = 0.0F;
      if (this.treelineShift) {
         SnowField.Chunk sf = this.snow(probe, ctx, seed, x, z);
         if (sf != null) {
            int i = TerrainFacts.index(x, z);
            shift += e.treelineShelter * sf.shelter[i] + e.treelineSun * (2.0F * sf.sun[i] - 1.0F) - e.treelineWind * sf.wind[i];
         }
      }
      if (e.treelineRagged != 0.0F) {
         shift += e.treelineRagged * (2.0F * CoverNoise.value2(seed, x, z, 951, Math.max(2.0F, e.treelineRaggedSize)) - 1.0F);
      }
      return shift;
   }

   private TerrainFacts.Chunk facts(Probe probe, GeneratorContext ctx, long seed, int x, int z) {
      if (probe.facts == null) {
         probe.facts = TerrainFacts.chunk(ctx, seed, x >> 4, z >> 4);
      }
      return probe.facts;
   }

   private SnowField.Chunk snow(Probe probe, GeneratorContext ctx, long seed, int x, int z) {
      if (!probe.snowLooked) {
         probe.snowLooked = true;
         probe.snow = SnowField.get(ctx, seed, x >> 4, z >> 4);
      }
      return probe.snow;
   }

   /** The biome at the column: the level's (placement, as the filter always read it) or the chunk's noise biome. */
   private static Biome biome(WorldGenLevel level, BlockPos pos, ChunkAccess chunk, int x, int y, int z) {
      if (level != null) {
         return level.getBiome(pos).value();
      }
      return chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)).value();
   }

   /**
    * requires_option "own_surface": in snow_curve worlds only where the biome keeps Still Life's own surface (tag
    * keeps_own_surface); everywhere in other worlds.
    */
   static final String OWN_SURFACE = "own_surface";

   static boolean optionOn(String option) {
      Settings s = WorldLock.activeSettings();
      return switch (option) {
         case "shores" -> BridgeConfig.enabled() && s.shores();
         case "ground_cover" -> GroundCover.active(s);
         case "mountain_snow" -> MountainSnow.active(s);
         case "natural_snow_layers" -> NaturalSnow.active(s);
         case "forest_variety" -> BridgeConfig.enabled() && s.forestVariety();
         // the snow-line curve (format 18 and later, mountain_snow on): mountain_snow decides snow ground there, so
         // content that paints or erases snow across chunk borders gets a variant that leaves it alone
         case "snow_curve" -> MountainSnow.active(s) && s.snowCurve();
         // its opposite, so content can be written for both ("no_snow_curve" / "!no_snow_curve") and bridges that do not
         // know the name (any name they don't know counts as on) keep only the first
         case "no_snow_curve" -> !(MountainSnow.active(s) && s.snowCurve());
         default -> true;
      };
   }

   private static String opposite(String facing) {
      return switch (facing) {
         case "north" -> "south";
         case "south" -> "north";
         case "east" -> "west";
         case "west" -> "east";
         default -> "";
      };
   }
}

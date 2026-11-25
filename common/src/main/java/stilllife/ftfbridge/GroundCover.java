package stilllife.ftfbridge;

import etcodehome.freeterraforged.tags.FTFBlockTags;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.IChunkCellsHolder;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.ContinentalHydrology;
import etcodehome.freeterraforged.world.worldgen.cell.terrain.TerrainType;
import etcodehome.freeterraforged.world.worldgen.feature.ColumnDecorator;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import etcodehome.freeterraforged.world.worldgen.noise.module.Noise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * ground_cover: takes over FreeTerraForged's erosion decorator (its "scree decorator", raw generation step, every column).
 *
 * <p>FTF decides by slope and height whether a column's top becomes bare rock (cliffs, high peaks), scree (steep slopes)
 * or thin soil (moderate slopes high up), then uses one mix for the whole world: tuff on every scree slope, andesite,
 * moss blocks and coarse dirt. It also only touches dirt, gravel and snow, so sandy slopes stay sand at any angle.
 *
 * <p>This keeps FTF's bare rock (same slope and height thresholds from the preset, same noise), but treats scree as what
 * it is, a deposit below rock ({@link TalusField}):
 * <ul>
 *   <li>talus runs down the fall line from cliffs and bare rock, in tongues below chutes and cones below gullies, sorted
 *       from gravel at the apex to blocks and boulders at the toe, mostly of the rock under the ground here (the rock the
 *       cliffs above expose), with the province's loose rock (gravel, tuff, calcite, sand...) in the fines; soil and
 *       plants take the toe back where it is mild and wet, it stays bare where it is cold or dry;</li>
 *   <li>past the toe, a fan of fines where the land holds sediment (always in dry land);</li>
 *   <li>steep ground with no rock above it gets thin rocky soil with outcrops, not rubble;</li>
 *   <li>thin soil on moderate slopes high up, the alpine belt below the snow line, river banks and dry washes as before.</li>
 * </ul>
 * Which rock family the loose ground is, and how much scree, how blocky, how green and how rocky a range is, comes from
 * the {@link Provinces rock province}, so neighbouring ranges look different.
 */
final class GroundCover {
   private static final ThreadLocal<WorldGenLevel> LEVEL = new ThreadLocal<>();
   /** FreeTerraForged's generator context of the erosion pass running on this thread (looked up once per pass). */
   private static final ThreadLocal<GeneratorContext> CTX = new ThreadLocal<>();
   private static final ThreadLocal<Cell> WINDOW_CELL = ThreadLocal.withInitial(Cell::new);

   enum Climate { COLD, TEMPERATE, HUMID, ARID, RED_ARID }

   private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
   private static final BlockState STONE = Blocks.STONE.defaultBlockState();
   private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
   private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
   private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
   private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
   private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
   private static final BlockState COBBLED_DEEPSLATE = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
   private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
   private static final BlockState ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState();
   private static final BlockState SAND = Blocks.SAND.defaultBlockState();
   private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
   private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
   private static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();
   private static final BlockState MUD = Blocks.MUD.defaultBlockState();
   private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
   private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

   /** A weighted palette: entries picked by a 0..1 sample. A null entry keeps the existing top block. */
   private record Palette(BlockState[] blocks, float[] upTo) {
      static Palette of(Object... entries) {
         int n = entries.length / 2;
         BlockState[] b = new BlockState[n];
         float[] w = new float[n];
         float total = 0.0F;
         for (int i = 0; i < n; i++) {
            total += ((Number)entries[2 * i + 1]).floatValue();
         }
         float acc = 0.0F;
         for (int i = 0; i < n; i++) {
            b[i] = (BlockState)entries[2 * i];
            acc += ((Number)entries[2 * i + 1]).floatValue() / total;
            w[i] = acc;
         }
         return new Palette(b, w);
      }

      BlockState pick(float sample) {
         for (int i = 0; i < this.upTo.length; i++) {
            if (sample <= this.upTo[i]) {
               return this.blocks[i];
            }
         }
         return this.blocks[this.blocks.length - 1];
      }
   }

   // One family of colours, after FreeTerraForged's own mix (tuff, andesite, gravel, coarse dirt, moss) and real mountains:
   // grey rock and scree (stone, tuff, andesite, gravel), moss where it is wet, coarse dirt and gravel where soil thins out.
   private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
   private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();

   private static final BlockState PODZOL = Blocks.PODZOL.defaultBlockState();

   private static final Palette THIN_COLD = Palette.of(null, 3, COARSE_DIRT, 2, GRAVEL, 1, MOSS, 1);
   private static final Palette THIN_TEMPERATE = Palette.of(null, 4, COARSE_DIRT, 2, MOSS, 2, GRAVEL, 1);
   private static final Palette THIN_HUMID = Palette.of(null, 4, MOSS, 3, COARSE_DIRT, 1);
   private static final Palette THIN_ARID = Palette.of(null, 4, COARSE_DIRT, 2, GRAVEL, 1);
   private static final Palette THIN_RED = Palette.of(null, 4, COARSE_DIRT, 2, GRAVEL, 1);

   private GroundCover() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.groundCover();
   }

   /** The erosion pass's settings and noise on this thread (for the ground of columns the snow pass bares afterwards). */
   private static final class Pass {
      ScreeFeature.Config config;
      Levels levels;
      Noise rand;
   }

   private static final ThreadLocal<Pass> PASS = ThreadLocal.withInitial(Pass::new);

   /** Keeps the erosion pass's settings and noise on this thread (every column, before the snow test that may skip it). */
   static void pass(ScreeFeature.Config config, Levels levels, Noise rand) {
      Pass p = PASS.get();
      p.config = config;
      p.levels = levels;
      p.rand = rand;
   }

   static void begin(WorldGenLevel level) {
      LEVEL.set(level);
      CTX.remove();
   }

   static void end() {
      LEVEL.remove();
      CTX.remove();
      Pass p = PASS.get();
      p.config = null;
      p.levels = null;
      p.rand = null;
   }

   /** The level of the erosion pass running on this thread, or null outside it. */
   static WorldGenLevel level() {
      return LEVEL.get();
   }

   /** Replaces FTF's erodeColumn for one column. */
   static void erodeColumn(
      ScreeFeature.Config config, Levels levels, Noise rand, ChunkAccess chunk, Cell cell, BlockPos.MutableBlockPos pos, int surfaceY
   ) {
      if (cell.terrain.isWetland() || cell.terrain == TerrainType.VOLCANO_PIPE) {
         return;
      }
      int x = pos.getX();
      int z = pos.getZ();
      pos.setY(surfaceY);
      BlockState top = chunk.getBlockState(pos);
      boolean sand = top.is(Blocks.SAND) || top.is(Blocks.RED_SAND);
      boolean snow = top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.POWDER_SNOW);
      if (MountainSnow.onIce(chunk, pos, surfaceY)
         || MountainSnow.seaBiome(chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surfaceY), QuartPos.fromBlock(z)))) {
         // icebergs, ice shelves and sea ice, and the ground of seas: no rock, scree or soil (Still Life's and vanilla's
         // own look)
         return;
      }
      if (MountainSnow.keepsOwnSurface(chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surfaceY), QuartPos.fromBlock(z)))
         && cell.gradient + rand.compute(x, z, 1) * config.slopeModifier() <= config.screeSteepness()) {
         // Still Life's arctic and frozen biomes (the keeps_own_surface tag): their snow, ice, gravel and mud as Still Life
         // lays them at any height (FTF's rock line included); only steep ground gets rock and scree
         return;
      }
      // the bare rock surface of high peaks (Still Life's stone, basalt, calcite...): only frost scree fields, below
      boolean rockTop = !sand && !top.is(FTFBlockTags.ERODIBLE) && peakRock(top);
      if (!sand && !top.is(FTFBlockTags.ERODIBLE) && !rockTop) {
         return;
      }
      WorldGenLevel level = LEVEL.get();
      long seed = level != null ? level.getSeed() : 0L;
      if (rockTop) {
         if (level != null && !cell.terrain.isRiver()) {
            float steepness = cell.gradient + rand.compute(x, z, 1) * config.slopeModifier();
            TalusField bankField = field(level, chunk, config, levels, seed);
            if (bankField == null || !riverBank(bankField, cell, x, z, surfaceY)) {
               if (StillLifeClimateBridge.snowCurve()) {
                  highGround(level, chunk, config, levels, steepness, pos, x, z, surfaceY, seed, top);
               } else if (steepness <= config.screeSteepness()) {
                  screeField(level, chunk, config, levels, cell, pos, x, z, surfaceY, seed, top);
               }
            }
         }
         return;
      }
      if (cell.terrain.isRiver()) {
         return;
      }

      // FTF's own thresholds and noise (ScreeFeature.getMaterial), so rock lines and slopes stay where the preset puts them
      float height = cell.height + rand.compute(x, z, 0) * config.heightModifier();
      float steepness = cell.gradient + rand.compute(x, z, 1) * config.slopeModifier();
      float uplift = ContinentalHydrology.getComplexWaterHeight(cell.waterTable, cell.globalContinentScale, cell.continentSizeModifier);
      float base = levels.water + uplift;
      float rockThreshold = ColumnDecorator.sampleNoise(x, z, config.rockVar() / 255.0F, base + config.rockMin() / 255.0F);
      float dirtThreshold = ColumnDecorator.sampleNoise(x, z, config.dirtVar() / 255.0F, base + config.dirtMin() / 255.0F);

      TalusField field = level != null ? field(level, chunk, config, levels, seed) : null;
      if (field != null && riverBank(field, cell, x, z, surfaceY)) {
         // river banks: the bridge leaves them as Still Life and FTF make them (no bars, strips, strata, risers, talus,
         // thin soil or scree); only FTF's own bare rock where FTF's rule says rock, as FTF lays it
         if (!sand && (steepness > config.rockSteepness() || height > rockThreshold)) {
            plainRock(chunk, pos, surfaceY);
         }
         return;
      }
      if (steepness > config.rockSteepness() || height > rockThreshold) {
         // above the rock line but not a cliff: frost-shattered scree fields where it is high and cold enough
         if (!sand && !snow && level != null && StillLifeClimateBridge.snowCurve() && steepness <= config.rockSteepness()
            && highGround(level, chunk, config, levels, steepness, pos, x, z, surfaceY, seed, top)) {
            return;
         }
         if (!sand && !snow && level != null && !StillLifeClimateBridge.snowCurve() && steepness <= config.screeSteepness()
            && screeField(level, chunk, config, levels, cell, pos, x, z, surfaceY, seed, top)) {
            return;
         }
         if (!sand) {
            Climate climate = climate(chunk, pos, top, surfaceY);
            float joint = CoverNoise.clamp01((steepness - config.dirtSteepness()) / Math.max(0.05F, config.rockSteepness() - config.dirtSteepness()));
            exposeRock(chunk, pos, surfaceY, seed, field, climate, wetness(climate, chunk, pos, x, surfaceY, z), joint);
         }
         return;
      }
      if (snow) {
         return;
      }

      Climate climate = climate(chunk, pos, top, surfaceY);
      boolean steep = steepness > config.screeSteepness();
      if (field == null) {
         // no terrain window (not an FTF world?): FTF's rule with this place's blocks
         if (steep) {
            BlockState m = talusBlock(seed, x, z, climate, biomeTemperature(chunk, pos, surfaceY), patch(seed, x, z, 11, 9.0F));
            ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 3, m);
         }
         return;
      }

      Provinces.Province province = Provinces.at(seed, x, z);
      field.trace(x, z, province.debris());
      boolean dry = climate == Climate.ARID || climate == Climate.RED_ARID;
      float wet = wetness(climate, chunk, pos, x, surfaceY, z);
      float sediment = TerrainFilter.sediment(cell);
      if (!steep) {
         risers(field, chunk, pos, x, z, surfaceY, seed, climate, top, wet, steepness, province);
      }
      if (sandy(top) && !dry) {
         // sand outside dry land (beaches, sandy banks): left as it is
         return;
      }
      field.frost = frost(climate, x, surfaceY, z);
      int kind = field.classify(steep, province.coarse(), province.green(), province.bare(), wet, dry, sediment);
      if (kind != TalusField.KEEP) {
         deposit(kind, field, chunk, pos, x, z, surfaceY, seed, climate, top, wet, province);
         return;
      }
      if (alpine(level, field, chunk, pos, x, z, surfaceY, seed, climate, top, steepness, config.dirtSteepness(), wet)) {
         return;
      }
      if (steepness > config.dirtSteepness() && height > dirtThreshold) {
         // patches drawn out down the fall line
         BlockState m = thin(climate).pick(field.streakB);
         if (m != null) {
            ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
         }
         return;
      }
      // dry washes on sediment
      if (dry && sediment > 0.35F && CoverNoise.value2(seed, x, z, 41, 12.0F) > 0.62F) {
         BlockState m = field.streakA > 0.5F ? GRAVEL : drySoil(top);
         ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
      }
   }

   // ---------------------------------------------------------------- the talus model, read-only

   /** The kind {@link #talusKind} gives bare rock by FTF's rule (cliffs, ground above the rock line); TalusField's kinds otherwise. */
   static final int BARE_ROCK = 10;

   /**
    * What the erosion pass's talus model makes of a column, without reading or writing blocks (for the terrain filter and
    * surface condition): the steps of {@link #erodeColumn} up to the deposit kind, from FTF's cell, FTF's scree settings
    * and noise, and a talus window covering the column's chunk. It leaves out what erodeColumn decides from the top block
    * (tops it does not touch, sand outside dry land, snow, the patchy river-bank bars), so it is the model's answer, the
    * same at every stage of generation. Leaves the trace in field (dSrc, sortR) for the caller; returns the kind
    * (TalusField.KEEP for none).
    *
    * @param wet the column's {@link #wetness}
    */
   static int talusKind(
      ScreeFeature.Config config, Levels levels, Noise rand, Cell cell, TalusField field, int x, int z, Climate climate, float wet,
      Provinces.Province province
   ) {
      field.dSrc = -1;
      field.sortR = 0.0F;
      field.runout = 0.0F;
      if (cell.terrain == null || cell.terrain.isWetland() || cell.terrain == TerrainType.VOLCANO_PIPE || cell.terrain.isRiver()) {
         return TalusField.KEEP;
      }
      float height = cell.height + rand.compute(x, z, 0) * config.heightModifier();
      float steepness = cell.gradient + rand.compute(x, z, 1) * config.slopeModifier();
      float uplift = ContinentalHydrology.getComplexWaterHeight(cell.waterTable, cell.globalContinentScale, cell.continentSizeModifier);
      float rockThreshold = ColumnDecorator.sampleNoise(x, z, config.rockVar() / 255.0F, levels.water + uplift + config.rockMin() / 255.0F);
      if (riverBank(field, cell, x, z, levels.scale(cell.height))) {
         // river banks: no talus (FTF's own bare rock only)
         if (steepness > config.rockSteepness() || height > rockThreshold) {
            field.dSrc = 0;
            return BARE_ROCK;
         }
         return TalusField.KEEP;
      }
      if (steepness > config.rockSteepness() || height > rockThreshold) {
         if (steepness <= config.screeSteepness()) {
            // above the rock line but not a cliff: a frost scree field where it is high and cold enough
            field.trace(x, z, province.debris());
            field.frost = frost(climate, x, levels.scale(cell.height), z);
            int kind = field.screeField(x, z);
            if (kind == TalusField.FINE || kind == TalusField.MEDIUM) {
               field.dSrc = -1;
               field.sortR = 0.0F;
               return kind;
            }
         }
         field.dSrc = 0;
         return BARE_ROCK;
      }
      field.trace(x, z, province.debris());
      boolean dry = climate == Climate.ARID || climate == Climate.RED_ARID;
      field.frost = frost(climate, x, levels.scale(cell.height), z);
      return field.classify(steepness > config.screeSteepness(), province.coarse(), province.green(), province.bare(), wet, dry,
         TerrainFilter.sediment(cell));
   }

   /**
    * How strongly frost shatters steep ground in place (TalusField.frost), 0..1: rising from 70 blocks below the line
    * where the snowy peaks begin to full strength 10 below it and above; in cold climates it starts lower (the treeline is
    * lower), up to 0.7 from 160 blocks below. Only on real mountains ({@link #mountain}): a low line in a cold latitude
    * does not turn gentle low ground into scree.
    */
   static float frost(Climate climate, int x, int y, int z) {
      double d = StillLifeClimateBridge.capLineDistance(x, y, z);
      if (Double.isNaN(d)) {
         return 0.0F;
      }
      float f = CoverNoise.clamp01((float)((d + 70.0) / 60.0));
      if (climate == Climate.COLD) {
         f = Math.max(f, 0.7F * CoverNoise.clamp01((float)((d + 160.0) / 100.0)));
      }
      return f > 0.0F ? f * mountain(x, z) : 0.0F;
   }

   /**
    * How much the column belongs to real mountains, 0..1, from the land's shape rather than the climate: its relief (how
    * far it stands above the lowest ground 12 and 32 blocks around: none below 12 blocks, full from 40) or its height
    * above the sea (high country from 200 blocks up, full from 300). Where the snow line or the cold climate comes down
    * to low, gentle land (cold latitudes) frost scree and the alpine fringe stay off it: that ground keeps Still Life's
    * look. 1 when the snow field is not known.
    */
   static float mountain(int x, int z) {
      GeneratorContext ctx = StillLifeClimateBridge.lastContext();
      if (ctx == null) {
         return 1.0F;
      }
      WorldGenLevel level = LEVEL.get();
      SnowField.Chunk k = SnowField.get(ctx, level != null ? level.getSeed() : StillLifeClimateBridge.lastSeed(), x >> 4, z >> 4);
      if (k == null) {
         return 1.0F;
      }
      int i = (z & 15) * 16 + (x & 15);
      // (format 19: in the reference preset's blocks; k.inv is 1 on that preset and in older worlds)
      float relief = CoverNoise.clamp01((float)(k.relief[i] * k.inv - 12.0F) / 28.0F);
      float high = CoverNoise.clamp01((float)((k.height[i] - k.sea) * k.inv - 200.0F) / 100.0F);
      return Math.max(relief, high);
   }

   /** Rock a high peak's surface may be made of (Still Life's and FTF's), which frost breaks into scree fields. */
   private static boolean peakRock(BlockState top) {
      return genericStone(top) || top.is(Blocks.BASALT) || top.is(Blocks.SMOOTH_BASALT) || top.is(Blocks.CALCITE);
   }

   /**
    * Frost-shattered scree fields on high ground that is not a cliff but would be bare rock (above FTF's rock line, or a
    * peak's rock surface): gravel and cobble in patches down the fall line, rock between them. True when laid.
    */
   private static boolean screeField(
      WorldGenLevel level, ChunkAccess chunk, ScreeFeature.Config config, Levels levels, Cell cell, BlockPos.MutableBlockPos pos, int x,
      int z, int surfaceY, long seed, BlockState top
   ) {
      Climate climate = climate(chunk, pos, top, surfaceY);
      float frost = frost(climate, x, surfaceY, z);
      if (frost <= 0.0F) {
         return false;
      }
      TalusField field = field(level, chunk, config, levels, seed);
      if (field == null) {
         return false;
      }
      Provinces.Province province = Provinces.at(seed, x, z);
      field.trace(x, z, province.debris());
      field.frost = frost;
      int kind = field.screeField(x, z);
      if (kind != TalusField.FINE && kind != TalusField.MEDIUM) {
         return false;
      }
      deposit(kind, field, chunk, pos, x, z, surfaceY, seed, climate, top, 0.0F, province);
      return true;
   }

   /**
    * Format 18: the ground of bare high-mountain terrain that is not a cliff (above FTF's rock line, a peak's rock surface,
    * or ground the snow pass bared): talus aprons below real cliffs (sorted, fine at the apex, coarse at the toe, along
    * the fall line; the rock line itself is no source up here, or every column would be an apron's apex), else frost-shattered
    * scree fields of gravel and cobble in large coherent areas with rock ribs showing through, else the rock as it is.
    * True when something was laid.
    */
   private static boolean highGround(
      WorldGenLevel level, ChunkAccess chunk, ScreeFeature.Config config, Levels levels, float steepness, BlockPos.MutableBlockPos pos,
      int x, int z, int surfaceY, long seed, BlockState top
   ) {
      if (steepness > config.rockSteepness()) {
         return false; // cliffs: bedded rock
      }
      TalusField field = field(level, chunk, config, levels, seed);
      if (field == null) {
         return false;
      }
      Climate climate = climate(chunk, pos, top, surfaceY);
      Provinces.Province province = Provinces.at(seed, x, z);
      field.cliffSources = true;
      try {
         field.trace(x, z, province.debris());
      } finally {
         field.cliffSources = false;
      }
      field.frost = frost(climate, x, surfaceY, z);
      boolean steep = steepness > config.screeSteepness();
      int kind = TalusField.KEEP;
      if (field.dSrc > 0 && field.strong) {
         kind = field.classify(steep, province.coarse(), province.green(), province.bare(), 0.0F, false, 0.0F);
         if (kind != TalusField.FINE && kind != TalusField.MEDIUM && kind != TalusField.COARSE && kind != TalusField.FAN) {
            kind = TalusField.KEEP;
         }
      }
      if (kind == TalusField.KEEP && !steep) {
         kind = field.highScree(x, z);
      }
      if (kind == TalusField.KEEP) {
         return false;
      }
      deposit(kind, field, chunk, pos, x, z, surfaceY, seed, climate, top, 0.0F, province);
      pos.setY(surfaceY);
      return true;
   }

   /**
    * Format 18: the snow pass bared this column (Still Life's snow on a peak where the model has none): its ground as for
    * any bare high-mountain terrain ({@link #highGround}), in the erosion pass's settings. Only during that pass.
    */
   static void coverBared(ChunkAccess chunk, int x, int y, int z) {
      WorldGenLevel level = LEVEL.get();
      Pass pass = PASS.get();
      if (level == null || pass.config == null || !active(WorldLock.activeSettings())) {
         return;
      }
      GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return;
      }
      Cell cell = FtfTerrain.cell(ctx, chunk, x, z);
      if (cell.terrain == null || cell.terrain.isWetland() || cell.terrain.isRiver() || cell.terrain == TerrainType.VOLCANO_PIPE) {
         return;
      }
      float steepness = cell.gradient + pass.rand.compute(x, z, 1) * pass.config.slopeModifier();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
      highGround(level, chunk, pass.config, pass.levels, steepness, pos, x, z, y, level.getSeed(), chunk.getBlockState(pos));
   }

   /** True when Nature's Spirit's kaolins are installed (dry provinces may then be kaolin clay rock, see Strata.suite). */
   static boolean kaolinRock() {
      return Extra.get().kaolin != null;
   }

   // ---------------------------------------------------------------- the terrain window

   /**
    * The talus window of the chunk being decorated: FTF's terrain heights and rock lines of the chunk and the 16 blocks
    * around it (from FTF's cells of the 3 x 3 chunks, which FTF keeps per chunk), built once per chunk and thread (and
    * shared with the terrain filter and surface condition, see {@link TalusField#cached}).
    */
   private static TalusField field(WorldGenLevel level, ChunkAccess chunk, ScreeFeature.Config config, Levels levels, long seed) {
      GeneratorContext ctx = CTX.get();
      if (ctx == null) {
         ctx = FtfTerrain.context(level);
         if (ctx == null) {
            return null;
         }
         CTX.set(ctx);
      }
      int cx = chunk.getPos().x;
      int cz = chunk.getPos().z;
      float rockVar = config.rockVar() / 255.0F;
      float rockMin = config.rockMin() / 255.0F;
      TalusField f = TalusField.cached(ctx, cx, cz, seed, config.rockSteepness(), config.screeSteepness(), rockVar, rockMin);
      if (f != null) {
         return f;
      }
      f = TalusField.claim(cx, cz, seed, config.rockSteepness(), config.screeSteepness(), rockVar, rockMin);
      GeneratorContext context = ctx;
      fill(f, ctx, levels, (ccx, ccz) -> {
         ChunkAccess c = ccx == cx && ccz == cz ? chunk : level.getChunk(ccx, ccz);
         return c instanceof IChunkCellsHolder holder ? holder.freeterraforged$getOrCreateChunkCells(context) : null;
      });
      f.owner = ctx;
      return f;
   }

   /** Where a window's cells come from: the cells of chunk (cx, cz), or null to look them up one by one. */
   interface CellSource {
      Cell[] cells(int cx, int cz);
   }

   /**
    * Fills a claimed window (heights and rock lines of the chunk and the 16 blocks around it) and works out its slopes.
    * FTF's cells are the same whichever way they are read (FTF's per-chunk cells are its filtered chunk cache's), so the
    * window is the same whether the erosion pass reads them through the world or the terrain filter through the cache.
    */
   static void fill(TalusField f, GeneratorContext ctx, Levels levels, CellSource source) {
      int cx = f.chunkX;
      int cz = f.chunkZ;
      float unit = levels.terrainScaleFactor;
      float rockVar = f.rockVar;
      float rockMin = f.rockMin;
      Cell scratch = WINDOW_CELL.get();
      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            Cell[] cells = source.cells(cx + dx, cz + dz);
            for (int lz = 0; lz < 16; lz++) {
               for (int lx = 0; lx < 16; lx++) {
                  int wx = (cx + dx) * 16 + lx;
                  int wz = (cz + dz) * 16 + lz;
                  Cell cell;
                  if (cells != null) {
                     cell = cells[lz * 16 + lx];
                  } else {
                     ctx.lookup.applyCell(scratch.reset(), wx, wz, false, false);
                     cell = scratch;
                  }
                  int i = f.index(wx, wz);
                  f.h[i] = cell.height * unit;
                  float uplift = ContinentalHydrology.getComplexWaterHeight(cell.waterTable, cell.globalContinentScale, cell.continentSizeModifier);
                  f.rockLine[i] = ColumnDecorator.sampleNoise(wx, wz, rockVar, levels.water + uplift + rockMin) * unit;
                  f.riverWater[i] = cell.terrain != null && cell.terrain.isRiver() && !cell.terrain.isLake() && cell.riverWaterLevel > 0.0F
                     && cell.height < cell.riverWaterLevel ? cell.riverWaterLevel * unit : Float.NaN;
               }
            }
         }
      }
      f.computeSlopes(levels.water * unit, unit);
   }

   // ---------------------------------------------------------------- deposits

   /** Lays the ground of one deposit kind on the column. */
   private static void deposit(
      int kind, TalusField field, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surfaceY, long seed, Climate climate,
      BlockState top, float wet, Provinces.Province province
   ) {
      Family family = family(province, climate);
      boolean sandTop = sandy(top);
      if ((family == Family.SAND || family == Family.RED) && !sandTop) {
         // dry ground that is not sand: no sand-family blocks, the province's natural rock instead
         family = natural(province);
      }
      // material inside a size class: clusters drawn out down the fall line (the field's streak noise), a little
      // small-scale mixing on top, so gravel and cobble form streaks rather than salt and pepper
      float v = CoverNoise.value(seed, x, z, 611, 1.7F);
      float v2 = field.streakC * 0.8F + CoverNoise.value(seed, x, z, 613, 2.3F) * 0.2F;
      BlockState m;
      int depth = 3;
      if (kind == TalusField.FINE || kind == TalusField.MEDIUM || kind == TalusField.COARSE || kind == TalusField.MOSSY) {
         // debris covers the steps inside the apron too: down to the lowest neighbour's ground (at most 10 blocks)
         int drop = (int)Math.floor(field.h[field.index(x, z)] - field.lowestNeighbour(x, z));
         depth = Math.max(3, Math.min(10, drop + 1));
      }
      switch (kind) {
         case TalusField.FINE -> {
            Extra e = Extra.get();
            // kaolin fines only in the clay rock regions themselves (the same rule as their bedrock)
            Strata.Suite suite = !sandTop && kaolinRegion(province) ? Strata.suite(province, seed, true) : null;
            if (suite == Strata.Suite.KAOLIN_BUFF || suite == Strata.Suite.KAOLIN_OCHRE) {
               // clay rock regions: crumbled kaolin with gravel
               m = v < 0.4F ? GRAVEL : rockState(v2 < 0.6F ? suite.main : suite.companion);
            } else {
               m = fine(family, v, top);
            }
         }
         case TalusField.MEDIUM -> {
            // broken pieces of the beds of the face above (the local bedrock suite at this height)
            BlockState rock = bedrock(chunk, pos, surfaceY);
            Strata.Column col = column(seed, x, z, climate);
            if (rock != null && v < rockShare(family, top)) {
               // broken pieces of the face above: its rock_strata rock at this height, or the real rock under the column
               m = col != null && genericStone(rock)
                  ? rockState(Strata.broken(Strata.rock(col, surfaceY + 2, 0.0F, 0.0F), v2 < wet * 0.5F))
                  : broken(rock, wet, v2);
            } else {
               m = medium(family, wet, v, v2);
            }
         }
         case TalusField.COARSE -> {
            BlockState rock = bedrock(chunk, pos, surfaceY);
            Strata.Column col = column(seed, x, z, climate);
            if (rock != null && v < rockShare(family, top) + 0.1F) {
               if (col != null && genericStone(rock)) {
                  int bed = Strata.rock(col, surfaceY + 2, 0.0F, 0.0F);
                  m = rockState(v2 < 0.3F ? Strata.broken(bed, v2 < wet * 0.15F) : bed);
               } else {
                  m = v2 < 0.3F ? broken(rock, wet, v2 / 0.3F) : rock;
               }
            } else {
               m = coarse(family, wet, v, v2);
            }
         }
         case TalusField.MOSSY -> {
            Extra e = Extra.get();
            m = v < 0.45F ? MOSS : family == Family.PALE && e.mossyTravertine != null ? e.mossyTravertine : MOSSY_COBBLE;
         }
         case TalusField.VEG -> {
            // the toe taken back: the ground Still Life laid stays (grass, podzol...), bare tops get soil
            if (soil(top) || !top.is(Blocks.GRAVEL)) {
               return;
            }
            m = wet > 0.75F && v < 0.5F ? MOSS : v2 < 0.5F ? COARSE_DIRT : ROOTED_DIRT;
            depth = 1;
         }
         case TalusField.FAN -> {
            float r = field.runout > 0.0F ? field.dSrc / field.runout : 1.0F;
            boolean far = v < (r - 1.0F) / 0.6F; // finer further out
            m = switch (climate) {
               case ARID, RED_ARID -> far || v2 < 0.6F ? (sandTop ? top : drySoil(top)) : GRAVEL;
               default -> far ? COARSE_DIRT : GRAVEL;
            };
            depth = 1;
         }
         case TalusField.THIN_SOIL -> {
            m = switch (climate) {
               case ARID -> v < 0.6F ? drySoil(top) : GRAVEL;
               case RED_ARID -> v < 0.6F ? drySoil(top) : GRAVEL;
               case HUMID -> v < 0.4F ? MOSS : v < 0.75F ? COARSE_DIRT : ROOTED_DIRT;
               default -> v < 0.8F ? COARSE_DIRT : ROOTED_DIRT;
            };
            depth = 1;
         }
         case TalusField.THIN_GRAVEL -> {
            m = family == Family.DARK && v < 0.4F ? TUFF : GRAVEL;
            depth = 1;
         }
         case TalusField.OUTCROP -> {
            BlockState rock = bedrock(chunk, pos, surfaceY);
            Strata.Column col = column(seed, x, z, climate);
            if (rock != null && (col == null || !genericStone(rock))) {
               // the real rock under the column
               m = rock;
               depth = 2;
               break;
            }
            if (rock != null) {
               // the province's rock
               for (int dy = 0; dy < 2; dy++) {
                  ColumnDecorator.replaceSolid(chunk, pos.setY(surfaceY - dy), rockState(Strata.rock(col, surfaceY - dy, wet, 0.5F)));
               }
               pos.setY(surfaceY);
               return;
            }
            m = coarse(family, 0.0F, v, v2);
            depth = 2;
         }
         default -> {
            return;
         }
      }
      ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - depth, m);
   }

   /** The rock under this column's soil (what FTF's bare-rock rule exposes on the cliffs around), or null. */
   private static BlockState bedrock(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y) {
      BlockState found = null;
      for (int dy = 1; dy < 24; dy++) {
         BlockState state = chunk.getBlockState(pos.setY(y - dy));
         if (state.is(FTFBlockTags.ROCK)) {
            found = state;
            break;
         }
      }
      pos.setY(y);
      return found;
   }

   /**
    * The share of mid-size and coarse talus that is the rock under it, rather than the family's own rubble. Sandy dry
    * ground keeps its sand top on cliffs too (FTF only exposes rock under soil), so its scree is the family's own.
    */
   private static float rockShare(Family f, BlockState top) {
      // a minority: talus is mostly gravel and cobble, with some of the face's own rock
      return switch (f) {
         case SAND, RED -> sandy(top) ? -1.0F : 0.15F;
         default -> 0.15F;
      };
   }

   /**
    * Thin soil on slopes: on steep ground below the scree threshold, the ground under a soil top on a step is turned into
    * the bedrock down to the lowest neighbouring ground (at most 12 blocks), so stair risers read as rock ledges under grass
    * treads instead of brown dirt. Only in patches drawn out down the fall line, more on steeper ground, in rocky provinces
    * and in cold or dry climates, little in lush lowland. The top block is left as it is.
    */
   private static void risers(
      TalusField field, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surfaceY, long seed, Climate climate,
      BlockState top, float wet, float steepness, Provinces.Province province
   ) {
      if (!soil(top) || top.is(Blocks.MOSS_BLOCK)) {
         return;
      }
      Strata.Column col = column(seed, x, z, climate);
      if (col == null) {
         // rock_strata=off: risers stay as the world made them
         return;
      }
      // only on steep ground and not in hollows: on gentle stepped uplands rock in every riser read as grey stripes along
      // each terrace (owner, round 3); convex and steep ground keeps it
      float slope = CoverNoise.clamp01((steepness - 0.45F) / 0.35F);
      if (slope <= 0.0F || field.hollow(x, z) > 0.4F) {
         return;
      }
      float clim = switch (climate) {
         case COLD -> 0.9F;
         case ARID, RED_ARID -> 0.55F;
         default -> 0.8F * (1.0F - 0.65F * wet);
      };
      float p = slope * (0.2F + 0.55F * province.bare()) * clim;
      if (field.streakB >= p) {
         return;
      }
      int drop = (int)Math.floor(field.h[field.index(x, z)] - field.lowestNeighbour(x, z));
      if (drop < 2) {
         return;
      }
      int bottom = surfaceY - Math.min(12, drop);
      for (int y = surfaceY - 1; y >= bottom; y--) {
         BlockState state = chunk.getBlockState(pos.setY(y));
         if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.PODZOL) || genericStone(state)) {
            chunk.setBlockState(pos, rockState(Strata.rock(col, y, wet, 0.6F)), false);
         }
      }
      pos.setY(surfaceY);
   }

   /** Sand ground (sand or red sand top): the only place sand-family blocks may appear. */
   private static boolean sandy(BlockState top) {
      return top.is(Blocks.SAND) || top.is(Blocks.RED_SAND);
   }

   // Talus of the natural-rock families is gravel and cobblestone, with the province's rock as a minority that ties it to
   // the face above (deposit() adds the face's own rock on top of these): fines mostly gravel, mid-size gravel and
   // cobble, blocks mostly cobble; mossy cobble where it is wet.

   /** The family's own rock among its scree. */
   private static BlockState familyRock(Family f) {
      return switch (f) {
         case DARK -> TUFF;
         case PALE -> DIORITE;
         default -> ANDESITE;
      };
   }

   private static BlockState cobble(float wet, float v) {
      return v < wet * 0.5F ? MOSSY_COBBLE : COBBLE;
   }

   private static BlockState fine(Family f, float v, BlockState top) {
      return switch (f) {
         case GREY -> v < 0.1F ? ANDESITE : GRAVEL;
         case DARK -> v < 0.2F ? TUFF : GRAVEL;
         case PALE -> v < 0.15F ? DIORITE : GRAVEL;
         case SAND, RED -> v < 0.3F ? GRAVEL : top;
      };
   }

   private static BlockState medium(Family f, float wet, float v, float v2) {
      return switch (f) {
         case SAND -> v2 < 0.35F ? SANDSTONE : GRAVEL;
         case RED -> v2 < 0.35F ? RED_SANDSTONE : GRAVEL;
         default -> v2 < 0.45F ? GRAVEL : v2 < 0.88F ? cobble(wet, v) : familyRock(f);
      };
   }

   private static BlockState coarse(Family f, float wet, float v, float v2) {
      return switch (f) {
         case SAND -> v2 < 0.5F ? SANDSTONE : COBBLE;
         case RED -> v2 < 0.5F ? RED_SANDSTONE : COBBLE;
         default -> v2 < 0.7F ? cobble(wet, v) : v2 < 0.85F ? familyRock(f) : GRAVEL;
      };
   }

   /**
    * How readily plants retake bare ground here, 0..1: none where it is cold or dry, most where it is warm and wet (Still
    * Life's humidity, so it changes smoothly), fading out towards the snow line.
    */
   private static float wetness(Climate climate, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int y, int z) {
      return wetness(climate, x, y, z);
   }

   /** {@link #wetness(Climate, ChunkAccess, BlockPos.MutableBlockPos, int, int, int)} without the unused block arguments. */
   static float wetness(Climate climate, int x, int y, int z) {
      float w = switch (climate) {
         case COLD, ARID, RED_ARID -> 0.0F;
         case HUMID -> 0.85F;
         default -> 0.5F;
      };
      if (w == 0.0F) {
         return 0.0F;
      }
      float humidity = StillLifeClimateBridge.humidity(x, y, z);
      w = CoverNoise.clamp01(w + humidity * 0.3F);
      double line = StillLifeClimateBridge.capLineDistance(x, y, z);
      if (!Double.isNaN(line)) {
         w *= CoverNoise.clamp01((float)(-line / 90.0));
      }
      return w;
   }

   /** Grass and soil tops the alpine belt may break up. */
   private static boolean soil(BlockState top) {
      return top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.PODZOL) || top.is(Blocks.COARSE_DIRT)
         || top.is(Blocks.ROOTED_DIRT) || top.is(Blocks.MOSS_BLOCK) || top.is(Blocks.MYCELIUM);
   }

   // ---------------------------------------------------------------- the alpine belt

   /** 2D gradient noise (a slice of noise3: no preference for the lattice axes) re-stretched to about 0..1. */
   private static float patchNoise(long seed, int x, int z, int salt, float size) {
      return CoverNoise.clamp01(0.5F + (CoverNoise.noise3(seed, x / size, 0.37F + salt * 0.11F, z / size, salt) - 0.5F) * 2.6F);
   }

   /**
    * The erosion pass's part of the alpine belt: only a soft-edged fringe of bare, wet ground (gravel, coarse dirt) around
    * snow patches in the 45 blocks below where the snowy peaks begin. The belt's ground mosaic and plants are Still Life's own
    * features re-gated to the belt (data/stilllife_ftf_bridge, belt_*: docs/bridge.md section 15). True when this column was
    * changed.
    */
   private static boolean alpine(
      WorldGenLevel level, TalusField field, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surfaceY, long seed,
      Climate climate, BlockState top, float steepness, float dirtSteepness, float wet
   ) {
      if (!soil(top)) {
         return false;
      }
      if (LEGACY_BELT) {
         return legacyAlpine(level, field, chunk, pos, x, z, surfaceY, seed, climate, steepness, dirtSteepness);
      }
      // the belt's ground is laid by Still Life's own disk and patch features, re-gated to the belt (data, belt_*); here
      // only what features cannot do: a fringe of bare, wet ground around snow patches instead of grass running into snow
      double d = StillLifeClimateBridge.capLineDistance(x, surfaceY, z);
      if (Double.isNaN(d) || d >= 0.0 || d < -45.0) {
         return false;
      }
      if (!MountainSnow.active(WorldLock.activeSettings()) || mountain(x, z) < 0.5F || !nearSnow(level, x, z, seed)) {
         // (only on real mountains: not around snow patches on low, gentle land)
         return false;
      }
      // soft-edged: a patch noise decides which fringe columns go bare, gravel or coarse dirt by a second one
      if (patchNoise(seed, x, z, 309, 4.0F) > 0.55F) {
         return false;
      }
      BlockState m = patchNoise(seed, x, z, 310, 5.0F) < 0.45F ? GRAVEL : COARSE_DIRT;
      ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
      return true;
   }

   /**
    * Test switch for before/after comparisons on one build (-Dstilllife_ftf_bridge.legacyBelt=true): the 6.0 belt of
    * coarse dirt, gravel and stone streaks (the 60 blocks below the line) instead of the snow-patch fringe.
    */
   private static final boolean LEGACY_BELT = Boolean.getBoolean("stilllife_ftf_bridge.legacyBelt");

   private static boolean legacyAlpine(WorldGenLevel level, TalusField field, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z,
                                       int surfaceY, long seed, Climate climate, float steepness, float dirtSteepness) {
      double d = StillLifeClimateBridge.capLineDistance(x, surfaceY, z);
      if (Double.isNaN(d) || d >= 0.0 || d < -60.0) {
         return false;
      }
      float p = (float)Math.pow((d + 60.0) / 60.0, 1.4) * 0.75F;
      if (field.facesSouth()) {
         p -= 0.15F;
      }
      if (field.hollow(x, z) > 0.3F) {
         p -= 0.2F;
      }
      if (steepness > dirtSteepness) {
         p += 0.15F;
      }
      if (MountainSnow.active(WorldLock.activeSettings()) && nearSnow(level, x, z, seed)) {
         p += 0.45F;
      }
      if (field.streakB >= p) {
         return false;
      }
      float a = field.streakA;
      BlockState m = switch (climate) {
         case HUMID -> a < 0.375F ? MOSS : a < 0.625F ? COARSE_DIRT : a < 0.75F ? GRAVEL : MOSSY_COBBLE;
         case ARID, RED_ARID -> a < 0.4F ? GRAVEL : a < 0.8F ? COARSE_DIRT : STONE;
         case COLD -> a < 0.375F ? COARSE_DIRT : a < 0.625F ? GRAVEL : a < 0.75F ? TUFF : a < 0.875F ? MOSS : STONE;
         default -> a < 0.375F ? COARSE_DIRT : a < 0.625F ? MOSS : a < 0.875F ? GRAVEL : TUFF;
      };
      if (m == STONE || m == TUFF) {
         BlockState rock = bedrock(chunk, pos, surfaceY);
         m = rock != null ? rock : talusBlock(seed, x, z, climate, biomeTemperature(chunk, pos, surfaceY), CoverNoise.value(seed, x, z, 124, 3.0F));
      }
      ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
      return true;
   }


   /** True when mountain_snow puts snow ground 2 blocks away in one of the four directions. */
   private static boolean nearSnow(WorldGenLevel level, int x, int z, long seed) {
      for (int i = 0; i < 4; i++) {
         int nx = x + (i == 0 ? 2 : i == 1 ? -2 : 0);
         int nz = z + (i == 2 ? 2 : i == 3 ? -2 : 0);
         int ny = FtfTerrain.ground(level, nx, nz);
         Cell c = FtfTerrain.cell(level, nx, nz);
         if (c == null) {
            return false;
         }
         float slope = c.gradient;
         Holder<Biome> biome = level.getBiome(new BlockPos(nx, ny, nz));
         double score = MountainSnow.score(level, nx, ny, nz, slope, biome, seed);
         if (!Double.isNaN(score) && score >= 0.0) {
            return true;
         }
      }
      return false;
   }

   /**
    * True when the column is on a river bank, which the bridge's ground passes leave as Still Life and FTF make it: FTF's
    * riverbed and banks zones, and any ground within {@link TalusField#BANK_REACH} blocks of river water and no more than
    * {@link TalusField#BANK_HEIGHT} blocks above it (the stepped bank up to the floodplain or forest floor). Cliffs and
    * talus higher up a valley side are not affected. (The bridge's own river bank decoration, gravel / mud / clay / cobble
    * bars at the water and green strips in dry land, was removed.)
    */
   static boolean riverBank(TalusField field, Cell cell, int x, int z, int y) {
      String zone = TerrainFilter.riverZone(cell);
      if (zone.equals("riverbed") || zone.equals("banks")) {
         return true;
      }
      return field.riverBank(x, z, y);
   }

   /** FTF's erodeRock exactly: the soil above the first rock within 32 blocks becomes that rock (gravel when there is none). */
   private static void plainRock(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y) {
      int depth = 32;
      BlockState material = GRAVEL;
      for (int dy = 3; dy < 32; dy++) {
         BlockState state = chunk.getBlockState(pos.setY(y - dy));
         if (state.is(FTFBlockTags.ROCK)) {
            material = state;
            depth = dy + 1;
            break;
         }
      }
      for (int dy = 0; dy < depth; dy++) {
         ColumnDecorator.replaceSolid(chunk, pos.setY(y - dy), material);
      }
      pos.setY(y);
   }

   static float biomeTemperature(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y) {
      return chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(y), QuartPos.fromBlock(pos.getZ())).value().getBaseTemperature();
   }

   /**
    * The climate class of a column: dry from the biome (no rain, or tagged dry), red when the dry ground is red sand or
    * terracotta, cold from the biome's temperature, humid from Still Life's humidity (smooth, so the class doesn't flicker
    * from block to block with podzol or moss patches; the top block decides only before Still Life's climate is known).
    */
   static Climate climate(ChunkAccess chunk, BlockPos.MutableBlockPos pos, BlockState top, int surfaceY) {
      Holder<Biome> holder = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(surfaceY), QuartPos.fromBlock(pos.getZ()));
      Biome biome = holder.value();
      // dry from the biome (no rain, or tagged dry), not from a sand top: beaches and sandy coasts are not deserts
      if (!biome.hasPrecipitation() || holder.is(MountainSnow.DRY)) {
         return top.is(Blocks.RED_SAND) || top.is(BlockTags.TERRACOTTA) ? Climate.RED_ARID : Climate.ARID;
      }
      float t = biome.getBaseTemperature();
      if (t < 0.3F) {
         return Climate.COLD;
      }
      float humidity = StillLifeClimateBridge.humidity(pos.getX(), surfaceY, pos.getZ());
      if (humidity != 0.0F) {
         // a wavy rather than straight line where it turns humid (smooth noise, a few blocks across)
         float edge = (CoverNoise.value(0L, pos.getX(), pos.getZ(), 617, 11.0F) - 0.5F) * 0.12F;
         float need = t >= 0.85F ? 0.1F : 0.35F;
         return humidity + edge > need ? Climate.HUMID : Climate.TEMPERATE;
      }
      if (top.is(Blocks.PODZOL) || top.is(Blocks.MOSS_BLOCK) || top.is(Blocks.MUD) || top.is(BlockTags.MOSS_REPLACEABLE) && t >= 0.85F) {
         return Climate.HUMID;
      }
      return Climate.TEMPERATE;
   }

   /**
    * The rock families of loose ground (see {@link Provinces} for how regions get one):
    * <ul>
    *   <li>GREY: gravel, cobble, andesite and stone;</li>
    *   <li>DARK: tuff, cobbled deepslate, andesite and gravel;</li>
    *   <li>PALE: limestone hills: calcite and gravel fines, Nature's Spirit travertine (diorite without it);</li>
    *   <li>SAND: sand ground's own sand with gravel and some sandstone (deserts; dry ground that is not sand uses the province's natural family);</li>
    *   <li>RED: red sand ground's own red sand with gravel and some red sandstone (red deserts).</li>
    * </ul>
    * In wet places moss (and mossy cobble or mossy travertine) comes in, in temperate ones some coarse dirt.
    */
   enum Family { GREY, DARK, PALE, SAND, RED }

   /**
    * The family of a province in a climate. The roll is the province's, and in climates with rain it doesn't depend on
    * the climate, so a range keeps its rock from its warm foot to its cold top.
    */
   static Family family(Provinces.Province p, Climate c) {
      float r = p.roll();
      return switch (c) {
         case ARID -> Family.SAND;
         case RED_ARID -> Family.RED;
         default -> natural(p);
      };
   }

   /**
    * The province's natural rock family, whatever the climate (dry ground that is not sand uses it too): from its step
    * on the bedrock lightness ladder ({@link Strata#level}), so the scree matches the rock it fell from.
    */
   static Family natural(Provinces.Province p) {
      int level = Strata.level(p);
      return level == 0 ? Family.DARK : level >= 3 ? Family.PALE : Family.GREY;
   }

   static Family family(long seed, int x, int z, Climate c, float temperature) {
      return family(Provinces.at(seed, x, z), c);
   }

   /** Nature's Spirit's ground blocks (installed with Still Life Naturalized), looked up once; null when not installed. */
   private static final class Extra {
      static volatile Extra instance;
      final BlockState sandySoil;
      /** Nature's Spirit's red moss (Still Life Naturalized's red tundra ground). */
      final BlockState redMoss;
      final BlockState travertine;
      final BlockState cobbledTravertine;
      final BlockState mossyTravertine;
      /** Nature's Spirit kaolins: null when not installed. */
      final BlockState kaolin;
      final BlockState lightGrayKaolin;
      final BlockState grayKaolin;
      final BlockState orangeKaolin;
      final BlockState brownKaolin;
      final BlockState whiteKaolin;
      final java.util.Map<String, Palette> talus = new java.util.concurrent.ConcurrentHashMap<>();
      /** Create's limestone: null when Create is not installed (rock_strata=deep only). */
      BlockState createLimestone;
      /** Blocks of Strata's rocks, by index. */
      final BlockState[] rocks = new BlockState[Strata.COUNT];

      private Extra() {
         this.sandySoil = ns("sandy_soil");
         this.redMoss = ns("red_moss_block");
         this.travertine = ns("travertine");
         this.cobbledTravertine = ns("cobbled_travertine");
         this.mossyTravertine = ns("mossy_cobbled_travertine");
         this.kaolin = ns("kaolin");
         this.lightGrayKaolin = ns("light_gray_kaolin");
         this.grayKaolin = ns("gray_kaolin");
         this.orangeKaolin = ns("orange_kaolin");
         this.brownKaolin = ns("brown_kaolin");
         this.whiteKaolin = ns("white_kaolin");
         BlockState[] r = this.rocks;
         r[Strata.STONE] = STONE;
         r[Strata.ANDESITE] = ANDESITE;
         r[Strata.TUFF] = TUFF;
         r[Strata.DEEPSLATE] = Blocks.DEEPSLATE.defaultBlockState();
         r[Strata.DIORITE] = DIORITE;
         r[Strata.CALCITE] = CALCITE;
         r[Strata.GRANITE] = Blocks.GRANITE.defaultBlockState();
         r[Strata.DRIPSTONE] = Blocks.DRIPSTONE_BLOCK.defaultBlockState();
         r[Strata.BASALT] = Blocks.SMOOTH_BASALT.defaultBlockState();
         r[Strata.TRAVERTINE] = this.travertine != null ? this.travertine : DIORITE;
         r[Strata.COBBLE] = COBBLE;
         r[Strata.MOSSY_COBBLE] = MOSSY_COBBLE;
         r[Strata.COBBLED_DEEPSLATE] = COBBLED_DEEPSLATE;
         // kaolin suites are only chosen with Nature's Spirit installed; natural stone of similar value otherwise
         r[Strata.K_LIGHT_GRAY] = this.lightGrayKaolin != null ? this.lightGrayKaolin : DIORITE;
         r[Strata.K_GRAY] = this.grayKaolin != null ? this.grayKaolin : ANDESITE;
         r[Strata.K_PLAIN] = this.kaolin != null ? this.kaolin : STONE;
         r[Strata.K_ORANGE] = this.orangeKaolin != null ? this.orangeKaolin : ANDESITE;
         r[Strata.K_BROWN] = this.brownKaolin != null ? this.brownKaolin : TUFF;
         r[Strata.K_WHITE] = this.whiteKaolin != null ? this.whiteKaolin : CALCITE;
         // rock_strata=deep only (chosen only with Nature's Spirit installed)
         BlockState chert = ns("chert");
         BlockState yellowKaolin = ns("yellow_kaolin");
         r[Strata.CHERT] = chert != null ? chert : ANDESITE;
         r[Strata.K_YELLOW] = yellowKaolin != null ? yellowKaolin : CALCITE;
         this.createLimestone = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "limestone"))
            .map(net.minecraft.world.level.block.Block::defaultBlockState).orElse(null);
         r[Strata.C_LIMESTONE] = this.createLimestone != null ? this.createLimestone : DIORITE;
      }

      private static BlockState ns(String name) {
         return net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("natures_spirit", name))
            .map(net.minecraft.world.level.block.Block::defaultBlockState)
            .orElse(null);
      }

      static Extra get() {
         Extra e = instance;
         if (e == null) {
            e = new Extra();
            instance = e;
         }
         return e;
      }

      Palette talus(Family f, Climate c) {
         return this.talus.computeIfAbsent(f + "/" + c, k -> build(f, c));
      }

      private Palette build(Family f, Climate c) {
         java.util.List<Object> e = new java.util.ArrayList<>();
         boolean ns = this.travertine != null;
         switch (f) {
            case GREY -> e.addAll(java.util.List.of(GRAVEL, 3, STONE, 3, ANDESITE, 2, TUFF, 1, COBBLE, 1));
            case DARK -> e.addAll(java.util.List.of(TUFF, 4, ANDESITE, 2, GRAVEL, 2, COBBLE, 1));
            case PALE -> e.addAll(ns ? java.util.List.of(this.travertine, 3, this.cobbledTravertine, 2, GRAVEL, 2, CALCITE, 1)
               : java.util.List.of(DIORITE, 2, CALCITE, 1, GRAVEL, 2, STONE, 2));
            case SAND, RED -> e.addAll(java.util.List.of(GRAVEL, 3, COARSE_DIRT, 2, STONE, 1));
         }
         if (c == Climate.HUMID && f != Family.SAND && f != Family.RED) {
            e.addAll(java.util.List.of(MOSS, 2, f == Family.PALE && this.mossyTravertine != null ? this.mossyTravertine : MOSSY_COBBLE, 1));
         } else if (c == Climate.TEMPERATE && (f == Family.GREY || f == Family.DARK || f == Family.PALE)) {
            e.addAll(java.util.List.of(COARSE_DIRT, 1));
         }
         return Palette.of(e.toArray());
      }
   }

   /** Coarse dirt, or Nature's Spirit's sandy soil on sand ground when it is installed. */
   private static BlockState drySoil(BlockState top) {
      BlockState s = Extra.get().sandySoil;
      return s != null && sandy(top) ? s : COARSE_DIRT;
   }

   /**
    * The rock to show at an exposed block at x/y/z whose natural stone is `underlying`: with rock_strata on (and
    * ground_cover), the province's rock at that spot; with rock_strata=off, or for anything that is not a generic base
    * stone, `underlying` unchanged. Pure function of its arguments and the world's settings.
    */
   static BlockState surfaceRock(long seed, int x, int y, int z, BlockState underlying) {
      if (!genericStone(underlying)) {
         return underlying;
      }
      Strata.Column col = column(seed, x, z, Climate.TEMPERATE);
      return col == null ? underlying : rockState(Strata.rock(col, y, 0.0F, 0.6F));
   }

   /** This world's rock_strata (off, regional or bedded); off unless ground_cover is on. */
   static String rockStrata() {
      Settings s = WorldLock.activeSettings();
      return active(s) ? s.rockStrata() : Settings.STRATA_OFF;
   }

   private static final ThreadLocal<Strata.Column> COLUMN = ThreadLocal.withInitial(Strata.Column::new);

   /**
    * The rock column at x/z (its province's suite; dry climates may have kaolin clay rock when Nature's Spirit is
    * installed), cached per thread; null with rock_strata=off.
    */
   private static Strata.Column column(long seed, int x, int z, Climate climate) {
      String mode = rockStrata();
      if (mode.equals(Settings.STRATA_OFF)) {
         return null;
      }
      if (mode.equals(Settings.STRATA_DEEP)) {
         return deepColumn(seed, x, z);
      }
      boolean bedding = mode.equals(Settings.STRATA_BEDDED);
      int key = bedding ? 1 : 0;
      Strata.Column c = COLUMN.get();
      if (!c.is(seed, x, z, key)) {
         // the provinces sharing the column: their rocks interfinger across a ~150-block transition
         Provinces.Blend blend = Provinces.blend(seed, x, z, Provinces.BLEND);
         boolean[] kaolin = new boolean[blend.provinces().length];
         for (int i = 0; i < kaolin.length; i++) {
            kaolin[i] = kaolinRegion(blend.provinces()[i]);
         }
         c.set(seed, x, z, blend, bedding, kaolin, key);
      }
      return c;
   }

   /** True with rock_strata=deep (and ground_cover). */
   static boolean deep() {
      return rockStrata().equals(Settings.STRATA_DEEP);
   }

   /**
    * rock_strata=deep: the rock column at x/z (DeepRock: unit stacks, tilt by province), cached per thread; null when the
    * world is not in deep mode.
    */
   static Strata.Column deepColumn(long seed, int x, int z) {
      if (!deep()) {
         return null;
      }
      int key = 1 | 4;
      Strata.Column c = COLUMN.get();
      if (!c.is(seed, x, z, key)) {
         WorldGenLevel level = LEVEL.get();
         if (level != null) {
            DeepRock.world(level.getSeaLevel(), level.getMaxBuildHeight());
            if (DeepRock.ctx == null) {
               DeepRock.ctx = FtfTerrain.context(level);
            }
         }
         Provinces.Blend blend = Provinces.blend(seed, x, z, Provinces.BLEND);
         int n = blend.provinces().length;
         boolean ns = DeepRock.NS_ALLOWED && Extra.get().kaolin != null;
         boolean create = DeepRock.CREATE_ALLOWED && Extra.get().createLimestone != null;
         boolean[] kaolin = new boolean[n];
         int[] flags = new int[n];
         for (int i = 0; i < n; i++) {
            Provinces.Province p = blend.provinces()[i];
            kaolin[i] = ns && kaolinRegion(p);
            flags[i] = (kaolin[i] ? 1 : 0) | (ns ? 2 : 0) | (ns && drierHalf(p) ? 4 : 0) | (create ? 8 : 0);
         }
         c.set(seed, x, z, blend, true, kaolin, key);
         DeepRock.setup(c, seed, x, z, blend, flags);
      }
      return c;
   }

   /** The block of a Strata rock (for DeepRock's pass). */
   static BlockState rockBlock(int rock) {
      return rockState(rock);
   }

   /** True when a province lies in the drier half of the climate (Still Life humidity below 0 at its centre). */
   static boolean drierHalf(Provinces.Province p) {
      WorldGenLevel level = LEVEL.get();
      long seed = level != null ? level.getSeed() : StillLifeClimateBridge.lastSeed();
      long key = seed * 0x9E3779B97F4A7C15L ^ p.id() ^ 0x5DEECE66DL;
      Boolean known = DRIER.get(key);
      if (known != null) {
         return known;
      }
      if (!StillLifeClimateBridge.known()) {
         return false;
      }
      boolean dry = StillLifeClimateBridge.humidity((int)p.siteX(), 100, (int)p.siteZ()) < 0.0F;
      DRIER.put(key, dry);
      return dry;
   }

   private static final java.util.Map<Long, Boolean> DRIER = new java.util.concurrent.ConcurrentHashMap<>();

   /**
    * True when a province is dry country that may be kaolin clay rock: Nature's Spirit is installed and Still Life's
    * humidity at the province's centre is low. A property of the whole province (not of each column's biome), so clay
    * rock regions blend at their borders like any other rock and never stop at a biome edge.
    *
    * <p>The humidity is StillLifeClimateBridge.humidity: FTF's climate model at that spot, computed the same way from any
    * thread at any time. (It used to come from whatever climate sampler happened to be at hand, usually some chunk's own
    * with caches valid only for that chunk: a province switched between clay rock and grey rock from one chunk to the
    * next, along chunk borders.)
    */
   static boolean kaolinRegion(Provinces.Province p) {
      if (Extra.get().kaolin == null || !DeepRock.NS_ALLOWED && deep()) {
         return false;
      }
      // StillLifeClimateBridge.humidity is a pure function of the world and position (the same from any thread, in any
      // chunk, at any time), so the answer can be kept per seed and province
      WorldGenLevel level = LEVEL.get();
      long seed = level != null ? level.getSeed() : StillLifeClimateBridge.lastSeed();
      long key = seed * 0x9E3779B97F4A7C15L ^ p.id();
      Boolean known = KAOLIN.get(key);
      if (known != null) {
         return known;
      }
      if (!StillLifeClimateBridge.known()) {
         return false;
      }
      boolean dry = StillLifeClimateBridge.humidity((int)p.siteX(), 100, (int)p.siteZ()) < -0.35F;
      KAOLIN.put(key, dry);
      return dry;
   }

   /** Kaolin regions by world seed and province, worked out once. */
   private static final java.util.Map<Long, Boolean> KAOLIN = new java.util.concurrent.ConcurrentHashMap<>();

   /** Broken pieces of a real rock: stone breaks to cobble (moss on it where it is wet), the others stay themselves. */
   private static BlockState broken(BlockState rock, float wet, float v) {
      if (rock.is(Blocks.STONE)) {
         return v < wet * 0.5F ? MOSSY_COBBLE : COBBLE;
      }
      return rock;
   }

   /** Plain base stone (what the world is made of where nothing specific was put): the only rock the beds replace. */
   static boolean genericStone(BlockState s) {
      return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE)
         || s.is(Blocks.TUFF);
   }

   private static BlockState rockState(int rock) {
      return Extra.get().rocks[rock];
   }

   /** A talus block of this place's province and climate (for other passes: rocky shores, the alpine belt). */
   static BlockState talusBlock(long seed, int x, int z, Climate c, float temperature, float sample) {
      // the sand families need sand ground, which this can't see: the province's natural rock
      return Extra.get().talus(natural(Provinces.at(seed, x, z)), c).pick(sample);
   }

   private static Palette thin(Climate c) {
      return switch (c) {
         case COLD -> THIN_COLD;
         case HUMID -> THIN_HUMID;
         case ARID -> THIN_ARID;
         case RED_ARID -> THIN_RED;
         default -> THIN_TEMPERATE;
      };
   }

   /**
    * FTF's erodeRock: the soil above the first rock within 32 blocks becomes that rock (gravel when there is none). With
    * rock_strata on, the province's rock instead of plain stone, and the column is done down to the lowest neighbouring
    * ground (at most 24 blocks), so the faces of cliff steps show it too; below the soil only base stone is replaced.
    */
   private static void exposeRock(
      ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y, long seed, TalusField field, Climate climate, float wet, float joint
   ) {
      int x = pos.getX();
      int z = pos.getZ();
      int depth = 32;
      BlockState material = GRAVEL;
      for (int dy = 3; dy < 32; dy++) {
         pos.setY(y - dy);
         BlockState state = chunk.getBlockState(pos);
         if (state.is(FTFBlockTags.ROCK)) {
            material = state;
            depth = dy + 1;
            break;
         }
      }
      Strata.Column col = column(seed, x, z, climate);
      boolean beds = col != null && genericStone(material);
      for (int dy = 0; dy < depth; dy++) {
         int yy = y - dy;
         ColumnDecorator.replaceSolid(chunk, pos.setY(yy), beds ? rockState(Strata.rock(col, yy, wet, joint)) : material);
      }
      if (col != null && field != null) {
         int face = Math.min(24, (int)Math.floor(field.h[field.index(x, z)] - field.lowestNeighbour(x, z)));
         for (int dy = depth; dy <= face; dy++) {
            int yy = y - dy;
            if (genericStone(chunk.getBlockState(pos.setY(yy)))) {
               chunk.setBlockState(pos, rockState(Strata.rock(col, yy, wet, joint)), false);
            }
         }
      }
      pos.setY(y);
   }

   // ---------------------------------------------------------------- noise

   /** Smooth value noise in 0..1 with patches about `size` blocks across, plus a little dither so edges aren't clean. */
   static float patch(long seed, int x, int z, int salt, float size) {
      float v = CoverNoise.value(seed, x, z, salt, size);
      v += (hash(seed, x, z, salt + 1) - 0.5F) * 0.18F;
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   /** Hash of a position to 0..1. */
   static float hash(long seed, int x, int z, int salt) {
      return CoverNoise.hash(seed, x, z, salt);
   }
}

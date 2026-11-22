package stilllife.ftfbridge;

import etcodehome.freeterraforged.tags.FTFBlockTags;
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
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * ground_cover: takes over FreeTerraForged's erosion decorator (its "scree decorator", raw generation step, every column).
 *
 * <p>FTF decides by slope and height whether a column's top becomes bare rock (cliffs, high peaks), scree (steep slopes)
 * or thin soil (moderate slopes high up), then uses one mix for the whole world: tuff on every scree slope, andesite,
 * moss blocks and coarse dirt. It also only touches dirt, gravel and snow, so sandy slopes stay sand at any angle.
 *
 * <p>This keeps FTF's decision (same slope and height thresholds from the preset, same noise), but picks blocks that suit
 * the place, in patches rather than speckles:
 * <ul>
 *   <li>bare rock: as FTF (the rock under the soil is exposed);</li>
 *   <li>talus on steep slopes: gravel, stone, cobble and andesite where it is cold; gravel, coarse dirt, cobble and stone
 *       in temperate land, with mossy cobble where it is warm and wet; sandstone (or red sandstone and terracotta) with
 *       gravel in deserts;</li>
 *   <li>thin soil on moderate slopes: patches of coarse dirt, rooted dirt or gravel between the ground Still Life laid;</li>
 *   <li>at the foot of cliffs: an apron of rockfall gravel (sand and sandstone in deserts) and the odd boulder;</li>
 *   <li>in dry lands, gravel and coarse dirt washes where FTF's erosion left sediment on flat ground.</li>
 * </ul>
 */
final class GroundCover {
   private static final ThreadLocal<WorldGenLevel> LEVEL = new ThreadLocal<>();

   private enum Climate { COLD, TEMPERATE, HUMID, ARID, RED_ARID }

   private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
   private static final BlockState STONE = Blocks.STONE.defaultBlockState();
   private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
   private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
   private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
   private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
   private static final BlockState ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState();
   private static final BlockState SAND = Blocks.SAND.defaultBlockState();
   private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
   private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
   private static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();
   private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
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

   private static final Palette TALUS_COLD = Palette.of(GRAVEL, 3, STONE, 2, COBBLE, 2, ANDESITE, 1);
   private static final Palette TALUS_TEMPERATE = Palette.of(GRAVEL, 3, COARSE_DIRT, 2, COBBLE, 1, STONE, 1);
   private static final Palette TALUS_HUMID = Palette.of(GRAVEL, 2, COARSE_DIRT, 2, MOSSY_COBBLE, 2, COBBLE, 1);
   private static final Palette TALUS_ARID = Palette.of(SANDSTONE, 3, GRAVEL, 1, SAND, 1);
   private static final Palette TALUS_RED = Palette.of(RED_SANDSTONE, 2, TERRACOTTA, 1, GRAVEL, 1, RED_SAND, 1);

   private static final Palette ALPINE_COLD = Palette.of(COARSE_DIRT, 3, GRAVEL, 2, STONE, 1, COBBLE, 1);
   private static final Palette ALPINE_TEMPERATE = Palette.of(COARSE_DIRT, 3, GRAVEL, 2, ROOTED_DIRT, 1, STONE, 1);
   private static final Palette ALPINE_HUMID = Palette.of(Blocks.MOSS_BLOCK.defaultBlockState(), 2, COARSE_DIRT, 2, GRAVEL, 1, MOSSY_COBBLE, 1);
   private static final Palette ALPINE_ARID = Palette.of(GRAVEL, 2, COARSE_DIRT, 2, STONE, 1);
   private static final Palette ALPINE_RED = Palette.of(GRAVEL, 2, COARSE_DIRT, 1, TERRACOTTA, 1, STONE, 1);

   private static final Palette THIN_COLD = Palette.of(null, 3, COARSE_DIRT, 2, GRAVEL, 1);
   private static final Palette THIN_TEMPERATE = Palette.of(null, 4, COARSE_DIRT, 2, ROOTED_DIRT, 1);
   private static final Palette THIN_HUMID = Palette.of(null, 5, ROOTED_DIRT, 1, COARSE_DIRT, 1);
   private static final Palette THIN_ARID = Palette.of(null, 4, COARSE_DIRT, 1, SANDSTONE, 1);
   private static final Palette THIN_RED = Palette.of(null, 4, TERRACOTTA, 1, RED_SANDSTONE, 1);

   private GroundCover() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.groundCover();
   }

   static void begin(WorldGenLevel level) {
      LEVEL.set(level);
   }

   static void end() {
      LEVEL.remove();
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
      if (!sand && !top.is(FTFBlockTags.ERODIBLE)) {
         return;
      }
      WorldGenLevel riverLevel = LEVEL.get();
      if (!snow && riverside(config, levels, chunk, cell, pos, surfaceY, riverLevel != null ? riverLevel.getSeed() : 0L)) {
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

      if (steepness > config.rockSteepness() || height > rockThreshold) {
         if (!sand) {
            exposeRock(chunk, pos, surfaceY);
         }
         return;
      }
      if (snow) {
         return;
      }

      WorldGenLevel level = LEVEL.get();
      long seed = level != null ? level.getSeed() : 0L;
      Climate climate = climate(chunk, pos, top, surfaceY);

      if (steepness > config.screeSteepness()) {
         BlockState m = talus(climate).pick(patch(seed, x, z, 11, 9.0F));
         ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 3, m);
         return;
      }
      if (level != null && alpine(level, chunk, pos, x, z, surfaceY, seed, climate, top, steepness, config.dirtSteepness())) {
         return;
      }
      if (steepness > config.dirtSteepness() && height > dirtThreshold) {
         BlockState m = thin(climate).pick(patch(seed, x, z, 23, 7.0F));
         if (m != null) {
            ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
         }
         return;
      }

      // gentle ground: rockfall below cliffs, dry washes on sediment
      if (level != null && cliffFoot(level, chunk, pos, x, z, surfaceY, seed, climate, TerrainFilter.sediment(cell))) {
         return;
      }
      if ((climate == Climate.ARID || climate == Climate.RED_ARID) && TerrainFilter.sediment(cell) > 0.35F
         && patch(seed, x, z, 41, 12.0F) > 0.62F) {
         BlockState m = patch(seed, x, z, 43, 5.0F) > 0.5F ? GRAVEL : (climate == Climate.RED_ARID ? RED_SAND : drySoil());
         ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
      }
   }

   /**
    * Below cliffs and steep slopes (the terrain rising at least 1 + 2d blocks within d blocks): within 3 blocks a gravel
    * apron, thinner further out, with the odd boulder on it; where FTF's erosion left sediment, an alluvial fan reaching 6
    * blocks out that grades from gravel to coarse dirt (sand in deserts).
    */
   private static boolean cliffFoot(
      WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surfaceY, long seed, Climate climate,
      float sediment
   ) {
      int near = 0;
      for (int d = 1; d <= 6 && near == 0; d++) {
         int rise = 1 + 2 * d;
         for (int i = 0; i < 8; i++) {
            int dx = (i == 0 || i == 1 || i == 7) ? d : (i == 3 || i == 4 || i == 5) ? -d : 0;
            int dz = (i == 1 || i == 2 || i == 3) ? d : (i == 5 || i == 6 || i == 7) ? -d : 0;
            if (FtfTerrain.ground(level, x + dx, z + dz) >= surfaceY + rise) {
               near = d;
               break;
            }
         }
      }
      if (near == 0) {
         return false;
      }
      if (near >= 4) {
         return alluvialFan(chunk, pos, x, z, surfaceY, seed, climate, near, sediment);
      }
      float cover = near == 1 ? 0.8F : near == 2 ? 0.5F : 0.25F;
      if (patch(seed, x, z, 61, 3.0F) > cover) {
         return false;
      }
      BlockState apron = switch (climate) {
         case ARID -> patch(seed, x, z, 63, 4.0F) > 0.5F ? GRAVEL : SAND;
         case RED_ARID -> patch(seed, x, z, 63, 4.0F) > 0.5F ? GRAVEL : RED_SAND;
         default -> GRAVEL;
      };
      ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, apron);

      if (near <= 2 && hash(seed, x, z, 67) < 0.05F) {
         pos.setY(surfaceY + 1);
         if (chunk.getBlockState(pos).isAir()) {
            BlockState rock = switch (climate) {
               case ARID -> SANDSTONE;
               case RED_ARID -> RED_SANDSTONE;
               case HUMID -> hash(seed, x, z, 69) < 0.6F ? MOSSY_COBBLE : COBBLE;
               case COLD -> hash(seed, x, z, 69) < 0.5F ? STONE : COBBLE;
               default -> hash(seed, x, z, 69) < 0.25F ? MOSSY_COBBLE : COBBLE;
            };
            chunk.setBlockState(pos, rock, false);
         }
         pos.setY(surfaceY);
      }
      return true;
   }

   /** Grass and soil tops the alpine belt may break up. */
   private static boolean soil(BlockState top) {
      return top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.PODZOL) || top.is(Blocks.COARSE_DIRT)
         || top.is(Blocks.ROOTED_DIRT) || top.is(Blocks.MOSS_BLOCK) || top.is(Blocks.MYCELIUM);
   }

   /**
    * The alpine belt: the 60 blocks below where the snowy peaks begin here (which follows peak_line_variation and
    * snow_line_climate). Going up, the grass breaks up into patches of coarse dirt, gravel and frost-shattered stone (moss
    * where it is wet), with a scattering of boulders; sunny south faces and sheltered hollows stay green longest, steep
    * ground goes first. True when this column was changed.
    */
   private static boolean alpine(
      WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surfaceY, long seed, Climate climate,
      BlockState top, float steepness, float dirtSteepness
   ) {
      double d = StillLifeClimateBridge.capLineDistance(x, surfaceY, z);
      if (Double.isNaN(d) || d >= 0.0 || d < -60.0 || !soil(top)) {
         return false;
      }
      float t = (float)((d + 60.0) / 60.0);
      float p = (float)Math.pow(t, 1.4) * 0.75F;
      if (FtfTerrain.facing(level, x, z).equals("south")) {
         p -= 0.15F;
      }
      if (FtfTerrain.hollow(level, x, z) > 0.3F) {
         p -= 0.2F;
      }
      if (steepness > dirtSteepness) {
         p += 0.15F;
      }
      // a fringe of bare, wet ground (gravel, coarse dirt) around snow patches, instead of grass running into snow
      if (MountainSnow.active(WorldLock.activeSettings()) && nearSnow(level, x, z, seed)) {
         p += 0.45F;
      }
      boolean changed = false;
      if (patch(seed, x, z, 121, 5.0F) < p) {
         Extra e = Extra.get();
         Palette palette = switch (climate) {
            case HUMID -> e.present() ? e.alpineHumid : ALPINE_HUMID;
            case ARID -> e.present() ? e.alpineArid : ALPINE_ARID;
            case RED_ARID -> ALPINE_RED;
            case COLD -> e.present() ? e.alpineCold : ALPINE_COLD;
            default -> ALPINE_TEMPERATE;
         };
         ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, palette.pick(patch(seed, x, z, 123, 3.0F)));
         changed = true;
      }
      if (hash(seed, x, z, 125) < 0.012F * t) {
         pos.setY(surfaceY + 1);
         if (chunk.getBlockState(pos).isAir()) {
            float r = hash(seed, x, z, 127);
            BlockState rock = climate == Climate.HUMID && r < 0.5F ? MOSSY_COBBLE : r < 0.5F ? STONE : COBBLE;
            chunk.setBlockState(pos, rock, false);
            changed = true;
         }
         pos.setY(surfaceY);
      }
      return changed;
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

   /** The outer part of a fan below a steep slope: only where FTF's erosion deposited sediment. */
   private static boolean alluvialFan(
      ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surfaceY, long seed, Climate climate, int near, float sediment
   ) {
      if (sediment < 0.25F) {
         return false;
      }
      float cover = (near == 4 ? 0.55F : near == 5 ? 0.35F : 0.2F) * Math.min(1.0F, sediment / 0.5F);
      if (patch(seed, x, z, 71, 4.0F) > cover) {
         return false;
      }
      boolean coarse = patch(seed, x, z, 73, 3.0F) < (near - 3) / 3.0F; // finer further out
      BlockState m = switch (climate) {
         case ARID -> coarse ? SAND : GRAVEL;
         case RED_ARID -> coarse ? RED_SAND : GRAVEL;
         default -> coarse ? COARSE_DIRT : GRAVEL;
      };
      ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
      return true;
   }

   /**
    * Ground along rivers (FTF's river zones), on gentle slopes:
    * <ul>
    *   <li>bank edges within a block of the water: gravel and sand bars (mud where warm and wet, sand in deserts);</li>
    *   <li>in dry climates, up to 6 blocks above the water: a green strip of grass and coarse dirt, dense on the banks,
    *       thinner across the valley floor, fading out at its edge.</li>
    * </ul>
    * True when this column was handled.
    */
   private static boolean riverside(
      ScreeFeature.Config config, Levels levels, ChunkAccess chunk, Cell cell, BlockPos.MutableBlockPos pos, int surfaceY, long seed
   ) {
      String zone = TerrainFilter.riverZone(cell);
      if (zone.equals("none") || zone.equals("riverbed") || cell.gradient > config.screeSteepness()) {
         return false;
      }
      int x = pos.getX();
      int z = pos.getZ();
      BlockState top = chunk.getBlockState(pos.setY(surfaceY));
      int waterY = cell.riverWaterLevel > 0.0F ? levels.scale(cell.riverWaterLevel) : levels.waterLevel;
      int above = surfaceY - waterY;
      if (above < 0) {
         return false;
      }
      Climate climate = climate(chunk, pos, top, surfaceY);

      if (zone.equals("banks") && above <= 1) {
         float p = patch(seed, x, z, 81, 4.0F);
         BlockState m = switch (climate) {
            case ARID -> p < 0.3F ? GRAVEL : null;
            case RED_ARID -> p < 0.3F ? GRAVEL : null;
            case HUMID -> p < 0.35F ? MUD : p < 0.55F ? GRAVEL : null;
            case COLD -> p < 0.55F ? GRAVEL : null;
            default -> p < 0.4F ? GRAVEL : p < 0.55F ? SAND : null;
         };
         if (m != null) {
            ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
            return true;
         }
         return false;
      }

      if ((climate == Climate.ARID || climate == Climate.RED_ARID) && above <= 6) {
         float cover = switch (zone) {
            case "banks" -> 0.75F;
            case "valley_floor" -> 0.45F;
            default -> 0.15F;
         };
         float p = patch(seed, x, z, 83, 6.0F);
         if (p < cover) {
            BlockState m = p < cover * 0.8F ? GRASS : drySoil();
            ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY, m);
            ColumnDecorator.fillDownSolid(chunk, pos, surfaceY - 1, surfaceY - 2, DIRT);
            return true;
         }
      }
      return false;
   }

   private static Climate climate(ChunkAccess chunk, BlockPos.MutableBlockPos pos, BlockState top, int surfaceY) {
      if (top.is(Blocks.RED_SAND)) {
         return Climate.RED_ARID;
      }
      Holder<Biome> holder = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(surfaceY), QuartPos.fromBlock(pos.getZ()));
      Biome biome = holder.value();
      if (top.is(Blocks.SAND) || !biome.hasPrecipitation()) {
         return Climate.ARID;
      }
      float t = biome.getBaseTemperature();
      if (t < 0.3F) {
         return Climate.COLD;
      }
      if (top.is(Blocks.PODZOL) || top.is(Blocks.MOSS_BLOCK) || top.is(Blocks.MUD) || top.is(BlockTags.MOSS_REPLACEABLE) && t >= 0.85F) {
         return Climate.HUMID;
      }
      return Climate.TEMPERATE;
   }

   /**
    * Nature's Spirit's ground blocks (installed with Still Life Naturalized), looked up once: chert as a second rock in
    * talus and alpine ground, sandy soil instead of coarse dirt in dry land, red moss on cold wet alpine ground. Null
    * fields when Nature's Spirit is not installed; the vanilla mixes are used then.
    */
   private static final class Extra {
      static volatile Extra instance;
      final BlockState chert;
      final BlockState sandySoil;
      final BlockState redMoss;
      final Palette talusCold;
      final Palette talusTemperate;
      final Palette alpineCold;
      final Palette alpineHumid;
      final Palette alpineArid;

      private Extra() {
         this.chert = block("chert");
         this.sandySoil = block("sandy_soil");
         this.redMoss = block("red_moss_block");
         BlockState chertOr = this.chert != null ? this.chert : STONE;
         BlockState soilOr = this.sandySoil != null ? this.sandySoil : COARSE_DIRT;
         BlockState mossOr = this.redMoss != null ? this.redMoss : Blocks.MOSS_BLOCK.defaultBlockState();
         this.talusCold = Palette.of(GRAVEL, 3, STONE, 2, chertOr, 2, COBBLE, 1, ANDESITE, 1);
         this.talusTemperate = Palette.of(GRAVEL, 3, COARSE_DIRT, 2, chertOr, 1, COBBLE, 1, STONE, 1);
         this.alpineCold = Palette.of(COARSE_DIRT, 3, GRAVEL, 2, chertOr, 1, mossOr, 1, STONE, 1);
         this.alpineHumid = Palette.of(Blocks.MOSS_BLOCK.defaultBlockState(), 2, COARSE_DIRT, 2, mossOr, 1, GRAVEL, 1, MOSSY_COBBLE, 1);
         this.alpineArid = Palette.of(GRAVEL, 2, soilOr, 2, chertOr, 1);
      }

      static Extra get() {
         Extra e = instance;
         if (e == null) {
            e = new Extra();
            instance = e;
         }
         return e;
      }

      boolean present() {
         return this.chert != null;
      }

      private static BlockState block(String name) {
         return net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("natures_spirit", name))
            .map(net.minecraft.world.level.block.Block::defaultBlockState)
            .orElse(null);
      }
   }

   /** Coarse dirt, or Nature's Spirit's sandy soil in dry land when it is installed. */
   private static BlockState drySoil() {
      BlockState s = Extra.get().sandySoil;
      return s != null ? s : COARSE_DIRT;
   }

   private static Palette talus(Climate c) {
      Extra e = Extra.get();
      if (e.present() && (c == Climate.COLD || c == Climate.TEMPERATE)) {
         return c == Climate.COLD ? e.talusCold : e.talusTemperate;
      }
      return switch (c) {
         case COLD -> TALUS_COLD;
         case HUMID -> TALUS_HUMID;
         case ARID -> TALUS_ARID;
         case RED_ARID -> TALUS_RED;
         default -> TALUS_TEMPERATE;
      };
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

   /** FTF's erodeRock: the soil above the first rock within 32 blocks becomes that rock (gravel when there is none). */
   private static void exposeRock(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y) {
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
      for (int dy = 0; dy < depth; dy++) {
         ColumnDecorator.replaceSolid(chunk, pos.setY(y - dy), material);
      }
      pos.setY(y);
   }

   // ---------------------------------------------------------------- noise

   /** Smooth value noise in 0..1 with patches about `size` blocks across, plus a little dither so edges aren't clean. */
   static float patch(long seed, int x, int z, int salt, float size) {
      float fx = x / size;
      float fz = z / size;
      int ix = (int)Math.floor(fx);
      int iz = (int)Math.floor(fz);
      float tx = smooth(fx - ix);
      float tz = smooth(fz - iz);
      float a = lattice(seed, ix, iz, salt);
      float b = lattice(seed, ix + 1, iz, salt);
      float c = lattice(seed, ix, iz + 1, salt);
      float d = lattice(seed, ix + 1, iz + 1, salt);
      float v = a + (b - a) * tx + (c - a + (a - b - c + d) * tx) * tz;
      v += (hash(seed, x, z, salt + 1) - 0.5F) * 0.18F;
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   private static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   private static float lattice(long seed, int x, int z, int salt) {
      return hash(seed, x, z, salt * 31 + 7);
   }

   /** Hash of a position to 0..1. */
   static float hash(long seed, int x, int z, int salt) {
      long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
      h ^= h >>> 33;
      h *= 0xFF51AFD7ED558CCDL;
      h ^= h >>> 33;
      h *= 0xC4CEB9FE1A85EC53L;
      h ^= h >>> 33;
      return (h >>> 40) / (float)(1L << 24);
   }
}

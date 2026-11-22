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

   /** Replaces FTF's erodeColumn for one column. */
   static void erodeColumn(
      ScreeFeature.Config config, Levels levels, Noise rand, ChunkAccess chunk, Cell cell, BlockPos.MutableBlockPos pos, int surfaceY
   ) {
      if (cell.terrain.isRiver() || cell.terrain.isWetland() || cell.terrain == TerrainType.VOLCANO_PIPE) {
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
      if (steepness > config.dirtSteepness() && height > dirtThreshold) {
         BlockState m = thin(climate).pick(patch(seed, x, z, 23, 7.0F));
         if (m != null) {
            ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
         }
         return;
      }

      // gentle ground: rockfall below cliffs, dry washes on sediment
      if (level != null && cliffFoot(level, chunk, pos, x, z, surfaceY, seed, climate)) {
         return;
      }
      if ((climate == Climate.ARID || climate == Climate.RED_ARID) && TerrainFilter.sediment(cell) > 0.35F
         && patch(seed, x, z, 41, 12.0F) > 0.62F) {
         BlockState m = patch(seed, x, z, 43, 5.0F) > 0.5F ? GRAVEL : (climate == Climate.RED_ARID ? RED_SAND : COARSE_DIRT);
         ColumnDecorator.fillDownSolid(chunk, pos, surfaceY, surfaceY - 1, m);
      }
   }

   /**
    * Within 3 blocks of the foot of a cliff (the terrain rising at least 3 blocks within 1 block, 5 within 2 or 7 within 3):
    * a gravel apron, thinner further out, and now and then a boulder on it.
    */
   private static boolean cliffFoot(
      WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int surfaceY, long seed, Climate climate
   ) {
      int near = 0;
      for (int d = 1; d <= 3 && near == 0; d++) {
         int rise = 1 + 2 * d;
         for (int i = 0; i < 8; i++) {
            int dx = (i == 0 || i == 1 || i == 7) ? d : (i == 3 || i == 4 || i == 5) ? -d : 0;
            int dz = (i == 1 || i == 2 || i == 3) ? d : (i == 5 || i == 6 || i == 7) ? -d : 0;
            if (level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + dx, z + dz) - 1 >= surfaceY + rise) {
               near = d;
               break;
            }
         }
      }
      if (near == 0) {
         return false;
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

   private static Palette talus(Climate c) {
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

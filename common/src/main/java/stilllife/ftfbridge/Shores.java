package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * shores: the ground where land meets the sea or a lake, after FTF's erosion pass (raw generation, every column).
 * <ul>
 *   <li>rocky shores: gravel, stone and cobble (mossy where it is wet) up to a few blocks above the water below steep
 *       coasts, cliffs and steep lake sides;</li>
 *   <li>mud flats: mud and some clay in shallow (1-2 blocks), gently sloping sea water where it is warm and wet;</li>
 *   <li>lake shores and beds: sand, gravel (cold) or mud (warm and wet) on low lake shores and in the shallows, clay
 *       patches deeper down.</li>
 * </ul>
 * Where the water is comes from FreeTerraForged's terrain model (sea level, lake and river water levels), never from
 * neighbouring chunks' blocks, so the result does not depend on the order chunks are generated in.
 */
final class Shores {
   private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
   private static final BlockState STONE = Blocks.STONE.defaultBlockState();
   private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
   private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
   private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
   private static final BlockState SAND = Blocks.SAND.defaultBlockState();
   private static final BlockState MUD = Blocks.MUD.defaultBlockState();
   private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();

   private Shores() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.shores();
   }

   static void chunk(WorldGenLevel level, ChunkAccess chunk) {
      GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return;
      }
      long seed = level.getSeed();
      int x0 = chunk.getPos().getMinBlockX();
      int z0 = chunk.getPos().getMinBlockZ();
      int max = chunk.getMaxBuildHeight() - 1;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int z = z0 + lz;
            int y = FtfTerrain.ground(level, x, z);
            BlockState top = chunk.getBlockState(pos.set(x, y, z));
            if (!soft(top)) {
               continue;
            }
            Cell cell = FtfTerrain.cell(ctx, chunk, x, z);
            float slope = cell.gradient;
            String category = TerrainFilter.category(cell);
            GroundCover.Climate climate = GroundCover.climate(chunk, pos, top, y);
            pos.setY(y);

            if (chunk.getBlockState(pos.set(x, y + 1, z)).getFluidState().is(FluidTags.WATER)) {
               int surface = y + 1;
               while (surface < max && chunk.getBlockState(pos.set(x, surface + 1, z)).getFluidState().is(FluidTags.WATER)) {
                  surface++;
               }
               int depth = surface - y;
               pos.set(x, y, z);
               if (category.equals("lake")) {
                  lakeBed(chunk, pos, x, y, z, depth, climate, seed);
               } else if (depth <= 2 && slope < 0.25F && !category.equals("river") && wetAndWarm(climate)) {
                  float p = GroundCover.patch(seed, x, z, 151, 7.0F);
                  if (p < 0.6F) {
                     set(chunk, pos, y, p < 0.5F ? MUD : CLAY);
                  }
               }
               continue;
            }

            // dry ground: is there water within 3 blocks?
            int waterY = Integer.MIN_VALUE;
            boolean lake = false;
            search:
            for (int d = 1; d <= 3; d++) {
               for (int i = 0; i < 8; i++) {
                  int dx = (i == 0 || i == 1 || i == 7) ? d : (i == 3 || i == 4 || i == 5) ? -d : 0;
                  int dz = (i == 1 || i == 2 || i == 3) ? d : (i == 5 || i == 6 || i == 7) ? -d : 0;
                  int w = FtfTerrain.water(level, x + dx, z + dz);
                  if (w != Integer.MIN_VALUE) {
                     waterY = w;
                     lake = FtfTerrain.lakeWater(level, x + dx, z + dz);
                     break search;
                  }
               }
            }
            if (waterY == Integer.MIN_VALUE) {
               continue;
            }
            int above = y - waterY;
            if (above < 0 || above > 4) {
               continue;
            }
            pos.set(x, y, z);
            if (steep(level, x, z, y, slope)) {
               float cover = above <= 2 ? 0.85F : 0.5F;
               if (GroundCover.patch(seed, x, z, 153, 4.0F) < cover) {
                  set(chunk, pos, y, rocky(seed, x, z, climate, GroundCover.biomeTemperature(chunk, pos, y), GroundCover.patch(seed, x, z, 155, 3.0F)));
                  if (above <= 2) {
                     set(chunk, pos, y - 1, GRAVEL);
                  }
               }
            } else if (lake && above <= 1) {
               BlockState m = lakeShore(climate, GroundCover.patch(seed, x, z, 157, 5.0F));
               if (m != null && GroundCover.patch(seed, x, z, 159, 6.0F) < 0.6F) {
                  set(chunk, pos, y, m);
               }
            }
         }
      }
   }

   /** Below a steep coast: the shore column itself is steep, or the land rises 4+ blocks within 3 inland. */
   private static boolean steep(WorldGenLevel level, int x, int z, int y, float slope) {
      if (slope > 0.55F) {
         return true;
      }
      for (int i = 0; i < 8; i++) {
         int dx = (i == 0 || i == 1 || i == 7) ? 3 : (i == 3 || i == 4 || i == 5) ? -3 : 0;
         int dz = (i == 1 || i == 2 || i == 3) ? 3 : (i == 5 || i == 6 || i == 7) ? -3 : 0;
         if (FtfTerrain.estimate(level, x + dx, z + dz) - y >= 4) {
            return true;
         }
      }
      return false;
   }

   private static void lakeBed(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int y, int z, int depth,
                               GroundCover.Climate climate, long seed) {
      if (depth <= 2) {
         BlockState m = lakeShore(climate, GroundCover.patch(seed, x, z, 157, 5.0F));
         if (m != null && GroundCover.patch(seed, x, z, 161, 6.0F) < 0.65F) {
            set(chunk, pos, y, m);
         }
      } else if (depth <= 6 && GroundCover.patch(seed, x, z, 163, 5.0F) < 0.3F) {
         set(chunk, pos, y, CLAY);
      }
   }

   /** Rocky shore: gravel at the water's edge and the region's own rock (its talus), so shores match their hills. */
   private static BlockState rocky(long seed, int x, int z, GroundCover.Climate climate, float temperature, float sample) {
      if (sample < 0.4F) {
         return GRAVEL;
      }
      return GroundCover.talusBlock(seed, x, z, climate, temperature, (sample - 0.4F) / 0.6F);
   }

   private static BlockState lakeShore(GroundCover.Climate climate, float sample) {
      return switch (climate) {
         case COLD -> sample < 0.6F ? GRAVEL : SAND;
         case HUMID -> sample < 0.6F ? MUD : sample < 0.8F ? CLAY : SAND;
         case ARID, RED_ARID -> sample < 0.7F ? SAND : GRAVEL;
         default -> sample < 0.45F ? SAND : sample < 0.75F ? GRAVEL : MUD;
      };
   }

   private static boolean wetAndWarm(GroundCover.Climate climate) {
      return climate == GroundCover.Climate.HUMID;
   }

   /** Ground a shore may change: soil, sand, gravel, clay and mud (not rock, snow or Still Life's special blocks). */
   private static boolean soft(BlockState state) {
      return state.is(BlockTags.DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
         || state.is(Blocks.CLAY) || state.is(Blocks.MUD) || state.is(Blocks.GRASS_BLOCK);
   }

   private static void set(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y, BlockState state) {
      chunk.setBlockState(pos.setY(y), state, false);
      pos.setY(y);
   }
}

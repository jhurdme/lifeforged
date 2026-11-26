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
 *   <li>lake shores and beds: gravel, mud and clay on low lake shores and in the shallows (sand only where the ground is
 *       sand already), clay patches deeper down.</li>
 * </ul>
 * Ground under ice or snow blocks (sea ice, ice shelves, icebergs) is left alone.
 * Where the water is comes from FreeTerraForged's terrain model (sea level, lake and river water levels), never from
 * neighbouring chunks' blocks, so the result does not depend on the order chunks are generated in.
 */
final class Shores {
   private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
   private static final BlockState STONE = Blocks.STONE.defaultBlockState();
   private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
   private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
   private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
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
            BlockState over = chunk.getBlockState(pos.set(x, y + 1, z));
            if (MountainSnow.ice(over) || SnowCaps.isSnow(over)) {
               // under sea ice, an ice shelf or an iceberg: left as it is
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
                  lakeBed(chunk, pos, x, y, z, depth, climate, seed, top);
               } else if (depth <= 2 && slope < 0.25F && !category.equals("river") && mudFlat(level, top, x, y, z, climate)) {
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
            boolean river = false;
            search:
            for (int d = 1; d <= 3; d++) {
               for (int i = 0; i < 8; i++) {
                  int dx = (i == 0 || i == 1 || i == 7) ? d : (i == 3 || i == 4 || i == 5) ? -d : 0;
                  int dz = (i == 1 || i == 2 || i == 3) ? d : (i == 5 || i == 6 || i == 7) ? -d : 0;
                  int w = FtfTerrain.water(level, x + dx, z + dz);
                  if (w != Integer.MIN_VALUE) {
                     waterY = w;
                     lake = FtfTerrain.lakeWater(level, x + dx, z + dz);
                     river = FtfTerrain.riverWater(level, x + dx, z + dz);
                     break search;
                  }
               }
            }
            if (waterY == Integer.MIN_VALUE || river) {
               // river banks are left as Still Life and FTF make them (sea coasts and lakes keep their shores)
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
                  GroundCover.Climate rockClimate = climate == GroundCover.Climate.HUMID ? GroundCover.Climate.TEMPERATE : climate;
                  set(chunk, pos, y, rocky(seed, x, z, rockClimate, GroundCover.biomeTemperature(chunk, pos, y), GroundCover.patch(seed, x, z, 155, 3.0F)));
                  if (above <= 2) {
                     set(chunk, pos, y - 1, GRAVEL);
                  }
               }
            } else if (lake && above <= 1) {
               BlockState m = lakeShore(climate, GroundCover.patch(seed, x, z, 157, 5.0F), top);
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
                               GroundCover.Climate climate, long seed, BlockState top) {
      if (depth <= 2) {
         BlockState m = lakeShore(climate, GroundCover.patch(seed, x, z, 157, 5.0F), top);
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

   /**
    * Lake shores and shallows: gravel, mud and clay, never sand (or any sand-family block) on ground that was not sand
    * already; sandy ground keeps its sand with some gravel. Null keeps the ground as it is.
    */
   private static BlockState lakeShore(GroundCover.Climate climate, float sample, BlockState top) {
      if (top.is(Blocks.SAND) || top.is(Blocks.RED_SAND)) {
         return sample < 0.35F ? GRAVEL : null;
      }
      return switch (climate) {
         case COLD -> sample < 0.55F ? GRAVEL : null;
         case HUMID -> sample < 0.55F ? MUD : sample < 0.75F ? CLAY : null;
         case ARID, RED_ARID -> sample < 0.4F ? GRAVEL : null;
         default -> sample < 0.35F ? GRAVEL : sample < 0.55F ? MUD : sample < 0.65F ? CLAY : null;
      };
   }

   private static boolean wetAndWarm(GroundCover.Climate climate) {
      return climate == GroundCover.Climate.HUMID;
   }

   /**
    * Sea mud flats: only in warm, wet places whose sea floor is already muddy or soil (never sand, red sand or gravel),
    * or in swamp and mangrove biomes, and never within 16 blocks of dry (desert-like) land. GroundCover's humid class
    * follows Still Life's humidity, which is high along almost any coast, so it can't decide this alone.
    */
   private static boolean mudFlat(WorldGenLevel level, BlockState top, int x, int y, int z, GroundCover.Climate climate) {
      if (!wetAndWarm(climate) || top.is(Blocks.SAND) || top.is(Blocks.RED_SAND) || top.is(Blocks.GRAVEL)) {
         return false;
      }
      net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> here = level.getBiome(new BlockPos(x, y, z));
      String name = here.unwrapKey().map(k -> k.location().getPath()).orElse("");
      boolean swampy = name.contains("swamp") || name.contains("mangrove") || name.contains("marsh") || name.contains("bayou")
         || name.contains("fen") || name.contains("bog");
      boolean muddy = top.is(Blocks.MUD) || top.is(Blocks.CLAY) || top.is(BlockTags.DIRT);
      if (!swampy && !muddy) {
         return false;
      }
      for (int i = 0; i < 8; i++) {
         int dx = (i == 0 || i == 1 || i == 7) ? 16 : (i == 3 || i == 4 || i == 5) ? -16 : 0;
         int dz = (i == 1 || i == 2 || i == 3) ? 16 : (i == 5 || i == 6 || i == 7) ? -16 : 0;
         net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> b = level.getBiome(new BlockPos(x + dx, y, z + dz));
         if (!b.value().hasPrecipitation() || b.is(MountainSnow.DRY)) {
            return false;
         }
      }
      return true;
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

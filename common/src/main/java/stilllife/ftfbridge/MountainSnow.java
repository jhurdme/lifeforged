package stilllife.ftfbridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * mountain_snow: shapes the snow ground of mountains around the snow line, in FTF's erosion pass (raw generation, every
 * column), with real snow blocks and bare rock rather than thin layers:
 * <ul>
 *   <li>wind: a prevailing wind by latitude (see {@link StillLifeClimateBridge#windFromWest}); slopes facing into it and
 *       exposed ridges are scoured to bare rock in patches (more in dry climates), the sheltered side keeps its snow
 *       (and gets deeper drifts in {@link NaturalSnow});</li>
 *   <li>the snow line follows the sun: south faces go bare in patches up to 30 blocks above where the snowy peaks begin;</li>
 *   <li>below the snow line, leftover snow ground that grows going up: from nothing about 120 blocks down (further in
 *       cold climates, so lower mountains get some too) to most of the ground just under the line, in the most sheltered
 *       spots first (hollows and gullies, north faces, the side away from the wind);</li>
 *   <li>how much of this happens follows the climate: dry biomes have less snow and more scour, wet ones more snow, warm
 *       ones only keep it in the deepest gullies.</li>
 * </ul>
 */
final class MountainSnow {
   static final TagKey<Biome> DRY = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_dry"));
   static final TagKey<Biome> WET = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_wet"));

   private MountainSnow() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.mountainSnow();
   }

   /** How snowy this biome's climate is: 0.6 dry, 1.0 normal, 1.25 wet; warm biomes less again. */
   static float snowiness(Holder<Biome> biome) {
      float f = biome.is(DRY) || !biome.value().hasPrecipitation() ? 0.6F : biome.is(WET) ? 1.25F : 1.0F;
      float t = biome.value().getBaseTemperature();
      if (t > 0.8F) {
         f *= 0.4F;
      } else if (t > 0.4F) {
         f *= 0.75F;
      }
      return f;
   }

   /** True when the wind faces into this slope ("west" with a westerly). */
   static boolean windward(String facing, int z) {
      return facing.equals(StillLifeClimateBridge.windFromWest(z) > 0 ? "west" : "east");
   }

   static boolean lee(String facing, int z) {
      return facing.equals(StillLifeClimateBridge.windFromWest(z) > 0 ? "east" : "west");
   }

   /** True when this column was handled (FTF's own erosion is then skipped for it). */
   static boolean column(ChunkAccess chunk, int x, int y, int z, float slope) {
      WorldGenLevel level = GroundCover.level();
      if (level == null) {
         return false;
      }
      double d = StillLifeClimateBridge.capLineDistance(x, y, z);
      if (Double.isNaN(d) || d < -2.0 * REACH * 1.25) {
         return false;
      }
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
      BlockState top = chunk.getBlockState(pos);
      boolean snow = SnowCaps.isSnow(top);
      long seed = level.getSeed();
      Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
      float snowy = snowiness(biome);
      String facing = FtfTerrain.facing(level, x, z);
      float hollow = FtfTerrain.hollow(level, x, z);

      if (snow) {
         // wind scour: into the wind, steep and exposed
         if (windward(facing, z) && slope > 0.3F && hollow < 0.2F) {
            float p = (1.6F - snowy) * 0.45F * Math.min(1.0F, (slope - 0.3F) / 0.4F) + (hollow < -0.4F ? 0.15F : 0.0F);
            if (GroundCover.patch(seed, x, z, 101, 6.0F) < p) {
               bare(chunk, x, y, z);
               return true;
            }
         }
         // the snow line sits higher on sunny south faces
         if (facing.equals("south") && d >= 0.0 && d < 30.0 && hollow < 0.3F) {
            float p = (float)(1.0 - d / 30.0) * 0.7F / Math.max(0.6F, snowy);
            if (GroundCover.patch(seed, x, z, 109, 7.0F) < p) {
               bare(chunk, x, y, z);
               return true;
            }
         }
         return false;
      }

      if (d >= 0.0 || !snowable(top)) {
         return false;
      }
      return gradient(chunk, pos, x, y, z, d, slope, hollow, facing, biome, snowy, seed);
   }

   /** How far below the snow line leftover snow reaches, at most (blocks; scaled by the climate's snowiness). */
   static final double REACH = 120.0;

   /**
    * Below the snow line: leftover snow, more and more going up. The share of ground that holds snow rises from nothing
    * REACH blocks below the line (further in cold, wet climates, less in dry and warm ones) to most of it just under the
    * line. Which spots hold it is decided by how sheltered they are: deep hollows and steep gullies (couloirs) first, then
    * north faces and the side away from the wind; sunny south faces and wind-facing slopes last. So low down there are
    * only patches in the best spots, which grow and join up towards the line.
    */
   private static boolean gradient(
      ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int y, int z, double d, float slope, float hollow, String facing,
      Holder<Biome> biome, float snowy, long seed
   ) {
      // cold climates bring the snow further down (and so onto lower mountains); warm ones keep it up high
      float t = biome.value().getBaseTemperature();
      double reach = REACH * snowy * (t < 0.3F ? 1.0 + Math.min(1.0, (0.3F - t) * 1.5) : 1.0);
      if (d < -reach) {
         return false;
      }
      double u = 1.0 + d / reach; // 0 at the bottom of the reach, 1 at the line
      double coverage = u * u * 0.85;

      float shelter = 0.5F * Math.max(-1.0F, Math.min(1.5F, hollow));
      if (hollow > 0.45F && slope > 0.4F) {
         shelter += 0.35F; // couloir
      }
      if (facing.equals("north")) {
         shelter += 0.35F;
      } else if (facing.equals("south")) {
         shelter -= 0.35F;
      }
      if (lee(facing, z)) {
         shelter += 0.25F;
      } else if (windward(facing, z)) {
         shelter -= 0.25F;
      }
      // 0..1: how readily this spot keeps snow, with patchy noise so it forms patches rather than outlines
      double keep = 0.5 + shelter * 0.45 + (GroundCover.patch(seed, x, z, 103, 6.0F) - 0.5) * 0.5;
      if (keep < 1.0 - coverage) {
         return false;
      }
      snowGround(chunk, pos, y);
      return true;
   }

   private static boolean snowable(BlockState top) {
      return top.is(BlockTags.DIRT) || top.is(Blocks.GRAVEL) || top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(Blocks.COBBLESTONE)
         || top.is(Blocks.CALCITE) || top.is(Blocks.PACKED_MUD);
   }

   /** The top becomes snow ground: a snow block, and the soil right under it too (rock stays rock). */
   private static void snowGround(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y) {
      BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
      chunk.setBlockState(pos.setY(y), snowBlock, false);
      BlockState below = chunk.getBlockState(pos.setY(y - 1));
      if (below.is(BlockTags.DIRT) || below.is(Blocks.GRAVEL)) {
         chunk.setBlockState(pos, snowBlock, false);
      }
      pos.setY(y);
   }

   private static void bare(ChunkAccess chunk, int x, int y, int z) {
      SnowCaps.bareRock(chunk, x, y, z);
      SnowCaps.markBare(chunk, x, z);
   }
}

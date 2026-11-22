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

   /**
    * How much snow a place gets from its climate, about 0.3 (dry, warm) to 1.8 (wet, cold): the biome (dry / wet tags, base
    * temperature), the local humidity of Still Life's climate, and a slow regional variation (about a kilometre across),
    * so ranges differ from each other: some heavily snowed, some mostly bare rock.
    */
   static float climateSnow(Holder<Biome> biome, int x, int y, int z, long seed) {
      float humidity = StillLifeClimateBridge.humidity(x, y, z);
      float region = GroundCover.patch(seed, x, z, 131, 1100.0F) - 0.5F;
      return Math.max(0.3F, Math.min(1.8F, snowiness(biome) * (1.0F + 0.45F * humidity + 0.8F * region)));
   }

   /**
    * > 0 when this spot keeps snow ground, < 0 when it is bare: how sheltered it is (hollows and gullies, north faces, the
    * side away from the wind; sunny south faces, wind-facing slopes and exposed ridges last) against how much of the
    * ground holds snow at this height in this climate. The share rises from nothing well below the snow line, through
    * about 85% at it, to all of it some way above; wetter climates move the whole curve down the mountain and drier ones
    * up, so dry ranges have patchy peaks and wet ones snow far down. Below the line in cold climates it reaches further
    * down, onto lower mountains too.
    */
   static double score(WorldGenLevel level, int x, int y, int z, float slope, Holder<Biome> biome, long seed) {
      double d = StillLifeClimateBridge.capLineDistance(x, y, z);
      if (Double.isNaN(d)) {
         return Double.NaN;
      }
      float snow = climateSnow(biome, x, y, z, seed);
      double dEff = d + 60.0 * (snow - 1.0);
      double coverage;
      if (dEff < 0.0) {
         float t = biome.value().getBaseTemperature();
         double reach = REACH * Math.min(1.3, snow) * (t < 0.3F ? 1.0 + Math.min(1.0, (0.3F - t) * 1.5) : 1.0);
         if (dEff < -reach) {
            return -1.0;
         }
         double u = 1.0 + dEff / reach;
         coverage = u * u * 0.85;
      } else {
         double top = Math.max(0.7, Math.min(1.0, 0.6 + 0.4 * snow));
         coverage = 0.85 + (top - 0.85) * Math.min(1.0, dEff / 40.0);
      }

      String facing = FtfTerrain.facing(level, x, z);
      float hollow = FtfTerrain.hollow(level, x, z);
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
         // the wind scours steep slopes facing it, more in dry places
         shelter -= 0.25F + Math.max(0.0F, Math.min(1.0F, (slope - 0.3F) / 0.4F)) * 0.35F * (1.6F - Math.min(1.6F, snow));
      }
      double keep = 0.5 + shelter * 0.45 + (GroundCover.patch(seed, x, z, 103, 6.0F) - 0.5) * 0.5;
      return keep - (1.0 - coverage);
   }

   /** True when this column was handled (FTF's own erosion is then skipped for it). */
   static boolean column(ChunkAccess chunk, int x, int y, int z, float slope) {
      WorldGenLevel level = GroundCover.level();
      if (level == null) {
         return false;
      }
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
      BlockState top = chunk.getBlockState(pos);
      boolean snow = SnowCaps.isSnow(top);
      if (!snow && !snowable(top)) {
         return false;
      }
      long seed = level.getSeed();
      Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
      double score = score(level, x, y, z, slope, biome, seed);
      if (Double.isNaN(score)) {
         return false;
      }
      if (snow && score < 0.0) {
         bare(chunk, x, y, z);
         return true;
      }
      if (!snow && score >= 0.0) {
         snowGround(chunk, pos, y);
         return true;
      }
      return false;
   }

   /** How far below the snow line leftover snow reaches (blocks, before the climate scales it). */
   static final double REACH = 120.0;

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

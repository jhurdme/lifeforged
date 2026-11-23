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
    * How much snow falls here, about 0.25 (arid) to 1.75 (wet): Still Life's humidity at this place (the climate around a
    * mountain, not the peak biome on its top; blended per block, so no 4-block steps), a slow regional variation (about a
    * kilometre across) so ranges differ, and less in warm latitudes.
    */
   static float climateSnow(Holder<Biome> biome, int x, int y, int z, long seed) {
      float humidity = StillLifeClimateBridge.humidity(x, y, z);
      float region = GroundCover.patch(seed, x, z, 131, 1100.0F) - 0.5F;
      float moist = Math.max(0.0F, Math.min(1.0F, 0.5F + 0.7F * humidity + 0.6F * region));
      // warm latitudes get less snow at the same height (Still Life's latitude temperature; snow_line_climate raises the
      // line there too)
      float lat = StillLifeClimateBridge.latitudeTemperature(z);
      if (lat > 0.2F) {
         moist *= 1.0F - Math.min(0.7F, (lat - 0.2F) * 0.9F);
      }
      return 0.25F + 1.5F * moist;
   }

   /**
    * More than 0 when this spot holds snow, less than 0 when it is bare. The one snow model of the bridge, used for snow
    * ground, bare rock and snow layers alike.
    *
    * <p>Each place has its own snow line: where Still Life's snowy peaks begin (which already follows latitude with
    * snow_line_climate), raised in dry climates and lowered in wet ones (by up to about 80 blocks), and brought down
    * towards the sea in cold latitudes. Above it, the share of ground that holds snow is capped by the climate: about a
    * quarter in arid places (snow only in the most sheltered spots), about two thirds in a normal climate, nearly all of it
    * where it is wet. Below the line the share falls off over the reach (further in cold, wet places). Which spots get it
    * is decided by shelter: hollows and gullies, north faces and the side away from the wind first; sunny south faces,
    * wind-facing slopes and exposed ridges last.
    */
   static double score(WorldGenLevel level, int x, int y, int z, float slope, Holder<Biome> biome, long seed) {
      double d = StillLifeClimateBridge.capLineDistance(x, y, z);
      if (Double.isNaN(d)) {
         return Double.NaN;
      }
      float snow = climateSnow(biome, x, y, z, seed);
      float lat = StillLifeClimateBridge.latitudeTemperature(z);
      double lineShift = (1.0 - snow) * 110.0;
      if (lat < -0.3F) {
         lineShift -= (-0.3F - lat) * 500.0;
      }
      double dEff = d - lineShift;
      double top = Math.max(0.2, Math.min(1.0, 0.1 + 0.55 * snow));
      double coverage;
      if (dEff < 0.0) {
         double reach = REACH * Math.min(1.3, snow) * (lat < -0.3F ? 1.0 + Math.min(1.0, (-0.3F - lat) * 1.5) : 1.0);
         if (dEff < -reach) {
            return -1.0;
         }
         double u = 1.0 + dEff / reach;
         coverage = top * u * u;
      } else {
         coverage = top;
      }

      // shelter from the shape of the land over several blocks, not single steps (which drew bare rows along terraces)
      String facing = FtfTerrain.facingWide(level, x, z);
      float hollow = FtfTerrain.hollowWide(level, x, z) / 2.5F;
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
      // 0..1 (capped, so where the climate allows almost no snow, even the most sheltered spot stays bare)
      double keep = Math.max(0.0, Math.min(0.995, 0.5 + shelter * 0.45 + (GroundCover.patch(seed, x, z, 103, 6.0F) - 0.5) * 0.5));
      return keep - (1.0 - coverage);
   }

   /** Biomes whose snow ground belongs to the mountains (so the model may bare it), not to a cold lowland climate. */
   static final TagKey<Biome> MOUNTAIN = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_mountain"));

   /** Glaciers and ice keep their snow whatever the model says. */
   static boolean glacier(Holder<Biome> biome) {
      String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
      return path.contains("glacier") || path.contains("ice") || path.contains("frozen");
   }

   static boolean mountainBiome(Holder<Biome> biome) {
      if (biome.is(MOUNTAIN)) {
         return true;
      }
      String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
      return path.contains("peak") || path.contains("alpine") || path.contains("glacier");
   }

   /**
    * The snow ground of one chunk, after FTF's erosion pass (raw generation): every column, not only those FTF's erosion
    * looks at. Snow ground the model leaves bare becomes the rock under it (whatever biome made it: with mountain_cooling,
    * Still Life puts cold biomes high up in warm lands too; the model keeps snow down to the sea in cold latitudes; glaciers
    * keep theirs), and soil or rock where the model has snow becomes snow ground.
    */
   static void chunk(WorldGenLevel level, ChunkAccess chunk) {
      etcodehome.freeterraforged.world.worldgen.GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return;
      }
      long seed = level.getSeed();
      int x0 = chunk.getPos().getMinBlockX();
      int z0 = chunk.getPos().getMinBlockZ();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int z = z0 + lz;
            int y = FtfTerrain.ground(level, x, z);
            pos.set(x, y, z);
            BlockState top = chunk.getBlockState(pos);
            boolean snow = SnowCaps.isSnow(top);
            if (!snow && !snowable(top)) {
               continue;
            }
            Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
            if (snow && glacier(biome)) {
               continue;
            }
            float slope = FtfTerrain.cell(ctx, chunk, x, z).gradient;
            double score = score(level, x, y, z, slope, biome, seed);
            if (Double.isNaN(score)) {
               continue;
            }
            if (snow && score < 0.0) {
               bare(chunk, x, y, z);
            } else if (!snow && score >= 0.0) {
               snowGround(chunk, pos, y);
            }
         }
      }
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

package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Still Life puts snow blocks on its snowy peaks with surface rules. FreeTerraForged's erosion decorator only erodes
 * snow, powder snow and gravel (its 'erodible' tag), turning that snow into stone above its rock line, into tuff on
 * slopes and into moss/coarse dirt in its dirt band. With snow_caps the snow stays, except on cliffs. With snow_cap_fade the
 * lowest blocks of a snowy peaks cap keep their snow only in patches, so the cap fades in instead of starting at a solid edge.
 */
final class SnowCaps {
   private SnowCaps() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.snowCaps();
   }

   static boolean isSnow(BlockState state) {
      return state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
   }

   private static final ResourceLocation SNOWY_PEAKS = ResourceLocation.fromNamespaceAndPath("still_life", "snowy_peaks");
   private static final ThreadLocal<BareMask> BARE = ThreadLocal.withInitial(BareMask::new);

   /**
    * snow_cap_fade, in FTF's erosion pass (every column, before FTF's snow pass): true when this column of the lowest blocks
    * of a snowy peaks cap should have no snow (patches thinning downwards; ridges and steep spots first, hollows last). The column
    * is remembered, so FTF's snow pass later clears any snow layer on it (the game lays one on every cold surface).
    */
   static boolean fadeBare(ChunkAccess chunk, int x, int y, int z, float slope) {
      if (MountainSnow.active(WorldLock.activeSettings())) {
         return false; // mountain_snow decides where caps are bare
      }
      Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
      if (!biome.is(SNOWY_PEAKS)) {
         return false;
      }
      double share = StillLifeClimateBridge.capSnowShare(x, y, z, slope);
      if (share >= 1.0) {
         return false;
      }
      // patches about 7 blocks across rather than a block-by-block dither; snow holds longest in hollows and gullies,
      // ridges and edges go bare first
      net.minecraft.world.level.WorldGenLevel level = GroundCover.level();
      long seed = level != null ? level.getSeed() : 0L;
      double sample = GroundCover.patch(seed, x, z, 97, 7.0F);
      if (level != null) {
         share += 0.35 * Math.max(-1.0, Math.min(1.0, FtfTerrain.hollow(level, x, z)));
      }
      if (sample < share) {
         return false;
      }
      BARE.get().mark(chunk.getPos().x, chunk.getPos().z, x, z);
      return true;
   }

   /** Replaces the snow on top of this column (and the dirt Still Life puts under it) with the rock or scree below. */
   static void bareRock(ChunkAccess chunk, int x, int y, int z) {
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
      int k = 1;
      BlockState below = chunk.getBlockState(pos.setY(y - 1));
      while (k < 6 && (isSnow(below) || below.is(BlockTags.DIRT))) {
         k++;
         below = chunk.getBlockState(pos.setY(y - k));
      }
      BlockState rock = below.isAir() || !below.getFluidState().isEmpty() || isSnow(below) || below.is(BlockTags.DIRT)
         ? Blocks.STONE.defaultBlockState() : below;
      // the region's own rock in its beds where ground_cover knows it (during FTF's erosion pass), else as found
      net.minecraft.world.level.WorldGenLevel level = GroundCover.level();
      for (int dy = y - k + 1; dy <= y; dy++) {
         chunk.setBlockState(pos.setY(dy), level != null ? GroundCover.surfaceRock(level.getSeed(), x, dy, z, rock) : rock, false);
      }
   }

   /** Marks a column bared in the erosion pass, so FTF's snow pass later clears the snow layer the game lays on it. */
   static void markBare(ChunkAccess chunk, int x, int z) {
      BARE.get().mark(chunk.getPos().x, chunk.getPos().z, x, z);
   }

   /** True when fadeBare() marked this column of the chunk now being decorated. */
   static boolean bared(int x, int z) {
      return BARE.get().is(x, z);
   }

   /** Columns marked by fadeBare() in the chunk being decorated (its features run in order on one thread). */
   private static final class BareMask {
      int cx = Integer.MIN_VALUE;
      int cz = Integer.MIN_VALUE;
      final long[] bits = new long[4];

      void mark(int chunkX, int chunkZ, int x, int z) {
         if (chunkX != cx || chunkZ != cz) {
            cx = chunkX;
            cz = chunkZ;
            java.util.Arrays.fill(bits, 0L);
         }
         int i = (z & 15) << 4 | (x & 15);
         bits[i >> 6] |= 1L << (i & 63);
      }

      boolean is(int x, int z) {
         if (x >> 4 != cx || z >> 4 != cz) {
            return false;
         }
         int i = (z & 15) << 4 | (x & 15);
         return (bits[i >> 6] & 1L << (i & 63)) != 0L;
      }
   }

   /** Slope from which snow is eroded: the bridge's own value, or FTF's rock steepness from the preset. */
   static float cliffSteepness(Settings s, ScreeFeature.Config config) {
      return s.snowCliffSteepness() > 0.0 ? (float)s.snowCliffSteepness() : config.rockSteepness();
   }
}

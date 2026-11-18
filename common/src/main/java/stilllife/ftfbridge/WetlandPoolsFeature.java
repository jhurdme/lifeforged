package stilllife.ftfbridge;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Shallow pools of standing water in Still Life's wetland biomes, at any height.
 *
 * <p>Still Life floods its swamps, marshes and bogs only at sea level (its surface rules put water and mud at Y 62-63), because
 * Lithosphere's terrain drops those biomes to sea level. FreeTerraForged's terrain does not, so above sea level they were dry.
 * This places noise-shaped pool patches (a few to twenty blocks across) in the top ground block. Water only sits where the ground
 * is level for 2 blocks around (nothing lower, nothing more than a block higher), so it is always held and never lines the
 * 1-block steps of a slope; the sloped parts of a patch become mud instead, level with the ground. The decision depends only on
 * the terrain, so pools also match across chunk borders.
 *
 * <p>Salt lakes get their own kind of water, as on Lithosphere: wide irregular sheets one block deep over white calcite, with
 * round calcite islands and no mud, covering about salt_lake_water of the flat (Still Life's own salt lakes: about a fifth,
 * 1 block deep, calcite floor, sheets up to ~100 blocks across).
 */
final class WetlandPoolsFeature extends Feature<NoneFeatureConfiguration> {
   static final TagKey<Biome> POOL_BIOMES = TagKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath(StillLifeFtfBridge.MOD_ID, "has_wetland_pools")
   );
   /** Noise above this: pool patch (about 35% of the ground). */
   private static final double POOL = 0.1183;
   /** Noise between this and POOL: muddy rim (about another 10%). */
   private static final double RIM = 0.0386;
   /** Noise above this: the middle of a bigger pool is two blocks deep. */
   private static final double DEEP = 0.45;
   /** Water needs level ground this many blocks around it. */
   private static final int FLAT_R = 2;
   /** salt_lake_slope_pools: wide sheets only where the ground is level over this radius (a terrace at least 13 blocks wide). */
   private static final int SHEET_R = 6;
   private static final ResourceKey<Biome> SALT_LAKE = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("still_life", "salt_lake"));
   /** Salt water: noise share = salt_lake_water x this (the flat-ground rule and the islands take back about a fifth). */
   private static final double SALT_SHARE_GAIN = 1.25;
   /** Salt water sheets: the shape noise is read at a point displaced by up to about this many blocks (ragged shores, arms). */
   private static final double SALT_WARP = 32.0;
   /** Calcite islands: one chance per cell of this size, radius 3-6 (Still Life's salt crust disks are radius 5-7). */
   private static final int ISLAND_CELL = 20;
   private static final double ISLAND_CHANCE = 0.55;
   private static volatile Noises noises;

   WetlandPoolsFeature(Codec<NoneFeatureConfiguration> codec) {
      super(codec);
   }

   private record Noises(
      long seed, NormalNoise pool, NormalNoise salt, NormalNoise warpX, NormalNoise warpZ, double[] saltQuantiles, NormalNoise saltPools, double[] poolQuantiles
   ) {
      double salt(double x, double z) {
         return this.salt.getValue(x + SALT_WARP * this.warpX.getValue(x, 0.0, z), 0.0, z + SALT_WARP * this.warpZ.getValue(x, 0.0, z));
      }

      /** Salt noise value above which this share of the ground is water. */
      double saltThreshold(double share) {
         return quantile(this.saltQuantiles, share);
      }

      /** The same for the small round pools used on terraced ground (salt_lake_slope_pools). */
      double poolThreshold(double share) {
         return quantile(this.poolQuantiles, share);
      }

      private static double quantile(double[] q, double share) {
         int i = (int)Math.round((1.0 - Math.max(0.0, Math.min(1.0, share))) * (q.length - 1));
         return q[Math.max(0, Math.min(q.length - 1, i))];
      }
   }

   private static Noises noises(long seed) {
      Noises n = noises;
      if (n == null || n.seed != seed) {
         NormalNoise pool = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x3C6EF372FE94F82BL), -4, new double[]{1.0, 0.6});
         NormalNoise salt = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x5A17_1A4E_5A17_1A4EL), -7, new double[]{0.6, 1.0, 0.8, 0.5, 0.3});
         NormalNoise warpX = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x12345678ABCDEF01L), -7, new double[]{1.0, 0.5});
         NormalNoise warpZ = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0xABCDEF0112345678L), -7, new double[]{1.0, 0.5});
         NormalNoise saltPools = NormalNoise.create(new XoroshiroRandomSource(seed ^ 0x5A17_1A4E_5A17_1A4EL), -5, new double[]{1.0, 0.6, 0.3});
         n = new Noises(seed, pool, salt, warpX, warpZ, null, saltPools, null);
         double[] q = new double[4096];
         double[] qp = new double[4096];
         for (int i = 0; i < q.length; i++) {
            q[i] = n.salt((i % 64) * 97.0, (i / 64) * 97.0);
            qp[i] = saltPools.getValue((i % 64) * 97.0, 0.0, (i / 64) * 97.0);
         }

         java.util.Arrays.sort(q);
         java.util.Arrays.sort(qp);
         n = new Noises(seed, pool, salt, warpX, warpZ, q, saltPools, qp);
         noises = n;
      }

      return n;
   }

   /** True inside one of the round calcite islands that break up the salt water. */
   private static boolean island(long seed, int x, int z) {
      int c0x = Math.floorDiv(x - 6, ISLAND_CELL);
      int c1x = Math.floorDiv(x + 6, ISLAND_CELL);
      int c0z = Math.floorDiv(z - 6, ISLAND_CELL);
      int c1z = Math.floorDiv(z + 6, ISLAND_CELL);
      for (int cz = c0z; cz <= c1z; cz++) {
         for (int cx = c0x; cx <= c1x; cx++) {
            long h = cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ seed ^ 0x51A7CA1C17EL;
            h ^= h >>> 31;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 29;
            h *= 0x94D049BB133111EBL;
            h ^= h >>> 32;
            if ((h & 0xFFFF) / 65536.0 >= ISLAND_CHANCE) {
               continue;
            }

            double px = cx * ISLAND_CELL + ((h >>> 16) & 0xFFFF) % ISLAND_CELL;
            double pz = cz * ISLAND_CELL + ((h >>> 32) & 0xFFFF) % ISLAND_CELL;
            double r = 3.0 + 3.0 * ((h >>> 48) & 0xFF) / 255.0;
            double dx = x - px;
            double dz = z - pz;
            if (dx * dx + dz * dz <= r * r) {
               return true;
            }
         }
      }

      return false;
   }

   /** Heights of the ground (top solid block or water surface, under plants) around one chunk, read once each. */
   private static final class Ground {
      private static final int PAD = SHEET_R;
      private static final int W = 16 + 2 * PAD;
      private final WorldGenLevel level;
      private final int x0;
      private final int z0;
      private final int[] y = new int[W * W];
      private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

      Ground(WorldGenLevel level, int x0, int z0) {
         this.level = level;
         this.x0 = x0 - PAD;
         this.z0 = z0 - PAD;
         java.util.Arrays.fill(this.y, Integer.MIN_VALUE);
      }

      int at(int x, int z) {
         int i = (z - this.z0) * W + (x - this.x0);
         int v = this.y[i];
         if (v == Integer.MIN_VALUE) {
            v = this.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            for (int k = 0; k < 4; k++) {
               this.pos.set(x, v, z);
               BlockState s = this.level.getBlockState(this.pos);
               if (s.isSolid() || !s.getFluidState().isEmpty()) {
                  break;
               }

               v--;
            }

            this.y[i] = v;
         }

         return v;
      }

      /** Level ground for FLAT_R blocks around: nothing lower than top, nothing more than one block higher. */
      boolean flat(int x, int top, int z) {
         for (int dz = -FLAT_R; dz <= FLAT_R; dz++) {
            for (int dx = -FLAT_R; dx <= FLAT_R; dx++) {
               int g = this.at(x + dx, z + dz);
               if (g < top || g > top + 1) {
                  return false;
               }
            }
         }

         return true;
      }

      /** Every horizontal neighbour's ground reaches at least as high as top. */
      boolean held(int x, int top, int z) {
         return this.at(x + 1, z) >= top && this.at(x - 1, z) >= top && this.at(x, z + 1) >= top && this.at(x, z - 1) >= top;
      }

      /** A wide level terrace: the ground is at one level for SHEET_R blocks around. */
      boolean wide(int x, int z) {
         int lo = Integer.MAX_VALUE;
         int hi = Integer.MIN_VALUE;
         for (int dz = -SHEET_R; dz <= SHEET_R; dz++) {
            for (int dx = -SHEET_R; dx <= SHEET_R; dx++) {
               int g = this.at(x + dx, z + dz);
               lo = Math.min(lo, g);
               hi = Math.max(hi, g);
               if (hi != lo) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   @Override
   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
      if (!BridgeConfig.enabled() || !WorldLock.activeSettings().wetlandPools()) {
         return false;
      }

      WorldGenLevel level = context.level();
      long seed = level.getSeed();
      Noises n = noises(seed);
      BlockPos origin = context.origin();
      int x0 = SectionPos.blockToSectionCoord(origin.getX()) << 4;
      int z0 = SectionPos.blockToSectionCoord(origin.getZ()) << 4;
      int minY = level.getMinBuildHeight();
      BlockState water = Blocks.WATER.defaultBlockState();
      BlockState mud = Blocks.MUD.defaultBlockState();
      BlockState calcite = Blocks.CALCITE.defaultBlockState();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      Ground ground = new Ground(level, x0, z0);
      boolean placed = false;
      double saltShare = WorldLock.activeSettings().saltLakeWater();
      double saltThreshold = saltShare > 0.0 ? n.saltThreshold(Math.min(0.95, saltShare * SALT_SHARE_GAIN)) : Double.POSITIVE_INFINITY;
      double poolThreshold = saltShare > 0.0 ? n.poolThreshold(Math.min(0.95, saltShare * SALT_SHARE_GAIN)) : Double.POSITIVE_INFINITY;
      boolean slopePools = WorldLock.activeSettings().saltLakeSlopePools();

      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int z = z0 + lz;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            if (top - 3 <= minY || level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1 != top) {
               continue;
            }

            pos.set(x, top, z);
            if (level.getBiome(pos).is(SALT_LAKE)) {
               // salt lakes: one block of water over calcite where the sheet noise says so, outside the islands, on level ground
               if (saltShare <= 0.0) {
                  continue;
               }

               BlockState g = level.getBlockState(pos);
               if (!g.is(Blocks.CALCITE) && !g.is(Blocks.CLAY) && !g.is(BlockTags.DIRT) && !g.is(Blocks.SAND)) {
                  continue;
               }

               // salt_lake_slope_pools: on terraced ground (not a wide level terrace) small round pools instead of sheets, which
               // would only lie in bands along each step there
               boolean sheet = !slopePools || ground.wide(x, z);
               if (sheet ? n.salt(x, z) < saltThreshold || island(seed, x, z) : n.saltPools.getValue(x, 0.0, z) < poolThreshold) {
                  continue;
               }

               // sheets need level ground around them; the small pools on terraced ground only need their four neighbours to hold
               // them (as in 5.1 test 1), so they sit in each step as round pools with a one-block rim
               if (sheet ? !ground.flat(x, top, z) : !ground.held(x, top, z)) {
                  continue;
               }

               pos.set(x, top - 1, z);
               if (!level.getBlockState(pos).isSolid()) {
                  continue;
               }

               level.setBlock(pos, calcite, 2);
               pos.set(x, top, z);
               level.setBlock(pos, water, 2);
               placed = true;
               continue;
            }

            double v = n.pool.getValue(x, 0.0, z);
            if (v < RIM) {
               continue;
            }

            BlockState g = level.getBlockState(pos);
            if (!g.is(BlockTags.DIRT) && !g.is(Blocks.CLAY) || !level.getBiome(pos).is(POOL_BIOMES)) {
               continue;
            }

            boolean soil = g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.DIRT) || g.is(Blocks.COARSE_DIRT) || g.is(Blocks.PODZOL);
            if (v < POOL || !ground.flat(x, top, z)) {
               // muddy rim, and the parts of a pool patch on sloping ground: mud, level with the ground
               if (soil) {
                  level.setBlock(pos, mud, 2);
                  placed = true;
               }

               continue;
            }

            int depth = v >= DEEP ? 2 : 1;
            pos.set(x, top - depth, z);
            if (!level.getBlockState(pos).isSolid()) {
               depth = 1;
               pos.set(x, top - 1, z);
               if (!level.getBlockState(pos).isSolid()) {
                  continue;
               }
            }

            level.setBlock(pos, mud, 2);
            for (int d = 0; d < depth; d++) {
               pos.set(x, top - d, z);
               level.setBlock(pos, water, 2);
            }

            placed = true;
         }
      }

      return placed;
   }
}

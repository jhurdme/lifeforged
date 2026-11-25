package stilllife.ftfbridge;

import etcodehome.freeterraforged.tags.FTFBlockTags;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * natural_snow_layers: replaces FreeTerraForged's smooth snow layers.
 *
 * <p>FTF's smooth decorator takes the decimal part of its own terrain height as the snow depth (height 143.6 over a block
 * at 143 = 5 layers). The blocks the game builds are interpolated between noise cells a few blocks apart, so they only
 * match FTF's height at those points: in between the real ground sits up to a few blocks above or below it. Where it is a
 * block or more lower FTF places a full snow block, where it is higher the thinnest layer, and where they differ by more
 * than 2 blocks it skips the column, which repeats every few blocks as a grid of stripes, lumps and gaps.
 *
 * <p>This pass works from the real ground only. Ground heights come from the chunk generator's terrain height map (the
 * same in every chunk, so the snow lines up across chunk borders). Like real drifts, snow fills the foot of every step up
 * to the level above and tapers away from it over 2-3 blocks (each column is raised to its highest neighbour, at most one
 * block, and that surface is smoothed), so a staircase of 1-block steps reads as a slope of snow; open flats and the tops
 * of steps get a thin cover, exposed edges stay thin. FTF's slope thins it on steep ground and clears it off cliffs; it is
 * a little deeper on north faces and in colder places; bare rock and gravel only hold it in hollows. Never more than 7
 * layers, so no lone snow blocks.
 */
final class NaturalSnow {
   /** Blur radius of the snow surface, and how far around the chunk the ground is read (blur + 1 for the drift fill). */
   private static final int B = 2;
   private static final int R = B + 1;
   private static final int SIZE = 16 + 2 * R;
   private static final float[][] WEIGHT = new float[2 * B + 1][2 * B + 1];

   static {
      for (int dz = -B; dz <= B; dz++) {
         for (int dx = -B; dx <= B; dx++) {
            WEIGHT[dz + B][dx + B] = (float)Math.exp(-(dx * dx + dz * dz) / 2.5);
         }
      }
   }

   private NaturalSnow() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.naturalSnowLayers();
   }

   /** Runs after FTF's snow decorator (whose smoothing the mixin switched off) on one chunk. */
   static void decorate(WorldGenLevel level, BlockPos origin, boolean coverPlants, ScreeFeature.Config erodeConfig) {
      GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return;
      }
      Settings s = WorldLock.activeSettings();
      ChunkPos cp = new ChunkPos(origin);
      ChunkAccess chunk = level.getChunk(cp.x, cp.z);
      int x0 = cp.getMinBlockX();
      int z0 = cp.getMinBlockZ();

      // terrain height (top solid block before features) around the chunk
      int[] ground = new int[SIZE * SIZE];
      for (int dz = 0; dz < SIZE; dz++) {
         for (int dx = 0; dx < SIZE; dx++) {
            ground[dz * SIZE + dx] = FtfTerrain.ground(level, x0 + dx - R, z0 + dz - R);
         }
      }

      // drift surface: each column filled up to its highest neighbour (at most 1 block up), so snow fills the foot of
      // every step up to the level above; smoothed below, it ramps away from the step instead of ending in an edge
      int[] drift = new int[SIZE * SIZE];
      for (int dz = 1; dz < SIZE - 1; dz++) {
         for (int dx = 1; dx < SIZE - 1; dx++) {
            int h0 = ground[dz * SIZE + dx];
            int m = h0;
            for (int oz = -1; oz <= 1; oz++) {
               for (int ox = -1; ox <= 1; ox++) {
                  m = Math.max(m, ground[(dz + oz) * SIZE + dx + ox]);
               }
            }
            drift[dz * SIZE + dx] = Math.min(m, h0 + 1);
         }
      }

      float cliff = SnowCaps.active(s) ? SnowCaps.cliffSteepness(s, erodeConfig) : erodeConfig.rockSteepness();
      long seed = level.getSeed();
      // mountain_snow's field for this chunk (null without mountain_snow, or before the world is known)
      SnowField.Chunk field = MountainSnow.active(s) ? SnowField.get(ctx, seed, cp.x, cp.z) : null;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int[] trees = new int[256];
      int treeCount = 0;
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int z = z0 + lz;
            int g = ground[(lz + R) * SIZE + lx + R];
            boolean mountainSnow = MountainSnow.active(s);
            // the column's top block (ChunkAccess.getHeight is already the top block's y, unlike LevelReader.getHeight:
            // one lower, the snow layer lying on top of a tree was never looked at, so leaf snow was never cleared)
            int canopy = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            if (canopy > g + 1 && treeTop(chunk, pos, x, canopy, z)) {
               // under trees and bushes (g is the terrain's own top, below trunks and leaves): Minecraft put its snow on
               // the leaves only. With mountain_snow, leaves keep it only where the tree stands in a snowfield: the
               // ground under it and nearly all of it around holds snow, so trees aren't white over green or bare ground
               // decided after the ground of the whole chunk is done (below), so it can look at the snow really lying there
               if (mountainSnow && field != null && !MountainSnow.keepsOwnSurface(level.getBiome(pos.set(x, g, z)))) {
                  trees[treeCount++] = lz << 4 | lx;
               }
               continue;
            }
            int top = realGround(chunk, pos, x, z);
            if (top < 0) {
               continue;
            }
            // the model's column, or null in a keeps_own_surface biome (Still Life's arctic and frozen biomes) off real
            // faces and cliffs: there its own snow, ice and ground stay, with the layers as without mountain_snow
            boolean keep = field != null && MountainSnow.keepsOwnSurface(level.getBiome(pos.set(x, top, z)));
            SnowField.Chunk model = field != null && !(keep && !MountainSnow.steepRock(SnowModel.face(ground, lx + R, lz + R))) ? field : null;
            if (model != null && !keep) {
               // (never in those biomes: their ice is Still Life's, at any slope)
               top = meltIce(level, chunk, pos, x, top, z, model);
            }
            // something stands on the terrain here (a boulder, a log): leave it
            if (Math.abs(top - g) > 1) {
               continue;
            }
            pos.set(x, top + 1, z);
            BlockState above = chunk.getBlockState(pos);
            boolean hasSnow = above.is(Blocks.SNOW);
            boolean plant = !hasSnow && coverPlants && coverable(above);
            if (model != null && !hasSnow && !plant) {
               // mountain_snow may want snow where FTF laid none: a ledge on a cliff, the thin edge of a snowfield on
               // ground the erosion pass bared, a mountain too warm for Minecraft's own freezing
               net.minecraft.core.Holder<Biome> b = level.getBiome(pos);
               if (above.isAir() && !MountainSnow.glacier(b) && !MountainSnow.waterBiome(b)) {
                  mountainLayers(level, chunk, pos, field, ground, drift, lx, lz, top, cliff, ctx, false, seed);
               }
               continue;
            }
            if (!hasSnow && !plant) {
               continue;
            }
            net.minecraft.core.Holder<Biome> holder = level.getBiome(pos);
            Biome biome = holder.value();
            if (model != null && !MountainSnow.glacier(holder) && !MountainSnow.waterBiome(holder)) {
               // format 18: the model decides thin snow on plant-topped ground too, whatever Minecraft's warmth test says
               // (Still Life's temperate forests and highlands are never "cold enough" by it, so their patch edges had no
               // layers and ended as hard blocks); under trees no snow is laid (the canopy above is skipped earlier)
               if (!plant || StillLifeClimateBridge.snowCurve() || biome.coldEnoughToSnow(pos)) {
                  mountainLayers(level, chunk, pos, field, ground, drift, lx, lz, top, cliff, ctx, plant, seed);
               }
               continue;
            }
            if (plant && !biome.coldEnoughToSnow(pos)) {
               continue;
            }

            Cell cell = FtfTerrain.cell(ctx, chunk, x, z);
            float slope = cell.gradient;
            // (with mountain_snow, only glaciers, seas, beaches and rivers, Still Life's arctic lowland and a world not
            // known yet come this way: the layers as before)
            if (slope > cliff) {
               if (hasSnow) {
                  clear(chunk, pos);
               }
               continue;
            }

            pos.setY(top);
            BlockState groundBlock = chunk.getBlockState(pos);
            boolean rock = ROCK.contains(groundBlock.getBlock());
            boolean snowGround = SnowCaps.isSnow(groundBlock);
            pos.setY(top + 1);
            boolean mountain = MountainSnow.active(s);
            int layers = layers(ground, drift, lx + R, lz + R, top, slope, cliff, temperature(biome, top), rock,
               mountain ? StillLifeClimateBridge.windFromWest(z) : 0, mountain ? MountainSnow.climateSnow(x, top, z, seed) : 1.0F);
            // on snow ground a single layer adds nothing but a seam: only drifts (2+) go on it
            if (snowGround && layers < 2) {
               layers = 0;
            }
            if (layers == 0) {
               if (hasSnow) {
                  clear(chunk, pos);
               }
               continue;
            }
            if (plant && layers < 2) {
               continue;
            }
            BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
            if (!snow.canSurvive(level, pos)) {
               continue;
            }
            chunk.setBlockState(pos, snow, false);
            pos.setY(top);
            BlockState below = chunk.getBlockState(pos);
            if (below.hasProperty(BlockStateProperties.SNOWY) && !below.getValue(BlockStateProperties.SNOWY)) {
               chunk.setBlockState(pos, below.setValue(BlockStateProperties.SNOWY, true), false);
            }
         }
      }

      // leaves: their snow stays only where the model has a snowfield AND snow really lies on the open ground around
      for (int k = 0; k < treeCount; k++) {
         int lx = trees[k] & 15;
         int lz = trees[k] >> 4;
         int x = x0 + lx;
         int z = z0 + lz;
         int g = ground[(lz + R) * SIZE + lx + R];
         pos.set(x, g, z);
         if (MountainSnow.glacier(level.getBiome(pos))) {
            continue;
         }
         if (!treeSnow(ctx, field, seed, ground, lx, lz) || !snowLiesAround(chunk, pos, x0, z0, lx, lz)) {
            clearLeafSnow(chunk, pos, x, z, g, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz));
         }
      }
   }

   /**
    * True unless the open ground around this column (the spots of treeSnow that lie in this chunk and are not under leaves
    * themselves) is mostly bare in the blocks as they now are: whatever the model says, leaves never stay white over
    * ground that shows no snow.
    */
   private static boolean snowLiesAround(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x0, int z0, int lx, int lz) {
      int open = 0;
      int snowy = 0;
      for (int k = 0; k < TREE_DX.length; k++) {
         int sx = lx + TREE_DX[k];
         int sz = lz + TREE_DZ[k];
         if (sx < 0 || sx > 15 || sz < 0 || sz > 15) {
            continue;
         }
         int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz);
         BlockState top = chunk.getBlockState(pos.set(x0 + sx, y, z0 + sz));
         if (top.is(net.minecraft.tags.BlockTags.LEAVES) || top.is(net.minecraft.tags.BlockTags.LOGS)) {
            continue; // under a tree: tells nothing
         }
         if (top.is(Blocks.SNOW)) {
            BlockState under = chunk.getBlockState(pos.setY(y - 1));
            if (under.is(net.minecraft.tags.BlockTags.LEAVES) || under.is(net.minecraft.tags.BlockTags.LOGS)) {
               continue;
            }
         }
         open++;
         if (top.is(Blocks.SNOW) || SnowCaps.isSnow(top)) {
            snowy++;
         }
      }
      return open == 0 || snowy * 2 > open;
   }

   /**
    * Bare rock and gravel: Still Life keeps snow off them (its snow_layers features, which the bridge switches off in its
    * place); here snow on them stays only where it collects, in hollows and against steps.
    */
   private static final java.util.Set<net.minecraft.world.level.block.Block> ROCK = java.util.Set.of(
      Blocks.GRAVEL, Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF, Blocks.CALCITE, Blocks.DEEPSLATE,
      Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SANDSTONE,
      Blocks.RED_SANDSTONE, Blocks.TERRACOTTA);

   /** Snow layers (0-7) for the column at (cx, cz) of the ground arrays, whose real top block is at y top. */
   static int layers(int[] ground, int[] drift, int cx, int cz, int top, float slope, float cliff, float temperature, boolean rock,
                     int wind, float snowy) {
      float sum = 0.0F;
      float weights = 0.0F;
      for (int dz = -B; dz <= B; dz++) {
         for (int dx = -B; dx <= B; dx++) {
            float w = WEIGHT[dz + B][dx + B];
            sum += drift[(cz + dz) * SIZE + cx + dx] * w;
            weights += w;
         }
      }
      // how far the smoothed drift surface lies above this column's ground: ~0.9 at the foot of a step, falling off over
      // 2-3 blocks; ~0 on open flats and the tops of steps; below 0 on ridges and edges
      float fill = sum / weights - top;
      if (rock) {
         // bare rock and gravel only hold snow on gentle ledges and in real hollows: the plain ground around (not the
         // step-filled drift surface) clearly higher. On a steep staircase of rock every step foot would count otherwise,
         // which striped whole rock faces with snow.
         float plain = 0.0F;
         for (int dz = -B; dz <= B; dz++) {
            for (int dx = -B; dx <= B; dx++) {
               plain += Math.min(top + 3, ground[(cz + dz) * SIZE + cx + dx]) * WEIGHT[dz + B][dx + B];
            }
         }
         plain = plain / weights - top;
         if (slope > 0.35F || plain < 0.35F) {
            return 0;
         }
      }

      float depth = 1.0F + Math.max(0.0F, fill) * 7.0F * snowy;
      if (fill < -0.3F) {
         depth = 1.0F; // exposed edge or ridge: wind keeps it thin
      }

      // prevailing wind (mountain_snow): it strips slopes facing into it and drops the snow on the sheltered side
      if (wind != 0) {
         int rise = ground[cz * SIZE + cx + 2] - ground[cz * SIZE + cx - 2]; // > 0: the ground rises towards the east
         if (rise * wind >= 2) {
            depth = Math.min(depth, 1.0F);
         } else if (rise * wind <= -2) {
            depth += 2.0F * snowy;
         }
      }

      // north faces (ground falling towards -z) keep a little more
      if (ground[(cz + 1) * SIZE + cx] - ground[(cz - 1) * SIZE + cx] >= 1) {
         depth += 0.75F;
      }
      // colder = a little deeper (altitude lowers it too)
      depth += Math.max(0.0F, Math.min(1.0F, (0.15F - temperature) / 0.6F));

      // steep slopes shed snow
      if (cliff > 0.0F) {
         float steep = slope / cliff;
         if (steep > 0.6F) {
            depth = Math.min(depth, 1.0F + (1.0F - steep) * 5.0F);
         }
      }
      return Math.max(rock ? 2 : 1, Math.min(7, Math.round(depth)));
   }

   /**
    * mountain_snow's layers on one column (pos at top + 1): the snow depth of the model, shaped by the real ground (drifts
    * at the foot of steps, thin on edges, only ledges on rock faces). Clears the snow where the model has none.
    */
   private static void mountainLayers(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, SnowField.Chunk field,
                                      int[] ground, int[] drift, int lx, int lz, int top, float cliff, GeneratorContext ctx,
                                      boolean plant, long seed) {
      int x = pos.getX();
      int z = pos.getZ();
      boolean hasSnow = chunk.getBlockState(pos).is(Blocks.SNOW);
      int i = lz * 16 + lx;
      double score = field.score(i, top);
      if (!(score >= 0.0)) {
         if (hasSnow) {
            clear(chunk, pos);
         }
         return;
      }
      float slope = FtfTerrain.cell(ctx, chunk, x, z).gradient;
      float steep = cliff > 0.0F ? slope / cliff : 0.0F;
      // the smoothed ground's height above this column (not the step-filled drift surface: on a slope every column has a
      // higher neighbour, so that lifted every tread by a block's worth and the snow kept the stairs)
      float fill = SnowModel.fill(ground, lx + R, lz + R, top);
      int face = SnowModel.face(ground, lx + R, lz + R);
      // a stepped slope where the snow is thin is rock, as the erosion pass decided from the same heights: no layers on
      // its narrow treads (they made white lines over grey risers)
      int g = ground[(lz + R) * SIZE + lx + R];
      double gScore = field.score(i, g);
      if (SnowModel.step(seed, x, z, ground, lx + R, lz + R, face, gScore, field.depth(i, g)) == SnowModel.STEP_BARE) {
         if (hasSnow) {
            clear(chunk, pos);
         }
         return;
      }
      int layers = SnowModel.layers(field.depth(i, top), fill, face, steep, SnowModel.ledge(seed, x, z));
      if (layers == 0 || plant && layers < 2) {
         if (hasSnow) {
            clear(chunk, pos);
         }
         return;
      }
      BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
      if (!snow.canSurvive(level, pos)) {
         return;
      }
      chunk.setBlockState(pos, snow, false);
      pos.setY(top);
      BlockState below = chunk.getBlockState(pos);
      if (below.hasProperty(BlockStateProperties.SNOWY) && !below.getValue(BlockStateProperties.SNOWY)) {
         chunk.setBlockState(pos, below.setValue(BlockStateProperties.SNOWY, true), false);
      }
      pos.setY(top + 1);
   }

   /** Snow depth (layers) the ground under a tree needs for its leaves to keep their snow. */
   static final float TREE_DEPTH = 2.0F;

   /** Offsets of the ground sampled around a tree: 8 points 3 blocks out. */
   private static final int[] TREE_DX = {3, -3, 0, 0, 2, 2, -2, -2};
   private static final int[] TREE_DZ = {0, 0, 3, -3, 2, -2, 2, -2};

   /**
    * mountain_snow: true when the leaves over this column may keep their snow: the ground under them holds a real snow cover
    * (at least TREE_DEPTH layers deep, and not a cliff the layer pass leaves bare) and the model has snow on at least 7 of 8
    * spots 2-3 blocks around (ground heights from the chunk's ground window; neighbouring chunks' fields where the spots
    * lie outside). So a tree is white only standing in a snowfield, and a crown at a snowfield's edge goes bare as a whole
    * rather than half.
    */
   static boolean treeSnow(GeneratorContext ctx, SnowField.Chunk field, long seed, int[] ground, int lx, int lz) {
      int g = ground[(lz + R) * SIZE + lx + R];
      float depth = field.depth(lz * 16 + lx, g);
      int face = SnowModel.face(ground, lx + R, lz + R);
      if (depth < TREE_DEPTH || face == SnowModel.CLIFF || face == SnowModel.FACE && depth < SnowModel.BLOCK_DEPTH) {
         return false; // too thin, or ground the layer pass leaves bare
      }
      int x0 = field.cx << 4;
      int z0 = field.cz << 4;
      int snowy = 0;
      for (int k = 0; k < TREE_DX.length; k++) {
         int sx = lx + TREE_DX[k];
         int sz = lz + TREE_DZ[k];
         int y = ground[(sz + R) * SIZE + sx + R];
         SnowField.Chunk f = sx >= 0 && sx < 16 && sz >= 0 && sz < 16 ? field : SnowField.get(ctx, seed, (x0 + sx) >> 4, (z0 + sz) >> 4);
         if (f != null && f.score(((z0 + sz) & 15) * 16 + ((x0 + sx) & 15), y) >= 0.0) {
            snowy++;
         }
         if (k + 1 - snowy > TREE_DX.length - 7) {
            return false; // a second spot without snow
         }
      }
      return snowy >= 7;
   }

   /** Removes snow layers lying on leaves or logs anywhere in the column above the ground (overhangs included). */
   private static void clearLeafSnow(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int g, int canopy) {
      BlockState below = chunk.getBlockState(pos.set(x, canopy, z));
      for (int y = canopy; y > g + 1; y--) {
         BlockState here = below;
         below = chunk.getBlockState(pos.set(x, y - 1, z));
         if (here.is(Blocks.SNOW) && (below.is(net.minecraft.tags.BlockTags.LEAVES) || below.is(net.minecraft.tags.BlockTags.LOGS))) {
            chunk.setBlockState(pos.setY(y), Blocks.AIR.defaultBlockState(), false);
         }
      }
   }

   /** True when the top of this column is a tree (leaves or a log, or a snow layer on one). */
   private static boolean treeTop(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int y, int z) {
      BlockState state = chunk.getBlockState(pos.set(x, y, z));
      if (state.is(Blocks.SNOW)) {
         state = chunk.getBlockState(pos.set(x, y - 1, z));
      }
      return state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS);
   }

   /**
    * mountain_snow: Still Life's ice on mountains (ice sheets, ice boulders and the packed ice in its snowy ground) becomes
    * snow ground where the snow model has snow and rock where it has none; glaciers keep their ice. Returns the column's
    * top after the change.
    */
   private static int meltIce(WorldGenLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int top, int z,
                              SnowField.Chunk field) {
      pos.set(x, top, z);
      BlockState state = chunk.getBlockState(pos);
      if (!state.is(Blocks.PACKED_ICE) && !state.is(Blocks.BLUE_ICE)) {
         return top;
      }
      net.minecraft.core.Holder<Biome> holder = level.getBiome(pos);
      if (MountainSnow.glacier(holder) || MountainSnow.waterBiome(holder) || top - level.getSeaLevel() < SEA_ICE_MARGIN
         || iceToWater(chunk, pos, x, top, z)) {
         // glaciers, and sea ice, icebergs and ice shelves (in a sea, beach or river biome, near sea level, or standing on
         // water): ice formations keep their own look
         pos.set(x, top, z);
         return top;
      }
      pos.set(x, top, z);
      double score = field.score((z & 15) * 16 + (x & 15), top);
      if (Double.isNaN(score)) {
         return top;
      }
      BlockState with = score >= 0.0 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
      int y = top;
      for (int k = 0; k < 6 && y > chunk.getMinBuildHeight(); k++, y--) {
         BlockState here = chunk.getBlockState(pos.setY(y));
         if (!here.is(Blocks.PACKED_ICE) && !here.is(Blocks.BLUE_ICE) && !here.is(Blocks.ICE)) {
            break;
         }
         chunk.setBlockState(pos, score >= 0.0 ? with : GroundCover.surfaceRock(level.getSeed(), x, y, z, with), false);
      }
      return top;
   }

   /** Ice whose top is less than this many blocks above sea level is sea ice, an iceberg or an ice shelf: never melted. */
   private static final int SEA_ICE_MARGIN = 12;

   /** True when the ice under top runs down into water (within 32 blocks): floating ice, not ice on a mountain. */
   private static boolean iceToWater(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int top, int z) {
      for (int y = top - 1; y > top - 32 && y > chunk.getMinBuildHeight(); y--) {
         BlockState s = chunk.getBlockState(pos.set(x, y, z));
         if (!s.getFluidState().isEmpty()) {
            return true;
         }
         if (!MountainSnow.ice(s) && !SnowCaps.isSnow(s)) {
            return false;
         }
      }
      return false;
   }

   /** Minecraft's temperature at a height: the biome's, cooling by 0.05 every 40 blocks above Y 80. */
   private static float temperature(Biome biome, int y) {
      return biome.getBaseTemperature() - Math.max(0, y - 80) * 0.05F / 40.0F;
   }

   /** The top solid block of the column (skipping snow layers and plants), or -1 when it is water or air. */
   private static int realGround(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z) {
      int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
      int min = chunk.getMinBuildHeight();
      for (; y >= min; y--) {
         BlockState state = chunk.getBlockState(pos.set(x, y, z));
         if (!state.getFluidState().isEmpty()) {
            return -1;
         }
         if (state.isAir() || state.is(Blocks.SNOW) || state.getCollisionShape(chunk, pos).isEmpty()) {
            continue;
         }
         return y;
      }
      return -1;
   }

   private static boolean coverable(BlockState state) {
      return state.is(FTFBlockTags.SNOW_COVERABLE)
         && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
         && !(state.getBlock() instanceof DoublePlantBlock)
         && !state.hasBlockEntity()
         && state.getFluidState().isEmpty();
   }

   private static void clear(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
      chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
      pos.setY(pos.getY() - 1);
      BlockState below = chunk.getBlockState(pos);
      if (below.hasProperty(BlockStateProperties.SNOWY) && below.getValue(BlockStateProperties.SNOWY)) {
         chunk.setBlockState(pos, below.setValue(BlockStateProperties.SNOWY, false), false);
      }
   }
}

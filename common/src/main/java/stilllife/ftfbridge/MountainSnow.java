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
 * mountain_snow: the bridge's one snow model (see {@link SnowModel} for the arithmetic, {@link SnowField} for how it is
 * worked out per chunk). Every column gets a score (at least 0 where it holds snow) and a snow depth from it, and every
 * pass uses those two: the snow ground and bare rock here, the snow layers in {@link NaturalSnow}, treetops, ice, and the
 * fringe of bare ground ground_cover lays around snow.
 *
 * <p>Here, after FTF's erosion pass (raw generation, every column): where the snow is deep the ground itself becomes snow
 * (snow blocks); where the model has thin snow (the feathered edge of a snowfield, small patches below the snow line) the
 * ground stays or becomes rock or soil, and the snow layer pass covers it with a few layers; where it has none the snow
 * ground becomes the rock under it. Without natural_snow_layers, all snow is snow ground as before. Glaciers keep theirs;
 * seas, beaches, rivers and ice formations (icebergs, ice shelves, sea ice) are left as they are. Towards the pole the
 * model's lowland cover follows Still Life's own layout ({@link SnowModel#polarLine}): full only where its permanently
 * snowy arctic biomes begin, patchy just before, none in the rest of the tundra band.
 */
final class MountainSnow {
   static final TagKey<Biome> DRY = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_dry"));
   static final TagKey<Biome> WET = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_wet"));

   private MountainSnow() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.mountainSnow();
   }

   /**
    * How much snow falls here, about 0.25 (arid) to 1.75 (wet): Still Life's humidity at this place (the climate around a
    * mountain, not the peak biome on its top; blended per block, so no 4-block steps), a slow regional variation (about a
    * kilometre across) so ranges differ, and less in warm latitudes. Still Life's humidity -0.15 (a little dry, common in
    * its mountain country) is about 0.95, a normal climate; only deserts (below about -0.5) are arid.
    */
   static float climateSnow(int x, int y, int z, long seed) {
      return snowiness(StillLifeClimateBridge.humidity(x, y, z), GroundCover.patch(seed, x, z, 131, 1100.0F), StillLifeClimateBridge.latitudeTemperature(z));
   }

   /**
    * climateSnow from its parts: Still Life's humidity, the regional variation (GroundCover.patch at salt 131, 1100 blocks)
    * and the latitude temperature (warm latitudes get less snow at the same height). Also used by the climate bridge for the
    * format 18 band placement, which needs the snowiness where it builds a biome point.
    */
   static float snowiness(float humidity, float regionPatch, float lat) {
      float moist = Math.max(0.0F, Math.min(1.0F, 0.55F + 0.6F * humidity + 0.3F * (regionPatch - 0.5F)));
      if (lat > 0.2F) {
         moist *= 1.0F - Math.min(0.7F, (lat - 0.2F) * 0.9F);
      }
      return 0.25F + 1.5F * moist;
   }

   /** The snow field of the chunk holding (x, z), or null (not an FTF world, or the climate not known yet). */
   static SnowField.Chunk field(WorldGenLevel level, int x, int z) {
      etcodehome.freeterraforged.world.worldgen.GeneratorContext ctx = FtfTerrain.context(level);
      return ctx == null ? null : SnowField.get(ctx, level.getSeed(), x >> 4, z >> 4);
   }

   /**
    * At least 0 when the column at x, z with its top at y holds snow (layers or snow ground), less than 0 when it is bare;
    * NaN before the world is known. The slope and biome are not needed any more (the model reads the terrain's shape
    * itself; glaciers are the caller's business) and kept for the callers.
    */
   static double score(WorldGenLevel level, int x, int y, int z, float slope, Holder<Biome> biome, long seed) {
      SnowField.Chunk f = field(level, x, z);
      return f == null ? Double.NaN : f.score((z & 15) * 16 + (x & 15), y);
   }

   /**
    * True when mountain_snow is on and has snow ground at x/z (the depth that makes even a 1:1 face snow ground): for
    * features that would paint over it.
    */
   static boolean deepSnow(WorldGenLevel level, int x, int z) {
      if (!active(WorldLock.activeSettings())) {
         return false;
      }
      SnowField.Chunk f = field(level, x, z);
      if (f == null) {
         return false;
      }
      int i = (z & 15) * 16 + (x & 15);
      int y = FtfTerrain.ground(level, x, z);
      return f.score(i, y) >= 0.0 && f.depth(i, y) >= SnowModel.FACE_BLOCK_DEPTH;
   }

   /** Biomes whose snow ground belongs to the mountains (so the model may bare it), not to a cold lowland climate. */
   static final TagKey<Biome> MOUNTAIN = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_mountain"));

   /**
    * Glaciers and ice keep their snow whatever the model says: biomes whose name has a word "ice", "frozen" or
    * "glacier..." ("ice_spikes", "frozen_peaks", "arctic_glacier"; not "rice_paddies" or "slice").
    */
   static boolean glacier(Holder<Biome> biome) {
      String path = path(biome);
      return word(path, "ice", false) || word(path, "iceberg", true) || word(path, "frozen", false) || word(path, "glacier", true);
   }

   /**
    * Biomes whose surface the bridge leaves to the biome's own pack everywhere except on genuinely steep rock (the
    * datapack tag stilllife_ftf_bridge:keeps_own_surface; by default Still Life's arctic biomes (#still_life:is_arctic:
    * arctic deserts, glaciers, glacial basins, arctic wetlands and beaches, frozen and snowy tundra, taiga and muskeg),
    * #c:is_icy and the arctic river). Still Life draws their look itself (snow ground, gravel, mud and ice sheets from
    * its surface rules and disk features), at whatever height FTF puts them; the snow model, the snow layer pass's
    * melting of ice and ground_cover's scree and rock would otherwise strip and re-rock it on every upland.
    */
   static final TagKey<Biome> KEEPS_OWN_SURFACE = TagKey.create(Registries.BIOME,
      ResourceLocation.fromNamespaceAndPath(StillLifeFtfBridge.MOD_ID, "keeps_own_surface"));

   static boolean keepsOwnSurface(Holder<Biome> biome) {
      return biome.is(KEEPS_OWN_SURFACE);
   }

   /** Real-block face classes on which the bridge still works inside keeps_own_surface biomes: faces and cliffs. */
   static boolean steepRock(int face) {
      return face == SnowModel.FACE || face == SnowModel.CLIFF;
   }

   static boolean mountainBiome(Holder<Biome> biome) {
      if (biome.is(MOUNTAIN)) {
         return true;
      }
      String path = path(biome);
      return word(path, "peak", true) || word(path, "alpine", false) || word(path, "glacier", true);
   }

   private static String path(Holder<Biome> biome) {
      return biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
   }

   /** True when the path has this word ('_' or '/' around it; with prefix, anything may follow it, as in "glaciers"). */
   static boolean word(String path, String word, boolean prefix) {
      for (int i = path.indexOf(word); i >= 0; i = path.indexOf(word, i + 1)) {
         int end = i + word.length();
         boolean start = i == 0 || path.charAt(i - 1) == '_' || path.charAt(i - 1) == '/';
         if (start && (prefix || end == path.length() || path.charAt(end) == '_' || path.charAt(end) == '/')) {
            return true;
         }
      }
      return false;
   }

   /**
    * The snow ground of one chunk, after FTF's erosion pass (raw generation): every column, not only those FTF's erosion
    * looks at. Snow ground where the model has no snow, or only a thin cover, becomes the rock under it (whatever biome
    * made it: with mountain_cooling, Still Life puts cold biomes high up in warm lands too; glaciers keep theirs), and soil
    * or rock where the model has deep snow becomes snow ground.
    */
   static void chunk(WorldGenLevel level, ChunkAccess chunk) {
      etcodehome.freeterraforged.world.worldgen.GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return;
      }
      SnowField.Chunk field = SnowField.get(ctx, level.getSeed(), chunk.getPos().x, chunk.getPos().z);
      if (field == null) {
         return;
      }
      // with the snow layer pass, thin snow is layers on the ground, so only deep snow is snow ground
      boolean feather = NaturalSnow.active(WorldLock.activeSettings());
      int x0 = chunk.getPos().getMinBlockX();
      int z0 = chunk.getPos().getMinBlockZ();
      // the real ground around the chunk (the same heights the snow layer pass reads), to tell rock faces from slopes
      int[] heights = new int[SnowModel.GSIZE * SnowModel.GSIZE];
      for (int dz = 0; dz < SnowModel.GSIZE; dz++) {
         for (int dx = 0; dx < SnowModel.GSIZE; dx++) {
            heights[dz * SnowModel.GSIZE + dx] = FtfTerrain.ground(level, x0 + dx - SnowModel.GR, z0 + dz - SnowModel.GR);
         }
      }
      long seed = level.getSeed();
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      StringBuilder dump = new StringBuilder();
      for (int lz = 0; lz < 16; lz++) {
         for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            int z = z0 + lz;
            int y = heights[(lz + SnowModel.GR) * SnowModel.GSIZE + lx + SnowModel.GR];
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
            if (waterBiome(biome) || onIce(chunk, pos, y)) {
               // sea ice, icebergs, ice shelves and the ground of seas, beaches and rivers keep Still Life's look
               continue;
            }
            int i = lz * 16 + lx;
            if (keepsOwnSurface(biome) && !steepRock(SnowModel.face(heights, lx + SnowModel.GR, lz + SnowModel.GR))) {
               // Still Life's arctic and frozen biomes: their ground as Still Life lays it, at any height (only real
               // faces and cliffs are the model's)
               continue;
            }
            double score = field.score(i, y);
            // with the layer pass: deep snow only, and on rock faces only where the snowfield is deep (1:1 faces) or on
            // ledges; cliffs stay rock
            int step = SnowModel.STEP_NONE;
            boolean ground;
            if (feather) {
               int gx = lx + SnowModel.GR;
               int gz = lz + SnowModel.GR;
               int face = SnowModel.face(heights, gx, gz);
               float depth = field.depth(i, y);
               // stepped slopes are decided as a whole: one snowfield down the risers, or rock (SnowModel.step)
               step = SnowModel.step(seed, x, z, heights, gx, gz, face, score, depth);
               ground = step == SnowModel.STEP_WHITE
                  || step == SnowModel.STEP_NONE && score >= 0.0 && SnowModel.snowGround(depth, face, SnowModel.ledge(seed, x, z));
            } else {
               ground = score >= 0.0;
            }
            if (snow && !ground) {
               SnowCaps.bareRock(chunk, x, y, z);
               if (StillLifeClimateBridge.snowCurve()) {
                  // format 18: the bared ground gets the ground of bare high-mountain terrain (talus, scree fields), as
                  // ground_cover lays it on rock the erosion pass saw (it never saw this column: it was snow then)
                  GroundCover.coverBared(chunk, x, y, z);
               }
               if (score < 0.0 || step == SnowModel.STEP_BARE) {
                  // no snow at all: FTF's snow pass clears the layer the game lays here (thin snow keeps it, and the
                  // layer pass sets its depth)
                  SnowCaps.markBare(chunk, x, z);
               }
            } else if (!snow && ground) {
               snowGround(chunk, pos, y);
            }
            if (DUMP != null && x >= DUMP[0] && z >= DUMP[1] && x <= DUMP[2] && z <= DUMP[3]) {
               int gx = lx + SnowModel.GR;
               int gz = lz + SnowModel.GR;
               dump.append(String.format(java.util.Locale.ROOT, "%d,%d,%d,%.3f,%.1f,%.3f,%.3f,%.3f,%.3f,%.2f,%.1f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%s%n",
                  x, z, y, field.snow[i], field.dist(i, y), field.temp[i], field.keep[i], field.coverage(i, y), score, field.depth(i, y),
                  field.standing[i], field.slope8[i], field.sun[i], field.wind[i], field.shelter[i], SnowModel.face(heights, gx, gz), step,
                  ground ? 1 : 0, path(biome)));
            }
            if (ground && step == SnowModel.STEP_WHITE) {
               // the riser under this tread is snow too, down to the lower neighbour's ground (at most RISER_CAP blocks)
               snowRiser(chunk, pos, y, Math.min(SnowModel.RISER_CAP, SnowModel.drop(heights, lx + SnowModel.GR, lz + SnowModel.GR) - 1));
            }
         }
      }
      if (dump.length() > 0) {
         writeDump(dump);
      }
   }

   /**
    * Test tool, off unless the JVM is started with -Dstilllife_ftf_bridge.snowdump=x0,z0,x1,z1,file: appends the snow model's
    * terms for every column this pass decides inside the box to a CSV. Never changes world generation.
    */
   private static final int[] DUMP;
   private static final String DUMP_FILE;

   static {
      String spec = System.getProperty("stilllife_ftf_bridge.snowdump");
      int[] box = null;
      String file = null;
      if (spec != null) {
         String[] p = spec.split(",", 5);
         box = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
         file = p[4];
      }
      DUMP = box;
      DUMP_FILE = file;
   }

   private static synchronized void writeDump(StringBuilder sb) {
      try {
         java.nio.file.Files.writeString(java.nio.file.Path.of(DUMP_FILE), sb, java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND);
      } catch (java.io.IOException e) {
         StillLifeFtfBridge.LOG.error("[{}] snow dump failed", StillLifeFtfBridge.MOD_ID, e);
      }
   }

   private static final TagKey<Biome> OCEAN = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_ocean"));
   private static final TagKey<Biome> BEACH = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_beach"));
   private static final TagKey<Biome> RIVER = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_river"));

   /**
    * Seas, beaches and rivers (by tag or name: ocean, sea, beach, shore, river, iceberg): their ground and ice are Still
    * Life's and vanilla's, never the bridge's mountain snow, rock or scree.
    */
   static boolean waterBiome(Holder<Biome> biome) {
      if (seaBiome(biome) || biome.is(BEACH) || biome.is(RIVER)) {
         return true;
      }
      String path = path(biome);
      return word(path, "beach", false) || word(path, "shore", false) || word(path, "river", false);
   }

   /** Seas (by tag or name: ocean, sea, iceberg). */
   static boolean seaBiome(Holder<Biome> biome) {
      if (biome.is(OCEAN)) {
         return true;
      }
      String path = path(biome);
      return word(path, "ocean", false) || word(path, "sea", false) || word(path, "iceberg", true);
   }

   /** Ice blocks (ice, packed, blue and frosted ice). */
   static boolean ice(BlockState s) {
      return s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.FROSTED_ICE);
   }

   /**
    * True when the column's top at y is ice, or snow lying on ice or water within 6 blocks below (an iceberg, an ice shelf,
    * sea ice): ice formations keep their own look.
    */
   static boolean onIce(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y) {
      int x = pos.getX();
      int z = pos.getZ();
      boolean found = false;
      for (int dy = 0; dy <= 6; dy++) {
         BlockState s = chunk.getBlockState(pos.set(x, y - dy, z));
         if (ice(s) || !s.getFluidState().isEmpty()) {
            found = true;
            break;
         }
         if (dy > 0 && !SnowCaps.isSnow(s)) {
            break; // ground under the snow: not ice
         }
      }
      pos.set(x, y, z);
      return found;
   }

   private static boolean snowable(BlockState top) {
      return top.is(BlockTags.DIRT) || top.is(Blocks.GRAVEL) || top.is(BlockTags.BASE_STONE_OVERWORLD) || top.is(Blocks.COBBLESTONE)
         || top.is(Blocks.CALCITE) || top.is(Blocks.PACKED_MUD) || top.is(Blocks.MOSSY_COBBLESTONE) || top.is(BlockTags.TERRACOTTA)
         || clayRock(top.getBlock()) || deepRock(top) && GroundCover.deep();
   }

   /** rock_strata=deep's other bare rocks: smooth basalt, Create's limestone, Nature's Spirit's chert. */
   private static boolean deepRock(BlockState top) {
      if (top.is(Blocks.SMOOTH_BASALT)) {
         return true;
      }
      net.minecraft.resources.ResourceLocation k = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(top.getBlock());
      return k.getNamespace().equals("create") && k.getPath().equals("limestone")
         || k.getNamespace().equals("natures_spirit") && k.getPath().equals("chert");
   }

   private static final java.util.Map<net.minecraft.world.level.block.Block, Boolean> CLAY_ROCK = new java.util.concurrent.ConcurrentHashMap<>();

   /** Nature's Spirit's kaolin and travertine: the bridge's rock strata lay them as bare rock, so snow may cover them too. */
   private static boolean clayRock(net.minecraft.world.level.block.Block b) {
      return CLAY_ROCK.computeIfAbsent(b, k -> {
         String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(k).getPath();
         return path.endsWith("kaolin") || path.equals("travertine");
      });
   }

   /**
    * The blocks under a snowy tread, down its riser (blocks deep), become snow ground, so the riser's face is white: soil,
    * gravel and plain rock only (ores, ice and anything placed stay).
    */
   private static void snowRiser(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int y, int blocks) {
      BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
      for (int k = 1; k <= blocks; k++) {
         BlockState s = chunk.getBlockState(pos.setY(y - k));
         if (s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.CALCITE)
            || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.PACKED_MUD) || s.is(Blocks.SMOOTH_BASALT)
            || clayRock(s.getBlock())) {
            chunk.setBlockState(pos, snowBlock, false);
         }
      }
      pos.setY(y);
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
}

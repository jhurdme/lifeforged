package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import etcodehome.freeterraforged.world.worldgen.noise.module.Noise;
import etcodehome.freeterraforged.world.worldgen.noise.module.Noises;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Per-column terrain facts for the terrain filter and the terrain surface condition ({@link TerrainSpec}): the rock
 * province, the climate class and moisture, and what the talus model makes of the column. All of it is a pure function of
 * the world (seed, FreeTerraForged's cells, the climate bridge, FTF's scree settings) and the column, read without touching
 * blocks, so it is the same from any chunk, thread and stage of generation; each fact is worked out at most once per
 * column and thread while its chunk stays in this thread's cache (most recently used chunks, see {@link #chunk}).
 *
 * <p>The y-dependent parts (climate class from the biome at the ground, humidity, wetness, the talus model's wetness)
 * use FreeTerraForged's model ground height of the column, not the real top block, so they don't change as blocks do.
 */
final class TerrainFacts {
   private TerrainFacts() {
   }

   // ---------------------------------------------------------------- FTF's scree settings

   /**
    * FreeTerraForged's erosion decorator settings of a world: the config FTF builds its erosion feature with
    * (PresetConfiguredFeatures: the preset's scree settings, height and slope noise 6/255 and 3/255) and the noise its
    * scree decorator uses. Read from the preset, so they are known before the first erosion pass has run (surface rules
    * run before it). config is null when the preset has the scree decorator off (no talus then).
    */
   static final class Erosion {
      final WeakReference<GeneratorContext> ctx;
      final ScreeFeature.Config config;
      final Noise rand;
      final Levels levels;

      Erosion(GeneratorContext ctx, ScreeFeature.Config config, Noise rand, Levels levels) {
         this.ctx = new WeakReference<>(ctx);
         this.config = config;
         this.rand = rand;
         this.levels = levels;
      }
   }

   private static volatile Erosion lastErosion;
   private static volatile boolean erosionLogged;

   static Erosion erosion(GeneratorContext ctx) {
      Erosion e = lastErosion;
      if (e != null && e.ctx.get() == ctx) {
         return e;
      }
      ScreeFeature.Config config = null;
      Noise rand = null;
      Levels levels = ctx.heightmap.levels();
      try {
         var preset = ctx.preset;
         if (preset != null && preset.miscellaneous().screeDecorator) {
            var scree = preset.surface().scree();
            config = new ScreeFeature.Config(scree.rockVariance, scree.rockMin, scree.dirtVariance, scree.dirtMin, scree.rockSteepness,
               scree.dirtSteepness, scree.screeSteepness, 0.023529412F, 0.011764706F);
            // as ScreeFeature.place picks it
            rand = ctx.screeNoises != null ? ctx.screeNoises.rand : Noises.white(ctx.heightmap.climate().randomSeed(), 1);
         }
      } catch (LinkageError | RuntimeException ex) {
         config = null;
         if (!erosionLogged) {
            erosionLogged = true;
            StillLifeFtfBridge.LOG.error("[{}] cannot read FreeTerraForged's scree settings (FreeTerraForged changed?): the terrain filter's talus fields are ignored",
               StillLifeFtfBridge.MOD_ID, ex);
         }
      }
      e = new Erosion(ctx, config, rand, levels);
      lastErosion = e;
      return e;
   }

   // ---------------------------------------------------------------- per-chunk facts

   static final int PROVINCE = 1;
   static final int CLIMATE = 2;
   static final int TALUS = 4;

   /** The facts of one chunk's 16 x 16 columns (index z * 16 + x), filled in lazily per column. */
   static final class Chunk {
      WeakReference<GeneratorContext> ctx = new WeakReference<>(null);
      long seed;
      int cx = Integer.MIN_VALUE;
      int cz = Integer.MIN_VALUE;
      long used;
      /** Which of PROVINCE, CLIMATE, TALUS a column has. */
      final byte[] have = new byte[256];
      final Provinces.Province[] province = new Provinces.Province[256];
      /** FTF's model ground height (the top block by FTF's terrain model). */
      final int[] top = new int[256];
      /** GroundCover.Climate ordinal. */
      final byte[] climate = new byte[256];
      final float[] humidity = new float[256];
      final float[] wetness = new float[256];
      /** The talus model's kind (TalusField kinds, GroundCover.BARE_ROCK), -1 when FTF's scree decorator is off. */
      final byte[] talus = new byte[256];
      /** 0 at the apex of the deposit (below its face), 1 at its toe; 1 for a fan; NaN where the column has no deposit. */
      final float[] position = new float[256];
      /** Blocks along the fall line up to the face feeding the column (0 on bare rock); +inf with none within 15. */
      final float[] faceDistance = new float[256];
   }

   private static final int SLOTS = 12;

   private static final class Cache {
      final Chunk[] chunks = new Chunk[SLOTS];
      long tick;
      final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
   }

   private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

   /**
    * This thread's facts of chunk (cx, cz) (least recently used chunk dropped first; twelve kept, so a chunk being
    * decorated and its neighbours stay while features look across its borders).
    */
   static Chunk chunk(GeneratorContext ctx, long seed, int cx, int cz) {
      Cache c = CACHE.get();
      int oldest = 0;
      for (int s = 0; s < SLOTS; s++) {
         Chunk k = c.chunks[s];
         if (k == null) {
            oldest = s;
            break;
         }
         if (k.cx == cx && k.cz == cz && k.seed == seed && k.ctx.get() == ctx) {
            k.used = ++c.tick;
            return k;
         }
         if (k.used < c.chunks[oldest].used) {
            oldest = s;
         }
      }
      Chunk k = c.chunks[oldest];
      if (k == null) {
         k = new Chunk();
         c.chunks[oldest] = k;
      }
      java.util.Arrays.fill(k.have, (byte)0);
      java.util.Arrays.fill(k.province, null);
      k.cx = cx;
      k.cz = cz;
      k.seed = seed;
      k.ctx = new WeakReference<>(ctx);
      k.used = ++c.tick;
      return k;
   }

   static int index(int x, int z) {
      return (z & 15) * 16 + (x & 15);
   }

   /** The rock province of the column. */
   static Provinces.Province province(Chunk k, int x, int z) {
      int i = index(x, z);
      if ((k.have[i] & PROVINCE) == 0) {
         k.province[i] = Provinces.at(k.seed, x, z);
         k.have[i] |= PROVINCE;
      }
      return k.province[i];
   }

   /**
    * Fills in the column's model ground height, climate class, humidity and wetness.
    *
    * @param cell  the column's FTF cell
    * @param chunk the chunk holding the column (for its biomes)
    */
   static void climate(Chunk k, Cell cell, Levels levels, ChunkAccess chunk, int x, int z) {
      int i = index(x, z);
      if ((k.have[i] & CLIMATE) != 0) {
         return;
      }
      int top = levels.scale(cell.height);
      BlockPos.MutableBlockPos pos = CACHE.get().pos.set(x, top, z);
      // the top block only matters before the climate bridge knows the world (then these fields are not used)
      GroundCover.Climate climate = GroundCover.climate(chunk, pos, Blocks.AIR.defaultBlockState(), top);
      k.top[i] = top;
      k.climate[i] = (byte)climate.ordinal();
      k.humidity[i] = StillLifeClimateBridge.humidity(x, top, z);
      k.wetness[i] = GroundCover.wetness(climate, x, top, z);
      k.have[i] |= CLIMATE;
   }

   static GroundCover.Climate climateOf(Chunk k, int x, int z) {
      return GroundCover.Climate.values()[k.climate[index(x, z)]];
   }

   /**
    * Fills in what the talus model makes of the column (kind, position, face distance); the kind is -1 when FTF's scree
    * decorator is off or its settings can't be read.
    */
   static void talus(Chunk k, GeneratorContext ctx, Cell cell, ChunkAccess chunk, int x, int z) {
      int i = index(x, z);
      if ((k.have[i] & TALUS) != 0) {
         return;
      }
      Erosion e = erosion(ctx);
      if (e.config == null) {
         k.talus[i] = -1;
         k.position[i] = Float.NaN;
         k.faceDistance[i] = Float.POSITIVE_INFINITY;
         k.have[i] |= TALUS;
         return;
      }
      climate(k, cell, e.levels, chunk, x, z);
      Provinces.Province province = province(k, x, z);
      TalusField field = window(ctx, e, k.seed, x >> 4, z >> 4);
      int kind = GroundCover.talusKind(e.config, e.levels, e.rand, cell, field, x, z, climateOf(k, x, z), k.wetness[i], province);
      k.talus[i] = (byte)kind;
      k.position[i] = switch (kind) {
         case TalusField.FINE, TalusField.MEDIUM, TalusField.COARSE, TalusField.MOSSY, TalusField.VEG -> field.sortR;
         case TalusField.FAN -> 1.0F;
         default -> Float.NaN;
      };
      k.faceDistance[i] = field.dSrc >= 0 ? field.dSrc : Float.POSITIVE_INFINITY;
      k.have[i] |= TALUS;
   }

   /** The talus window of chunk (cx, cz): the one the erosion pass built on this thread if it is still kept, or a new one. */
   private static TalusField window(GeneratorContext ctx, Erosion e, long seed, int cx, int cz) {
      ScreeFeature.Config config = e.config;
      float rockVar = config.rockVar() / 255.0F;
      float rockMin = config.rockMin() / 255.0F;
      TalusField f = TalusField.cached(ctx, cx, cz, seed, config.rockSteepness(), config.screeSteepness(), rockVar, rockMin);
      if (f != null) {
         return f;
      }
      f = TalusField.claim(cx, cz, seed, config.rockSteepness(), config.screeSteepness(), rockVar, rockMin);
      // FTF's filtered chunk cache: the cells FTF's chunks hold, read without touching the world (any chunk, any stage)
      GroundCover.fill(f, ctx, e.levels, ctx.filteredChunkCache::getFilteredChunk);
      f.owner = ctx;
      return f;
   }

   // ---------------------------------------------------------------- the real ground's shape (feature placement only)

   private static final ThreadLocal<int[]> FACE_GRID = ThreadLocal.withInitial(() -> new int[SnowModel.GSIZE * SnowModel.GSIZE]);

   /**
    * The face class of the real ground at x/z (SnowModel.OPEN, LEDGE, FACE or CLIFF), from the terrain's blocks within 2
    * blocks ({@link FtfTerrain#ground}: the same reading the snow passes use). Feature placement only.
    */
   static int face(WorldGenLevel level, int x, int z) {
      int[] g = FACE_GRID.get();
      int w = SnowModel.GSIZE;
      int c = SnowModel.GR * w + SnowModel.GR;
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            g[c + dz * w + dx] = FtfTerrain.ground(level, x + dx, z + dz);
         }
      }
      int f = SnowModel.face(g, SnowModel.GR, SnowModel.GR);
      if (f == SnowModel.OPEN && isLedge(g, c, w)) {
         // SnowModel only looks for ledges on ground it already counts as a face (the 5 x 5 plane steeper than 0.9), and a
         // flat 3 x 3 bench barely tilts the plane, so in generated cliff bands almost no column came out as a ledge (3 of
         // about 3,150 sampled columns of a real mountainside, where 13% of the ground is such benches; the snow passes want that: snow lies on such benches like on open
         // ground). For the filter a ledge is the bench itself, whatever the plane says.
         return SnowModel.LEDGE;
      }
      return f;
   }

   /** A flat 3 x 3 (at most 1 block of relief) with a wall behind: at least two columns 2 blocks away standing 2+ higher. */
   private static boolean isLedge(int[] g, int c, int w) {
      int h0 = g[c];
      int min3 = h0;
      int max3 = h0;
      int wall = 0;
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            int h = g[c + dz * w + dx];
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
               min3 = Math.min(min3, h);
               max3 = Math.max(max3, h);
            } else if (h - h0 >= 2) {
               wall++;
            }
         }
      }
      return max3 - min3 <= 1 && wall >= 2;
   }

   // ---------------------------------------------------------------- the model ground's shape (surface rules)

   /** FTF's model ground heights of a chunk and the 2 blocks around it (20 x 20), per thread. */
   private static final class Heights {
      WeakReference<GeneratorContext> ctx = new WeakReference<>(null);
      int cx = Integer.MIN_VALUE;
      int cz = Integer.MIN_VALUE;
      final int[] h = new int[20 * 20];
      final Cell scratch = new Cell();
   }

   private static final ThreadLocal<Heights> HEIGHTS = ThreadLocal.withInitial(Heights::new);

   private static Heights heights(GeneratorContext ctx, int cx, int cz) {
      Heights w = HEIGHTS.get();
      if (w.cx == cx && w.cz == cz && w.ctx.get() == ctx) {
         return w;
      }
      Levels levels = ctx.heightmap.levels();
      int ox = (cx << 4) - 2;
      int oz = (cz << 4) - 2;
      for (int ccz = cz - 1; ccz <= cz + 1; ccz++) {
         for (int ccx = cx - 1; ccx <= cx + 1; ccx++) {
            int xa = Math.max(ox, ccx << 4);
            int xb = Math.min(ox + 19, (ccx << 4) + 15);
            int za = Math.max(oz, ccz << 4);
            int zb = Math.min(oz + 19, (ccz << 4) + 15);
            if (xa > xb || za > zb) {
               continue;
            }
            Cell[] cells = ctx.filteredChunkCache.getFilteredChunk(ccx, ccz);
            for (int z = za; z <= zb; z++) {
               for (int x = xa; x <= xb; x++) {
                  float height;
                  if (cells != null) {
                     height = cells[(z & 15) * 16 + (x & 15)].height;
                  } else {
                     ctx.lookup.applyCell(w.scratch.reset(), x, z, false, false);
                     height = w.scratch.height;
                  }
                  w.h[(z - oz) * 20 + x - ox] = levels.scale(height);
               }
            }
         }
      }
      w.cx = cx;
      w.cz = cz;
      w.ctx = new WeakReference<>(ctx);
      return w;
   }

   private static int model(Heights w, int x, int z) {
      return w.h[(z - (w.cz << 4) + 2) * 20 + x - (w.cx << 4) + 2];
   }

   /** {@link FtfTerrain#hollow} on FTF's model ground instead of the blocks (for surface rules, which run before them). */
   static float modelHollow(GeneratorContext ctx, int x, int z) {
      Heights w = heights(ctx, x >> 4, z >> 4);
      int h0 = model(w, x, z);
      float sum = 0.0F;
      float weights = 0.0F;
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            float wt = (float)Math.exp(-(dx * dx + dz * dz) / 2.5);
            int h = Math.max(h0 - 4, Math.min(h0 + 4, model(w, x + dx, z + dz)));
            sum += h * wt;
            weights += wt;
         }
      }
      return sum / weights - h0;
   }

   /** {@link FtfTerrain#facing} on FTF's model ground. */
   static String modelFacing(GeneratorContext ctx, int x, int z) {
      Heights w = heights(ctx, x >> 4, z >> 4);
      int dx = model(w, x + 2, z) - model(w, x - 2, z);
      int dz = model(w, x, z + 2) - model(w, x, z - 2);
      if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) {
         return "flat";
      }
      if (Math.abs(dz) >= Math.abs(dx)) {
         return dz > 0 ? "north" : "south";
      }
      return dx > 0 ? "west" : "east";
   }
}

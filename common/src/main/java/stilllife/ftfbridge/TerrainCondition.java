package stilllife.ftfbridge;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import etcodehome.freeterraforged.world.worldgen.FTFRandomState;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Surface rule condition {@code stilllife_ftf_bridge:terrain}: the placement filter's vocabulary ({@link TerrainFilter},
 * the same JSON fields) for surface rules, true where FreeTerraForged's terrain at the column matches. Registered under the
 * same id as the placement filter (a different registry). Every field is optional; outside a FreeTerraForged world (or
 * when FTF's random state can't be read) it is always true, and fields whose bridge option is off are ignored, as in the
 * filter.
 *
 * <p>Surface rules run per block during the surface stage, before features and before the bridge's erosion pass, so:
 * <ul>
 *   <li>it is a per-column condition, worked out once per column (like vanilla's lazy XZ conditions) for every block of
 *       the column: y-dependent fields use FTF's model ground height of the column (the top block by FTF's terrain
 *       model, within a block or two of the real one): the snow line, treeline, temperature and max_above_water at the
 *       model ground + 1 (where a feature would stand), the snow fields at the model ground;</li>
 *   <li>{@code hollow}, {@code prefer_facing} and {@code avoid_chutes} read FTF's model ground instead of blocks;
 *       {@code faces} and {@code forest_edge} need real blocks and are refused here (a decoding error);</li>
 *   <li>probability fields ({@code facing_bias}, {@code ridge_thinning}, {@code treeline}, {@code regional_mix},
 *       {@code wind_thinning}, {@code shade_thinning}) use a hash of the column instead of a random draw, so a whole
 *       column agrees and the answer is the same in every chunk and order.</li>
 * </ul>
 *
 * <p>How the erosion pass (ground_cover, {@link GroundCover}) interacts: it runs later, in the features stage, and rewrites
 * the top of columns on steep ground and talus (bare rock, scree, thin soil, the alpine belt, river bars) whatever the
 * surface rules put there; mountain_snow then lays or bares snow ground. So a surface rule using this condition decides
 * the ground that stays on gentle ground (meadows, valley floors, benches) and what lies under the top block everywhere
 * (the erosion pass reaches 1-3 blocks down on talus, deeper only on rock faces). The talus fields here are the model's
 * answer: they say where the erosion pass lays (or would lay) its deposits, so a rule can, for example, put gravel under
 * a talus toe or keep a meadow block off ground the talus will cover anyway.
 *
 * <p>Cost: per column and use of the condition in a rule tree, one FTF cell copy and the tests asked for; the province,
 * climate and talus facts are kept per column and thread ({@link TerrainFacts}) and shared with the placement filter,
 * the snow and exposure fields per chunk and thread ({@link SnowField}). Roughly: cell-only fields well under a
 * microsecond per column (about 0.1 ms per chunk); province or moisture fields a few microseconds per column the first
 * time; exposure / snow fields one snow field per chunk (a few ms, built once per chunk and thread and reused by the snow
 * passes on the same thread); talus fields one talus window per chunk (well under a ms) plus one trace per column
 * (a few microseconds, about 1 ms per chunk).
 */
public final class TerrainCondition implements SurfaceRules.ConditionSource {
   public static final MapCodec<TerrainCondition> MAP_CODEC = TerrainSpec.CODEC.flatXmap(
      spec -> {
         String blocks = spec.blockFields();
         return blocks.isEmpty()
            ? DataResult.success(new TerrainCondition(spec))
            : DataResult.error(() -> "stilllife_ftf_bridge:terrain as a surface rule condition can't use " + blocks
               + " (they read the real blocks, which surface rules run before)");
      },
      c -> DataResult.success(c.spec)
   );
   public static final KeyDispatchDataCodec<TerrainCondition> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

   private final TerrainSpec spec;

   private TerrainCondition(TerrainSpec spec) {
      this.spec = spec;
   }

   @Override
   public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
      return CODEC;
   }

   @Override
   public SurfaceRules.Condition apply(SurfaceRules.Context context) {
      return new Test(context, this.spec);
   }

   @Override
   public String toString() {
      return "stilllife_ftf_bridge:terrain";
   }

   /** Set when Minecraft's surface context can't be read (an access transformer / widener missing): then it is always true. */
   private static volatile boolean broken;
   /** Set when the context's random state can't be read: FTF's context is then the climate bridge's last world's. */
   private static volatile boolean noRandomState;

   private static final class Test implements SurfaceRules.Condition {
      private final SurfaceRules.Context context;
      private final TerrainSpec spec;
      private boolean looked;
      private GeneratorContext ctx;
      private long seed;
      private long column = Long.MIN_VALUE;
      private boolean result;

      Test(SurfaceRules.Context context, TerrainSpec spec) {
         this.context = context;
         this.spec = spec;
      }

      @Override
      public boolean test() {
         if (broken) {
            return true;
         }
         try {
            SurfaceRules.Context c = this.context;
            if (c.lastUpdateXZ != this.column) {
               this.column = c.lastUpdateXZ;
               this.result = this.compute(c);
            }
            return this.result;
         } catch (LinkageError | RuntimeException e) {
            broken = true;
            StillLifeFtfBridge.LOG.error("[{}] the terrain surface condition cannot read the surface (FreeTerraForged or Minecraft changed?); it is true from here on",
               StillLifeFtfBridge.MOD_ID, e);
            return true;
         }
      }

      private boolean compute(SurfaceRules.Context c) {
         TerrainSpec s = this.spec;
         if (s.skipped) {
            return false;
         }
         if (!this.looked) {
            this.looked = true;
            this.lookUp(c);
         }
         GeneratorContext ctx = this.ctx;
         if (ctx == null) {
            return true;
         }
         if (!s.enabledHere()) {
            return false;
         }
         int x = c.blockX;
         int z = c.blockZ;
         Cell cell = FtfTerrain.cell(ctx, c.chunk, x, z);
         int ground = ctx.heightmap.levels().scale(cell.height);
         return s.test(null, null, null, ctx, this.seed, c.chunk, cell, x, ground + 1, z);
      }

      /** FTF's generator context and the world seed, from the context's random state (or the climate bridge's world). */
      private void lookUp(SurfaceRules.Context c) {
         if (!noRandomState) {
            try {
               Object rs = c.randomState;
               if (rs instanceof FTFRandomState ftf) {
                  this.ctx = ftf.generatorContext();
                  this.seed = ftf.seed();
               }
               return;
            } catch (LinkageError e) {
               noRandomState = true;
               StillLifeFtfBridge.LOG.warn("[{}] the terrain surface condition cannot read the surface context's random state; using the climate bridge's world",
                  StillLifeFtfBridge.MOD_ID);
            }
         }
         this.ctx = StillLifeClimateBridge.lastContext();
         this.seed = StillLifeClimateBridge.lastSeed();
      }
   }
}

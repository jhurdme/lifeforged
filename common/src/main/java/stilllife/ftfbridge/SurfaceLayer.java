package stilllife.ftfbridge;

import com.mojang.serialization.MapCodec;
import etcodehome.freeterraforged.world.worldgen.surface.ISurfaceContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Surface rule condition {@code stilllife_ftf_bridge:surface_layer}: true for the blocks of a column's surface layer, the run
 * of solid blocks that reaches up to open air (or to water standing on it: sea, lake and river beds), and false under any
 * roof: cave floors, pockets, the ground under an overhang.
 *
 * <p>Minecraft's own {@code above_preliminary_surface} compares with an estimated surface, interpolated between chunk
 * corners from the noise before its detail; on FreeTerraForged's terrain that estimate can sit 20-25 blocks below the real
 * ground, so small pockets that close to the surface still count as surface. This reads the real column: the highest solid
 * block (from the chunk's world-surface height map, past any water) and how many solid blocks lie between the current block
 * and the air above it (the surface system's stone depth).
 *
 * <p>Uses fields of {@code SurfaceRules.Context} that FreeTerraForged's access widener (Fabric) / access transformer
 * (NeoForge) opens (chunk, blockX/Y/Z, lastUpdateXZ, the Condition interface), and FreeTerraForged's {@link ISurfaceContext}
 * for the stone depth. The bridge needs FreeTerraForged anyway.
 */
public final class SurfaceLayer implements SurfaceRules.ConditionSource {
   public static final SurfaceLayer INSTANCE = new SurfaceLayer();
   public static final KeyDispatchDataCodec<SurfaceLayer> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

   private SurfaceLayer() {
   }

   @Override
   public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
      return CODEC;
   }

   @Override
   public SurfaceRules.Condition apply(SurfaceRules.Context context) {
      return new Test(context);
   }

   @Override
   public String toString() {
      return "stilllife_ftf_bridge:surface_layer";
   }

   /** Set when FreeTerraForged no longer opens what this reads: the condition then lets everything through (no guard). */
   private static volatile boolean broken;

   private static final class Test implements SurfaceRules.Condition {
      private final SurfaceRules.Context context;
      private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      private long column = Long.MIN_VALUE;
      private int top;

      Test(SurfaceRules.Context context) {
         this.context = context;
      }

      @Override
      public boolean test() {
         if (broken) {
            return true;
         }

         try {
            return this.inSurfaceLayer();
         } catch (LinkageError | RuntimeException e) {
            broken = true;
            StillLifeFtfBridge.LOG.error("[{}] the cave floor guard cannot read the surface (FreeTerraForged changed?); it is off from here on",
               StillLifeFtfBridge.MOD_ID, e);
            return true;
         }
      }

      private boolean inSurfaceLayer() {
         SurfaceRules.Context c = this.context;
         if (c.lastUpdateXZ != this.column) {
            // surface rules run from the top of each column down, so the column is read before anything in it changed
            this.column = c.lastUpdateXZ;
            this.top = solidTop(c.chunk, c.blockX, c.blockZ);
         }

         // the solid run this block belongs to starts stoneDepthAbove - 1 blocks above it (water does not end a run)
         int above = ((ISurfaceContext)(Object)c).freeterraforged$getStoneDepthAbove();
         return c.blockY + above - 1 >= this.top;
      }

      private int solidTop(ChunkAccess chunk, int x, int z) {
         int min = chunk.getMinBuildHeight();
         for (int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z); y >= min; y--) {
            BlockState state = chunk.getBlockState(this.pos.set(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty()) {
               return y;
            }
         }

         return min - 1;
      }
   }
}

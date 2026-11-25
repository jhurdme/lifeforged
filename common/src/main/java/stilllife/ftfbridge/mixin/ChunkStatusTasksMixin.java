package stilllife.ftfbridge.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import stilllife.ftfbridge.BridgeHooks;

/**
 * rock_strata=deep: the regional rock through the body of the land, laid at the start of a chunk's LIGHT step,
 * when the chunk and its eight neighbours have finished their features (see DeepRock). Optional: without it deep falls
 * back to the skin only.
 */
@Mixin(ChunkStatusTasks.class)
abstract class ChunkStatusTasksMixin {
   @Inject(method = "light", at = @At("HEAD"), require = 0)
   private static void stilllife$deepRock(
      WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
      CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
   ) {
      BridgeHooks.deepRock(context.level(), chunk);
   }
}

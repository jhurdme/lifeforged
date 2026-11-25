package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import etcodehome.freeterraforged.world.worldgen.noise.module.Noise;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import stilllife.ftfbridge.BridgeHooks;

/**
 * FreeTerraForged's erosion decorator: keeps Still Life's snow on peaks out of it, except on cliffs (and fades the cap's
 * base); with ground_cover the bridge picks the blocks for each column instead (see GroundCover).
 */
@Mixin(value = ScreeFeature.class, remap = false)
abstract class ScreeFeatureMixin {
   @Inject(method = "erodeColumn", at = @At("HEAD"), cancellable = true)
   private static void stilllife$keepSnowCaps(
      ScreeFeature.Config config,
      Levels levels,
      Noise rand,
      Noise clusterNoise,
      Noise warpX,
      Noise warpZ,
      ChunkGenerator generator,
      ChunkAccess chunk,
      Cell cell,
      MutableBlockPos pos,
      int surfaceY,
      CallbackInfo ci
   ) {
      BridgeHooks.erosionPass(config, levels, rand);
      if (BridgeHooks.keepSnow(config, chunk, pos.getX(), surfaceY, pos.getZ(), cell.gradient + rand.compute(pos.getX(), pos.getZ(), 1) * config.slopeModifier())) {
         ci.cancel();
         return;
      }
      if (BridgeHooks.groundCover(config, levels, rand, chunk, cell, pos, surfaceY)) {
         ci.cancel();
      }
   }

   @Inject(method = "place", at = @At("HEAD"))
   private void stilllife$begin(FeaturePlaceContext<ScreeFeature.Config> context, CallbackInfoReturnable<Boolean> cir) {
      BridgeHooks.groundCoverBegin(context.level());
   }

   @Inject(method = "place", at = @At("RETURN"))
   private void stilllife$end(FeaturePlaceContext<ScreeFeature.Config> context, CallbackInfoReturnable<Boolean> cir) {
      BridgeHooks.shoresChunk(context.level(), context.origin());
      BridgeHooks.mountainSnowChunk(context.level(), context.origin());
      BridgeHooks.groundCoverEnd();
   }
}

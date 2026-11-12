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
import stilllife.ftfbridge.BridgeHooks;

/** Keeps Still Life's snow on peaks out of FreeTerraForged's erosion decorator, except on cliffs. */
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
      if (BridgeHooks.keepSnow(config, chunk.getBlockState(pos), cell.gradient + rand.compute(pos.getX(), pos.getZ(), 1) * config.slopeModifier())) {
         ci.cancel();
      }
   }
}

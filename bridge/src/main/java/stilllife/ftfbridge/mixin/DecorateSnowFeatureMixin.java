package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.feature.DecorateSnowFeature;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import stilllife.ftfbridge.BridgeHooks;

/**
 * FreeTerraForged's snow decorator clears snow layers off any slope steeper than 0.45 above a height made for
 * 256-block worlds (Y 95), so in tall worlds almost no snow survives on mountains. With snow_caps it only clears cliffs.
 */
@Mixin(value = DecorateSnowFeature.class, remap = false)
abstract class DecorateSnowFeatureMixin {
   @Inject(method = "snowErosion", at = @At("HEAD"), cancellable = true)
   private static void stilllife$onlyCliffs(
      ScreeFeature.Config erodeConfig, float x, float z, float steepness, float height, CallbackInfoReturnable<Boolean> cir
   ) {
      Boolean erode = BridgeHooks.snowLayerErosion(erodeConfig, steepness);
      if (erode != null) {
         cir.setReturnValue(erode);
      }
   }
}

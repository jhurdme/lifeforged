package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import etcodehome.freeterraforged.world.worldgen.feature.DecorateSnowFeature;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import stilllife.ftfbridge.BridgeHooks;

/**
 * FreeTerraForged's snow decorator. With snow_caps its erosion only clears cliffs (it clears snow layers off any slope
 * steeper than 0.45 above a height made for 256-block worlds). With natural_snow_layers its smooth-layer step is switched
 * off and the bridge's own pass sets the snow depth from the real ground afterwards.
 */
@Mixin(value = DecorateSnowFeature.class, remap = false)
abstract class DecorateSnowFeatureMixin {
   @Inject(method = "snowErosion", at = @At("HEAD"), cancellable = true)
   private static void stilllife$onlyCliffs(
      ScreeFeature.Config erodeConfig, float x, float z, float steepness, float height, CallbackInfoReturnable<Boolean> cir
   ) {
      Boolean erode = BridgeHooks.snowLayerErosion(erodeConfig, x, z, steepness);
      if (erode != null) {
         cir.setReturnValue(erode);
      }
   }

   @ModifyExpressionValue(
      method = "place",
      at = @At(value = "FIELD", target = "Letcodehome/freeterraforged/world/worldgen/feature/DecorateSnowFeature$Config;smooth:Z")
   )
   private boolean stilllife$skipFtfSmoothing(boolean smooth) {
      return smooth && !BridgeHooks.naturalSnowLayers();
   }

   @Inject(method = "place", at = @At("RETURN"))
   private void stilllife$naturalSnow(FeaturePlaceContext<DecorateSnowFeature.Config> context, CallbackInfoReturnable<Boolean> cir) {
      DecorateSnowFeature.Config config = context.config();
      if (cir.getReturnValueZ() && config.smooth()) {
         BridgeHooks.naturalSnow(context.level(), context.origin(), config.coverPlants(), config.erodeConfig());
      }
   }
}

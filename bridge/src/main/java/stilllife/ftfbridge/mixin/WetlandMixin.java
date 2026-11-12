package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.wetland.Wetland;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stilllife.ftfbridge.BridgeHooks;

/** Wetlands only flood land near their water level, so they never cut pits into hills and mountain sides. */
@Mixin(value = Wetland.class, remap = false)
abstract class WetlandMixin {
   @Shadow
   @Final
   private Levels levels;

   @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
   private void stilllife$beforeApply(Cell cell, float rx, float rz, float x, float z, CallbackInfo ci) {
      if (BridgeHooks.wetlandBefore(cell, this.levels)) {
         ci.cancel();
      }
   }

   @Inject(method = "apply", at = @At("RETURN"))
   private void stilllife$afterApply(Cell cell, float rx, float rz, float x, float z, CallbackInfo ci) {
      BridgeHooks.wetlandAfter(cell);
   }
}

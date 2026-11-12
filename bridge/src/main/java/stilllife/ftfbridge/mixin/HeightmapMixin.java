package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stilllife.ftfbridge.BridgeHooks;

/** Remembers each FreeTerraForged terrain, so a river can look at the ground along its course. */
@Mixin(value = Heightmap.class, remap = false)
abstract class HeightmapMixin {
   @Inject(method = "<init>", at = @At("RETURN"))
   private void stilllife$remember(CallbackInfo ci) {
      BridgeHooks.heightmapCreated((Heightmap)(Object)this);
   }
}

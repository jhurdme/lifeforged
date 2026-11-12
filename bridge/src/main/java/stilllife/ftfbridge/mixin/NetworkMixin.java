package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.Network;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stilllife.ftfbridge.RiverAccess;

/** Tells each branch river which river it joins, so a branch joining a dropped stretch is dropped too. */
@Mixin(value = Network.class, remap = false)
abstract class NetworkMixin {
   @Inject(method = "carve", at = @At("HEAD"))
   private void stilllife$linkBranches(Cell cell, float x, float z, float nx, float nz, CallbackInfo ci) {
      Network self = (Network)(Object)this;
      for (Network child : self.children()) {
         if (child.riverCarver() instanceof RiverAccess access && access.stilllife$parent() == null) {
            access.stilllife$setParent(self.riverCarver());
         }
      }
   }
}

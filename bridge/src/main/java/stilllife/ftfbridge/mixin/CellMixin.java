package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import stilllife.ftfbridge.LakeCell;

/** Carries the bridge's "lake water" mark on FreeTerraForged cells, through FTF's cell caches (copyFrom) and reuse (reset). */
@Mixin(value = Cell.class, remap = false)
abstract class CellMixin implements LakeCell {
   @Unique
   private boolean stilllife$lake;

   @Override
   public boolean stilllife$isLake() {
      return this.stilllife$lake;
   }

   @Override
   public void stilllife$setLake(boolean lake) {
      this.stilllife$lake = lake;
   }

   @Inject(method = "copyFrom", at = @At("RETURN"))
   private void stilllife$copyLake(Cell other, CallbackInfo ci) {
      this.stilllife$lake = LakeCell.isLake(other);
   }

   @Inject(method = "reset", at = @At("HEAD"))
   private void stilllife$resetLake(CallbackInfoReturnable<Cell> cir) {
      this.stilllife$lake = false;
   }
}

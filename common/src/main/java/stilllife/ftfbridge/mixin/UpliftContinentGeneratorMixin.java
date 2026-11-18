package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import stilllife.ftfbridge.UpliftSmoothing;

/** With uplift_smoothing, the uplift climbs back gradually from FTF's coastal band instead of in a cliff. */
@Mixin(targets = "etcodehome.freeterraforged.world.worldgen.cell.continent.uplift.UpliftContinentGenerator", remap = false)
abstract class UpliftContinentGeneratorMixin {
   @Shadow
   protected float frequency;
   @Shadow
   protected Levels levels;

   @WrapOperation(
      method = "apply",
      at = @At(value = "FIELD", target = "Letcodehome/freeterraforged/world/worldgen/cell/Cell;waterTable:F", opcode = 181),
      require = 0
   )
   private void stilllife$smoothCoastalUplift(Cell cell, float value, Operation<Void> original, @Local(name = "customPeak") float customPeak) {
      original.call(cell, UpliftSmoothing.waterTable(value, customPeak, this.frequency, this.levels.water));
   }
}

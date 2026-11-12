package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import stilllife.ftfbridge.MountainWidth;

/** mountain_width: mountain chains rise only in the upper part of FTF's chain mask (see MountainWidth). */
@Mixin(value = Heightmap.class, remap = false)
abstract class MountainLayoutMixin {
   @ModifyArgs(
      method = "make",
      at = @At(
         value = "INVOKE",
         target = "Letcodehome/freeterraforged/world/worldgen/cell/terrain/Blender;<init>(Letcodehome/freeterraforged/world/worldgen/noise/module/Noise;Letcodehome/freeterraforged/world/worldgen/cell/CellPopulator;Letcodehome/freeterraforged/world/worldgen/cell/CellPopulator;FFF)V"
      ),
      require = 0
   )
   private static void stilllife$narrowChains(Args args) {
      stilllife.ftfbridge.MountainShapeTap.capture(args.get(0));
      float min = args.get(3);
      float max = args.get(4);
      args.set(3, MountainWidth.chainThreshold(min));
      args.set(4, MountainWidth.chainThreshold(max));
   }
}

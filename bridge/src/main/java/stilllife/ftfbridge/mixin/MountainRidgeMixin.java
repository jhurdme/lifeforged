package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.terrain.Populators;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import stilllife.ftfbridge.MountainWidth;

/** mountain_width: narrower ridge noise for FTF's mountains and mountain chains (see MountainWidth). */
@Mixin(value = Populators.class, remap = false)
abstract class MountainRidgeMixin {
   @ModifyVariable(
      method = "makeMountains(Letcodehome/freeterraforged/world/worldgen/cell/terrain/Terrain;Letcodehome/freeterraforged/world/worldgen/util/Seed;Letcodehome/freeterraforged/world/worldgen/noise/module/Noise;Letcodehome/freeterraforged/data/worldgen/preset/settings/TerrainSettings$Terrain;FFZZF)Letcodehome/freeterraforged/world/worldgen/cell/terrain/populator/TerrainPopulator;",
      at = @At("STORE"),
      name = "scaleH",
      require = 0
   )
   private static int stilllife$narrowRidges(int scaleH) {
      return MountainWidth.ridgeScale(scaleH);
   }
}

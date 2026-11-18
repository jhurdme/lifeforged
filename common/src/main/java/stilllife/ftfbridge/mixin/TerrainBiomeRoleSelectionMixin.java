package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import stilllife.ftfbridge.BridgeHooks;

/**
 * FreeTerraForged (1.0.0027+) re-picks the biome of submerged wetland cells in TerrainBiomeRoleSelection.resolve: it keeps the
 * biome chosen there only if it counts as a wetland ({@code WetlandBiomeTags.isWetland}: swamp/wetland tags or a name with
 * swamp, marsh, bog, fen...). Still Life's other wetland-climate biomes (muskegs, floodplain, flooded prairie, salt lake...; tag
 * stilllife_ftf_bridge:wetland_biomes) count too, as every biome did before 1.0.0027. Optional: skipped if FTF changes this code.
 */
@Mixin(targets = "etcodehome.freeterraforged.world.worldgen.runtime.TerrainBiomeRoleSelection", remap = false)
abstract class TerrainBiomeRoleSelectionMixin {
   @WrapOperation(
      method = "resolve",
      at = @At(
         value = "INVOKE",
         target = "Letcodehome/freeterraforged/world/worldgen/biome/WetlandBiomeTags;isWetland(Ljava/lang/Object;)Z"
      ),
      require = 0
   )
   private boolean stilllife$keepWetlandBiome(Object selected, Operation<Boolean> original) {
      return original.call(selected) || BridgeHooks.keepsWetlandBiome(selected);
   }
}

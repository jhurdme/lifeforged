package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import etcodehome.freeterraforged.world.worldgen.cell.terrain.Terrain;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import stilllife.ftfbridge.BridgeHooks;

/**
 * FreeTerraForged swaps the biome of every river cell for the nearest river biome. Lake water whose climate gives a lake biome
 * of its own (Still Life's salt lake) keeps it. Optional: skipped if FTF changes this code.
 */
@Mixin(targets = "etcodehome.freeterraforged.world.worldgen.runtime.TerrainBiomeRoleSelection", remap = false)
abstract class TerrainBiomeRoleSelectionMixin {
   @WrapOperation(
      method = "resolve",
      at = @At(value = "INVOKE", target = "Letcodehome/freeterraforged/world/worldgen/cell/terrain/Terrain;isRiver()Z"),
      require = 0
   )
   private boolean stilllife$keepLakeBiome(Terrain terrain, Operation<Boolean> original, @Local(argsOnly = true) Holder<Biome> selected) {
      return original.call(terrain) && !BridgeHooks.keepsLakeBiome(selected);
   }
}

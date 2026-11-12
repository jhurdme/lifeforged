package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import etcodehome.freeterraforged.world.worldgen.biome.FTFClimateSampler;
import etcodehome.freeterraforged.world.worldgen.search.FTFBiomeLocateQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes "/ftf locate biome" failing with "An unexpected error occurred" (tooltip: "spawnSearch", a NullPointerException):
 * the locate search copies the world's spawn-search setting onto its own private climate sampler, and in this pack that
 * setting can be empty (it is only set when a world's spawn is first chosen). The spawn search plays no part in locating
 * biomes, so an empty one is simply not copied; the private sampler keeps its own default. Optional: if FreeTerraForged
 * changes this code, the fix is skipped instead of stopping the game.
 */
@Mixin(value = FTFBiomeLocateQuery.class, remap = false)
abstract class FTFBiomeLocateQueryMixin {
   @WrapOperation(
      method = "isolateSampler",
      at = @At(
         value = "INVOKE",
         target = "Letcodehome/freeterraforged/world/worldgen/biome/FTFClimateSampler;setSpawnSearch(Letcodehome/freeterraforged/world/worldgen/biome/FTFClimateSampler$SpawnSearch;)V"
      ),
      require = 0
   )
   private static void stilllife$skipEmptySpawnSearch(FTFClimateSampler sampler, FTFClimateSampler.SpawnSearch search, Operation<Void> original) {
      if (search != null) {
         original.call(sampler, search);
      }
   }
}

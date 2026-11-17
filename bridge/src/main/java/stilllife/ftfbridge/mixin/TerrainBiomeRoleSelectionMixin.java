package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import stilllife.ftfbridge.BridgeHooks;

/**
 * FreeTerraForged (1.0.0027+) re-picks the biome of submerged river, lake and wetland cells in
 * TerrainBiomeRoleSelection.resolve. Two of its checks are widened so Still Life's own water biomes stay:
 * <ul>
 * <li>Wetland water keeps the biome chosen there if FTF counts it as a wetland biome ({@code WetlandBiomeTags.isWetland}:
 * swamp/wetland tags or a name with swamp, marsh, bog, fen...). Still Life's other wetland-climate biomes (muskegs, floodplain,
 * flooded prairie, salt lake...; tag stilllife_ftf_bridge:wetland_biomes) count too, as every biome did before 1.0.0027.</li>
 * <li>River and lake water get the nearest river biome unless the chosen biome already is a river
 * ({@code selected.is(BiomeTags.IS_RIVER) ? selected : river}). Lake water whose climate gives a lake biome of its own (Still
 * Life's salt lake, tag stilllife_ftf_bridge:lake_biomes) counts as river there. This is the second Holder.is call in the
 * method's bytecode: the first is in the wetland branch, the third in the dry-bank branch, both left as FTF has them.</li>
 * </ul>
 * Optional: skipped if FTF changes this code.
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

   @WrapOperation(
      method = "resolve",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 1),
      require = 0
   )
   private boolean stilllife$keepLakeBiome(Holder<Biome> selected, TagKey<Biome> tag, Operation<Boolean> original) {
      return original.call(selected, tag) || BridgeHooks.keepsLakeBiome(selected);
   }
}

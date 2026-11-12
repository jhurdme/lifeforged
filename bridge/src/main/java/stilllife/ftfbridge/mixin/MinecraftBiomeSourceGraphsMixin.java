package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import etcodehome.freeterraforged.world.worldgen.runtime.BiomeCandidateRoot;
import etcodehome.freeterraforged.world.worldgen.runtime.MinecraftBiomeSourceGraphs;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import stilllife.ftfbridge.BridgeHooks;

/** The multi-noise layout FreeTerraForged places biomes from gets the VanillaBackport biomes, at each world's prevalence. */
@Mixin(value = MinecraftBiomeSourceGraphs.class, remap = false)
abstract class MinecraftBiomeSourceGraphsMixin {
   @ModifyReturnValue(method = "multiNoiseRoot", at = @At("RETURN"))
   private static BiomeCandidateRoot stilllife$vanillaBackport(BiomeCandidateRoot root, BiomeSource source, HolderLookup.Provider lookups) {
      return BridgeHooks.vanillaBackportBiomes(root, lookups);
   }
}

package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import etcodehome.freeterraforged.world.worldgen.runtime.WorldgenPlans.SamplerDecoration;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.Climate.TargetPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import stilllife.ftfbridge.StillLifeClimateBridge;

@Mixin(value = SamplerDecoration.class, remap = false)
abstract class SamplerDecorationMixin {
   @ModifyReturnValue(method = "sample", at = @At("RETURN"))
   private TargetPoint stilllife$translate(TargetPoint original, Sampler sampler, int quartX, int quartY, int quartZ) {
      return StillLifeClimateBridge.apply(sampler, original, quartX, quartY, quartZ);
   }
}

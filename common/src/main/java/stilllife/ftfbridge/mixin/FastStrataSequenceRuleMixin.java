package stilllife.ftfbridge.mixin;

import etcodehome.freeterraforged.world.worldgen.surface.rule.FastStrataSequenceRule;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import stilllife.ftfbridge.BridgeHooks;

/**
 * FreeTerraForged's surface rule passes through here before every chunk's surface: the cave floor guard rewrites it once per
 * world. Optional (require = 0): if FTF changes this method, the guard switches itself off.
 */
@Mixin(value = FastStrataSequenceRule.class, remap = false)
abstract class FastStrataSequenceRuleMixin {
   @ModifyVariable(method = "optimize", at = @At("HEAD"), argsOnly = true, require = 0)
   private static SurfaceRules.RuleSource stilllife$caveFloorGuard(SurfaceRules.RuleSource root) {
      return BridgeHooks.caveFloorGuard(root);
   }
}

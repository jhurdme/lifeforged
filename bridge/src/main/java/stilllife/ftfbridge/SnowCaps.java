package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Still Life puts snow blocks on its snowy peaks with surface rules. FreeTerraForged's erosion decorator only erodes
 * snow, powder snow and gravel (its 'erodible' tag), turning that snow into stone above its rock line, into tuff on
 * slopes and into moss/coarse dirt in its dirt band. With snow_caps the snow stays, except on cliffs.
 */
final class SnowCaps {
   private SnowCaps() {
   }

   static boolean active(Settings s) {
      return BridgeConfig.enabled() && s.snowCaps();
   }

   static boolean isSnow(BlockState state) {
      return state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
   }

   /** Slope from which snow is eroded: the bridge's own value, or FTF's rock steepness from the preset. */
   static float cliffSteepness(Settings s, ScreeFeature.Config config) {
      return s.snowCliffSteepness() > 0.0 ? (float)s.snowCliffSteepness() : config.rockSteepness();
   }
}

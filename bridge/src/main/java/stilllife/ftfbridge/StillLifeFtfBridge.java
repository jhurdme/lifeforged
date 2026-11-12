package stilllife.ftfbridge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(StillLifeFtfBridge.MOD_ID)
public final class StillLifeFtfBridge {
   public static final String MOD_ID = "stilllife_ftf_bridge";
   static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
   private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);

   static {
      // always registered: the data pack in this jar places it in VanillaBackport's Sulfur Caves (it does nothing when disabled)
      FEATURES.register("sulfur_rock", () -> new SulfurRockFeature(NoneFeatureConfiguration.CODEC));
      // likewise placed in Still Life's wetland biomes; does nothing unless the world has wetland_pools on
      FEATURES.register("wetland_pools", () -> new WetlandPoolsFeature(NoneFeatureConfiguration.CODEC));
   }

   public StillLifeFtfBridge(IEventBus modBus) {
      FEATURES.register(modBus);
      BridgeConfig.load();
      if (!BridgeConfig.enabled()) {
         LOG.warn("[{}] disabled in config: Still Life biomes will be placed by FreeTerraForged's own climate (misplaced)", MOD_ID);
      } else {
         FtfCompat.verify();
         NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> WorldLock.open(e.getServer()));
         NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> WorldLock.close());
         LOG.info("[{}] defaults for new worlds: {}", MOD_ID, BridgeConfig.settings());
      }
   }
}

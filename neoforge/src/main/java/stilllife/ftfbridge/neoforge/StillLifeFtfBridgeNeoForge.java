package stilllife.ftfbridge.neoforge;

import com.mojang.serialization.MapCodec;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import stilllife.ftfbridge.Platform;
import stilllife.ftfbridge.StillLifeFtfBridge;

/**
 * NeoForge entry point. The features are added to their biomes by the biome modifiers in
 * data/stilllife_ftf_bridge/neoforge/biome_modifier.
 */
@Mod(StillLifeFtfBridge.MOD_ID)
public final class StillLifeFtfBridgeNeoForge {
   private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, StillLifeFtfBridge.MOD_ID);
   private static final DeferredRegister<MapCodec<? extends SurfaceRules.ConditionSource>> CONDITIONS =
      DeferredRegister.create(Registries.MATERIAL_CONDITION, StillLifeFtfBridge.MOD_ID);

   static {
      FEATURES.register(StillLifeFtfBridge.SULFUR_ROCK.getPath(), StillLifeFtfBridge::sulfurRockFeature);
      FEATURES.register(StillLifeFtfBridge.WETLAND_POOLS.getPath(), StillLifeFtfBridge::wetlandPoolsFeature);
      CONDITIONS.register(StillLifeFtfBridge.SURFACE_LAYER.getPath(), StillLifeFtfBridge::surfaceLayerCodec);
   }

   public StillLifeFtfBridgeNeoForge(IEventBus modBus) {
      FEATURES.register(modBus);
      CONDITIONS.register(modBus);
      StillLifeFtfBridge.init(new Platform() {
         @Override
         public String name() {
            return "NeoForge";
         }

         @Override
         public Path configDir() {
            return FMLPaths.CONFIGDIR.get();
         }

         @Override
         public Optional<String> modVersion(String modId) {
            return ModList.get().getModContainerById(modId).map(m -> String.valueOf((Object)m.getModInfo().getVersion()));
         }
      });
      if (StillLifeFtfBridge.enabled()) {
         NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> StillLifeFtfBridge.serverStarting(e.getServer()));
         NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> StillLifeFtfBridge.serverStopped());
      }
   }
}

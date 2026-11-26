package stilllife.ftfbridge.fabric;

import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.GenerationStep;
import stilllife.ftfbridge.Platform;
import stilllife.ftfbridge.StillLifeFtfBridge;

/**
 * Fabric entry point. Registers the two features and adds them to their biomes with Fabric API's biome modifications
 * (what the biome modifier files do on NeoForge).
 */
public final class StillLifeFtfBridgeFabric implements ModInitializer {
   @Override
   public void onInitialize() {
      StillLifeFtfBridge.init(new Platform() {
         @Override
         public String name() {
            return "Fabric";
         }

         @Override
         public Path configDir() {
            return FabricLoader.getInstance().getConfigDir();
         }

         @Override
         public Optional<String> modVersion(String modId) {
            return FabricLoader.getInstance().getModContainer(modId).map(m -> m.getMetadata().getVersion().getFriendlyString());
         }
      });
      Registry.register(BuiltInRegistries.FEATURE, StillLifeFtfBridge.SULFUR_ROCK, StillLifeFtfBridge.sulfurRockFeature());
      Registry.register(BuiltInRegistries.FEATURE, StillLifeFtfBridge.WETLAND_POOLS, StillLifeFtfBridge.wetlandPoolsFeature());
      Registry.register(BuiltInRegistries.MATERIAL_CONDITION, StillLifeFtfBridge.SURFACE_LAYER, StillLifeFtfBridge.surfaceLayerCodec());
      Registry.register(BuiltInRegistries.MATERIAL_CONDITION, StillLifeFtfBridge.TERRAIN, StillLifeFtfBridge.terrainConditionCodec());
      Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, StillLifeFtfBridge.TERRAIN, stilllife.ftfbridge.TerrainFilter.TYPE);
      java.util.List<net.minecraft.resources.ResourceLocation> vegetation = new java.util.ArrayList<>(StillLifeFtfBridge.RIVERSIDE);
      if (StillLifeFtfBridge.isLoaded("natures_spirit")) {
         vegetation.addAll(StillLifeFtfBridge.NATURES_SPIRIT);
      }
      if (StillLifeFtfBridge.isLoaded("still_life_naturalized")) {
         vegetation.addAll(StillLifeFtfBridge.NATURALIZED);
      }
      for (net.minecraft.resources.ResourceLocation riverside : vegetation) {
         BiomeModifications.addFeature(
            BiomeSelectors.tag(StillLifeFtfBridge.HAS_RIVERSIDE_PLANTS),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            StillLifeFtfBridge.placed(riverside)
         );
      }
      // the subalpine belt per biome group: Still Life's ground features in its own step, then its plants
      for (StillLifeFtfBridge.BeltGroup group : StillLifeFtfBridge.BELT_GROUPS) {
         for (net.minecraft.resources.ResourceLocation f : group.ground()) {
            BiomeModifications.addFeature(BiomeSelectors.tag(group.biomes()), GenerationStep.Decoration.UNDERGROUND_ORES, StillLifeFtfBridge.placed(f));
         }
         for (net.minecraft.resources.ResourceLocation f : group.plants()) {
            BiomeModifications.addFeature(BiomeSelectors.tag(group.biomes()), GenerationStep.Decoration.VEGETAL_DECORATION, StillLifeFtfBridge.placed(f));
         }
      }
      BiomeModifications.addFeature(
         BiomeSelectors.tag(StillLifeFtfBridge.HAS_SULFUR_ROCK),
         GenerationStep.Decoration.RAW_GENERATION,
         StillLifeFtfBridge.placed(StillLifeFtfBridge.SULFUR_ROCK)
      );
      BiomeModifications.addFeature(
         BiomeSelectors.tag(StillLifeFtfBridge.HAS_WETLAND_POOLS),
         GenerationStep.Decoration.LAKES,
         StillLifeFtfBridge.placed(StillLifeFtfBridge.WETLAND_POOLS)
      );
      if (StillLifeFtfBridge.enabled()) {
         ServerLifecycleEvents.SERVER_STARTING.register(StillLifeFtfBridge::serverStarting);
         ServerLifecycleEvents.SERVER_STOPPED.register(server -> StillLifeFtfBridge.serverStopped());
      }
   }
}

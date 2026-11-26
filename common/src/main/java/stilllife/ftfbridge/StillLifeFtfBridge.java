package stilllife.ftfbridge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The loader-independent part of the mod's start-up. The NeoForge and Fabric entry points (in the neoforge and fabric
 * modules) call {@link #init} first, register the two features, add them to their biomes, and call {@link #serverStarting}
 * and {@link #serverStopped} around every world.
 */
public final class StillLifeFtfBridge {
   public static final String MOD_ID = "stilllife_ftf_bridge";
   static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

   /** Sulfur and cinnabar rock in VanillaBackport's Sulfur Caves: biomes tagged {@link #HAS_SULFUR_ROCK}, raw generation step. */
   public static final ResourceLocation SULFUR_ROCK = id("sulfur_rock");
   public static final TagKey<Biome> HAS_SULFUR_ROCK = TagKey.create(Registries.BIOME, id("has_sulfur_rock"));
   /** Standing water in Still Life's wetlands and salt lakes: biomes tagged {@link #HAS_WETLAND_POOLS}, lakes step. */
   public static final ResourceLocation WETLAND_POOLS = id("wetland_pools");
   /** Surface rule condition: the column's surface layer (see {@link SurfaceLayer}); registered for datapacks too. */
   public static final ResourceLocation SURFACE_LAYER = id("surface_layer");
   /**
    * Placement filter and surface rule condition (two registries, one id): FreeTerraForged's terrain and the bridge's terrain
    * models at a column (see {@link TerrainFilter}, {@link TerrainCondition}).
    */
   public static final ResourceLocation TERRAIN = id("terrain");
   public static final TagKey<Biome> HAS_WETLAND_POOLS = TagKey.create(Registries.BIOME, id("has_wetland_pools"));
   /** Biomes that get the riverside plants (placed only in FreeTerraForged's river banks and valley floors). */
   public static final TagKey<Biome> HAS_RIVERSIDE_PLANTS = TagKey.create(Registries.BIOME, id("has_riverside_plants"));
   /** The riverside plant features, in vegetal decoration order (trees first). */
   public static final java.util.List<ResourceLocation> RIVERSIDE = java.util.List.of(
      id("riverside_willows"), id("riverside_birches"), id("riverside_reeds"), id("riverside_plants"),
      // above the trees (every overworld biome; the filter decides where): the trees and shrubs that break up the treeline,
      // ledges, hollows (docs/bridge.md, section 15, "Mountain vegetation zones"); the belt's ground and plants per biome
      // group are BELT_GROUPS
      id("treeline_clumps"), id("treeline_singles"), id("krummholz"), id("talus_pioneers"), id("talus_pioneers_toe"),
      id("ledge_shrubs"), id("alpine_flowers"), id("ledge_turf"), id("ledge_moss_dark"), id("ledge_plants"),
      // lakes
      id("lake_reeds"), id("lake_lily_pads"),
      // forest edges
      id("forest_edge_birches"), id("forest_edge_spruces"), id("forest_edge_leaf_bushes"), id("forest_edge_bushes"));
   /** Lake reed edges built from Still Life Naturalized's own reed features, added only when it is installed. */
   public static final java.util.List<ResourceLocation> NATURALIZED = java.util.List.of(
      id("lake_reeds_cold"), id("lake_reeds_cold_water"), id("lake_reeds_mild"), id("lake_reeds_mild_water"), id("lake_reeds_dry"), id("lake_reeds_dry_water"));
   /** Variants of the alpine features built with Nature's Spirit plants, added only when it is installed. */
   public static final java.util.List<ResourceLocation> NATURES_SPIRIT = java.util.List.of(id("krummholz_ns"), id("alpine_flowers_ns"));

   /**
    * The subalpine belt per biome group: Still Life's own ground features (disks and patches, underground_ores step, as
    * Still Life places them) and plant features (vegetal_decoration), re-gated by the terrain filter to the belt above the
    * trees. Biomes by the tags stilllife_ftf_bridge:belt_&lt;group&gt; (NeoForge: biome_modifier/belt_*.json).
    */
   public record BeltGroup(TagKey<Biome> biomes, java.util.List<ResourceLocation> ground, java.util.List<ResourceLocation> plants) {
   }

   public static final java.util.List<BeltGroup> BELT_GROUPS = java.util.List.of(
      belt("tundra", java.util.List.of("coarse", "gravel"), java.util.List.of("berries", "heather", "bushes", "dwarf_shrubs")),
      belt("heath", java.util.List.of("coarse", "gravel"), java.util.List.of("heather", "berries", "dwarf_shrubs", "allium")),
      belt("forest", java.util.List.of("podzol", "gravel"), java.util.List.of()),
      belt("dry", java.util.List.of("gravel"), java.util.List.of("dry_grass")),
      // hot country (format 18 worlds: min_climate): Still Life's own savanna bush groups above the thinning broadleaf trees
      belt("hot", java.util.List.of(), java.util.List.of("bushes")));

   private static BeltGroup belt(String group, java.util.List<String> ground, java.util.List<String> plants) {
      java.util.List<ResourceLocation> g = new java.util.ArrayList<>();
      for (String n : ground) {
         g.add(id("belt_" + group + "_" + n));
      }
      if (group.equals("heath")) {
         g.add(id("belt_moss_ground"));
      }
      java.util.List<ResourceLocation> p = new java.util.ArrayList<>();
      for (String n : plants) {
         p.add(id("belt_" + group + "_" + n));
      }
      return new BeltGroup(TagKey.create(Registries.BIOME, id("belt_" + group)), java.util.List.copyOf(g), java.util.List.copyOf(p));
   }

   private static Platform platform;

   private StillLifeFtfBridge() {
   }

   /** Reads the config and checks FreeTerraForged. Call once, from the loader's entry point, before anything else. */
   /** True when the mod with this id is installed. */
   public static boolean isLoaded(String modId) {
      return platform != null && platform.modVersion(modId).isPresent();
   }

   public static void init(Platform p) {
      platform = p;
      BridgeConfig.load(p.configDir());
      if (!BridgeConfig.enabled()) {
         LOG.warn("[{}] disabled in config: Still Life biomes will be placed by FreeTerraForged's own climate (misplaced)", MOD_ID);
      } else {
         FtfCompat.verify();
         LOG.info("[{}] ({}) defaults for new worlds: {}", MOD_ID, p.name(), BridgeConfig.settings());
      }
   }

   /** Whether the bridge is on (config key enabled). */
   public static boolean enabled() {
      return BridgeConfig.enabled();
   }

   /** Before a world loads: opens its saved settings (or saves the current config as its settings). */
   public static void serverStarting(MinecraftServer server) {
      if (BridgeConfig.enabled()) {
         WorldLock.open(server);
      }
   }

   /** After a world has stopped. */
   public static void serverStopped() {
      if (BridgeConfig.enabled()) {
         WorldLock.close();
         CaveFloorGuard.reset();
         DeepRock.report();
         DeepRock.reset();
      }
   }

   /** The codec of the surface_layer surface rule condition, for the loaders to register. */
   public static com.mojang.serialization.MapCodec<SurfaceLayer> surfaceLayerCodec() {
      return SurfaceLayer.CODEC.codec();
   }

   /**
    * The codec of the terrain surface rule condition ({@link TerrainCondition}), for the loaders to register under
    * {@link #TERRAIN} in the surface rule condition registry (the placement filter has the same id in its own registry).
    */
   public static com.mojang.serialization.MapCodec<TerrainCondition> terrainConditionCodec() {
      return TerrainCondition.MAP_CODEC;
   }

   /** The sulfur rock feature (registered whatever the config says: it does nothing outside the Sulfur Caves or when off). */
   public static Feature<NoneFeatureConfiguration> sulfurRockFeature() {
      return new SulfurRockFeature(NoneFeatureConfiguration.CODEC);
   }

   /** The wetland pools feature (does nothing unless the world has wetland_pools on). */
   public static Feature<NoneFeatureConfiguration> wetlandPoolsFeature() {
      return new WetlandPoolsFeature(NoneFeatureConfiguration.CODEC);
   }

   public static ResourceKey<PlacedFeature> placed(ResourceLocation id) {
      return ResourceKey.create(Registries.PLACED_FEATURE, id);
   }

   static java.nio.file.Path configDir() {
      return platform != null ? platform.configDir() : null;
   }

   static String modVersion(String modId) {
      return platform != null ? platform.modVersion(modId).orElse("?") : "?";
   }

   static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
   }
}

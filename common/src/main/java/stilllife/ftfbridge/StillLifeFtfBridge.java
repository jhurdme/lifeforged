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
   /** Standing water in Still Life's wetlands and salt lakes: biomes tagged {@link #HAS_WETLAND_POOLS}, lakes step. */
   public static final ResourceLocation WETLAND_POOLS = id("wetland_pools");
   public static final TagKey<Biome> HAS_SULFUR_ROCK = TagKey.create(Registries.BIOME, id("has_sulfur_rock"));
   public static final TagKey<Biome> HAS_WETLAND_POOLS = TagKey.create(Registries.BIOME, id("has_wetland_pools"));

   private static Platform platform;

   private StillLifeFtfBridge() {
   }

   /** Reads the config and checks FreeTerraForged. Call once, from the loader's entry point, before anything else. */
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
      }
   }

   /** The sulfur rock feature (registered whatever the config says: it does nothing when its option is off). */
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

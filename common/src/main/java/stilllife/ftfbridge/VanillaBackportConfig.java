package stilllife.ftfbridge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * VanillaBackport's own switches for its biomes (config/vanillabackport-common.toml, the same file on NeoForge and Fabric):
 * "pale_garden", "has_dappled_forest" and "has_sulfur_caves". VanillaBackport keeps the biomes registered when they are off
 * and only stops placing them itself, but in a FreeTerraForged world the bridge places them, so it reads these switches
 * too: a biome switched off there is left out exactly as if its amount in the bridge's config were 0.
 */
final class VanillaBackportConfig {
   static final String FILE_NAME = "vanillabackport-common.toml";

   private VanillaBackportConfig() {
   }

   /** The VanillaBackport switch for one of the bridge's biome names, or null when it has none. */
   static String key(String biome) {
      return switch (biome) {
         case VanillaBackportBiomes.PALE_GARDEN -> "pale_garden";
         case VanillaBackportBiomes.DAPPLED_FOREST -> "has_dappled_forest";
         case VanillaBackportBiomes.SULFUR_CAVES -> "has_sulfur_caves";
         default -> null;
      };
   }

   /** Whether VanillaBackport's config leaves the biome on (true when the file or the key is missing, as in VanillaBackport). */
   static boolean biomeEnabled(Map<String, Boolean> switches, String biome) {
      String key = key(biome);
      return key == null || switches.getOrDefault(key, true);
   }

   /** The true/false keys of VanillaBackport's config file (empty when there is none yet, e.g. before its first start). */
   static Map<String, Boolean> read(Path configDir) {
      Map<String, Boolean> out = new HashMap<>();
      if (configDir == null) {
         return out;
      }
      Path file = configDir.resolve(FILE_NAME);
      try {
         if (!Files.exists(file)) {
            return out;
         }
         for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.strip();
            int eq = t.indexOf('=');
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("[") || eq <= 0) {
               continue;
            }
            String value = t.substring(eq + 1);
            int hash = value.indexOf('#');
            value = (hash >= 0 ? value.substring(0, hash) : value).strip().toLowerCase(Locale.ROOT);
            if (value.equals("true") || value.equals("false")) {
               out.put(t.substring(0, eq).strip(), Boolean.parseBoolean(value));
            }
         }
      } catch (Exception e) {
         StillLifeFtfBridge.LOG.warn("[{}] could not read {}; assuming its biomes are on", StillLifeFtfBridge.MOD_ID, file, e);
      }
      return out;
   }
}

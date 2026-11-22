package stilllife.ftfbridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * forest_variety's forest edges: where a forest biome meets open land (grassland, steppe, tundra, clearings...), the
 * bridge's edge features put a belt of bushes and young trees on both sides, instead of the forest ending in a line.
 * Biomes count as forest by name (forest, taiga, woods, woodlands, rainforest, jungle, grove), not clearings, plains,
 * steppe, prairie, savanna, fields, tundra, wetlands or rivers.
 */
final class ForestEdges {
   private ForestEdges() {
   }

   static boolean forest(Holder<Biome> biome) {
      String p = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
      if (p.contains("clearing") || p.contains("plains") || p.contains("steppe") || p.contains("prairie") || p.contains("savanna")
         || p.contains("fields") || p.contains("tundra") || p.contains("wetland") || p.contains("river") || p.contains("marsh")
         || p.contains("beach") || p.contains("buried") || p.contains("caves")) {
         return false;
      }
      return p.contains("forest") || p.contains("taiga") || p.contains("woods") || p.contains("woodland") || p.contains("rainforest")
         || p.contains("jungle") || p.contains("grove");
   }

   /** True when forest and open land meet within `distance` blocks of x/z (biomes sampled in 8 directions, 2 rings). */
   static boolean nearEdge(WorldGenLevel level, int x, int y, int z, int distance) {
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
      boolean here = forest(level.getBiome(pos));
      for (int ring = 1; ring <= 2; ring++) {
         int d = distance * ring / 2;
         for (int i = 0; i < 8; i++) {
            int dx = (i == 0 || i == 1 || i == 7) ? d : (i == 3 || i == 4 || i == 5) ? -d : 0;
            int dz = (i == 1 || i == 2 || i == 3) ? d : (i == 5 || i == 6 || i == 7) ? -d : 0;
            if (forest(level.getBiome(pos.set(x + dx, y, z + dz))) != here) {
               return true;
            }
         }
      }
      return false;
   }
}

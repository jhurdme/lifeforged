package stilllife.ftfbridge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.biome.Climate.Sampler;

/** Fails early, with a readable message, when FreeTerraForged no longer has what the bridge hooks into. */
final class FtfCompat {
   private static final String FTF = "etcodehome.freeterraforged.world.worldgen.";

   private FtfCompat() {
   }

   static void verify() {
      List<String> missing = new ArrayList<>();
      ClassLoader loader = FtfCompat.class.getClassLoader();
      Object[][] needed = new Object[][]{
         {"runtime.WorldgenPlans$SamplerDecoration", "method", "sample", Sampler.class, int.class, int.class, int.class},
         {"biome.FTFClimateSampler", "method", "climateQuerySemantics"},
         {"biome.ClimateQuerySemantics", "method", "surfaceContext"},
         {"biome.ClimateQuerySemantics", "method", "seed"},
         {"GeneratorContext", "field", "lookup"},
         {"GeneratorContext", "field", "levels"},
         {"GeneratorContext", "field", "heightmap"},
         {"GeneratorContext", "field", "filteredChunkCache"},
         {"cell.heightmap.FilteredChunkCache", "method", "getFilteredChunk", int.class, int.class},
         {"cell.heightmap.Heightmap", "method", "continent"},
         {"cell.CellPopulator", "method", "apply", FTF + "cell.Cell", float.class, float.class},
         {"densityfunction.CellSampler", "method", "getCachedOrLookup", FTF + "cell.heightmap.WorldLookup", int.class, int.class, boolean.class},
         {"cell.heightmap.WorldLookup", "method", "applyCell", FTF + "cell.Cell", int.class, int.class, boolean.class, boolean.class},
         {"cell.heightmap.Levels", "field", "terrainScaleFactor"},
         {"cell.heightmap.Levels", "field", "water"},
         {"cell.heightmap.Levels", "field", "unit"},
         {"cell.heightmap.Levels", "field", "waterY"},
         {"cell.heightmap.Levels", "field", "waterLevel"},
         {"cell.heightmap.Levels", "field", "worldHeight"},
         {"cell.heightmap.Levels", "field", "worldDepth"},
         {"cell.Cell", "method", "reset"},
         {"cell.Cell", "field", "height"},
         {"cell.Cell", "field", "gradient"},
         {"cell.Cell", "field", "waterTable"},
         {"cell.Cell", "field", "controlPoint"},
         {"cell.Cell", "field", "continentId"},
         {"cell.Cell", "field", "terrain"},
         {"cell.rivermap.ContinentalHydrology", "method", "getComplexWaterHeight", float.class, float.class, float.class},
         {"cell.rivermap.wetland.Wetland", "method", "apply", FTF + "cell.Cell", float.class, float.class, float.class, float.class},
         {"feature.ScreeFeature$Config", "method", "rockSteepness"},
         {"feature.ScreeFeature$Config", "method", "slopeModifier"},
         {"cell.terrain.Terrain", "method", "isDeepOcean"},
         {"cell.terrain.Terrain", "method", "isShallowOcean"},
         {"cell.terrain.Terrain", "method", "isRiver"},
         {"cell.terrain.Terrain", "method", "isLake"},
         {"cell.terrain.Terrain", "method", "isWetland"},
         {"cell.terrain.Terrain", "method", "getDelegate"},
         {"cell.terrain.TerrainCategory", "field", "COAST"},
         {"cell.terrain.TerrainCategory", "field", "HIGHLAND"},
         {"cell.terrain.TerrainType", "field", "MUSHROOM_FIELDS"},
         {"runtime.MinecraftBiomeSourceGraphs", "method", "multiNoiseRoot", net.minecraft.world.level.biome.BiomeSource.class, net.minecraft.core.HolderLookup.Provider.class},
         {"runtime.BiomeCandidateRoot", "method", "fromEntries", java.util.List.class},
         {"runtime.BiomeCandidateRoot", "method", "entries"}
      };

      for (Object[] n : needed) {
         String cls = FTF + n[0];

         try {
            Class<?> c = Class.forName(cls, false, loader);
            if ("field".equals(n[1])) {
               c.getField((String)n[2]);
            } else {
               Class<?>[] params = new Class[n.length - 3];
               for (int i = 3; i < n.length; i++) {
                  params[i - 3] = n[i] instanceof Class<?> k ? k : Class.forName((String)n[i], false, loader);
               }

               c.getMethod((String)n[2], params);
            }
         } catch (Throwable t) {
            missing.add(n[0] + "." + n[2]);
         }
      }

      if (!missing.isEmpty()) {
         String version = StillLifeFtfBridge.modVersion("freeterraforged");
         throw new IllegalStateException(
            "Still Life x FreeTerraForged Bridge does not support FreeTerraForged "
               + version
               + ": it no longer has "
               + String.join(", ", missing)
               + ". Update the bridge, or set enabled=false in config/" + BridgeConfig.FILE_NAME + " (Still Life biomes will then be misplaced)."
         );
      }
   }

   /**
    * What biome_sets=continents needs to find FTF's bridged landmasses (Landmasses). Unlike {@link #verify}, a miss does not
    * stop the game: it is logged as an error and the sets go per continent cell (as biome_sets=cells) instead.
    */
   static boolean verifyLandmasses() {
      List<String> missing = new ArrayList<>();
      ClassLoader loader = FtfCompat.class.getClassLoader();
      String uplift = "cell.continent.uplift.UpliftContinentGenerator";
      String plates = "cell.continent.uplift.PlateUpliftBridge";
      String voronoi = "cell.continent.uplift.PlateUpliftBridge$CellVoronoi";
      Object[][] needed = new Object[][]{
         {uplift, "method", "getPlateUpliftBridge"},
         {uplift, "method", "getBridgeChance"},
         {uplift, "method", "canBridge", int.class, int.class},
         {plates, "method", "getVoronoi", int.class, int.class},
         {voronoi, "field", "centroidX"},
         {voronoi, "field", "centroidY"},
         {voronoi, "field", "edgeCount"},
         {voronoi, "field", "ncx"},
         {voronoi, "field", "ncy"},
         {voronoi, "field", "isBridged"},
         {"cell.Cell", "field", "continentX"},
         {"cell.Cell", "field", "continentZ"},
         {"cell.Cell", "field", "continentId"}
      };
      for (Object[] n : needed) {
         try {
            Class<?> c = Class.forName(FTF + n[0], false, loader);
            if ("field".equals(n[1])) {
               c.getField((String)n[2]);
            } else {
               Class<?>[] params = new Class[n.length - 3];
               for (int i = 3; i < n.length; i++) {
                  params[i - 3] = (Class<?>)n[i];
               }
               c.getMethod((String)n[2], params);
            }
         } catch (Throwable t) {
            missing.add(n[0] + "." + n[2]);
         }
      }
      if (!missing.isEmpty()) {
         StillLifeFtfBridge.LOG.error(
            "[{}] FreeTerraForged {} no longer has {}. biome_sets=continents cannot find landmasses joined by continent bridges,"
               + " so every FreeTerraForged continent cell gets its own biome set (as biome_sets=cells) until the bridge is updated.",
            StillLifeFtfBridge.MOD_ID, StillLifeFtfBridge.modVersion("freeterraforged"), String.join(", ", missing)
         );
         return false;
      }
      return true;
   }
}

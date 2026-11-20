package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.ContinentalHydrology;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import net.minecraft.world.level.block.state.BlockState;

/** Entry points for the bridge's mixins into FreeTerraForged (mixin classes cannot share code directly). */
public final class BridgeHooks {
   private static final ThreadLocal<WetlandPass> WETLAND_PASS = ThreadLocal.withInitial(WetlandPass::new);

   private BridgeHooks() {
   }

   // ---------------------------------------------------------------- snow

   /**
    * True when FTF's erosion decorator should leave this column alone: a snow top that is not on a cliff. With
    * snow_cap_fade the lowest part of a snowy peaks cap is bared in patches here.
    */
   public static boolean keepSnow(ScreeFeature.Config config, net.minecraft.world.level.chunk.ChunkAccess chunk, int x, int y, int z, float steepness) {
      Settings s = WorldLock.activeSettings();
      if (!SnowCaps.active(s)) {
         return false;
      }
      float cliff = SnowCaps.cliffSteepness(s, config);
      boolean snow = SnowCaps.isSnow(chunk.getBlockState(new net.minecraft.core.BlockPos(x, y, z))) && steepness <= cliff;
      if (s.snowCapFade() > 0.0 && SnowCaps.fadeBare(chunk, x, y, z, cliff > 0.0F ? steepness / cliff : 0.0F) && snow) {
         SnowCaps.bareRock(chunk, x, y, z);
      }
      return snow;
   }

   /** Replacement answer for FTF's snow-layer erosion test, or null to keep FTF's own. */
   public static Boolean snowLayerErosion(ScreeFeature.Config config, float x, float z, float steepness) {
      Settings s = WorldLock.activeSettings();
      if (!SnowCaps.active(s)) {
         return null;
      }
      return steepness > SnowCaps.cliffSteepness(s, config) || SnowCaps.bared((int)Math.floor(x), (int)Math.floor(z));
   }

   // ---------------------------------------------------------------- biomes

   public static etcodehome.freeterraforged.world.worldgen.runtime.BiomeCandidateRoot vanillaBackportBiomes(
      etcodehome.freeterraforged.world.worldgen.runtime.BiomeCandidateRoot root, net.minecraft.core.HolderLookup.Provider lookups
   ) {
      etcodehome.freeterraforged.world.worldgen.runtime.BiomeCandidateRoot out = VanillaBackportBiomes.apply(root, lookups);
      if (out != null && BridgeConfig.enabled()) {
         try {
            CaveFloorGuard.layout(out.entries());
         } catch (Throwable t) {
            StillLifeFtfBridge.LOG.error("[{}] could not read the biome layout for the cave floor guard", StillLifeFtfBridge.MOD_ID, t);
         }
      }

      return out;
   }

   // ---------------------------------------------------------------- cave floors

   /** FreeTerraForged's surface rule, with Still Life's ground kept off cave floors when the world has cave_floor_guard on. */
   public static net.minecraft.world.level.levelgen.SurfaceRules.RuleSource caveFloorGuard(net.minecraft.world.level.levelgen.SurfaceRules.RuleSource root) {
      return CaveFloorGuard.apply(root);
   }

   // ---------------------------------------------------------------- wetland biomes

   private static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> KEEPS_WETLAND_BIOME = net.minecraft.tags.TagKey.create(
      net.minecraft.core.registries.Registries.BIOME,
      net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StillLifeFtfBridge.MOD_ID, "wetland_biomes")
   );

   /** Wetland water keeps a Still Life wetland biome FTF's own wetland test does not recognise. */
   @SuppressWarnings("unchecked")
   public static boolean keepsWetlandBiome(Object biome) {
      return BridgeConfig.enabled()
         && WorldLock.activeSettings().keepWetlandBiomes()
         && biome instanceof net.minecraft.core.Holder<?> h
         && ((net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>)h).is(KEEPS_WETLAND_BIOME);
   }

   // ---------------------------------------------------------------- wetlands

   /**
    * Before a wetland shapes a cell. Wetlands only flood land near their water: ground up to half of wetland_max_height above
    * the water is shaped normally, ground above the full value is left alone (returns true to skip), and in between the
    * wetland fades out, so wetlands never cut pits into hillsides (FTF flattens everything within a wetland's radius to its
    * water level, which on a mountainside digs a pit hundreds of blocks deep).
    */
   public static boolean wetlandBefore(Cell cell, Levels levels) {
      WetlandPass pass = WETLAND_PASS.get();
      pass.active = false;
      if (!BridgeConfig.enabled()) {
         return false;
      }

      double max = WorldLock.activeSettings().wetlandMaxHeight();
      if (max <= 0.0) {
         return false;
      }

      float water = levels.water + ContinentalHydrology.getComplexWaterHeight(cell.waterTable, cell.globalContinentScale, cell.continentSizeModifier);
      float above = (cell.height - water) / levels.unit;
      float lo = (float)max * 0.5F;
      float hi = (float)max;
      if (above <= lo) {
         return false;
      }

      if (above >= hi) {
         return true;
      }

      float w = (above - lo) / (hi - lo);
      pass.keep = w * w * (3.0F - 2.0F * w);
      pass.height = cell.height;
      pass.before.copyFrom(cell);
      pass.active = true;
      return false;
   }

   public static void wetlandAfter(Cell cell) {
      WetlandPass pass = WETLAND_PASS.get();
      if (!pass.active) {
         return;
      }

      pass.active = false;
      float h = cell.height + (pass.height - cell.height) * pass.keep;
      if (pass.keep >= 0.5F) {
         // mostly dry ground: keep the land's own terrain type, biome and water level
         cell.copyFrom(pass.before);
      }

      cell.height = h;
   }

   private static final class WetlandPass {
      boolean active;
      float height;
      float keep;
      final Cell before = new Cell();
   }
}

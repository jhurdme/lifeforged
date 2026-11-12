package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Heightmap;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.ContinentalHydrology;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.FTFRiverCarver;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.River;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.RiverWarp;
import etcodehome.freeterraforged.world.worldgen.feature.ScreeFeature;
import net.minecraft.world.level.block.state.BlockState;

/** Entry points for the bridge's mixins into FreeTerraForged (mixin classes cannot share code directly). */
public final class BridgeHooks {
   private static final ThreadLocal<RiverPass> RIVER_PASS = ThreadLocal.withInitial(RiverPass::new);
   private static final ThreadLocal<WetlandPass> WETLAND_PASS = ThreadLocal.withInitial(WetlandPass::new);

   private BridgeHooks() {
   }

   // ---------------------------------------------------------------- snow

   /** True when FTF's erosion decorator should leave this column alone: a snow top that is not on a cliff. */
   public static boolean keepSnow(ScreeFeature.Config config, BlockState top, float steepness) {
      Settings s = WorldLock.activeSettings();
      return SnowCaps.active(s) && SnowCaps.isSnow(top) && steepness <= SnowCaps.cliffSteepness(s, config);
   }

   /** Replacement answer for FTF's snow-layer erosion test, or null to keep FTF's own. */
   public static Boolean snowLayerErosion(ScreeFeature.Config config, float steepness) {
      Settings s = WorldLock.activeSettings();
      return SnowCaps.active(s) ? steepness > SnowCaps.cliffSteepness(s, config) : null;
   }

   // ---------------------------------------------------------------- biomes

   public static etcodehome.freeterraforged.world.worldgen.runtime.BiomeCandidateRoot vanillaBackportBiomes(
      etcodehome.freeterraforged.world.worldgen.runtime.BiomeCandidateRoot root, net.minecraft.core.HolderLookup.Provider lookups
   ) {
      return VanillaBackportBiomes.apply(root, lookups);
   }

   // ---------------------------------------------------------------- lakes

   /** A riverbed cell at least this much wider than the river's normal bed counts as lake water. */
   static final float LAKE_WIDENING = 1.5F;
   private static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> KEEPS_LAKE_BIOME = net.minecraft.tags.TagKey.create(
      net.minecraft.core.registries.Registries.BIOME,
      net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StillLifeFtfBridge.MOD_ID, "lake_biomes")
   );

   public static void riverbed(Cell cell, float lakeMultiplier) {
      if (lakeMultiplier >= LAKE_WIDENING && ((Object)cell) instanceof LakeCell lake) {
         lake.stilllife$setLake(true);
      }
   }

   /** 0 for ordinary river, rising to 1 where the river has widened into a lake (only with lake_fill on). */
   private static float lakeFill(float lakeMultiplier) {
      if (!(lakeMultiplier > 1.0F) || !BridgeConfig.enabled() || !WorldLock.activeSettings().lakeFill()) {
         return 0.0F;
      }

      return Math.min(1.0F, (lakeMultiplier - 1.0F) / (LAKE_WIDENING - 1.0F));
   }

   /**
    * Width FTF shapes a lake's bed for. FTF clears the lake out to its width-noise-varied radius but shapes the bed for the
    * river's plain width, so wherever the noise widens the lake the outer ring stays flat at water level, dry. With lake_fill
    * the bed is shaped for the cleared width (blended in as the river widens, so ordinary river beds are unchanged).
    */
   public static float lakeBedScale(float scale, float clearedScale, float lakeMultiplier) {
      float f = lakeFill(lakeMultiplier);
      return f > 0.0F ? scale + (clearedScale - scale) * f : scale;
   }

   /**
    * A filled lake's floor stays at least lake_min_depth blocks under its water surface block, so the water reaches the foot of
    * the bank. Measured in whole blocks: a water column's floor block is the one the bed height reaches into (rounded up), so a
    * bed less than a full block under the surface block would still end flush with the water, dry.
    */
   public static float lakeBedDepth(float bed, float water, float lakeMultiplier, Levels levels) {
      float f = lakeFill(lakeMultiplier);
      if (!(f > 0.0F)) {
         return bed;
      }

      float scale = levels.terrainScaleFactor;
      float floor = ((float)Math.floor(water * scale) - (float)WorldLock.activeSettings().lakeMinDepth() - 0.05F) / scale;
      return Math.min(bed, water + (floor - water) * f);
   }

   /** True for biomes that lake water keeps instead of FTF's river biome (Still Life's salt lake). */
   public static boolean keepsLakeBiome(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
      return BridgeConfig.enabled() && biome != null && biome.is(KEEPS_LAKE_BIOME);
   }

   // ---------------------------------------------------------------- terrain

   public static void heightmapCreated(Heightmap heightmap) {
      RiverHeadwaters.register(heightmap);
   }

   // ---------------------------------------------------------------- rivers

   public static float computeCut(River river, RiverWarp warp, Levels levels, FTFRiverCarver parent) {
      Settings s = WorldLock.activeSettings();
      return BridgeConfig.enabled() && s.riverHeadwaters() ? RiverHeadwaters.computeCut(river, warp, levels, parent, s) : RiverHeadwaters.NO_CUT;
   }

   /** Before a river carves a cell. Returns true to skip the carve (the cell is upstream of where the river starts). */
   public static boolean riverBefore(RiverAccess carver, River river, Cell cell, float t) {
      RiverPass pass = RIVER_PASS.get();
      pass.active = false;
      if (!BridgeConfig.enabled()) {
         return false;
      }

      Settings s = WorldLock.activeSettings();
      if (!s.riverHeadwaters()) {
         return false;
      }

      float cut = carver.stilllife$cut();
      if (t < cut) {
         return true;
      }

      if (cut == RiverHeadwaters.NO_CUT || s.riverFadeIn() <= 0.0) {
         return false;
      }

      float along = (t - cut) * river.length;
      if (along >= s.riverFadeIn()) {
         return false;
      }

      pass.active = true;
      pass.height = cell.height;
      pass.fade = (float)Math.max(0.0, along / s.riverFadeIn());
      pass.before.copyFrom(cell);
      return false;
   }

   /**
    * Inside FTF's carve, once it has picked the height for a cell beside the river (valley floor or fade-out, outside the
    * river's banks): the land may be lowered by at most river_flank_max_cut blocks, but close to the river that limit relaxes
    * along a slope rising from the riverbank, so the valley side is an even slope that meets the banks without a wall.
    */
   public static float riverFlank(Cell cell, float finalHeight, float dist, float bankRadius, float valleyFloor, Levels levels) {
      if (!(finalHeight < cell.height) || !(dist >= bankRadius) || !BridgeConfig.enabled()) {
         return finalHeight;
      }

      Settings s = WorldLock.activeSettings();
      double flank = s.riverFlankMaxCut();
      if (flank <= 0.0) {
         return finalHeight;
      }

      float unit = levels.unit;
      float slope = valleyFloor + (float)(s.riverFlankSlope() * (dist - bankRadius)) * unit;
      float limit = Math.min(cell.height - (float)flank * unit, slope);
      return Math.max(finalHeight, limit);
   }

   /** After a river carved a cell: fade in a shortened river below the mountains. */
   public static void riverAfter(Cell cell, Levels levels) {
      RiverPass pass = RIVER_PASS.get();
      if (!pass.active) {
         return;
      }

      pass.active = false;
      float before = pass.height;
      if (!(cell.height < before)) {
         return;
      }

      float f = Math.min(1.0F, Math.max(0.0F, pass.fade));
      f = f * f * (3.0F - 2.0F * f);
      float h = before - (before - cell.height) * f;
      if (f < 0.5F) {
         // Too shallow to be a river yet: a dry valley that keeps the land's own terrain type and biome.
         cell.copyFrom(pass.before);
      }

      cell.height = h;
   }

   // ---------------------------------------------------------------- wetlands

   /**
    * Before a wetland shapes a cell. Wetlands only flood land near their water: ground up to half of river_flank_max_cut above
    * the water is shaped normally, ground above the full value is left alone (returns true to skip), and in between the
    * wetland fades out, so wetlands never cut pits into hillsides.
    */
   public static boolean wetlandBefore(Cell cell, Levels levels) {
      WetlandPass pass = WETLAND_PASS.get();
      pass.active = false;
      if (!BridgeConfig.enabled()) {
         return false;
      }

      double flank = WorldLock.activeSettings().riverFlankMaxCut();
      if (flank <= 0.0) {
         return false;
      }

      float water = levels.water + ContinentalHydrology.getComplexWaterHeight(cell.waterTable, cell.globalContinentScale, cell.continentSizeModifier);
      float above = (cell.height - water) / levels.unit;
      float lo = (float)flank * 0.5F;
      float hi = (float)flank;
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

   private static final class RiverPass {
      boolean active;
      float height;
      float fade;
      final Cell before = new Cell();
   }

   private static final class WetlandPass {
      boolean active;
      float height;
      float keep;
      final Cell before = new Cell();
   }
}

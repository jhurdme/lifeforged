package stilllife.ftfbridge;

import etcodehome.freeterraforged.data.worldgen.preset.PresetManager.PM;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.climate.ClimateModule;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;

/**
 * coastal_moisture_reach (format 20): FreeTerraForged's Coastal Moisture Boost read as a boost of the coast.
 *
 * <p>FTF (1.0.0032-0034) computes a column's moisture from its 0..1 moisture noise in two steps
 * ({@code ClimateModule.modifyMoisture}): the boost, {@code m += (1 - m) * boost * (1 - elevation)}, and above its ground
 * level the rain shadow, {@code m ^ (2 ^ (elevation * rainShadowStrength))}, where elevation is the height above the sea
 * as a share of the world height. The boost so has nothing to do with the coast: it wets all low land alike, at any
 * distance from the sea. Still Life's humidity bands are narrow (edges at -0.6, -0.405, -0.275, -0.105, 0.035, 0.18,
 * 0.235, 0.33 and 0.4 of a -1..1 scale), so a boost of 0.2 moves most lowland two bands wetter, and a little more turns
 * whole lowlands into (coastal) temperate rainforest.
 *
 * <p>This takes both steps out again (they are invertible), and puts the boost back scaled by the distance from the sea:
 * all of it at the shore, none from coastal_moisture_reach blocks inland (the distance from FTF's control point, as the
 * bridge's continentalness). At the shore the result is FTF's own value. The biome lookup then gets FTF's rain shadow back
 * unchanged; the snow model reads the humidity without it (the climate around a mountain: with the rain shadow the tallest
 * peaks got less snow than their foothills).
 *
 * <p>FTF is an unstable release and this depends on its formula, so nothing here is assumed: {@link #of} compares this
 * class's own forward model with FTF's {@code modifyMoisture} on a grid of heights and moistures when a world is built, and
 * when they differ, or any FTF member is missing, the world keeps FTF's humidity as it is (and the log says so).
 */
final class CoastalMoisture {
   /** Boosts from here up leave too little of the moisture noise to recover (at 1 FTF's lowland is all 1). */
   private static final double MAX_BOOST = 0.9;
   private static final double TOLERANCE = 0.002;
   private final Levels levels;
   private final double reach;

   private CoastalMoisture(Levels levels, double reach) {
      this.levels = levels;
      this.reach = reach;
   }

   /** The model for a world, or null when the option is off (0) or FTF's moisture does not follow the formula above. */
   static CoastalMoisture of(GeneratorContext ctx, Settings settings) {
      double reach = settings.coastalMoistureReach();
      if (!(reach > 0.0)) {
         return null;
      }
      try {
         CoastalMoisture model = new CoastalMoisture(ctx.levels, reach);
         ClimateModule ftf = ctx.heightmap.climate().biomeNoise();
         Levels l = ctx.levels;
         for (int i = 0; i <= 8; i++) {
            // from below the sea to the top of the world
            float height = l.water * 0.5F + (1.0F - l.water * 0.5F) * i / 8.0F;
            for (int j = 0; j <= 10; j++) {
               float raw = j / 10.0F;
               double theirs = ftf.modifyMoisture(height, raw);
               double mine = model.shadow(model.boosted(raw, height, 1.0), height);
               if (!(Math.abs(theirs - mine) <= TOLERANCE)) {
                  StillLifeFtfBridge.LOG.warn("[{}] this FreeTerraForged version computes moisture differently than Lifeforged expects "
                     + "(height {}, moisture {}: {} against {}); coastal_moisture_reach is off and FreeTerraForged's humidity is used as it is",
                     StillLifeFtfBridge.MOD_ID, height, raw, theirs, mine);
                  return null;
               }
            }
         }
         return model;
      } catch (Throwable t) {
         StillLifeFtfBridge.LOG.warn("[{}] could not read FreeTerraForged's moisture model ({}); coastal_moisture_reach is off and "
            + "FreeTerraForged's humidity is used as it is", StillLifeFtfBridge.MOD_ID, t.toString());
         return null;
      }
   }

   // FTF reads its static preset copy for every column, so this does too: the two can never disagree
   private static double boost() {
      return PM.climateSettings.coastalMoistureBoost;
   }

   private static double rainShadow() {
      return PM.climateSettings.rainShadowStrength;
   }

   private double elevation(float height) {
      return height <= this.levels.ground ? 0.0 : clamp01(this.levels.getNormalizedInlandElevation(height));
   }

   /** FTF's boost on a 0..1 moisture, times `coast` (1 = FTF's own). */
   private double boosted(double raw, float height, double coast) {
      double m = clamp01(raw);
      return clamp01(m + (1.0 - m) * boost() * (1.0 - this.elevation(height)) * coast);
   }

   /** FTF's rain shadow on a 0..1 moisture. */
   private double shadow(double m, float height) {
      return height <= this.levels.ground ? m : Math.pow(m, Math.pow(2.0, this.elevation(height) * rainShadow()));
   }

   /** The 0..1 moisture noise under one of FTF's values (-1..1) at this height; NaN when the boost is too strong to tell. */
   private double raw(float ftfMoisture, float height) {
      double k = boost() * (1.0 - this.elevation(height));
      if (k > MAX_BOOST) {
         return Double.NaN;
      }
      double m = clamp01((ftfMoisture + 1.0) / 2.0);
      if (height > this.levels.ground) {
         m = Math.pow(m, 1.0 / Math.pow(2.0, this.elevation(height) * rainShadow()));
      }
      return clamp01((m - k) / (1.0 - k));
   }

   /** 1 at the shore and in the sea, falling smoothly to 0 at coastal_moisture_reach blocks inland. */
   private double coast(double seaDistance) {
      double t = clamp01(seaDistance / this.reach);
      return 1.0 - t * t * (3.0 - 2.0 * t);
   }

   /**
    * The humidity (-1..1) for the biome lookup where FTF's is `ftfMoisture`: the boost by the distance from the sea
    * (blocks), FTF's rain shadow as it is.
    */
   float biome(float ftfMoisture, float height, double seaDistance) {
      double raw = this.raw(ftfMoisture, height);
      if (Double.isNaN(raw)) {
         return ftfMoisture;
      }
      return (float)(this.shadow(this.boosted(raw, height, this.coast(seaDistance)), height) * 2.0 - 1.0);
   }

   /** The humidity (-1..1) the snow model reads: as {@link #biome} without the rain shadow. */
   float snow(float ftfMoisture, float height, double seaDistance) {
      double raw = this.raw(ftfMoisture, height);
      if (Double.isNaN(raw)) {
         return ftfMoisture;
      }
      return (float)(this.boosted(raw, height, this.coast(seaDistance)) * 2.0 - 1.0);
   }

   private static double clamp01(double v) {
      return v < 0.0 ? 0.0 : Math.min(v, 1.0);
   }
}

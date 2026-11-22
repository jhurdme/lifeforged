package stilllife.ftfbridge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code stilllife_ftf_bridge:terrain}: lets a feature through only where FreeTerraForged's terrain at that
 * column matches. Every field is optional; in a world that is not a FreeTerraForged world everything passes.
 *
 * <pre>
 * { "type": "stilllife_ftf_bridge:terrain",
 *   "min_slope": 0.0, "max_slope": 0.7,      // FTF slope (the preset's rock / scree / dirt steepness scale: 0.8 / 0.6 / 0.45)
 *   "min_sediment": 0.0, "max_sediment": 1.0, // FTF erosion sediment, 0..1 (deposited soil: valley floors, foot slopes)
 *   "river_zones": ["banks", "valley_floor"], // riverbed, banks, valley_floor, valley_fadeout, none
 *   "min_hollow": -9, "max_hollow": 9,        // blocks the ground around (5x5) lies above this spot: + hollows, gullies,
 *                                             // the foot of steps; - ridges, tops, edges
 *   "min_temperature": 0.4, "max_temperature": 2.0, // the biome's temperature here, cooled with height like Minecraft's
 *   "prefer_facing": "north", "facing_bias": 0.4,   // on slopes facing the other way, skipped this share of the time
 *   "ridge_thinning": 0.5,                    // skipped up to this share of the time on ground that stands out above the
 *                                             // land within 32 blocks (from 16 blocks up, fully at 48)
 *   "avoid_chutes": true,                     // not in steep gullies (avalanche chutes: open strips down mountain forests)
 *   "min_snow_line": -45, "max_snow_line": -8, // blocks above (+) / below (-) where the snowy peaks begin here
 *   "treeline": [-110, -45],                  // skipped more and more from the first height to the second (relative to
 *                                             // where the snowy peaks begin), always above it: trees thinning out upwards
 *   "max_above_water": 2,                     // at most this many blocks above the nearby river's water (river zones only)
 *   "requires_rain": true,                    // only in biomes with rain or snow (not deserts and other dry biomes)
 *   "skip_with_natural_snow": true,
 *   "skip_if_loaded": ["still_life_naturalized"],  // not when one of these mods is installed (it does this job itself)
 *   "terrain_categories": ["lake"],           // FTF terrain category of the column: deep_ocean, shallow_ocean, coast,
 *                                             // beach, river, lake, wetland, flatland, lowland, highland, island
 *   "requires_option": "shores" }             // only when this bridge option is on in the world (shores, ground_cover,
 *                                             // mountain_snow, natural_snow_layers)          // not in worlds where the bridge sets snow depth (natural_snow_layers): for
 *                                             // features that set snow layers themselves, which would cut through the
 *                                             // bridge's snow along chunk borders
 * </pre>
 */
public final class TerrainFilter extends PlacementFilter {
   private record Ranges(float minSlope, float maxSlope, float minSediment, float maxSediment, float minHollow, float maxHollow,
                         float minTemperature, float maxTemperature, float minSnowLine, float maxSnowLine) {
      static final MapCodec<Ranges> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("min_slope", Float.NEGATIVE_INFINITY).forGetter(Ranges::minSlope),
            Codec.FLOAT.optionalFieldOf("max_slope", Float.POSITIVE_INFINITY).forGetter(Ranges::maxSlope),
            Codec.FLOAT.optionalFieldOf("min_sediment", Float.NEGATIVE_INFINITY).forGetter(Ranges::minSediment),
            Codec.FLOAT.optionalFieldOf("max_sediment", Float.POSITIVE_INFINITY).forGetter(Ranges::maxSediment),
            Codec.FLOAT.optionalFieldOf("min_hollow", Float.NEGATIVE_INFINITY).forGetter(Ranges::minHollow),
            Codec.FLOAT.optionalFieldOf("max_hollow", Float.POSITIVE_INFINITY).forGetter(Ranges::maxHollow),
            Codec.FLOAT.optionalFieldOf("min_temperature", Float.NEGATIVE_INFINITY).forGetter(Ranges::minTemperature),
            Codec.FLOAT.optionalFieldOf("max_temperature", Float.POSITIVE_INFINITY).forGetter(Ranges::maxTemperature),
            Codec.FLOAT.optionalFieldOf("min_snow_line", Float.NEGATIVE_INFINITY).forGetter(Ranges::minSnowLine),
            Codec.FLOAT.optionalFieldOf("max_snow_line", Float.POSITIVE_INFINITY).forGetter(Ranges::maxSnowLine)
         ).apply(i, Ranges::new));
   }

   public static final MapCodec<TerrainFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
         Ranges.CODEC.forGetter(f -> f.ranges),
         Codec.STRING.listOf().optionalFieldOf("river_zones", List.of()).forGetter(f -> f.riverZones),
         Codec.STRING.optionalFieldOf("prefer_facing").forGetter(f -> f.preferFacing),
         Codec.FLOAT.optionalFieldOf("facing_bias", 0.4F).forGetter(f -> f.facingBias),
         Codec.FLOAT.optionalFieldOf("ridge_thinning", 0.0F).forGetter(f -> f.ridgeThinning),
         Codec.BOOL.optionalFieldOf("avoid_chutes", false).forGetter(f -> f.avoidChutes),
         Codec.BOOL.optionalFieldOf("skip_with_natural_snow", false).forGetter(f -> f.skipWithNaturalSnow),
         Codec.INT.optionalFieldOf("max_above_water", Integer.MAX_VALUE).forGetter(f -> f.maxAboveWater),
         Codec.BOOL.optionalFieldOf("requires_rain", false).forGetter(f -> f.requiresRain),
         Codec.FLOAT.listOf().optionalFieldOf("treeline", List.of()).forGetter(f -> f.treeline),
         Codec.STRING.listOf().optionalFieldOf("skip_if_loaded", List.of()).forGetter(f -> f.skipIfLoaded),
         Codec.STRING.listOf().optionalFieldOf("terrain_categories", List.of()).forGetter(f -> f.terrainCategories),
         Codec.STRING.optionalFieldOf("requires_option", "").forGetter(f -> f.requiresOption)
      ).apply(i, TerrainFilter::new));
   public static final PlacementModifierType<TerrainFilter> TYPE = () -> CODEC;

   private final Ranges ranges;
   private final List<String> riverZones;
   private final Optional<String> preferFacing;
   private final float facingBias;
   private final float ridgeThinning;
   private final boolean avoidChutes;
   private final boolean skipWithNaturalSnow;
   private final int maxAboveWater;
   private final boolean requiresRain;
   private final List<Float> treeline;
   private final List<String> skipIfLoaded;
   private final boolean skipped;
   private final List<String> terrainCategories;
   private final String requiresOption;

   private TerrainFilter(Ranges ranges, List<String> riverZones, Optional<String> preferFacing, float facingBias, float ridgeThinning,
                         boolean avoidChutes, boolean skipWithNaturalSnow, int maxAboveWater, boolean requiresRain,
                         List<Float> treeline, List<String> skipIfLoaded, List<String> terrainCategories, String requiresOption) {
      this.terrainCategories = terrainCategories.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
      this.requiresOption = requiresOption;
      this.treeline = treeline;
      this.skipIfLoaded = skipIfLoaded;
      this.skipped = skipIfLoaded.stream().anyMatch(StillLifeFtfBridge::isLoaded);
      this.skipWithNaturalSnow = skipWithNaturalSnow;
      this.maxAboveWater = maxAboveWater;
      this.requiresRain = requiresRain;
      this.ranges = ranges;
      this.riverZones = riverZones.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
      this.preferFacing = preferFacing.map(s -> s.toLowerCase(Locale.ROOT));
      this.facingBias = facingBias;
      this.ridgeThinning = ridgeThinning;
      this.avoidChutes = avoidChutes;
   }

   @Override
   protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
      if (this.skipped) {
         return false;
      }
      WorldGenLevel level = context.getLevel();
      GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return true;
      }
      if (this.skipWithNaturalSnow && NaturalSnow.active(WorldLock.activeSettings())) {
         return false;
      }
      if (!this.requiresOption.isEmpty() && !optionOn(this.requiresOption)) {
         return false;
      }
      int x = pos.getX();
      int z = pos.getZ();
      Cell cell = FtfTerrain.cell(ctx, level.getChunk(x >> 4, z >> 4), x, z);
      Ranges r = this.ranges;
      float slope = cell.gradient;
      if (slope < r.minSlope || slope > r.maxSlope) {
         return false;
      }
      float sediment = sediment(cell);
      if (sediment < r.minSediment || sediment > r.maxSediment) {
         return false;
      }
      if (!this.riverZones.isEmpty() && !this.riverZones.contains(riverZone(cell))) {
         return false;
      }
      if (!this.terrainCategories.isEmpty() && !this.terrainCategories.contains(category(cell))) {
         return false;
      }
      if (this.maxAboveWater != Integer.MAX_VALUE) {
         if (cell.riverWaterLevel <= 0.0F || pos.getY() - ctx.heightmap.levels().scale(cell.riverWaterLevel) > this.maxAboveWater) {
            return false;
         }
      }
      if (this.requiresRain && !level.getBiome(pos).value().hasPrecipitation()) {
         return false;
      }
      boolean lineRange = r.minSnowLine != Float.NEGATIVE_INFINITY || r.maxSnowLine != Float.POSITIVE_INFINITY;
      if (lineRange || this.treeline.size() == 2) {
         double d = StillLifeClimateBridge.capLineDistance(x, pos.getY(), z);
         if (!Double.isNaN(d)) {
            if (d < r.minSnowLine || d > r.maxSnowLine) {
               return false;
            }
            if (this.treeline.size() == 2) {
               float a = this.treeline.get(0);
               float b = this.treeline.get(1);
               double t = (d - a) / Math.max(1.0F, b - a);
               if (t >= 1.0 || t > 0.0 && random.nextFloat() < t) {
                  return false;
               }
            }
         }
      }

      boolean needHollow = r.minHollow != Float.NEGATIVE_INFINITY || r.maxHollow != Float.POSITIVE_INFINITY || this.avoidChutes;
      if (needHollow) {
         float hollow = FtfTerrain.hollow(level, x, z);
         if (hollow < r.minHollow || hollow > r.maxHollow) {
            return false;
         }
         if (this.avoidChutes && FtfTerrain.isChute(slope, hollow)) {
            return false;
         }
      }

      if (r.minTemperature != Float.NEGATIVE_INFINITY || r.maxTemperature != Float.POSITIVE_INFINITY) {
         float t = FtfTerrain.temperature(level, pos);
         if (t < r.minTemperature || t > r.maxTemperature) {
            return false;
         }
      }

      if (this.preferFacing.isPresent() && this.facingBias > 0.0F) {
         String facing = FtfTerrain.facing(level, x, z);
         if (facing.equals(opposite(this.preferFacing.get())) && random.nextFloat() < this.facingBias) {
            return false;
         }
      }

      if (this.ridgeThinning > 0.0F) {
         float relief = FtfTerrain.relief(ctx, x, z);
         float exposure = Math.max(0.0F, Math.min(1.0F, (relief - 16.0F) / 32.0F));
         if (exposure > 0.0F && random.nextFloat() < exposure * this.ridgeThinning) {
            return false;
         }
      }
      return true;
   }

   /** The column's FTF terrain category as a lower-case name (lake, coast, beach, river, ...). */
   static String category(Cell cell) {
      if (cell.terrain == null) {
         return "none";
      }
      if (cell.terrain.isLake()) {
         return "lake";
      }
      return String.valueOf(cell.terrain.getDelegate()).toLowerCase(Locale.ROOT);
   }

   private static boolean optionOn(String option) {
      Settings s = WorldLock.activeSettings();
      return switch (option) {
         case "shores" -> BridgeConfig.enabled() && s.shores();
         case "ground_cover" -> GroundCover.active(s);
         case "mountain_snow" -> MountainSnow.active(s);
         case "natural_snow_layers" -> NaturalSnow.active(s);
         default -> true;
      };
   }

   private static String opposite(String facing) {
      return switch (facing) {
         case "north" -> "south";
         case "south" -> "north";
         case "east" -> "west";
         case "west" -> "east";
         default -> "";
      };
   }

   /** FTF's sediment on a 0..1 scale (its scree decorator reads cell.sediment * 3/255 the same way). */
   static float sediment(Cell cell) {
      return cell.sediment * (3.0F / 255.0F);
   }

   /** The cell's river zone as a lower-case name: riverbed, banks, valley_floor, valley_fadeout or none. */
   static String riverZone(Cell cell) {
      if (cell.riverZone == null) {
         return "none";
      }
      return switch (cell.riverZone.name()) {
         case "Riverbed" -> "riverbed";
         case "Banks" -> "banks";
         case "ValleyFloor" -> "valley_floor";
         case "ValleyFadeout" -> "valley_fadeout";
         default -> "none";
      };
   }

   @Override
   public PlacementModifierType<?> type() {
      return TYPE;
   }
}

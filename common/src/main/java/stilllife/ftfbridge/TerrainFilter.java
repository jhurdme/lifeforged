package stilllife.ftfbridge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code stilllife_ftf_bridge:terrain}: lets a feature through only where FreeTerraForged's terrain at that
 * column matches. Every field is optional; in a world that is not a FreeTerraForged world everything passes.
 *
 * <pre>
 * { "type": "stilllife_ftf_bridge:terrain",
 *   "max_slope": 0.65,            // FTF slope (the same scale as the preset's rock/scree/dirt steepness)
 *   "min_slope": 0.0,
 *   "min_sediment": 0.0,          // FTF erosion sediment, 0..1 (deposited soil: valley floors, foot slopes)
 *   "max_sediment": 1.0,
 *   "river_zones": ["banks", "valley_floor"] }   // riverbed, banks, valley_floor, valley_fadeout, none
 * </pre>
 */
public final class TerrainFilter extends PlacementFilter {
   public static final MapCodec<TerrainFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
         Codec.FLOAT.optionalFieldOf("min_slope", Float.NEGATIVE_INFINITY).forGetter(f -> f.minSlope),
         Codec.FLOAT.optionalFieldOf("max_slope", Float.POSITIVE_INFINITY).forGetter(f -> f.maxSlope),
         Codec.FLOAT.optionalFieldOf("min_sediment", Float.NEGATIVE_INFINITY).forGetter(f -> f.minSediment),
         Codec.FLOAT.optionalFieldOf("max_sediment", Float.POSITIVE_INFINITY).forGetter(f -> f.maxSediment),
         Codec.STRING.listOf().optionalFieldOf("river_zones", List.of()).forGetter(f -> f.riverZones)
      ).apply(i, TerrainFilter::new));
   public static final PlacementModifierType<TerrainFilter> TYPE = () -> CODEC;

   private final float minSlope;
   private final float maxSlope;
   private final float minSediment;
   private final float maxSediment;
   private final List<String> riverZones;

   private TerrainFilter(float minSlope, float maxSlope, float minSediment, float maxSediment, List<String> riverZones) {
      this.minSlope = minSlope;
      this.maxSlope = maxSlope;
      this.minSediment = minSediment;
      this.maxSediment = maxSediment;
      this.riverZones = riverZones.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
   }

   @Override
   protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
      Cell cell = FtfTerrain.cell(context.getLevel(), pos.getX(), pos.getZ());
      if (cell == null) {
         return true;
      }
      float slope = cell.gradient;
      if (slope < this.minSlope || slope > this.maxSlope) {
         return false;
      }
      float sediment = sediment(cell);
      if (sediment < this.minSediment || sediment > this.maxSediment) {
         return false;
      }
      return this.riverZones.isEmpty() || this.riverZones.contains(riverZone(cell));
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

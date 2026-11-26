package stilllife.ftfbridge;

import com.mojang.serialization.MapCodec;
import etcodehome.freeterraforged.world.worldgen.GeneratorContext;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code stilllife_ftf_bridge:terrain}: lets a feature through only where FreeTerraForged's terrain at that
 * column matches. Every field is optional; in a world that is not a FreeTerraForged world everything passes. The same
 * fields (all but {@code faces} and {@code forest_edge}) work as a surface rule condition ({@link TerrainCondition}).
 * docs/bridge.md, "Terrain data for datapacks", has the table with units and typical values, and worked examples.
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
 *   "fade_in": [-70, -40],                    // the other way: never below the first height, more and more up to the
 *                                             // second (for belts above the trees: krummholz, subalpine shrubs); shifted
 *                                             // by treeline_shelter / _sun / _wind / _ragged like the treeline
 *   "max_above_water": 2,                     // at most this many blocks above the nearby river's water (river zones only)
 *   "requires_rain": true,                    // only in biomes with rain or snow (not deserts and other dry biomes)
 *   "skip_with_natural_snow": true,           // not in worlds where the bridge sets snow depth (natural_snow_layers): for
 *                                             // features that set snow layers themselves, which would cut through the
 *                                             // bridge's snow along chunk borders
 *   "skip_if_loaded": ["still_life_naturalized"],  // not when one of these mods is installed (it does this job itself)
 *   "terrain_categories": ["lake"],           // FTF terrain category of the column: deep_ocean, shallow_ocean, coast,
 *                                             // beach, river, lake, wetland, flatland, lowland, highland, island
 *   "requires_option": "shores",              // only when this bridge option is on in the world (shores, ground_cover,
 *                                             // mountain_snow, natural_snow_layers, forest_variety)
 *   "forest_edge": 12,                        // only within this many blocks of where forest meets open land
 *   "clearings": 0.1,                         // (forest_variety) skipped in natural clearings, about this share of the land
 *   "regional_mix": 0.4, "mix_salt": 17,      // (forest_variety) skipped up to this share of the time, by a slow
 *                                             // regional variation of its own (mix_salt), so tree mixes vary
 *
 *   // ---- exposure (from FTF's terrain model; needs the climate bridge to know the world, else ignored)
 *   "min_insolation": 0.0, "max_insolation": 1.0, // sun on the slope: 0.5 flat ground, 0 full shade, 1 = twice flat's light
 *   "min_wind": -1.0, "max_wind": 1.0,        // wind exposure: + windward slopes and crests (scoured), - lee (loaded)
 *   "min_shelter": -1.0, "max_shelter": 1.0,  // land shape 2-32 blocks around: + gullies, cirques, benches; - ribs, ridges
 *   "wind_thinning": 0.5,                     // skipped up to this share of the time, by wind exposure (0 at <= 0, all at 1)
 *   "shade_thinning": 0.5,                    // skipped up to this share, by shade (0 at insolation >= 0.5, all at 0)
 *   "treeline_shelter": 30, "treeline_sun": 20, "treeline_wind": 15, // treeline raised by blocks per unit of shelter,
 *                                             // of sun (2 * insolation - 1) and lowered per unit of wind exposure
 *   "treeline_ragged": 12, "treeline_ragged_size": 24, // plus smooth noise of +-this many blocks, patches this wide
 *
 *   // ---- ground (talus and province fields need ground_cover, rock_suites also rock_strata; else ignored)
 *   "talus": ["coarse", "old"],               // the talus model's kind here: none, fine, medium, coarse, mossy, overgrown,
 *                                             // fan, thin_soil, thin_gravel, outcrop, rock (bare rock); groups fresh
 *                                             // (fine, medium, coarse), old (mossy, overgrown), deposit (fresh + old)
 *   "avoid_talus": ["fresh", "rock"],         // not these kinds
 *   "min_talus_position": 0.6, "max_talus_position": 1.0, // 0 apex (below the face) .. 1 toe; fans 1; fails off deposits
 *   "min_face_distance": 0, "max_face_distance": 12, // blocks up the fall line to the face feeding it (0 on bare rock;
 *                                             // no face within 15: infinite)
 *   "faces": ["ledge"],                       // the REAL ground's shape (blocks, 5x5): open, ledge, face, cliff
 *   "rock_families": ["pale"],                // the province's rock family: grey, dark, pale
 *   "rock_suites": ["limestone", "dolomite"], // its bedrock suite: grey, andesitic, dark, limestone, dolomite, granitic,
 *                                             // kaolin_buff, kaolin_ochre (kaolin = both)
 *   "min_greenness": 0.3, "max_greenness": 1.4, // how readily plants retake its scree (0.3..1.4)
 *   "min_bare_rock": 0.0, "max_bare_rock": 1.0, // how much bare rock shows through its thin soil (0..1)
 *
 *   // ---- snow (needs mountain_snow, else ignored) and moisture (needs the climate bridge to know the world)
 *   "snow": false,                            // true: the column holds snow (layers or snow ground); false: bare
 *   "min_snow_depth": 0, "max_snow_depth": 2, // snow depth in layers (8 = a block; 0 bare, up to ~19)
 *   "min_snow_margin": -0.15, "max_snow_margin": 0.0, // the snow model's score: >= 0 holds snow, deeper inside a
 *                                             // snowfield; just below 0 = bare but at a snowfield's edge; -1 far below
 *   "min_humidity": -1, "max_humidity": 1,    // Still Life's humidity (-1 dry .. 1 wet)
 *   "climates": ["humid", "temperate"],       // ground_cover's climate class: cold, temperate, humid, arid
 *   "min_wetness": 0.0, "max_wetness": 1.0 }  // how readily plants retake bare ground: 0 cold / dry .. 1 warm and wet
 * </pre>
 *
 * <p>What is read where: the FTF cell (slope, sediment, river zone, category, water), the climate bridge (snow line,
 * humidity), the province, the talus model, the snow model and the exposure fields are pure functions of the world and
 * the column (FreeTerraForged's terrain model, not blocks): the same from any chunk, in any order. hollow, prefer_facing,
 * faces, forest_edge and the snow fields' ground height read the real blocks of the terrain within 2 blocks of the
 * feature ({@link FtfTerrain#ground}, which falls back on FTF's model for chunks not yet at the noise stage); that is safe
 * during feature placement, which may read the chunks around the one being decorated.
 *
 * <p>Cost: one FTF cell copy per attempt; the province, climate and talus facts are kept per column and thread
 * ({@link TerrainFacts}), the snow and exposure fields per chunk and thread ({@link SnowField}), so each is worked out
 * once per column (a talus trace, the most expensive, a few microseconds) however many features ask.
 */
public final class TerrainFilter extends PlacementFilter {
   public static final MapCodec<TerrainFilter> CODEC = TerrainSpec.CODEC.xmap(TerrainFilter::new, f -> f.spec);
   public static final PlacementModifierType<TerrainFilter> TYPE = () -> CODEC;

   private final TerrainSpec spec;

   private TerrainFilter(TerrainSpec spec) {
      this.spec = spec;
   }

   @Override
   protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
      TerrainSpec s = this.spec;
      if (s.skipped) {
         return false;
      }
      WorldGenLevel level = context.getLevel();
      GeneratorContext ctx = FtfTerrain.context(level);
      if (ctx == null) {
         return true;
      }
      if (!s.enabledHere()) {
         return false;
      }
      int x = pos.getX();
      int z = pos.getZ();
      ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
      Cell cell = FtfTerrain.cell(ctx, chunk, x, z);
      return s.test(level, pos, random, ctx, level.getSeed(), chunk, cell, x, pos.getY(), z);
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

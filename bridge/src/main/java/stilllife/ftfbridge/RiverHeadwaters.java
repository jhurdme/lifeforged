package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Heightmap;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.ContinentalHydrology;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.FTFRiverCarver;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.River;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.RiverWarp;
import etcodehome.freeterraforged.world.worldgen.noise.module.Line;
import etcodehome.freeterraforged.world.worldgen.util.PosUtil;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * FreeTerraForged's rivers are warped straight lines that ignore the terrain, so they slice through mountains. This
 * measures each river's course once on FTF's terrain (before any river carving) and finds the last point, going
 * downstream, where the course crosses mountains. The river is only carved below that point.
 */
final class RiverHeadwaters {
   /** No mountains on the course: carve the whole river. */
   static final float NO_CUT = Float.NEGATIVE_INFINITY;
   /** The whole river (or the part of its parent it joins) is in mountains: carve none of it. */
   static final float REMOVED = Float.POSITIVE_INFINITY;
   private static final float STEP = 48.0F;
   private static final float MARGIN = 48.0F;
   private static final Map<Levels, WeakReference<Heightmap>> HEIGHTMAPS = new WeakHashMap<>();
   private static volatile boolean warned;

   private RiverHeadwaters() {
   }

   static void register(Heightmap heightmap) {
      synchronized (HEIGHTMAPS) {
         HEIGHTMAPS.put(heightmap.levels(), new WeakReference<>(heightmap));
      }
   }

   static Heightmap heightmap(Levels levels) {
      synchronized (HEIGHTMAPS) {
         WeakReference<Heightmap> ref = HEIGHTMAPS.get(levels);
         return ref == null ? null : ref.get();
      }
   }

   /** Blocks the ground at (x, z) rises above the local river water level, on FTF's terrain before rivers. */
   static float relief(Heightmap heightmap, Levels levels, Cell scratch, float x, float z) {
      scratch.reset();
      heightmap.applyTerrain(scratch, x, z);
      float water = levels.water + ContinentalHydrology.getComplexWaterHeight(scratch.waterTable, scratch.globalContinentScale, scratch.continentSizeModifier);
      return (scratch.height - water) * levels.terrainScaleFactor;
   }

   static float computeCut(River river, RiverWarp warp, Levels levels, FTFRiverCarver parent, Settings settings) {
      Heightmap heightmap = heightmap(levels);
      if (heightmap == null) {
         if (!warned) {
            warned = true;
            StillLifeFtfBridge.LOG.warn("[{}] no FreeTerraForged terrain found for a river; river_headwaters skipped for it", StillLifeFtfBridge.MOD_ID);
         }

         return NO_CUT;
      }

      float length = river.length;
      if (!(length > 1.0F)) {
         return NO_CUT;
      }

      float threshold = (float)settings.riverMountainHeight();
      int steps = Math.max(4, (int)Math.ceil(length / STEP));
      Cell scratch = new Cell();
      float last = -1.0F;

      try {
         for (int i = 0; i <= steps; i++) {
            float t = (float)i / steps;
            long p = river.pos(t, warp == null ? RiverWarp.NONE : warp);
            if (relief(heightmap, levels, scratch, PosUtil.unpackLeftf(p), PosUtil.unpackRightf(p)) > threshold) {
               last = t;
            }
         }
      } catch (Throwable t) {
         StillLifeFtfBridge.LOG.warn("[{}] could not measure a river's course; carving it unchanged", StillLifeFtfBridge.MOD_ID, t);
         return NO_CUT;
      }

      float cut = last < 0.0F ? NO_CUT : last + MARGIN / length;
      if (cut >= 0.97F) {
         cut = REMOVED;
      }

      // A branch that would join its parent where the parent is no longer carved has nowhere to flow: drop it.
      if (cut != REMOVED && parent instanceof RiverAccess access) {
         float parentCut = access.stilllife$cut();
         if (parentCut != NO_CUT) {
            River p = parent.getRiver();
            float junction = Line.distanceOnLine(river.x2, river.z2, p.x1, p.z1, p.x2, p.z2);
            if (junction < parentCut) {
               cut = REMOVED;
            }
         }
      }

      return cut;
   }
}

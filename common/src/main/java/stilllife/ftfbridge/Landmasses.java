package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.cell.continent.Continent;
import etcodehome.freeterraforged.world.worldgen.cell.continent.uplift.PlateUpliftBridge;
import etcodehome.freeterraforged.world.worldgen.cell.continent.uplift.UpliftContinentGenerator;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * biome_sets=continents: FreeTerraForged continent cells that FTF's continent bridges join into one landmass share one biome
 * set.
 *
 * <p>On UPLIFT continents FTF joins neighbouring continent cells with land where {@code isBridged} says so (a pure, symmetric
 * function of the two cells and the seed, kept per Voronoi edge in {@code PlateUpliftBridge.CellVoronoi.isBridged}); every
 * other edge between two cells is coastline, so land never joins two cells without a bridge. A landmass is a connected part
 * of that bridge graph. Its canonical cell is its smallest cell (by z, then x), found by a breadth-first search over bridges.
 *
 * <p>With FTF's default continent settings almost the whole world can be one landmass, which would give it a single set. A
 * search therefore stops after {@link #CAP} cells: a landmass larger than that ("giant") is the same verdict from every one of
 * its cells, and is then split along a fixed grid of {@link #BLOCK} x {@link #BLOCK} cells (bridges leaving a block are
 * ignored), so its parts roll their own sets and set borders fall only on bridges across block lines. CAP and BLOCK are part of
 * the layout: changing them would change existing worlds.
 *
 * <p>Everything here is a pure function of FTF's generator (seed and preset) and the cell; the maps only memoise it, so the
 * result does not depend on generation order or thread.
 */
final class Landmasses {
   /** Landmasses of more cells than this are split by {@link #BLOCK}. */
   static final int CAP = 64;
   /** Giant landmasses are split along a grid of this many continent cells. */
   static final int BLOCK = 4;

   private static final Map<Continent, Landmasses> BY_GENERATOR = Collections.synchronizedMap(new WeakHashMap<>());
   private static volatile Boolean available;

   /** A landmass: its canonical cell, and whether it is a part of a giant landmass. */
   record Mass(int x, int z, boolean giant, boolean single) {
   }

   private final UpliftContinentGenerator gen;
   private final PlateUpliftBridge plates;
   private final float frequency;
   /** FTF cell (packed) -> its landmass. */
   private final ConcurrentHashMap<Long, Mass> masses = new ConcurrentHashMap<>();
   /** FTF cell (packed) found in a landmass of more than CAP cells. */
   private final Set<Long> giants = ConcurrentHashMap.newKeySet();
   /** Cell.continentX/Z (the cell's rounded centroid in blocks, packed) -> FTF cell (packed). */
   private final ConcurrentHashMap<Long, Long> cellOfCentroid = new ConcurrentHashMap<>();

   private Landmasses(UpliftContinentGenerator gen, float frequency) {
      this.gen = gen;
      this.plates = gen.getPlateUpliftBridge();
      this.frequency = frequency;
   }

   /**
    * The landmasses of a continent generator, or null when sets go per cell: not UPLIFT, no bridges (bridgeChance 0), or
    * FreeTerraForged no longer has what this needs (logged once by {@link FtfCompat#verifyLandmasses}).
    *
    * @param continentScale the preset's world.continent.continentScale (FTF's cell size is 4 x that)
    */
   static Landmasses of(Continent continent, int continentScale) {
      Boolean ok = available;
      if (ok == null) {
         ok = FtfCompat.verifyLandmasses();
         available = ok;
      }
      if (!ok || !(continent instanceof UpliftContinentGenerator uplift) || !(uplift.getBridgeChance() > 0.0F) || continentScale <= 0
         || uplift.getPlateUpliftBridge() == null) {
         return null;
      }
      // FTF's own frequency: 1 / (float)(continentScale * 4) (UpliftContinentGenerator's constructor)
      float frequency = 1.0F / (float)(continentScale * 4);
      Landmasses l = BY_GENERATOR.get(continent);
      if (l == null || l.frequency != frequency) {
         l = new Landmasses(uplift, frequency);
         BY_GENERATOR.put(continent, l);
      }
      return l;
   }

   static long pack(int x, int z) {
      return (long)z << 32 | x & 0xFFFFFFFFL;
   }

   /** The landmass of the FTF cell whose Cell.continentX/Z are given, or null if that cell cannot be found. */
   Mass ofCentroid(int continentX, int continentZ) {
      long key = pack(continentX, continentZ);
      Long cell = cellOfCentroid.get(key);
      if (cell == null) {
         cell = findCell(continentX, continentZ);
         cellOfCentroid.putIfAbsent(key, cell);
      }
      if (cell == Long.MIN_VALUE) {
         return null;
      }
      return of((int)(long)cell, (int)(cell >> 32));
   }

   /**
    * The FTF cell whose Voronoi centroid FTF rounded to (continentX, continentZ): the centroid lies inside its own cell, so it is
    * one of the 3 x 3 cells around it (checked: 0 misses in 360000 cells), and FTF's own rounding is redone to confirm it.
    */
   private long findCell(int continentX, int continentZ) {
      int xi = (int)Math.floor(continentX * frequency);
      int zi = (int)Math.floor(continentZ * frequency);
      for (int r = 1; r <= 2; r++) {
         for (int z = zi - r; z <= zi + r; z++) {
            for (int x = xi - r; x <= xi + r; x++) {
               if (r == 2 && Math.abs(x - xi) < 2 && Math.abs(z - zi) < 2) {
                  continue;
               }
               PlateUpliftBridge.CellVoronoi v = plates.getVoronoi(x, z);
               if (Math.round(v.centroidX / frequency) == continentX && Math.round(v.centroidY / frequency) == continentZ) {
                  return pack(x, z);
               }
            }
         }
      }
      return Long.MIN_VALUE;
   }

   /** The landmass of FTF continent cell (x, z). */
   Mass of(int x, int z) {
      long key = pack(x, z);
      Mass m = masses.get(key);
      if (m != null) {
         return m;
      }
      if (!gen.canBridge(x, z)) {
         m = new Mass(x, z, false, true);
         Mass prev = masses.putIfAbsent(key, m);
         return prev != null ? prev : m;
      }
      if (!giants.contains(key)) {
         // the whole landmass, unless it has more than CAP cells
         Set<Long> found = search(x, z, CAP + 1, false);
         if (found.size() <= CAP) {
            long canon = smallest(found);
            Mass whole = new Mass((int)canon, (int)(canon >> 32), false, found.size() == 1);
            for (long c : found) {
               masses.putIfAbsent(c, whole);
            }
            return masses.get(key);
         }
         giants.addAll(found);
      }
      // giant: the part of it inside this cell's block
      Set<Long> part = search(x, z, Integer.MAX_VALUE, true);
      long canon = smallest(part);
      Mass piece = new Mass((int)canon, (int)(canon >> 32), true, false);
      for (long c : part) {
         masses.putIfAbsent(c, piece);
      }
      return masses.get(key);
   }

   /** Cells reachable over bridges from (x, z), stopping once more than limit - 1 are found; inBlock: only inside its block. */
   private Set<Long> search(int x, int z, int limit, boolean inBlock) {
      int bx = Math.floorDiv(x, BLOCK);
      int bz = Math.floorDiv(z, BLOCK);
      Set<Long> seen = new HashSet<>();
      ArrayDeque<Long> queue = new ArrayDeque<>();
      long start = pack(x, z);
      seen.add(start);
      queue.add(start);
      while (!queue.isEmpty() && seen.size() < limit) {
         long c = queue.poll();
         // FTF returns a per-thread object that the next getVoronoi call may reuse: read it fully before asking again
         PlateUpliftBridge.CellVoronoi v = plates.getVoronoi((int)c, (int)(c >> 32));
         for (int k = 0; k < v.edgeCount && seen.size() < limit; k++) {
            if (!v.isBridged[k]) {
               continue;
            }
            int nx = v.ncx[k];
            int nz = v.ncy[k];
            if (inBlock && (Math.floorDiv(nx, BLOCK) != bx || Math.floorDiv(nz, BLOCK) != bz)) {
               continue;
            }
            long n = pack(nx, nz);
            if (seen.add(n)) {
               queue.add(n);
            }
         }
      }
      return seen;
   }

   /** The smallest cell, by z then x. */
   private static long smallest(Set<Long> cells) {
      long best = 0L;
      boolean first = true;
      for (long c : cells) {
         if (first || (int)(c >> 32) < (int)(best >> 32) || (int)(c >> 32) == (int)(best >> 32) && (int)c < (int)best) {
            best = c;
            first = false;
         }
      }
      return best;
   }

   /** The set roll (0..1) of a landmass of several cells, from its canonical cell and the world seed. */
   static double roll(Mass m, long seed) {
      long h = seed ^ pack(m.x(), m.z()) * 0x9E3779B97F4A7C15L ^ (m.giant() ? 0x632BE59BD9B4E019L : 0L);
      h ^= h >>> 31;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 29;
      h *= 0x94D049BB133111EBL;
      h ^= h >>> 32;
      return (h >>> 11) * 0x1.0p-53;
   }
}

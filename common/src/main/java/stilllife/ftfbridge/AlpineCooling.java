package stilllife.ftfbridge;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * mountain_cooling of format 18 worlds: whether the alpine band of a column gets the climate cooled by mountain_cooling for
 * its biome. Yes only when both hold:
 * <ul>
 *   <li>the column's own (uncooled) snowy peaks band is a snow cap (biome tag stilllife_ftf_bridge:snow_caps), and</li>
 *   <li>the cooled alpine band lands in a cold alpine biome (stilllife_ftf_bridge:cold_alpine) that differs from the uncooled
 *       one, which is not one already.</li>
 * </ul>
 * So warm, dry mountains whose alpine band would hold desert scrub right under a snow cap get alpine tundra or barren
 * highlands there, and nothing else changes (Still Life's table also holds dry shrubland and savanna highlands in the
 * alpine band; cooling a tropical mountain would turn its tropical highlands drier, which this rule never does).
 *
 * <p>Worked out from the climate layout FreeTerraForged places biomes from (read when the layout is built, so a datapack
 * that changes the layout or the tags is followed), by Minecraft's own rule (the nearest climate box: squared distance
 * outside each parameter's range, plus the offset), on the layout's surface entries of the two mountain weirdness bands.
 * The answer is memoised per small climate cell (0.005 of temperature, 0.01 of humidity, 0.02 of continentalness and
 * erosion): a pure function of the layout, the same from any thread in any order. No per-sample lookup in the layout.
 */
final class AlpineCooling {
   static final TagKey<Biome> COLD_ALPINE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(StillLifeFtfBridge.MOD_ID, "cold_alpine"));
   static final TagKey<Biome> SNOW_CAPS = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(StillLifeFtfBridge.MOD_ID, "snow_caps"));
   /** The tags' contents as shipped, for a layout read before tags are bound. */
   private static final Set<String> DEFAULT_COLD = Set.of(
      "still_life:alpine_tundra", "still_life:boreal_alpine_tundra", "still_life:snowy_boreal_alpine_tundra", "still_life:cold_barren_highlands",
      "still_life:barren_mediterranean_highlands"
   );
   private static final Set<String> DEFAULT_CAPS = Set.of(
      "still_life:snowy_peaks", "still_life:snowy_painted_peaks", "still_life:snowy_barren_peaks", "minecraft:snowy_slopes", "minecraft:frozen_peaks",
      "minecraft:jagged_peaks"
   );
   /** Weirdness magnitudes the bridge gives the middle of the alpine band and the snowy peaks band. */
   private static final float ALPINE_W = 0.48F;
   private static final float PEAKS_W = 0.6F;

   private record Entry(long[] lo, long[] hi, long offset, int kind) {
   }

   /** Entry kinds: 0 other, 1 cold alpine, 2 snow cap. */
   private record Layout(Entry[] entries, ConcurrentHashMap<Long, Boolean> memo) {
   }

   private static volatile Layout layout;

   private AlpineCooling() {
   }

   /** The climate layout FreeTerraForged places biomes from (BridgeHooks.biomeLayout). */
   static void layout(List<Pair<Climate.ParameterPoint, Holder<Biome>>> all, HolderLookup.Provider lookups) {
      Set<String> cold = ids(lookups, COLD_ALPINE, DEFAULT_COLD);
      Set<String> caps = ids(lookups, SNOW_CAPS, DEFAULT_CAPS);
      long surface = Climate.quantizeCoord(0.0F);
      long alpineLow = Climate.quantizeCoord(0.4F);
      List<Entry> out = new ArrayList<>();
      for (Pair<Climate.ParameterPoint, Holder<Biome>> e : all) {
         Climate.ParameterPoint p = e.getFirst();
         if (p.depth().min() > surface || p.depth().max() < surface) {
            continue;
         }
         // only boxes reaching the mountain bands (|weirdness| from 0.4)
         if (p.weirdness().max() < alpineLow && p.weirdness().min() > -alpineLow) {
            continue;
         }
         String id = e.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("");
         int kind = cold.contains(id) ? 1 : caps.contains(id) ? 2 : 0;
         List<Climate.Parameter> ps = List.of(p.temperature(), p.humidity(), p.continentalness(), p.erosion(), p.depth(), p.weirdness());
         long[] lo = new long[6];
         long[] hi = new long[6];
         // temperature, humidity, continentalness, erosion, depth, weirdness
         for (int k = 0; k < 6; k++) {
            lo[k] = ps.get(k).min();
            hi[k] = ps.get(k).max();
         }
         out.add(new Entry(lo, hi, p.offset(), kind));
      }
      layout = out.isEmpty() ? null : new Layout(out.toArray(new Entry[0]), new ConcurrentHashMap<>());
      StillLifeFtfBridge.LOG.info("[{}] mountain cooling: {} mountain climate boxes, {} cold alpine and {} snow-cap biomes", StillLifeFtfBridge.MOD_ID,
         out.size(), cold.size(), caps.size());
   }

   private static Set<String> ids(HolderLookup.Provider lookups, TagKey<Biome> tag, Set<String> fallback) {
      try {
         if (lookups != null) {
            java.util.Optional<HolderSet.Named<Biome>> set = lookups.lookupOrThrow(Registries.BIOME).get(tag);
            if (set.isPresent()) {
               Set<String> out = new HashSet<>();
               for (Holder<Biome> h : set.get()) {
                  h.unwrapKey().ifPresent(k -> out.add(k.location().toString()));
               }
               return out;
            }
         }
      } catch (Throwable t) {
         StillLifeFtfBridge.LOG.warn("[{}] could not read biome tag {}; using its default contents", StillLifeFtfBridge.MOD_ID, tag.location(), t);
         return fallback;
      }
      StillLifeFtfBridge.LOG.warn("[{}] biome tag {} is not available while the biome layout is built; using its default contents", StillLifeFtfBridge.MOD_ID, tag.location());
      return fallback;
   }

   /**
    * True when the alpine band of a column with this climate (Still Life's scale; weirdness sign of the column) takes the
    * temperature cooled by `cooling`. False without a layout (none read yet: no cooling).
    */
   static boolean cools(float t, float h, float c, float e, boolean positive, double cooling) {
      Layout l = layout;
      if (l == null || cooling <= 0.0) {
         return false;
      }
      int tb = (int)Math.round(clamp(t, -1.2, 1.2) / 0.005) + 300;
      int hb = (int)Math.round(clamp(h, -1.2, 1.2) / 0.01) + 150;
      int cb = (int)Math.round(clamp(c, -1.2, 1.2) / 0.02) + 70;
      int eb = (int)Math.round(clamp(e, -1.2, 1.2) / 0.02) + 70;
      int cl = (int)Math.round(cooling * 1000.0);
      long key = ((((long)tb * 512 + hb) * 256 + cb) * 256 + eb) * 2048 + cl;
      key = key * 2 + (positive ? 1 : 0);
      Boolean r = l.memo.get(key);
      if (r == null) {
         float tc = (float)((tb - 300) * 0.005);
         float hc = (float)((hb - 150) * 0.01);
         float cc = (float)((cb - 70) * 0.02);
         float ec = (float)((eb - 70) * 0.02);
         float s = positive ? 1.0F : -1.0F;
         boolean yes = false;
         if (nearest(l, tc, hc, cc, ec, s * PEAKS_W).kind == 2) {
            Entry base = nearest(l, tc, hc, cc, ec, s * ALPINE_W);
            if (base.kind != 1) {
               Entry cooled = nearest(l, (float)(tc - cooling), hc, cc, ec, s * ALPINE_W);
               yes = cooled.kind == 1;
            }
         }
         r = yes;
         if (l.memo.size() < 2_000_000) {
            l.memo.put(key, r);
         }
      }
      return r;
   }

   /** Minecraft's nearest climate box (squared distance outside each range, plus offset squared); first in order on ties. */
   private static Entry nearest(Layout l, float t, float h, float c, float e, float w) {
      long[] v = {Climate.quantizeCoord(t), Climate.quantizeCoord(h), Climate.quantizeCoord(c), Climate.quantizeCoord(e), Climate.quantizeCoord(0.0F),
         Climate.quantizeCoord(w)};
      Entry best = null;
      long bestD = Long.MAX_VALUE;
      for (Entry en : l.entries) {
         long d = en.offset * en.offset;
         for (int k = 0; k < 6 && d < bestD; k++) {
            long x = v[k] < en.lo[k] ? en.lo[k] - v[k] : v[k] > en.hi[k] ? v[k] - en.hi[k] : 0L;
            d += x * x;
         }
         if (d < bestD) {
            bestD = d;
            best = en;
         }
      }
      return best;
   }

   private static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : Math.min(v, hi);
   }
}

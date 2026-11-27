package stilllife.ftfbridge;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * coastal_biomes (format 20): Still Life's coast-only biomes on low coastal land.
 *
 * <p>Still Life has biomes it only places near the sea (continentalness up to 0.17, about 370 blocks): the coastal
 * temperate rainforests, pine woods, coastal xeric shrubland and others. All of them sit in its weirdness band 0.08-0.267,
 * the band above the beach band (0.05-0.08). On Lithosphere weirdness makes the terrain, so that band is most of the
 * coastal land at any height. The bridge reads weirdness back from the height, by the land's height percentiles over the
 * whole world, and on FreeTerraForged the land near the sea is the lowest land there is: nearly all of it falls in the
 * beach band, where Still Life's table only has the inland biome. So a coastal rainforest showed up only where the land
 * climbs steeply out of the sea (above Still Life height 76; Y 138 on a tall preset): a strip under coastal mountains,
 * nothing along a gentle coast.
 *
 * <p>The rule: land in the beach band that is past the beach itself (continentalness from 0.02) takes the band above when
 * the biome it gets there is one of those coast-only biomes; everywhere else nothing changes. "Coast-only" is read from
 * the climate layout FreeTerraForged places biomes from (a surface entry of the band above whose continentalness ends
 * below 0.2), by Minecraft's nearest-box rule, memoised per small climate cell as in {@link AlpineCooling}: a pure
 * function of the layout.
 */
final class CoastalBiomes {
   /** The weirdness magnitude the bridge gives land lifted out of the beach band (StillLifeClimateBridge.ABOVE_BEACH_W). */
   private static final float BAND_W = 0.09F;
   private static final float COAST_ONLY_BELOW = 0.2F;

   private record Entry(long[] lo, long[] hi, long offset, boolean coastOnly) {
   }

   private record Layout(Entry[] entries, ConcurrentHashMap<Long, Boolean> memo) {
   }

   private static volatile Layout layout;

   private CoastalBiomes() {
   }

   /** The climate layout FreeTerraForged places biomes from (BridgeHooks.biomeLayout). */
   static void layout(List<Pair<Climate.ParameterPoint, Holder<Biome>>> all) {
      long surface = Climate.quantizeCoord(0.0F);
      long band = Climate.quantizeCoord(BAND_W);
      long limit = Climate.quantizeCoord(COAST_ONLY_BELOW);
      List<Entry> out = new ArrayList<>();
      int coast = 0;
      for (Pair<Climate.ParameterPoint, Holder<Biome>> e : all) {
         Climate.ParameterPoint p = e.getFirst();
         if (p.depth().min() > surface || p.depth().max() < surface) {
            continue;
         }
         // only boxes reaching the band above the beach band, on either side of zero
         boolean positive = p.weirdness().min() <= band && p.weirdness().max() >= band;
         boolean negative = p.weirdness().min() <= -band && p.weirdness().max() >= -band;
         if (!positive && !negative) {
            continue;
         }
         List<Climate.Parameter> ps = List.of(p.temperature(), p.humidity(), p.continentalness(), p.erosion(), p.depth(), p.weirdness());
         long[] lo = new long[6];
         long[] hi = new long[6];
         for (int k = 0; k < 6; k++) {
            lo[k] = ps.get(k).min();
            hi[k] = ps.get(k).max();
         }
         boolean coastOnly = p.continentalness().max() < limit;
         coast += coastOnly ? 1 : 0;
         out.add(new Entry(lo, hi, p.offset(), coastOnly));
      }
      layout = out.isEmpty() ? null : new Layout(out.toArray(new Entry[0]), new ConcurrentHashMap<>());
      StillLifeFtfBridge.LOG.info("[{}] coastal biomes: {} climate boxes in the band above the beach, {} of them coast-only", StillLifeFtfBridge.MOD_ID,
         out.size(), coast);
   }

   /**
    * True when land with this climate (Still Life's scale; weirdness sign of the column) gets a coast-only biome in the band
    * above the beach band. False without a layout.
    */
   static boolean lifts(float t, float h, float c, float e, boolean positive) {
      Layout l = layout;
      if (l == null) {
         return false;
      }
      int tb = (int)Math.round(clamp(t, -1.2, 1.2) / 0.005) + 300;
      int hb = (int)Math.round(clamp(h, -1.2, 1.2) / 0.005) + 300;
      int cb = (int)Math.round(clamp(c, -1.2, 1.2) / 0.005) + 300;
      int eb = (int)Math.round(clamp(e, -1.2, 1.2) / 0.02) + 70;
      long key = ((((long)tb * 1024 + hb) * 1024 + cb) * 256 + eb) * 2 + (positive ? 1 : 0);
      Boolean r = l.memo.get(key);
      if (r == null) {
         long[] v = {Climate.quantizeCoord((float)((tb - 300) * 0.005)), Climate.quantizeCoord((float)((hb - 300) * 0.005)),
            Climate.quantizeCoord((float)((cb - 300) * 0.005)), Climate.quantizeCoord((float)((eb - 70) * 0.02)), Climate.quantizeCoord(0.0F),
            Climate.quantizeCoord(positive ? BAND_W : -BAND_W)};
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
         r = best != null && best.coastOnly;
         if (l.memo.size() < 2_000_000) {
            l.memo.put(key, r);
         }
      }
      return r;
   }

   private static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : Math.min(v, hi);
   }
}

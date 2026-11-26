package stilllife.ftfbridge;

/**
 * The arithmetic of mountain_snow's one snow field, free of Minecraft types (so it can be run on synthetic terrain).
 *
 * <p>Every column gets a <b>keep</b> value (0..1, roughly uniform over a landscape): how sheltered it is, from
 * <ul>
 *   <li>the shape of the land at three scales (the ground 2-4, 6-12 and 16-32 blocks around against the column: gullies,
 *       couloirs, cirques and benches hold snow, ribs, spurs and ridges lose it). This dominates;</li>
 *   <li>the sun: direct light on the slope from a sun low in the south (in the north south of Still Life's equator, high
 *       near it), slightly west of south (afternoon sun is the warm one). Continuous in the slope normal, so north faces
 *       of any steepness shade by degrees, steep north faces most;</li>
 *   <li>the wind: how far the ground upwind rises above the column (a horizon angle along the wind): slopes facing into
 *       it and crests are scoured, lee slopes and the ground behind crests are loaded. The wind turns smoothly between
 *       Still Life's latitude belts and meanders by region;</li>
 *   <li>avalanche runout: gentle ground below a long steep slope keeps snow longest;</li>
 *   <li>noise, for the ragged edge only: smeared down the fall line, so patches are tongues and streaks running down the
 *       slope, not round blobs; plus a fine grain.</li>
 * </ul>
 * A column holds snow when keep is at least 1 - coverage (coverage: the share of the ground holding snow at this height in
 * this climate), so the margin keep - (1 - coverage) is the score. The snow depth grows with the margin: thin at the edge
 * of a patch, deep inside, and deeper in hollows and avalanche runouts. Both the snow ground and the snow layers come
 * from that one depth, so they agree.
 */
final class SnowModel {
   /** Margin of the height window read around a chunk, and the window's width. */
   static final int R = 32;
   static final int W = 16 + 2 * R;

   /** Depth (in layers, 8 = one block) from which the ground itself is snow (a snow block), with natural snow layers. */
   static final float BLOCK_DEPTH = 4.5F;

   private static final int[] RING4 = ring(4);
   private static final int[] RING12 = ring(12);
   private static final int[] RING32 = ring(32);

   private SnowModel() {
   }

   /** Window index offsets of 8 points at radius r and 8 at r/2 (turned by 22.5 degrees). */
   private static int[] ring(int r) {
      int[] out = new int[16];
      for (int k = 0; k < 8; k++) {
         double a = k * Math.PI / 4.0;
         out[k] = (int)Math.round(Math.sin(a) * r) * W + (int)Math.round(Math.cos(a) * r);
         double b = a + Math.PI / 8.0;
         out[8 + k] = (int)Math.round(Math.sin(b) * r * 0.5) * W + (int)Math.round(Math.cos(b) * r * 0.5);
      }
      return out;
   }

   /** Mean of the ring around index c against h0 (each point clamped to h0 +- clamp, so a cliff counts like a slope). */
   private static float ringMean(float[] h, int c, float h0, int[] ring, float clamp) {
      float s = 0.0F;
      for (int k = 0; k < ring.length; k++) {
         s += Math.max(h0 - clamp, Math.min(h0 + clamp, h[c + ring[k]]));
      }
      return s / ring.length - h0;
   }

   /** Slots of the aux array keep() fills. */
   static final int AUX_HOLLOW = 0;
   static final int AUX_DEBRIS = 1;
   static final int AUX_SLOPE = 2;
   /** Sun on the slope (0..1): 0.5 on flat ground, 0 in full shade, 1 at twice the light of flat ground or more. */
   static final int AUX_SUN = 3;
   /** Wind exposure (-1..1): + slopes facing into the wind and crests (scoured), - lee slopes (loaded). */
   static final int AUX_WIND = 4;
   /** Shelter by the land's shape (-1..1): + gullies, couloirs, cirques, benches; - ribs, spurs, ridges. */
   static final int AUX_SHELTER = 5;
   /** Relief (blocks): how far the column stands above the lowest of the ground 12 and 32 blocks around it. */
   static final int AUX_RELIEF = 6;
   static final int AUX_SIZE = 7;

   /**
    * Shelter of one column (0..1). h: terrain heights (blocks, float) of a W x W window, (cx, cz) the column's place in it
    * (at least R from the window's edges), (x, z) its world position. snow: climateSnow there. latSigned: distance from the
    * equator in pole distances, + south of it, NaN without latitudes. aux receives the small-scale hollow (blocks), the
    * avalanche runout share (0..1), the local slope, and the sun, wind and shelter terms on fixed scales (AUX_SUN,
    * AUX_WIND, AUX_SHELTER; read-outs only, they don't change the result).
    */
   static float keep(float[] h, int cx, int cz, int x, int z, long seed, float snow, double latSigned, float[] aux) {
      return keep(h, cx, cz, x, z, seed, snow, latSigned, aux, false);
   }

   /**
    * keep(); with curve (format 18, the snow-line curve) the terrain decides over the noise, so through the transition zone
    * gullies, couloirs, avalanche runouts, lee and shaded hollows fill first and open slopes, convex shoulders and windward
    * crests last: shape 1.3, shade 0.8, wind 1.0, couloir 0.7, runout 0.6, steep -0.9, convex shoulders an extra -0.4, the
    * noise (smeared further down the fall line, 12 blocks) 0.25 and the grain 0.05 (instead of 0.55 and 0.12), gain 0.75.
    */
   static float keep(float[] h, int cx, int cz, int x, int z, long seed, float snow, double latSigned, float[] aux, boolean curve) {
      int c = cz * W + cx;
      float h0 = h[c];

      // slope normal over the land's shape (3 and 8 blocks) and the local slope (2 blocks)
      float gx = 0.5F * ((h[c + 3] - h[c - 3]) / 6.0F + (h[c + 8] - h[c - 8]) / 16.0F);
      float gz = 0.5F * ((h[c + 3 * W] - h[c - 3 * W]) / 6.0F + (h[c + 8 * W] - h[c - 8 * W]) / 16.0F);
      float g = (float)Math.sqrt(gx * gx + gz * gz);
      float lx = (h[c + 2] - h[c - 2]) / 4.0F;
      float lz = (h[c + 2 * W] - h[c - 2 * W]) / 4.0F;
      float local = (float)Math.sqrt(lx * lx + lz * lz);

      // shape of the land at three scales: + hollow, - ridge (blocks)
      float c4 = ringMean(h, c, h0, RING4, 4.0F);
      float c12 = ringMean(h, c, h0, RING12, 12.0F);
      float c32 = ringMean(h, c, h0, RING32, 32.0F);
      float shape = 0.5F * tanh(c4 / 1.2F) + 0.75F * tanh(c12 / 3.5F) + 0.55F * tanh(c32 / 10.0F);

      // sun
      double absLat = Double.isNaN(latSigned) ? 0.45 : Math.min(1.0, Math.abs(latSigned));
      double elevation = Math.toRadians(18.0 + 52.0 * Math.pow(1.0 - absLat, 1.5));
      double hemisphere = Double.isNaN(latSigned) ? 1.0 : (latSigned > 0.0 ? -1.0 : 1.0) * smoothstep(0.0, 0.06, absLat);
      double sy = Math.sin(elevation);
      double sh = Math.cos(elevation);
      double sx = -sh * 0.26; // a little west of south
      double sz = sh * 0.966 * hemisphere;
      double light = (sy - gx * sx - gz * sz) / Math.sqrt(1.0 + gx * gx + gz * gz);
      float shade = (float)((sy - Math.max(0.0, light)) / sy); // 0 on flat ground, up to 1 in full shade, < 0 facing the sun

      // wind: direction it blows towards; the belts of windFromWest, turning smoothly, and a slow regional meander
      double belt = Double.isNaN(latSigned) ? 1.0 : smoothstep(0.24, 0.36, absLat) - smoothstep(0.59, 0.71, absLat);
      double theta = Math.PI * (1.0 - belt) + 0.6 * noise(seed, x / 1800.0, z / 1800.0, 211);
      float wx = (float)Math.cos(theta);
      float wz = (float)Math.sin(theta);
      float horizon = -10.0F;
      for (int t : HORIZON) {
         int i = c - Math.round(wz * t) * W - Math.round(wx * t);
         horizon = Math.max(horizon, (h[i] - h0) / t);
      }
      float windStrength = 0.3F + 0.3F * clamp01(1.6F - snow);
      float wind = windStrength * tanh(horizon / 0.35F);
      if (c12 < 0.0F && horizon < 0.0F) {
         wind += 0.25F * tanh(c12 / 4.0F) * windStrength; // exposed crests facing the wind: scoured hardest
      }

      // fall line: downslope direction, and how much patches stretch along it
      float dx = g > 1.0E-4F ? -gx / g : 0.0F;
      float dz = g > 1.0E-4F ? -gz / g : 0.0F;
      float stretch = (float)smoothstep(0.12, 0.6, g);

      // avalanche runout: gentle ground with a long steep slope above it
      int up = c - Math.round(dz * 12.0F) * W - Math.round(dx * 12.0F);
      float ux = (h[up + 4] - h[up - 4]) / 8.0F;
      float uz = (h[up + 4 * W] - h[up - 4 * W]) / 8.0F;
      float debris = (float)(smoothstep(0.75, 1.3, Math.sqrt(ux * ux + uz * uz)) * (1.0 - smoothstep(0.35, 0.7, g)));

      // a couloir: a steep gully
      float couloir = (float)smoothstep(0.45, 1.0, g) * clamp01(c12 / 2.5F);
      // very steep ground sheds snow (rock faces are judged from the real blocks: face(); this only thins it)
      float steep = (float)smoothstep(1.1, 2.0, local);

      // noise: smeared down the fall line (tongues and streaks), plus a fine grain for the edge
      float n = 0.0F;
      float step = (curve ? 12.0F : 8.0F) * stretch;
      for (int j = -2; j <= 2; j++) {
         double px = x + j * step * dx;
         double pz = z + j * step * dz;
         n += LIC[j + 2] * (0.6F * noise(seed, px / 9.0, pz / 9.0, 101) + 0.4F * noise(seed, px / 23.0, pz / 23.0, 103));
      }
      float grain = 0.6F * noise(seed, x / 4.5, z / 4.5, 107) + 0.4F * noise(seed, x / 2.2, z / 2.2, 109);

      float sum = shape + 0.5F * shade + wind + 0.3F * couloir + 0.35F * debris - 0.4F * steep + 0.55F * n + 0.12F * grain;
      if (aux != null) {
         aux[AUX_HOLLOW] = c4;
         aux[AUX_DEBRIS] = debris;
         aux[AUX_SLOPE] = local;
         aux[AUX_SUN] = clamp01((float)(0.5 * Math.max(0.0, light) / sy));
         aux[AUX_WIND] = Math.max(-1.0F, Math.min(1.0F, -wind / windStrength));
         aux[AUX_SHELTER] = shape / 1.8F;
         float low = h0;
         for (int k = 0; k < RING32.length; k++) {
            low = Math.min(low, Math.min(h[c + RING32[k]], h[c + RING12[k]]));
         }
         aux[AUX_RELIEF] = h0 - low;
      }
      if (curve) {
         float convex = Math.min(0.0F, tanh(c32 / 10.0F)) * 0.4F;
         float terrain = 1.3F * shape + 0.8F * shade + 1.0F * wind + 0.7F * couloir + 0.6F * debris - 0.9F * steep + convex;
         return 0.5F + 0.5F * tanh(0.75F * (terrain + 0.25F * n + 0.05F * grain));
      }
      return 0.5F + 0.5F * tanh(0.9F * sum);
   }

   private static final int[] HORIZON = {4, 9, 16, 26};
   private static final float[] LIC = {0.12F, 0.22F, 0.32F, 0.22F, 0.12F};

   /**
    * Share of the ground holding snow (0..1) d blocks above (+) or below (-) where Still Life's snowy peaks begin here, in a
    * climate of this snowiness (climateSnow), at Still Life's temperature temp (at sea level; NaN when not known), above
    * blocks above sea level. Each place has its own snow line: raised in dry climates and lowered in wet ones (by up to
    * about 80 blocks). Just above it the share is set by the climate (about 0.4 where arid, 0.8 normally, nearly all where
    * wet) and it rises further up towards nearly everything (0.92) 120 blocks higher (less so where arid), so high summits
    * are white but for their steep faces and wind-scoured crests; below the line it falls off over the reach.
    *
    * <p>Towards the pole a second line, the {@link #polarLine polar snow line}, comes down to the sea where Still Life's own
    * permanently snowy lowland begins (its arctic biomes, temperature {@link #POLAR_T} and colder; their ground itself is
    * Still Life's, see MountainSnow.keepsOwnSurface). Towards the warmer side it rises quickly. Below it the share is light
    * patches (at most {@link #POLAR_PATCHES}, about what Still Life's own arctic ground shows), so the cover thins out
    * over the coldest part of the tundra band and the tundra itself keeps its own ground; above it the share rises to
    * that of a high summit (0.92) over 40 blocks, so high ground in tundra latitudes is white. Beyond the tundra band
    * ({@link #POLAR_END}) it climbs out of the way.
    */
   static double coverage(double d, float snow, float temp, double above) {
      double lineShift = (1.0 - snow) * 110.0;
      double dEff = d - lineShift;
      double top = Math.max(0.3, Math.min(0.92, 0.2 + 0.6 * snow));
      double share;
      if (dEff >= 0.0) {
         // never quite all: the most exposed crests and ribs stay scoured however high
         share = top + (0.92 - top) * clamp01(0.4F + 0.6F * snow) * smoothstep(0.0, 120.0, dEff);
      } else {
         double reach = REACH * Math.min(1.3, snow);
         double u = Math.max(0.0, 1.0 + dEff / reach);
         share = top * u * u;
      }
      if (!Float.isNaN(temp)) {
         double dp = above - polarLine(temp, snow);
         double polar;
         if (dp >= 0.0) {
            polar = POLAR_PATCHES + (0.92 - POLAR_PATCHES) * smoothstep(0.0, 40.0, dp);
         } else {
            double u = Math.max(0.0, 1.0 + dp / (POLAR_REACH * Math.min(1.3, snow)));
            polar = POLAR_PATCHES * u * u;
         }
         share = Math.max(share, polar);
      }
      return share;
   }

   /**
    * coverage() for worlds whose snow line comes from the snow-line curve (format 18): d is the height above (+) or below (-)
    * that line here (aspect shift included; the line already reaches the sea at the cold edge of the tundra), standing how
    * far the column stands above its valley floor (the lowest ground within about 96 blocks), slope its slope over 8 blocks.
    * <ul>
    *   <li>A transition zone, not a step (curveShare): from none 170 blocks below the climate-shifted line (wet climates'
    *       tongues further) to the summit share 240 above it; below the line only on real mountain ground. No height
    *       limit: tongues go as far down as the mountain sends them.</li>
    *   <li>Gentle ground keeps the cover of older worlds (gentleWeight): gentle lowland by relief and slope below the zone,
    *       and in cold country (temperature from -0.4 down) gentle uplands whatever the line: none, up to light patches
    *       (curvePatches) from {@link #polarLine} up, which reaches the sea only where Still Life's arctic begins.</li>
    * </ul>
    * above: blocks above the sea.
    */
   static double coverageCurve(double d, float snow, float temp, double above, double standing, double slope) {
      // the mountain part only: the light patches of the gentle ground near the arctic are laid separately as coherent
      // patches (curvePatches, decided by SnowField with a patch noise, not the fine-grained shelter)
      return curveShare(d, snow, standing) * (1.0 - gentleWeight(d, snow, temp, standing, slope));
   }

   /**
    * The light patches of coverageCurve's gentle ground (the share of it holding snow, 0..POLAR_PATCHES): from the
    * {@link #polarLine polar line} up, where gentle lowland and uplands near Still Life's arctic take some of its snow.
    * Laid by SnowField with {@link #patch}, a smooth noise drawn like Still Life's own big arctic snow patches (tens of
    * blocks across), so the tundra's edge towards the arctic gets patches in the arctic's own style rather than a
    * speckle from the shelter's fine grain (which read as a seam against Still Life's big patches at the biome edge).
    */
   static double curvePatches(double d, float snow, float temp, double above, double standing, double slope) {
      if (Float.isNaN(temp)) {
         return 0.0;
      }
      double w = gentleWeight(d, snow, temp, standing, slope);
      if (w <= 0.0) {
         return 0.0;
      }
      double patches = POLAR_PATCHES * smoothstep(0.0, 40.0, above - polarLine(temp, snow));
      return Math.min(curveShare(d, snow, standing), patches) * w;
   }

   /** Zone of the curve (format 18): full cover ZONE_TOP blocks above the climate-shifted line, none ZONE_BOTTOM below. */
   static final double ZONE_BOTTOM = -170.0;
   static final double ZONE_TOP = 240.0;
   /** The most wet climates lower the line by, and how much longer their tongues reach per unit of snowiness above 1. */
   static final double WET_LOWERING = 25.0;
   static final double WET_TAIL = 40.0;

   /** Blocks by which this climate's snowiness moves the line (+ up where dry or warm, - down where wet, at most 25). */
   static double lineShift(float snow) {
      return Math.max(-WET_LOWERING, (1.0 - snow) * 110.0);
   }

   /** The share of ground a high summit holds in this climate (0.45 arid to 0.94 wet). */
   static double summitShare(float snow) {
      return Math.max(0.45, Math.min(0.94, 0.58 + 0.34 * snow));
   }

   /**
    * coverageCurve's share before the gentle-ground rule: a transition zone rather than a step. From none ZONE_BOTTOM below
    * the climate-shifted line (wet climates' tongues reaching further) to the summit share ZONE_TOP above it, along a
    * smoothstep: about 40% at the line in a normal climate, near full only on summits standing well above it. Below the
    * line only on real mountain ground: the further below, the higher the ground must stand above its valley floor (0.4
    * blocks per block below the line, soft over 40), so tongues run down gullies of real mountains and fade out there.
    * Which columns of the share hold snow is the shelter's business (keep, with curve): gullies, lee and shaded hollows
    * first, open slopes next, convex and windward ground last.
    */
   private static double curveShare(double d, float snow, double standing) {
      double dEff = d - lineShift(snow);
      double lo = ZONE_BOTTOM - WET_TAIL * Math.max(0.0, snow - 1.0);
      double share = zoneShare(dEff, lo, summitShare(snow));
      if (dEff < 0.0) {
         double need = 0.4 * -dEff;
         share *= smoothstep(need - 20.0, need + 20.0, standing);
      }
      return share;
   }

   /**
    * The zone's share at dEff blocks above the climate-shifted line: the summit share along a smoothstep from lo to ZONE_TOP,
    * and above the line only, a little more: the rest of the way to the summit share closes faster (UPPER_FILL of it by
    * about 160 above the line), and high summits get up to UPPER_EXTRA beyond it. Both start at zero with zero slope at
    * the line, so nothing changes from the line down and no edge appears there.
    */
   static double zoneShare(double dEff, double lo, double summit) {
      double base = smoothstep(lo, ZONE_TOP, dEff);
      if (dEff <= 0.0) {
         return summit * base;
      }
      base += UPPER_FILL * smoothstep(0.0, 160.0, dEff) * (1.0 - base);
      double share = Math.min(1.0, summit * base + UPPER_EXTRA * smoothstep(100.0, 260.0, dEff));
      // high ground converges to near-full cover in every climate (little melts up there, even where it is dry): from
      // HIGH_FROM above the line to HIGH_TO the share closes on HIGH_SHARE
      share += Math.max(0.0, HIGH_SHARE - share) * smoothstep(HIGH_FROM, HIGH_TO, dEff);
      // never all of it: the worst-placed ground (windward crests, convex ribs) stays dark however high
      return Math.min(HIGH_SHARE, share);
   }

   /** Where (blocks above the climate-shifted line) and to what share high ground converges, whatever the climate. */
   static final double HIGH_FROM = 90.0;
   static final double HIGH_TO = 210.0;
   static final double HIGH_SHARE = 0.95;
   /** Blocks above the climate-shifted line over which the cold-country gentle-upland rule fades out. */
   static final double COLD_UPLAND_FADE_FROM = 60.0;
   static final double COLD_UPLAND_FADE_TO = 160.0;
   /** Coverage over which snow on high ground is deep however near the edge of the patch (format 18). */
   static final double DEEP_FROM = 0.78;
   static final double DEEP_TO = 0.93;

   /** Above the line: the share of the remaining way to the summit share filled early, and the extra on high summits. */
   static final double UPPER_FILL = 0.5;
   static final double UPPER_EXTRA = 0.06;

   /**
    * Blocks above the snow line (the curve's, before the climate shift) where the zone's base smoothstep (without the
    * upper fill of zoneShare, so the bands stay where the first P4 build put them) reaches this share in a climate of this
    * snowiness; NaN when it never does. For where the snowy peaks biome begins (StillLifeClimateBridge.bandLine).
    */
   static double coverHeight(double share, float snow) {
      double lo = ZONE_BOTTOM - WET_TAIL * Math.max(0.0, snow - 1.0);
      double target = share / summitShare(snow);
      if (target >= 1.0) {
         return Double.NaN;
      }
      double a = lo, b = ZONE_TOP;
      for (int k = 0; k < 40; k++) {
         double mid = 0.5 * (a + b);
         if (smoothstep(lo, ZONE_TOP, mid) < target) {
            a = mid;
         } else {
            b = mid;
         }
      }
      return 0.5 * (a + b) + lineShift(snow);
   }

   /**
    * How much the column is gentle ground that keeps the cover of older worlds (0..1).
    * <ul>
    *   <li>Gentle lowland by the land itself, not by height: ground standing little above its valley floor (25 to 70
    *       blocks) and gentle (slope over 8 blocks 0.35 to 0.7), below the zone (from 20 above the line down, full 60
    *       below it): so snow runs down real mountains as far as the model sends it, while valley floors, plains and
    *       gentle foothills stay as Still Life makes them, and a gentle plateau high above the line is a snowfield.</li>
    *   <li>In cold country (temperature from -0.4 down) gentle uplands too (standing under 30 to 90), whatever the line:
    *       only real mountains follow the low line there.</li>
    * </ul>
    */
   private static double gentleWeight(double d, float snow, float temp, double standing, double slope) {
      double dEff = d - lineShift(snow);
      double w = (1.0 - smoothstep(25.0, 70.0, standing)) * (1.0 - smoothstep(0.35, 0.7, slope)) * (1.0 - smoothstep(-60.0, 20.0, dEff));
      if (!Float.isNaN(temp) && temp < GENTLE_CAP_FROM) {
         // gentle uplands of cold country near the line, not the high ground of a massif: a basin or plateau standing
         // little above the lowest ground around it but far above the line is high mountain (it fades out from 60 to
         // 160 blocks above the line; before, whole high massifs at cold-taiga latitudes were bare)
         w = Math.max(w, smoothstep(GENTLE_CAP_FROM, GENTLE_CAP_FULL, temp) * (1.0 - smoothstep(30.0, 90.0, standing))
            * (1.0 - smoothstep(COLD_UPLAND_FADE_FROM, COLD_UPLAND_FADE_TO, dEff)));
      }
      return w;
   }

   /** Aspect shift (blocks the column counts as higher: + in shade and lee, - on sunny and windward ground; 24 at most). */
   static double aspectShift(float sun, float wind) {
      return 12.0 * (1.0 - 2.0 * sun) - 12.0 * wind;
   }

   /**
    * 0..1 patch noise for the polar patches, coherent over tens of blocks with a ragged edge (three octaves at 56, 20
    * and 7 blocks), on a lattice turned per salt so it has no axis grain.
    */
   static float patch(long seed, int x, int z) {
      float n = 0.62F * noise(seed, x / 56.0, z / 56.0, 141) + 0.28F * noise(seed, x / 20.0, z / 20.0, 143)
         + 0.10F * noise(seed, x / 7.0, z / 7.0, 145);
      return clamp01(0.5F + 0.75F * n);
   }

   /** Temperatures over which the tundra band's gentle-ground cap fades in (none above the first, full below the second). */
   static final float GENTLE_CAP_FROM = -0.4F;
   static final float GENTLE_CAP_FULL = -0.5F;

   /**
    * Still Life's temperature from which its lowland is permanently snowy: its arctic biomes (arctic desert, barren arctic
    * desert, glaciers, glacial basins, arctic wetlands, beaches and rivers; base temperatures -0.9 to -2, snow ground in
    * their surface rules) take Still Life's temperature axis below -0.8; tundra (-0.8 to -0.56) and taiga are warmer.
    */
   static final float POLAR_T = -0.8F;
   /** The share of the polar line's patches at the line itself (Still Life's own arctic lowland shows about 50-65% snow). */
   static final double POLAR_PATCHES = 0.5;
   /** How fast the polar snow line rises above the sea towards the warm side (blocks per unit of Still Life's temperature). */
   static final double POLAR_RISE = 3000.0;
   /** How far below the polar snow line its patches reach (blocks, before the climate scales it). */
   static final double POLAR_REACH = 40.0;

   /**
    * The polar snow line here, in blocks above sea level: at the sea where Still Life's temperature is POLAR_T, below it
    * where colder, rising POLAR_RISE blocks per unit of temperature towards the warm side (60 blocks at -0.78, just inside
    * the tundra band; 150 at -0.75; 300 at -0.7, its middle); a little higher where dry, lower where wet. Still Life's
    * temperature varies regionally on top of the latitude, so the boundary wanders as its arctic biomes do.
    */
   static double polarLine(float temp, float snow) {
      double line = (temp - POLAR_T) * POLAR_RISE;
      if (temp > POLAR_END) {
         // beyond the tundra band it climbs out of the way: the ordinary snow line rules the mountains of warmer lands
         line += (temp - POLAR_END) * 4.0 * POLAR_RISE;
      }
      return line + (1.0 - snow) * 30.0;
   }

   /** Still Life's warm edge of the tundra band (where its taiga begins). */
   static final float POLAR_END = -0.56F;

   /** How far below the snow line leftover snow reaches (blocks, before the climate scales it). */
   static final double REACH = 120.0;

   /** Score: at least 0 where the column holds snow. */
   static double score(float keep, double coverage) {
      return coverage <= 0.0 ? -1.0 : keep - (1.0 - coverage);
   }

   /**
    * Snow depth in layers (8 = a block) for a score: 0 where bare, growing from the edge of a patch inwards to 5-12 layers
    * (more in snowy climates); deeper in small hollows and avalanche runouts.
    */
   static float depth(double score, float snow, float hollow, float debris) {
      return depth(score, snow, hollow, debris, 0.0F);
   }

   /**
    * The same, with wind loading: lee (0..1, the AUX_WIND read-out turned round: how much the ground upwind shelters the
    * column) piles snow deep behind crests and on lee slopes, right up to the edge where the windward side is bare.
    */
   static float depth(double score, float snow, float hollow, float debris, float lee) {
      if (score < 0.0) {
         return 0.0F;
      }
      double max = 5.0 + 7.0 * clamp01((snow - 0.4F) / 1.1F);
      double d = max * smoothstep(0.0, 0.2, score);
      d += smoothstep(0.0, 0.06, score) * (3.0 * clamp01(hollow / 2.0F) + 4.0 * debris + 4.0 * clamp01(lee));
      return (float)d;
   }

   // ---------------------------------------------------------------- rock faces, from the real ground

   /** Not part of a face (gentle ground, including hillsides of 1-block steps 2 or more blocks apart). */
   static final int OPEN = 0;
   /** A ledge on a face: ground flat over 3 x 3 (at most 1 block of relief) with a wall at least 2 blocks high behind. */
   static final int LEDGE = 1;
   /** A face about 1:1: a staircase of 1-wide treads and 1-block risers. */
   static final int FACE = 2;
   /** A steeper face: about 60 degrees and more, or risers of 2 blocks and more on steep ground. */
   static final int CLIFF = 3;

   /** Slope (blocks per block, from the real blocks) from which ground is a face, and a cliff. */
   static final float FACE_SLOPE = 0.9F;
   static final float CLIFF_SLOPE = 1.7F;

   /** Depth (layers) from which a FACE is snow ground (it gets no layers: thin snow on it is either a block or nothing). */
   static final float FACE_BLOCK_DEPTH = 2.0F;

   /**
    * What the real ground (block heights, a GSIZE x GSIZE window) makes of the column at (cx, cz): OPEN, LEDGE, FACE or CLIFF.
    * Judged from the blocks, not from FTF's smooth slope (a face of 1-wide treads is a face whatever FTF's gradient says):
    * the slope of the plane fitted to the 5 x 5 blocks around (so one odd step or a diagonal doesn't count, and a hillside
    * rising 1 block every 2 is 0.5, far from a face); a narrow tread (2+ down on one side, up on another) on steep ground
    * counts as a cliff.
    */
   static int face(int[] ground, int cx, int cz) {
      int c = cz * GSIZE + cx;
      int h0 = ground[c];
      int min3 = h0;
      int max3 = h0;
      int max5 = h0;
      float sx = 0.0F;
      float sz = 0.0F;
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            int h = ground[c + dz * GSIZE + dx];
            sx += dx * h;
            sz += dz * h;
            max5 = Math.max(max5, h);
            if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
               min3 = Math.min(min3, h);
               max3 = Math.max(max3, h);
            }
         }
      }
      // least squares plane over 5 x 5: sum of dx^2 is 50
      float slope = (float)Math.sqrt(sx * sx + sz * sz) / 50.0F;
      int n = ground[c - GSIZE];
      int s = ground[c + GSIZE];
      int w = ground[c - 1];
      int e = ground[c + 1];
      int lo = Math.min(Math.min(n, s), Math.min(w, e));
      int hi = Math.max(Math.max(n, s), Math.max(w, e));
      boolean narrow = h0 - lo >= 2 && hi - h0 >= 1 && slope >= 1.0F;
      if (slope < FACE_SLOPE && !narrow) {
         return OPEN;
      }
      if (max3 - min3 <= 1 && max5 - h0 >= 2) {
         return LEDGE;
      }
      return slope >= CLIFF_SLOPE || narrow ? CLIFF : FACE;
   }

   // ---------------------------------------------------------------- stepped slopes

   /** Not a stepped slope's business (gentle ground, wide benches, faces, cliffs, no snow): the usual rules. */
   static final int STEP_NONE = 0;
   /** A stepped slope inside ample snow: one snowfield, snow ground on every tread and down its riser. */
   static final int STEP_WHITE = 1;
   /** A stepped slope where the snow is thin: its rock (or soil), no snow on the narrow treads. */
   static final int STEP_BARE = 2;
   /** Slope (5 x 5 plane, blocks per block) from which treads are narrow (up to about 4 blocks between risers). */
   static final float STEP_SLOPE = 0.22F;
   /** Snow depth (layers) from which a stepped slope is one snowfield, before the patch noise moves it by up to 2.5. */
   static final float STEP_DEPTH = 3.0F;
   /** The tallest part of a riser snow ground runs down (blocks below the tread's top); taller drops stay rock steps. */
   static final int RISER_CAP = 3;

   /**
    * What a stepped slope does here (STEP_NONE, STEP_WHITE or STEP_BARE). Stepped slopes were decided column by column:
    * on a face of mixed 1- and 2-block risers face() calls every column above a 2-block riser a cliff (its "narrow" test)
    * and the others a face, so the rows alternated bare rock and snow ground; on gentler steps the treads got snow ground
    * or thin layers over bare risers. Over a whole face that read as regular horizontal white and grey lines. Now every
    * stepped slope from STEP_SLOPE up to a real cliff (5 x 5 slope CLIFF_SLOPE, about 60 degrees, which stays rock) is
    * decided as a whole, by the model's depth moved by a smooth noise about 14 blocks across (so a white face and a bare
    * one meet along a ragged line): with ample snow, snow ground on every tread and down its riser (risers up to
    * RISER_CAP + 1 blocks; a taller drop stays a rock step); with thin snow, rock (or its soil), no snow on the treads
    * either. Gentle ground and wide benches (under STEP_SLOPE) keep the usual rules. ground: block heights of a
    * GSIZE x GSIZE window, (cx, cz) the column in it; both the erosion pass and the layer pass read the same terrain
    * heights, so they agree. (face is kept for the callers.)
    */
   static int step(long seed, int x, int z, int[] ground, int cx, int cz, int face, double score, float depth) {
      if (!(score >= 0.0)) {
         return STEP_NONE;
      }
      float slope = slope5(ground, cx, cz);
      if (slope < STEP_SLOPE || slope >= CLIFF_SLOPE) {
         // gentle ground and wide benches; real cliffs (steeper than about 60 degrees) stay rock by face()
         return STEP_NONE;
      }
      float n = noise(seed, x / 14.0, z / 14.0, 121);
      // steeper faces need deeper snow to be white (up to 6 layers more towards a cliff), so steep rock still shows in
      // patches on snowy peaks too
      float need = STEP_DEPTH + 6.0F * clamp01((slope - FACE_SLOPE) / (CLIFF_SLOPE - FACE_SLOPE));
      if (depth + 2.5F * n >= need && drop(ground, cx, cz) <= RISER_CAP + 1) {
         return STEP_WHITE;
      }
      return STEP_BARE;
   }

   /** How far the column stands above its lowest 4-neighbour (the height of its riser), 0 when none is lower. */
   static int drop(int[] ground, int cx, int cz) {
      int c = cz * GSIZE + cx;
      int lo = Math.min(Math.min(ground[c - 1], ground[c + 1]), Math.min(ground[c - GSIZE], ground[c + GSIZE]));
      return Math.max(0, ground[c] - lo);
   }

   /** The slope of the plane fitted to the 5 x 5 blocks around (as in face()). */
   static float slope5(int[] ground, int cx, int cz) {
      int c = cz * GSIZE + cx;
      float sx = 0.0F;
      float sz = 0.0F;
      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            int h = ground[c + dz * GSIZE + dx];
            sx += dx * h;
            sz += dz * h;
         }
      }
      return (float)Math.sqrt(sx * sx + sz * sz) / 50.0F;
   }

   /** 0..1, coherent over a few blocks: which ledges of a face get snow. */
   static float ledge(long seed, int x, int z) {
      return 0.5F + 0.5F * noise(seed, x / 4.0, z / 4.0, 113);
   }

   /**
    * True when the ground itself should be snow (a snow block) at snow depth d, on ground of this face class (OPEN, LEDGE,
    * FACE, CLIFF). A 1:1 face of 1-block risers is snow ground wherever the model has more than a dusting (each riser is the
    * side of the snow block above it, so it is white throughout, and it gets no layers); a cliff (2+ block risers, or
    * steeper) never is, as its risers would show rock under every snowy tread: it stays rock, with snow only on its
    * ledges, in patches. Snow so ends abruptly at the top and foot of rock faces.
    */
   static boolean snowGround(float d, int face, float ledge) {
      return switch (face) {
         case OPEN -> d >= BLOCK_DEPTH;
         case LEDGE -> d >= BLOCK_DEPTH && ledge >= 0.45F;
         case FACE -> d >= FACE_BLOCK_DEPTH;
         default -> false;
      };
   }

   /**
    * Snow layers (0-7) on a column whose snow depth is d, from the real ground: fill is how far the smoothed ground lies
    * above the column (about 0.35 at the foot of a step, 0 on flats and even slopes, below 0 on edges), face its class (face()),
    * steep FTF's slope against the cliff steepness, ledge the coherent dither of ledge(). Open ground gets a cover growing
    * with the depth (1-2 layers from a patch's edge inwards), the foot of every step a drift filling it towards the level
    * above as far as the depth allows (so a staircase of 1-block steps under deep snow reads as a smooth slope), exposed
    * edges stay thin. Faces get none (no striped staircases); ledges on them a layer or two, in patches.
    */
   static int layers(float d, float fill, int face, float steep, float ledge) {
      if (d <= 0.0F || face == FACE || face == CLIFF) {
         return 0;
      }
      if (face == LEDGE) {
         return ledge < 0.45F ? 0 : d >= BLOCK_DEPTH ? 2 : 1;
      }
      // a thin cover everywhere, plus the drift: the smoothed drift surface's height above the column in eighths of a block
      // (as far as the depth allows), so under deep snow a staircase of 1-block steps becomes a ramp of a few layers per
      // column instead of white stairs (a thick cover on every tread would only lift the stairs)
      float l = Math.min(0.5F * d, 2.0F) + clamp01(fill) * Math.min(8.0F, 1.5F * d);
      if (fill < -0.3F) {
         l = Math.min(l, 1.0F + 0.1F * d);
      }
      if (steep > 0.8F) {
         l = Math.min(l, 1.0F + (1.2F - steep) * 8.0F);
      }
      // the edge of a patch is where the depth runs out, not where rounding first gives a layer (that made lace of
      // the step feet around every thin patch)
      int n = Math.round(l);
      if (n == 0 && d >= 0.3F) {
         n = 1;
      }
      return Math.max(0, Math.min(7, n));
   }

   // ---------------------------------------------------------------- drift surface from the real ground

   /** Blur radius of the drift surface, and the margin of ground read around a chunk for it. */
   static final int B = 2;
   static final int GR = B + 1;
   static final int GSIZE = 16 + 2 * GR;
   private static final float[][] WEIGHT = new float[2 * B + 1][2 * B + 1];
   private static final float WEIGHTS;

   static {
      float sum = 0.0F;
      for (int dz = -B; dz <= B; dz++) {
         for (int dx = -B; dx <= B; dx++) {
            WEIGHT[dz + B][dx + B] = (float)Math.exp(-(dx * dx + dz * dz) / 2.5);
            sum += WEIGHT[dz + B][dx + B];
         }
      }
      WEIGHTS = sum;
   }

   /** Fills drift from ground (GSIZE x GSIZE): each column raised to its highest neighbour, at most one block. */
   static void drift(int[] ground, int[] drift) {
      for (int dz = 1; dz < GSIZE - 1; dz++) {
         for (int dx = 1; dx < GSIZE - 1; dx++) {
            int h0 = ground[dz * GSIZE + dx];
            int m = h0;
            for (int oz = -1; oz <= 1; oz++) {
               for (int ox = -1; ox <= 1; ox++) {
                  m = Math.max(m, ground[(dz + oz) * GSIZE + dx + ox]);
               }
            }
            drift[dz * GSIZE + dx] = Math.min(m, h0 + 1);
         }
      }
   }

   /**
    * How far the smoothed surface of a height window (the ground, or the step-filled drift surface) lies above a column at
    * top (cx, cz: its place in the GSIZE window).
    */
   static float fill(int[] drift, int cx, int cz, int top) {
      float sum = 0.0F;
      for (int dz = -B; dz <= B; dz++) {
         for (int dx = -B; dx <= B; dx++) {
            sum += drift[(cz + dz) * GSIZE + cx + dx] * WEIGHT[dz + B][dx + B];
         }
      }
      return sum / WEIGHTS - top;
   }

   // ---------------------------------------------------------------- noise

   /** Gradient noise, about -1..1, on a lattice turned by an angle that depends on the salt (no axis-aligned grain). */
   static float noise(long seed, double x, double z, int salt) {
      double a = salt * 0.7548776662466927;
      double ca = Math.cos(a);
      double sa = Math.sin(a);
      double px = x * ca - z * sa;
      double pz = x * sa + z * ca;
      int ix = (int)Math.floor(px);
      int iz = (int)Math.floor(pz);
      double fx = px - ix;
      double fz = pz - iz;
      double u = fade(fx);
      double v = fade(fz);
      double n00 = corner(seed, salt, ix, iz, fx, fz);
      double n10 = corner(seed, salt, ix + 1, iz, fx - 1.0, fz);
      double n01 = corner(seed, salt, ix, iz + 1, fx, fz - 1.0);
      double n11 = corner(seed, salt, ix + 1, iz + 1, fx - 1.0, fz - 1.0);
      double nx0 = n00 + (n10 - n00) * u;
      double nx1 = n01 + (n11 - n01) * u;
      return (float)Math.max(-1.0, Math.min(1.0, (nx0 + (nx1 - nx0) * v) * 1.6));
   }

   private static final float[] GX = new float[16];
   private static final float[] GZ = new float[16];

   static {
      for (int i = 0; i < 16; i++) {
         GX[i] = (float)Math.cos(i * Math.PI / 8.0 + 0.1);
         GZ[i] = (float)Math.sin(i * Math.PI / 8.0 + 0.1);
      }
   }

   private static double corner(long seed, int salt, int ix, int iz, double fx, double fz) {
      long h = seed ^ ix * 0x9E3779B97F4A7C15L ^ iz * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
      h ^= h >>> 33;
      h *= 0xFF51AFD7ED558CCDL;
      h ^= h >>> 33;
      h *= 0xC4CEB9FE1A85EC53L;
      h ^= h >>> 33;
      int i = (int)(h & 15);
      return GX[i] * fx + GZ[i] * fz;
   }

   private static double fade(double t) {
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
   }

   static double smoothstep(double a, double b, double v) {
      double t = Math.max(0.0, Math.min(1.0, (v - a) / (b - a)));
      return t * t * (3.0 - 2.0 * t);
   }

   static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   private static float tanh(float v) {
      if (v > 9.0F) {
         return 1.0F;
      }
      if (v < -9.0F) {
         return -1.0F;
      }
      float e = (float)Math.exp(2.0F * v);
      return (e - 1.0F) / (e + 1.0F);
   }
}

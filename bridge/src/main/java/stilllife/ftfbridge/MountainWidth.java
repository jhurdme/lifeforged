package stilllife.ftfbridge;

/**
 * Narrower mountains in the same places (mountain_width below 1).
 *
 * <p>FreeTerraForged's mountains.horizontalScale sets both how wide its mountains are and where its mountain chains run (the
 * chain layout grows with it), so narrowing mountains in the preset moves every chain. This keeps the preset's layout and
 * instead (1) shrinks the ridge noise that shapes the peaks by mountain_width and (2) only lets the chain rise in the upper part
 * of FTF's chain mask, so each chain covers a narrower band along the same line and rises more abruptly from the land beside it.
 */
public final class MountainWidth {
   private MountainWidth() {
   }

   public static double width() {
      if (!BridgeConfig.enabled()) {
         return 1.0;
      }

      double w = WorldLock.activeSettings().mountainWidth();
      return w > 0.0 && w < 1.0 ? w : 1.0;
   }

   /** FTF's ridge noise scale for mountains, narrowed. */
   public static int ridgeScale(int scaleH) {
      double w = width();
      return w == 1.0 ? scaleH : Math.max(16, (int)Math.round(scaleH * w));
   }

   /** Chain-mask values T[i] and the share of the world above each (measured over 32 x 32 km; the mask's statistics do not depend on the seed). */
   private static final double[] T = {0.2, 0.225, 0.25, 0.275, 0.3, 0.325, 0.35, 0.375, 0.4, 0.425, 0.45, 0.475, 0.5, 0.525, 0.55, 0.575, 0.6,
      0.625, 0.65, 0.675, 0.7, 0.725, 0.75, 0.775, 0.8, 0.825, 0.85, 0.875, 0.9, 0.925};
   private static final double[] S = {1.0, 0.9976, 0.9906, 0.9812, 0.9709, 0.9611, 0.9503, 0.937, 0.924, 0.911, 0.8965, 0.8822, 0.8678, 0.8485,
      0.8266, 0.7987, 0.7652, 0.7206, 0.6702, 0.6154, 0.5622, 0.512, 0.4601, 0.4093, 0.3574, 0.3004, 0.2086, 0.071, 0.0208, 0.0019};

   /**
    * A chain-mask threshold (FTF starts blending mountains in at 0.3 and has full mountains from 0.8), raised so that the land
    * above it shrinks to mountain_width of what it was: chains keep their lines but cover that share of the land.
    */
   public static float chainThreshold(float t) {
      double w = width();
      return w == 1.0 ? t : (float)maskAt(w * shareAbove(t));
   }

   private static double shareAbove(double t) {
      if (t <= T[0]) {
         return S[0];
      }

      for (int i = 1; i < T.length; i++) {
         if (t <= T[i]) {
            return S[i - 1] + (S[i] - S[i - 1]) * (t - T[i - 1]) / (T[i] - T[i - 1]);
         }
      }

      return S[S.length - 1];
   }

   private static double maskAt(double share) {
      for (int i = 1; i < S.length; i++) {
         if (share >= S[i]) {
            return T[i - 1] + (T[i] - T[i - 1]) * (S[i - 1] - share) / (S[i - 1] - S[i]);
         }
      }

      return T[T.length - 1];
   }
}

package stilllife.ftfbridge;

/** Keeps FTF's mountain chain mask for debugging tools (harness cell maps); unused in play. */
public final class MountainShapeTap {
   public static volatile Object shape;

   private MountainShapeTap() {
   }

   public static void capture(Object noise) {
      shape = noise;
   }
}

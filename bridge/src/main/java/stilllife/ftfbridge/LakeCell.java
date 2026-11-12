package stilllife.ftfbridge;

/**
 * Added to FreeTerraForged's Cell: whether a river carved the cell as part of one of its lakes (the widened, deepened stretches
 * FreeTerraForged makes on flat water-table steps). FTF itself marks lake water as plain river.
 */
public interface LakeCell {
   boolean stilllife$isLake();

   void stilllife$setLake(boolean lake);

   static boolean isLake(Object cell) {
      return cell instanceof LakeCell lake && lake.stilllife$isLake();
   }
}

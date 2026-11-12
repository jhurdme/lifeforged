package stilllife.ftfbridge;

import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.FTFRiverCarver;

/** Added to FreeTerraForged's river carvers: the river it branches off, and where its course leaves the mountains. */
public interface RiverAccess {
   FTFRiverCarver stilllife$parent();

   void stilllife$setParent(FTFRiverCarver parent);

   /** Position along the river (0 = source, 1 = mouth/junction) before which it is not carved. */
   float stilllife$cut();
}

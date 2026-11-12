package stilllife.ftfbridge.mixin;

import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.FTFRiverCarver;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.River;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.RiverWarp;
import etcodehome.freeterraforged.world.worldgen.cell.rivermap.river.UpliftRiverCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stilllife.ftfbridge.BridgeHooks;
import stilllife.ftfbridge.RiverAccess;

/** Rivers start below mountains instead of cutting through them, and their valley sides are limited to an even slope. */
@Mixin(value = UpliftRiverCarver.class, remap = false)
abstract class UpliftRiverCarverMixin implements RiverAccess {
   @Shadow
   public River river;
   @Shadow
   public RiverWarp warp;
   @Shadow
   private Levels levels;
   @Unique
   private volatile FTFRiverCarver stilllife$parentCarver;
   @Unique
   private volatile float stilllife$cutCache = Float.NaN;

   @Override
   public FTFRiverCarver stilllife$parent() {
      return this.stilllife$parentCarver;
   }

   @Override
   public void stilllife$setParent(FTFRiverCarver parent) {
      if (this.stilllife$parentCarver == null && parent != (Object)this) {
         this.stilllife$parentCarver = parent;
      }
   }

   @Override
   public float stilllife$cut() {
      float cut = this.stilllife$cutCache;
      if (cut != cut) {
         synchronized (this) {
            cut = this.stilllife$cutCache;
            if (cut != cut) {
               cut = BridgeHooks.computeCut(this.river, this.warp, this.levels, this.stilllife$parentCarver);
               this.stilllife$cutCache = cut;
            }
         }
      }

      return cut;
   }

   @Inject(method = "carve", at = @At("HEAD"), cancellable = true)
   private void stilllife$beforeCarve(Cell cell, float prevX, float prevZ, float prevT, float currX, float currZ, float currT, CallbackInfo ci) {
      if (BridgeHooks.riverBefore(this, this.river, cell, currT)) {
         ci.cancel();
      }
   }

   /** FTF's chosen height for this cell, just before it is compared with the cell's current height and applied. */
   @ModifyVariable(method = "carve", at = @At(value = "LOAD", ordinal = 0), name = "finalHeight")
   private float stilllife$gentleFlank(
      float finalHeight,
      Cell cell,
      float prevX,
      float prevZ,
      float prevT,
      float currX,
      float currZ,
      float currT,
      @Local(name = "currentLinearDist") float dist,
      @Local(name = "zone2Radius") float bankRadius,
      @Local(name = "actualValleyFloorHeight") float valleyFloor
   ) {
      return BridgeHooks.riverFlank(cell, finalHeight, dist, bankRadius, valleyFloor, this.levels);
   }

   /** Marks riverbed cells of FTF's lakes (the river widened to at least LAKE_WIDENING times its width) as lake water. */
   @Inject(
      method = "carve",
      at = @At(
         value = "INVOKE",
         target = "Letcodehome/freeterraforged/world/worldgen/cell/rivermap/river/UpliftRiverCarver;carveZone1Riverbed(Letcodehome/freeterraforged/world/worldgen/cell/Cell;FFFFFFFF)F",
         shift = At.Shift.AFTER
      ),
      require = 0
   )
   private void stilllife$markLake(
      Cell cell, float prevX, float prevZ, float prevT, float currX, float currZ, float currT, CallbackInfo ci,
      @Local(name = "lakeMultiplier") float lakeMultiplier
   ) {
      BridgeHooks.riverbed(cell, lakeMultiplier);
   }

   /** With lake_fill, a lake's bed is shaped for the full width FTF clears for it (see BridgeHooks.lakeBedScale). */
   @ModifyArg(
      method = "carve",
      at = @At(
         value = "INVOKE",
         target = "Letcodehome/freeterraforged/world/worldgen/cell/rivermap/river/UpliftRiverCarver;carveZone1Riverbed(Letcodehome/freeterraforged/world/worldgen/cell/Cell;FFFFFFFF)F"
      ),
      index = 4,
      require = 0
   )
   private float stilllife$lakeBedWidth(
      float sqScaleFactor, @Local(name = "biasedScale") float biasedScale, @Local(name = "lakeMultiplier") float lakeMultiplier
   ) {
      return BridgeHooks.lakeBedScale(sqScaleFactor, biasedScale, lakeMultiplier);
   }

   /** With lake_fill, a lake's bed stays under water right up to the bank (see BridgeHooks.lakeBedDepth). */
   @ModifyReturnValue(method = "carveZone1Riverbed", at = @At("RETURN"), require = 0)
   private float stilllife$lakeBedDepth(
      float bed, @Local(argsOnly = true, ordinal = 4) float targetWaterLevel, @Local(argsOnly = true, ordinal = 5) float widenMultiplier
   ) {
      return BridgeHooks.lakeBedDepth(bed, targetWaterLevel, widenMultiplier, this.levels);
   }

   @Inject(method = "carve", at = @At("RETURN"))
   private void stilllife$afterCarve(Cell cell, float prevX, float prevZ, float prevT, float currX, float currZ, float currT, CallbackInfo ci) {
      BridgeHooks.riverAfter(cell, this.levels);
   }
}

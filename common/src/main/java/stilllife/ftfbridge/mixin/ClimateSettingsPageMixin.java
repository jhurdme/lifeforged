package stilllife.ftfbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import etcodehome.freeterraforged.client.data.FTFTranslationKeys;
import etcodehome.freeterraforged.client.gui.widget.Slider;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FreeTerraForged's preset screen (1.0.0032-0034) builds its Coastal Moisture Boost slider from the preset's altitude
 * cooling strength, so the slider never shows the saved boost (it "resets" every time the page opens) and the first touch
 * writes a value counted from the wrong position. This hands the slider the preset's boost.
 *
 * <p>Client only, and optional throughout (FTF is an unstable release): the slider is recognised by its translation key,
 * not by its position, the preset is read by reflection, and anything missing leaves FTF's screen as it is. Once FTF
 * passes the right value itself this changes nothing.
 */
@Pseudo
@Mixin(targets = "etcodehome.freeterraforged.client.gui.screen.presetconfig.ClimateSettingsPage", remap = false)
public abstract class ClimateSettingsPageMixin {
   @WrapOperation(
      method = "init",
      at = @At(
         value = "INVOKE",
         target = "Letcodehome/freeterraforged/client/gui/screen/presetconfig/PresetWidgets;createFloatSlider(FFFLjava/lang/String;Letcodehome/freeterraforged/client/gui/widget/Slider$Callback;)Letcodehome/freeterraforged/client/gui/widget/Slider;"
      ),
      require = 0,
      remap = false
   )
   private Slider stilllife$coastalMoistureBoostValue(float value, float min, float max, String key, Slider.Callback callback, Operation<Slider> original) {
      float shown = value;
      try {
         if (FTFTranslationKeys.GUI_SLIDER_COASTAL_MOISTURE_BOOST.equals(key)) {
            shown = stilllife$presetBoost(value);
         }
      } catch (Throwable t) {
         shown = value;
      }
      return original.call(shown, min, max, key, callback);
   }

   /** The coastal moisture boost of the preset this page edits (its `preset` entry, declared in a superclass). */
   private float stilllife$presetBoost(float fallback) throws ReflectiveOperationException {
      for (Class<?> c = this.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
         Field field;
         try {
            field = c.getDeclaredField("preset");
         } catch (NoSuchFieldException e) {
            continue;
         }
         field.setAccessible(true);
         Object entry = field.get(this);
         if (entry == null) {
            return fallback;
         }
         Object preset = entry.getClass().getMethod("getPreset").invoke(entry);
         Object climate = preset.getClass().getMethod("climate").invoke(preset);
         float boost = climate.getClass().getField("coastalMoistureBoost").getFloat(climate);
         return boost >= 0.0F && boost <= 1.0F ? boost : fallback;
      }
      return fallback;
   }
}

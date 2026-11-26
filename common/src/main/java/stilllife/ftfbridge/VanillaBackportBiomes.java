package stilllife.ftfbridge;

import com.mojang.datafixers.util.Pair;
import etcodehome.freeterraforged.world.worldgen.runtime.BiomeCandidateRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Climate.Parameter;
import net.minecraft.world.level.biome.Climate.ParameterPoint;

/**
 * Places VanillaBackport's Pale Garden, Dappled Forest and Sulfur Caves inside Still Life's biome layout by taking a humidity
 * band of chosen Still Life biomes ("donors"). The band width follows each biome's prevalence setting: 1 = the standard
 * layout, 0 = none, 2 = about double.
 */
final class VanillaBackportBiomes {
   static final String PALE_GARDEN = "pale_garden";
   static final String DAPPLED_FOREST = "dappled_forest";
   static final String SULFUR_CAVES = "sulfur_caves";
   private static volatile String lastLog = "";

   private VanillaBackportBiomes() {
   }

   /** A humidity band [centre - half * p, centre + half * p] (or [top - width * p, top] when anchored at the top) of a donor. */
   private record Rule(String biome, String donor, float lo, float hi, boolean anchorTop, Predicate<ParameterPoint> filter) {
      float[] band(double prevalence) {
         // written so that prevalence 1 gives exactly the standard bounds
         float shrink = 1.0F - (float)Math.max(0.0, Math.min(2.0, prevalence));
         float width = this.hi - this.lo;
         if (this.anchorTop) {
            return new float[]{this.lo + width * shrink, this.hi};
         }

         return new float[]{this.lo + width * 0.5F * shrink, this.hi - width * 0.5F * shrink};
      }
   }

   private static final List<Rule> RULES = List.of(
      // Pale Garden: the most humid part of Still Life's temperate old-growth and mixed-wood forests
      new Rule(PALE_GARDEN, "still_life:old_growth_temperate_forest", 0.6F, 1.0F, true, p -> true),
      new Rule(PALE_GARDEN, "still_life:mixed_wood_forest", 0.6F, 1.0F, true, p -> true),
      // Dappled Forest: the drier, cool part of temperate forest / wooded prairie, aspen woodlands and inland mixed forest
      new Rule(DAPPLED_FOREST, "still_life:temperate_forest", -0.275F, -0.105F, false, tempIn(-0.35F, -0.27F)),
      new Rule(DAPPLED_FOREST, "still_life:wooded_prairie", -0.275F, -0.105F, false, tempIn(-0.35F, -0.27F)),
      new Rule(DAPPLED_FOREST, "still_life:aspen_woodlands", -0.275F, -0.105F, false, p -> true),
      new Rule(DAPPLED_FOREST, "still_life:temperate_mixed_forest", -0.275F, -0.105F, false, tempIn(-0.41F, -0.34F)),
      // Sulfur Caves: a thin humidity band of Still Life's barren caves in every climate, plus warm bands (shallow and deep)
      new Rule(SULFUR_CAVES, "still_life:barren_caves", 0.18F, 0.235F, false,
         p -> unq(p.humidity().min()) >= 0.179F && unq(p.humidity().max()) <= 0.236F),
      new Rule(SULFUR_CAVES, "still_life:barren_caves", 0.035F, 1.0F, true,
         p -> near(p.temperature().min(), 0.3F) && near(p.temperature().max(), 0.345F)),
      new Rule(SULFUR_CAVES, "still_life:barren_caves", -0.405F, 1.0F, true,
         p -> near(p.temperature().min(), 0.23F) && near(p.temperature().max(), 0.345F) && unq(p.depth().min()) > 0.5F)
   );

   /** Adds the VanillaBackport biomes to Still Life's multi-noise layout that FreeTerraForged places biomes from. */
   static BiomeCandidateRoot apply(BiomeCandidateRoot root, HolderLookup.Provider lookups) {
      if (root == null || lookups == null || !BridgeConfig.enabled()) {
         return root;
      }

      try {
         List<Pair<ParameterPoint, Holder<Biome>>> entries = root.entries();
         boolean stillLife = false;
         for (Pair<ParameterPoint, Holder<Biome>> e : entries) {
            String id = id(e.getSecond());
            if (id.startsWith("still_life:")) {
               stillLife = true;
            } else if (id.equals("minecraft:" + PALE_GARDEN) || id.equals("minecraft:" + DAPPLED_FOREST) || id.equals("minecraft:" + SULFUR_CAVES)) {
               log("this world's biome layout already contains VanillaBackport biomes (it was made with Still Life 0.3.1-ftf); "
                  + "it is used as saved and the pale_garden / dappled_forest / sulfur_caves settings do not apply to it");
               return root;
            }
         }

         if (!stillLife) {
            return root;
         }

         Settings s = WorldLock.activeSettings();
         Map<String, Boolean> vbSwitches = VanillaBackportConfig.read(StillLifeFtfBridge.configDir());
         HolderLookup.RegistryLookup<Biome> biomes = lookups.lookupOrThrow(Registries.BIOME);
         List<Pair<ParameterPoint, Holder<Biome>>> out = new ArrayList<>(entries);
         StringBuilder report = new StringBuilder();
         int total = 0;
         for (String biome : List.of(PALE_GARDEN, DAPPLED_FOREST, SULFUR_CAVES)) {
            // off in either config (amount 0 in the bridge's, or switched off in VanillaBackport's): left out
            boolean vbOn = VanillaBackportConfig.biomeEnabled(vbSwitches, biome);
            double p = vbOn ? prevalence(s, biome) : 0.0;
            Optional<Holder.Reference<Biome>> holder = biomes.get(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(biome)));
            int carved = 0;
            if (p > 0.0 && holder.isPresent()) {
               for (Rule rule : RULES) {
                  if (rule.biome().equals(biome)) {
                     carved += carve(out, rule, holder.get(), rule.band(p));
                  }
               }
            }

            report.append(report.length() == 0 ? "" : ", ").append(biome).append(' ').append(BridgeConfig.plain(p))
               .append(!holder.isPresent() ? " (not installed)"
                  : !vbOn ? " (off in VanillaBackport's config: " + VanillaBackportConfig.key(biome) + " = false)"
                  : " (" + carved + " climate cells)");
            total += carved;
         }

         log("VanillaBackport biomes in Still Life's layout: " + report);
         // none placed (the default): FreeTerraForged's own layout object, untouched
         return total == 0 ? root : BiomeCandidateRoot.fromEntries(out);
      } catch (Throwable t) {
         StillLifeFtfBridge.LOG.error("[{}] could not add the VanillaBackport biomes; using Still Life's layout unchanged", StillLifeFtfBridge.MOD_ID, t);
         return root;
      }
   }

   private static double prevalence(Settings s, String biome) {
      return switch (biome) {
         case PALE_GARDEN -> s.paleGarden();
         case DAPPLED_FOREST -> s.dappledForest();
         default -> s.sulfurCaves();
      };
   }

   /** Splits every donor entry along humidity; the part inside the band becomes the new biome. Returns the number split. */
   private static int carve(List<Pair<ParameterPoint, Holder<Biome>>> entries, Rule rule, Holder<Biome> biome, float[] band) {
      long lo = Climate.quantizeCoord(band[0]);
      long hi = Climate.quantizeCoord(band[1]);
      List<Pair<ParameterPoint, Holder<Biome>>> out = new ArrayList<>(entries.size() + 16);
      int n = 0;
      for (Pair<ParameterPoint, Holder<Biome>> e : entries) {
         ParameterPoint p = e.getFirst();
         if (!rule.donor().equals(id(e.getSecond())) || !rule.filter().test(p)) {
            out.add(e);
            continue;
         }

         long a = p.humidity().min();
         long b = p.humidity().max();
         long s = Math.max(a, lo);
         long t = Math.min(b, hi);
         if (t <= s) {
            out.add(e);
            continue;
         }

         n++;
         if (a < s) {
            out.add(Pair.of(withHumidity(p, a, s), e.getSecond()));
         }

         out.add(Pair.of(withHumidity(p, s, t), biome));
         if (b > t) {
            out.add(Pair.of(withHumidity(p, t, b), e.getSecond()));
         }
      }

      entries.clear();
      entries.addAll(out);
      return n;
   }

   private static ParameterPoint withHumidity(ParameterPoint p, long min, long max) {
      return new ParameterPoint(p.temperature(), new Parameter(min, max), p.continentalness(), p.erosion(), p.depth(), p.weirdness(), p.offset());
   }

   private static Predicate<ParameterPoint> tempIn(float lo, float hi) {
      return p -> unq(p.temperature().min()) >= lo - 1.0E-6F && unq(p.temperature().max()) <= hi + 1.0E-6F;
   }

   private static boolean near(long q, float v) {
      return Math.abs(unq(q) - v) < 1.0E-3F;
   }

   private static float unq(long q) {
      return Climate.unquantizeCoord(q);
   }

   private static String id(Holder<Biome> holder) {
      return holder.unwrapKey().map(k -> k.location().toString()).orElse("");
   }

   private static void log(String message) {
      if (!message.equals(lastLog)) {
         lastLog = message;
         StillLifeFtfBridge.LOG.info("[{}] {}", StillLifeFtfBridge.MOD_ID, message);
      }
   }
}

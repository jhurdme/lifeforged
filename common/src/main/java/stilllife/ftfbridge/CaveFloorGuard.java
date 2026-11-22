package stilllife.ftfbridge;

import com.mojang.datafixers.util.Pair;
import etcodehome.freeterraforged.world.worldgen.surface.rule.AcquiredSurfaceRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Keeps Still Life's ground blocks (snow blocks, sand, mud ...) off the floors of caves.
 *
 * <p>FreeTerraForged's underground biome banding leaves the surface biome in the caves of every cell that gets no cave biome
 * (its undergroundBiomeCoverage setting) and in a shell under the surface everywhere. Minecraft guards its own biome
 * surface rules with {@code above_preliminary_surface}; Still Life does not (in its own worlds every cave more than ~25
 * blocks down is a cave biome), so on FTF those cave floors got the ground of the biome above. And even
 * {@code above_preliminary_surface} lets small pockets 20-25 blocks under FTF's ground through (see {@link SurfaceLayer}).
 *
 * <p>Unless the world's {@code cave_floor_guard} is off, the surface rule FreeTerraForged builds (Still Life's, inside FTF's
 * own wrapper) is rewritten once per world, before FTF optimises it:
 * <ul>
 *   <li>exact: every rule that names only surface biomes, and does not already check the preliminary surface, runs only
 *       in the {@link SurfaceLayer surface layer}; every rule that checks {@code above_preliminary_surface} also has to be
 *       in the surface layer;</li>
 *   <li>vanilla: every rule that names only surface biomes, and does not already check it, checks
 *       {@code above_preliminary_surface}, as Minecraft's own biome rules do.</li>
 * </ul>
 * Cave-biome rules and rules with no biome (bedrock, deepslate, stone, FTF's strata) are unchanged. Surface and cave biomes
 * come from the climate layout FreeTerraForged places biomes from (cave = any entry below the surface, depth above 0).
 */
final class CaveFloorGuard {
   private static final SurfaceRules.RuleSource STONE = SurfaceRules.state(Blocks.STONE.defaultBlockState());
   private static final SurfaceRules.ConditionSource ABOVE_PRELIMINARY = SurfaceRules.abovePreliminarySurface();
   // the record classes behind Minecraft's factories (package-private, and named differently on Fabric)
   private static final Class<?> SEQUENCE = SurfaceRules.sequence(STONE, STONE).getClass();
   private static final Class<?> TEST = SurfaceRules.ifTrue(ABOVE_PRELIMINARY, STONE).getClass();
   private static final Class<?> NOT = SurfaceRules.not(ABOVE_PRELIMINARY).getClass();
   private static final Class<?> BIOME = SurfaceRules.isBiome(Biomes.PLAINS).getClass();
   private static final Class<?> STONE_DEPTH = SurfaceRules.stoneDepthCheck(0, false, 0, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR).getClass();
   /** A rule that starts this many blocks or more below a floor lays rock (Still Life's granite, diorite, basalt and
    *  terracotta layers), not ground: it keeps its above_preliminary_surface check only, so the layers stay continuous
    *  under small pockets near the surface. */
   private static final int ROCK_LAYER_DEPTH = 8;

   private static volatile Set<String> caveBiomes = Set.of();
   private static volatile Set<String> surfaceBiomes = Set.of();
   private static SurfaceRules.RuleSource lastIn;
   private static SurfaceRules.RuleSource lastOut;

   private CaveFloorGuard() {
   }

   /** The climate layout FreeTerraForged places biomes from. */
   static void layout(List<Pair<Climate.ParameterPoint, Holder<Biome>>> entries) {
      Set<String> cave = new HashSet<>();
      Set<String> all = new HashSet<>();
      long surface = Climate.quantizeCoord(0.0F);
      for (Pair<Climate.ParameterPoint, Holder<Biome>> e : entries) {
         String id = e.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("");
         all.add(id);
         if (e.getFirst().depth().min() > surface) {
            cave.add(id);
         }
      }

      all.removeAll(cave);
      caveBiomes = Set.copyOf(cave);
      surfaceBiomes = Set.copyOf(all);
      reset();
   }

   static synchronized void reset() {
      lastIn = null;
      lastOut = null;
   }

   /** The surface rule FreeTerraForged is about to use, with the guard applied when this world has it on. */
   static SurfaceRules.RuleSource apply(SurfaceRules.RuleSource root) {
      if (root == null || !BridgeConfig.enabled() || surfaceBiomes.isEmpty() || root.getClass().getName().endsWith("FastStrataSequenceRule$Source")) {
         return root;
      }

      Settings s = WorldLock.activeSettings();
      if (s == null || Settings.GUARD_OFF.equals(s.caveFloorGuard())) {
         return root;
      }
      boolean exact = !Settings.GUARD_VANILLA.equals(s.caveFloorGuard());

      synchronized (CaveFloorGuard.class) {
         if (root == lastIn) {
            return lastOut;
         }

         SurfaceRules.RuleSource out = root;
         try {
            Count count = new Count();
            out = rewrite(root, exact, count);
            StillLifeFtfBridge.LOG.info(exact
                  ? "[{}] cave floors (exact): {} surface-biome rules limited to the surface layer, {} above_preliminary_surface rules tightened"
                  : "[{}] cave floors (vanilla): {} surface-biome rules limited to above the preliminary surface",
               StillLifeFtfBridge.MOD_ID, count.wrapped, count.tightened);
         } catch (Throwable t) {
            StillLifeFtfBridge.LOG.error("[{}] could not apply the cave floor guard; the surface rule is used unchanged", StillLifeFtfBridge.MOD_ID, t);
            out = root;
         }

         lastIn = root;
         lastOut = out;
         return out;
      }
   }

   private static final class Count {
      int wrapped;
      int tightened;
   }

   private static SurfaceRules.RuleSource rewrite(SurfaceRules.RuleSource r, boolean exact, Count count) throws ReflectiveOperationException {
      if (r instanceof AcquiredSurfaceRule a) {
         SurfaceRules.RuleSource inner = rewrite(a.root(), exact, count);
         return inner == a.root() ? a : new AcquiredSurfaceRule(inner, a.sources(), a.failures());
      }

      if (r.getClass() == TEST && component(r, SurfaceRules.ConditionSource.class) == ABOVE_PRELIMINARY) {
         if (!exact) {
            return r;
         }
         return SurfaceRules.ifTrue(ABOVE_PRELIMINARY, tighten(component(r, SurfaceRules.RuleSource.class), count));
      }

      Refs refs = new Refs();
      scan(r, false, refs);
      if (refs.surface > 0 && refs.cave == 0 && refs.negated == 0 && !refs.gated) {
         count.wrapped++;
         return SurfaceRules.ifTrue(exact ? SurfaceLayer.INSTANCE : ABOVE_PRELIMINARY, r);
      }

      if (r.getClass() == SEQUENCE) {
         List<SurfaceRules.RuleSource> children = sequence(r);
         List<SurfaceRules.RuleSource> out = new ArrayList<>(children.size());
         boolean changed = false;
         for (SurfaceRules.RuleSource child : children) {
            SurfaceRules.RuleSource n = rewrite(child, exact, count);
            changed |= n != child;
            out.add(n);
         }

         return changed ? SurfaceRules.sequence(out.toArray(new SurfaceRules.RuleSource[0])) : r;
      }

      if (r.getClass() == TEST) {
         SurfaceRules.RuleSource then = component(r, SurfaceRules.RuleSource.class);
         SurfaceRules.RuleSource n = rewrite(then, exact, count);
         return n == then ? r : SurfaceRules.ifTrue(component(r, SurfaceRules.ConditionSource.class), n);
      }

      return r;
   }

   /** The ground rules under an above_preliminary_surface check also need the surface layer; rock layers keep the check alone. */
   private static SurfaceRules.RuleSource tighten(SurfaceRules.RuleSource r, Count count) throws ReflectiveOperationException {
      if (r.getClass() == SEQUENCE) {
         List<SurfaceRules.RuleSource> children = sequence(r);
         List<SurfaceRules.RuleSource> out = new ArrayList<>(children.size());
         for (SurfaceRules.RuleSource child : children) {
            out.add(rockLayer(child) ? child : layer(child, count));
         }
         return SurfaceRules.sequence(out.toArray(new SurfaceRules.RuleSource[0]));
      }
      return rockLayer(r) ? r : layer(r, count);
   }

   private static SurfaceRules.RuleSource layer(SurfaceRules.RuleSource r, Count count) {
      count.tightened++;
      return SurfaceRules.ifTrue(SurfaceLayer.INSTANCE, r);
   }

   private static boolean rockLayer(SurfaceRules.RuleSource r) throws ReflectiveOperationException {
      if (r.getClass() != TEST) {
         return false;
      }
      SurfaceRules.ConditionSource c = component(r, SurfaceRules.ConditionSource.class);
      return c.getClass() == STONE_DEPTH && component(c, int.class) >= ROCK_LAYER_DEPTH;
   }

   /** Biome references in a rule: positive ones split into surface / cave biomes, and whether it checks the preliminary surface. */
   private static final class Refs {
      int surface;
      int cave;
      int negated;
      boolean gated;
   }

   private static void scan(SurfaceRules.RuleSource r, boolean negated, Refs refs) throws ReflectiveOperationException {
      if (r instanceof AcquiredSurfaceRule a) {
         scan(a.root(), negated, refs);
      } else if (r.getClass() == SEQUENCE) {
         for (SurfaceRules.RuleSource child : sequence(r)) {
            scan(child, negated, refs);
         }
      } else if (r.getClass() == TEST) {
         scanCondition(component(r, SurfaceRules.ConditionSource.class), negated, refs);
         scan(component(r, SurfaceRules.RuleSource.class), negated, refs);
      }
   }

   private static void scanCondition(SurfaceRules.ConditionSource c, boolean negated, Refs refs) throws ReflectiveOperationException {
      if (c == ABOVE_PRELIMINARY) {
         refs.gated = true;
      } else if (c.getClass() == NOT) {
         scanCondition(component(c, SurfaceRules.ConditionSource.class), !negated, refs);
      } else if (c.getClass() == BIOME) {
         if (negated) {
            refs.negated++;
         }

         for (Object key : biomeKeys(c)) {
            String id = ((ResourceKey<?>)key).location().toString();
            // names in no layout (Still Life's own typo still_life:momsoon_forest, biomes switched off) match nothing
            if (caveBiomes.contains(id)) {
               refs.cave++;
            } else if (surfaceBiomes.contains(id)) {
               refs.surface++;
            }
         }
      }
   }

   // ---------------------------------------------------------------- reflection (component types, not names: Fabric renames them)

   @SuppressWarnings("unchecked")
   private static List<SurfaceRules.RuleSource> sequence(SurfaceRules.RuleSource r) throws ReflectiveOperationException {
      return (List<SurfaceRules.RuleSource>)component(r, List.class);
   }

   @SuppressWarnings("unchecked")
   private static <T> T component(Object record, Class<T> type) throws ReflectiveOperationException {
      for (RecordComponent rc : record.getClass().getRecordComponents()) {
         if (rc.getType() == type) {
            Method m = rc.getAccessor();
            m.setAccessible(true);
            return (T)m.invoke(record);
         }
      }

      throw new NoSuchFieldException(type.getSimpleName() + " in " + record.getClass().getName());
   }

   private static List<?> biomeKeys(SurfaceRules.ConditionSource c) throws ReflectiveOperationException {
      for (Field f : c.getClass().getDeclaredFields()) {
         if (f.getType() == List.class) {
            f.setAccessible(true);
            return (List<?>)f.get(c);
         }
      }

      throw new NoSuchFieldException("biome list in " + c.getClass().getName());
   }
}

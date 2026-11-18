import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.impl.lib.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.loader.impl.lib.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.loader.impl.lib.mappingio.format.tiny.Tiny1FileReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import net.fabricmc.loader.impl.lib.tinyremapper.IMappingProvider;
import net.fabricmc.loader.impl.lib.tinyremapper.InputTag;
import net.fabricmc.loader.impl.lib.tinyremapper.NonClassCopyMode;
import net.fabricmc.loader.impl.lib.tinyremapper.OutputConsumerPath;
import net.fabricmc.loader.impl.lib.tinyremapper.TinyRemapper;

/**
 * Remaps a jar between Mojang's names ("named", what the bridge is written and compiled in) and Fabric's intermediary
 * names (what Fabric runs Minecraft with), with the mapping and remapping libraries inside the Fabric loader jar.
 * Fabric Loom does this in a Gradle build; tools/build-javac.sh uses this instead.
 *
 * usage: java -cp fabric-loader.jar tools/RemapJar.java <named|intermediary> <in.jar> <out.jar> <mojang mappings .txt>
 *        <intermediary jar> [classpath jars...]
 *   intermediary = named -> intermediary (the Fabric mod jar); named = intermediary -> named (Fabric API, to compile against)
 */
public final class RemapJar {
   public static void main(String[] a) throws Exception {
      boolean toIntermediary = switch (a[0]) {
         case "intermediary" -> true;
         case "named" -> false;
         default -> throw new IllegalArgumentException("first argument: named or intermediary");
      };
      Path in = Path.of(a[1]), out = Path.of(a[2]), mojang = Path.of(a[3]), intermediary = Path.of(a[4]);
      List<Path> classpath = new ArrayList<>();
      for (int i = 5; i < a.length; i++) {
         classpath.add(Path.of(a[i]));
      }

      // official (obfuscated) -> intermediary, then Mojang's named -> official turned around and merged in
      MemoryMappingTree tree = new MemoryMappingTree();
      try (FileSystem fs = FileSystems.newFileSystem(intermediary); Reader r = Files.newBufferedReader(fs.getPath("mappings/mappings.tiny"))) {
         Tiny1FileReader.read(r, tree);
      }
      try (Reader r = Files.newBufferedReader(mojang)) {
         ProGuardFileReader.read(r, "named", "official", new MappingSourceNsSwitch(tree, "official", true));
      }
      int named = tree.getNamespaceId("named");
      int inter = tree.getNamespaceId("intermediary");
      int from = toIntermediary ? named : inter;
      int to = toIntermediary ? inter : named;
      int[] counts = new int[3];
      IMappingProvider mappings = acceptor -> {
         for (MappingTree.ClassMapping c : tree.getClasses()) {
            String owner = name(c, from);
            String target = name(c, to);
            if (owner == null || target == null) {
               continue;
            }
            acceptor.acceptClass(owner, target);
            counts[0]++;
            for (MappingTree.FieldMapping f : c.getFields()) {
               String n = f.getName(from), t = f.getName(to), d = f.getDesc(from);
               if (n != null && t != null && d != null) {
                  acceptor.acceptField(new IMappingProvider.Member(owner, n, d), t);
                  counts[1]++;
               }
            }
            for (MappingTree.MethodMapping m : c.getMethods()) {
               String n = m.getName(from), t = m.getName(to), d = m.getDesc(from);
               if (n != null && t != null && d != null && !n.startsWith("<")) {
                  acceptor.acceptMethod(new IMappingProvider.Member(owner, n, d), t);
                  counts[2]++;
               }
            }
         }
      };

      TinyRemapper remapper = TinyRemapper.newRemapper((level, message) -> System.err.println("[remap " + level + "] " + message))
         .withMappings(mappings)
         .build();
      Files.deleteIfExists(out);
      try (OutputConsumerPath output = new OutputConsumerPath.Builder(out).assumeArchive(true).build()) {
         output.addNonClassFiles(in, NonClassCopyMode.FIX_META_INF, remapper);
         InputTag tag = remapper.createInputTag();
         remapper.readInputsAsync(tag, in).join();
         remapper.readClassPathAsync(classpath.toArray(Path[]::new)).join();
         remapper.apply(output, tag);
      } finally {
         remapper.finish();
      }
      System.out.println("remapped " + in.getFileName() + " -> " + out.getFileName() + " (" + (toIntermediary ? "named -> intermediary" : "intermediary -> named")
         + "; " + counts[0] + " classes, " + counts[1] + " fields, " + counts[2] + " methods mapped)");
   }

   /**
    * A class name in one namespace; classes Mojang leaves unobfuscated keep their official name. (Members without a name
    * in a namespace are skipped instead: they are overrides, and the remapper carries the name over from the method they
    * override, or are not obfuscated at all.)
    */
   private static String name(MappingTree.ElementMapping e, int ns) {
      String n = e.getName(ns);
      return n != null ? n : e.getSrcName();
   }
}

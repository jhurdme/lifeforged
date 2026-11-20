import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Compile-time copy of the few Minecraft members the bridge reads that FreeTerraForged's access widener / transformer opens
 * at run time (SurfaceRules.Context's chunk, blockX/Y/Z, lastUpdateXZ and the SurfaceRules.Condition interface), made public
 * so javac accepts them. Only for compiling: the jar is never shipped or loaded by the game.
 * usage: java -cp <asm> tools/Widen.java <minecraft jar (Mojang names)> <out jar>
 */
public class Widen {
   static final String OUTER = "net/minecraft/world/level/levelgen/SurfaceRules";
   static final Set<String> CLASSES = Set.of(OUTER + "$Context", OUTER + "$Condition");
   static final Set<String> FIELDS = Set.of("chunk", "blockX", "blockY", "blockZ", "lastUpdateXZ");

   static int open(int access) {
      return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
   }

   public static void main(String[] a) throws IOException {
      try (ZipFile in = new ZipFile(a[0]); ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]))) {
         for (String name : List.of(OUTER + ".class", OUTER + "$Context.class", OUTER + "$Condition.class")) {
            byte[] bytes = in.getInputStream(in.getEntry(name)).readAllBytes();
            ClassReader r = new ClassReader(bytes);
            ClassWriter w = new ClassWriter(0);
            String self = name.substring(0, name.length() - 6);
            r.accept(new ClassVisitor(Opcodes.ASM9, w) {
               @Override
               public void visit(int v, int access, String n, String sig, String sup, String[] itf) {
                  super.visit(v, CLASSES.contains(n) ? open(access) : access, n, sig, sup, itf);
               }

               @Override
               public void visitInnerClass(String n, String outer, String inner, int access) {
                  super.visitInnerClass(n, outer, inner, CLASSES.contains(n) ? open(access) : access);
               }

               @Override
               public FieldVisitor visitField(int access, String n, String d, String sig, Object v) {
                  boolean widen = self.equals(OUTER + "$Context") && FIELDS.contains(n);
                  return super.visitField(widen ? open(access) : access, n, d, sig, v);
               }
            }, 0);
            out.putNextEntry(new ZipEntry(name));
            out.write(w.toByteArray());
            out.closeEntry();
         }
      }
   }
}

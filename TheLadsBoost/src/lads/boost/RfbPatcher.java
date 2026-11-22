package lads.boost;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * RetroFuturaBootstrap's LaunchClassLoader (1.8.9 Forge). Two changes:
 * <pre>
 * private byte[] runTransformers(String name, String tname, byte[] b) {   // original renamed lads$orig$runTransformers
 *     byte[] c = BoostHooks.rfbLookup(this, tname);
 *     if (c != null) return c;                       // final bytes (FML, Mixin and rfb transformers already applied)
 *     byte[] r = lads$orig$runTransformers(name, tname, b);
 *     BoostHooks.rfbPending(tname);
 *     return r;
 * }
 * // findClass: runRfbTransformers(...) -> lads$rfb(...)
 * public byte[] lads$rfb(Collection t, Context x, Manifest m, String tname, byte[] b) {
 *     int s = BoostHooks.rfbBefore(tname);
 *     if (s == 1) return b;                          // cache hit: already transformed
 *     byte[] r = runRfbTransformers(t, x, m, tname, b);
 *     if (s == 2) BoostHooks.store(this, tname, r, true);
 *     return r;
 * }
 * </pre>
 */
public final class RfbPatcher implements Opcodes {
    static final String RT = "runTransformers", RT_D = "(Ljava/lang/String;Ljava/lang/String;[B)[B";
    static final String RR = "runRfbTransformers";
    static final String RR_D = "(Ljava/util/Collection;Lcom/gtnewhorizons/retrofuturabootstrap/api/RfbClassTransformer$Context;Ljava/util/jar/Manifest;Ljava/lang/String;[B)[B";
    static final String ORIG = "lads$orig$" + RT, WRAP = "lads$rfb";
    static final String HOOKS = "lads/boost/BoostHooks";

    public static byte[] patch(byte[] in) {
        ClassReader cr = new ClassReader(in);
        int[] found = new int[3]; // runTransformers, rfb$parent field, replaced call sites in findClass
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        String[] owner = new String[1];
        cr.accept(new ClassVisitor(ASM9, cw) {
            @Override public void visit(int v, int a, String n, String s, String sn, String[] i) {
                owner[0] = n; super.visit(v, a, n, s, sn, i);
            }
            @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) {
                if (n.equals("rfb$parent")) found[1]++;
                return super.visitField(a, n, d, s, v);
            }
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                if (n.equals(RT) && d.equals(RT_D) && (a & ACC_STATIC) == 0) {
                    found[0]++;
                    return super.visitMethod(a, ORIG, d, s, e);
                }
                MethodVisitor mv = super.visitMethod(a, n, d, s, e);
                if (!n.equals("findClass") || !d.equals("(Ljava/lang/String;)Ljava/lang/Class;")) return mv;
                return new MethodVisitor(ASM9, mv) {
                    @Override public void visitMethodInsn(int op, String o, String nm, String ds, boolean itf) {
                        if (op == INVOKEVIRTUAL && nm.equals(RR) && ds.equals(RR_D)) { found[2]++; super.visitMethodInsn(op, owner[0], WRAP, ds, itf); }
                        else super.visitMethodInsn(op, o, nm, ds, itf);
                    }
                };
            }
            @Override public void visitEnd() {
                if (found[0] == 1 && found[1] == 1 && found[2] == 1) { emitRun(cv, owner[0]); emitRfb(cv, owner[0]); }
                super.visitEnd();
            }
        }, 0);
        if (found[0] != 1 || found[1] != 1 || found[2] != 1)
            throw new IllegalStateException("LaunchClassLoader layout differs (" + found[0] + "," + found[1] + "," + found[2] + "), not patched");
        return cw.toByteArray();
    }

    private static void emitRun(ClassVisitor cv, String o) {
        MethodVisitor m = cv.visitMethod(ACC_PRIVATE, RT, RT_D, null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKESTATIC, HOOKS, "rfbLookup", "(Ljava/lang/Object;Ljava/lang/String;)[B", false);
        m.visitVarInsn(ASTORE, 4);
        m.visitVarInsn(ALOAD, 4);
        Label l = new Label();
        m.visitJumpInsn(IFNULL, l);
        m.visitVarInsn(ALOAD, 4); m.visitInsn(ARETURN);
        m.visitLabel(l);
        m.visitFrame(F_APPEND, 1, new Object[] {"[B"}, 0, null);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ALOAD, 3);
        m.visitMethodInsn(INVOKESPECIAL, o, ORIG, RT_D, false);
        m.visitVarInsn(ASTORE, 5);
        m.visitVarInsn(ALOAD, 2);
        m.visitMethodInsn(INVOKESTATIC, HOOKS, "rfbPending", "(Ljava/lang/String;)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitInsn(ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void emitRfb(ClassVisitor cv, String o) {
        MethodVisitor m = cv.visitMethod(ACC_PUBLIC, WRAP, RR_D, null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 4);
        m.visitMethodInsn(INVOKESTATIC, HOOKS, "rfbBefore", "(Ljava/lang/String;)I", false);
        m.visitVarInsn(ISTORE, 6);
        m.visitVarInsn(ILOAD, 6); m.visitInsn(ICONST_1);
        Label l1 = new Label(), l2 = new Label();
        m.visitJumpInsn(IF_ICMPNE, l1);
        m.visitVarInsn(ALOAD, 5); m.visitInsn(ARETURN);
        m.visitLabel(l1);
        m.visitFrame(F_APPEND, 1, new Object[] {INTEGER}, 0, null);
        m.visitVarInsn(ALOAD, 0);
        for (int i = 1; i <= 5; i++) m.visitVarInsn(ALOAD, i);
        m.visitMethodInsn(INVOKEVIRTUAL, o, RR, RR_D, false);
        m.visitVarInsn(ASTORE, 7);
        m.visitVarInsn(ILOAD, 6); m.visitInsn(ICONST_2);
        m.visitJumpInsn(IF_ICMPNE, l2);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 4); m.visitVarInsn(ALOAD, 7); m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKESTATIC, HOOKS, "store", "(Ljava/lang/Object;Ljava/lang/String;[BZ)V", false);
        m.visitLabel(l2);
        m.visitFrame(F_APPEND, 1, new Object[] {"[B"}, 0, null);
        m.visitVarInsn(ALOAD, 7); m.visitInsn(ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }
}

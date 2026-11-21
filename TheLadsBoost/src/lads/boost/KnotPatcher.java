package lads.boost;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Renames KnotClassDelegate.getPostMixinClassByteArray(String,boolean) to lads$orig$... and emits a wrapper:
 * <pre>
 * boolean init = this.transformInitialized;
 * byte[] c = BoostHooks.lookup(this, name, init);
 * if (c != null) return c;
 * byte[] r = this.lads$orig$getPostMixinClassByteArray(name, allowFromParent);
 * BoostHooks.store(this, name, r, init);
 * return r;
 * </pre>
 * No COMPUTE_FRAMES: the single frame is written by hand.
 */
public final class KnotPatcher implements Opcodes {
    static final String M = "getPostMixinClassByteArray";
    static final String D = "(Ljava/lang/String;Z)[B";
    static final String ORIG = "lads$orig$" + M;
    static final String HOOKS = "lads/boost/BoostHooks";

    public static byte[] patch(byte[] in) {
        ClassReader cr = new ClassReader(in);
        boolean[] found = new boolean[2]; // method, field
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        String[] owner = new String[1];
        cr.accept(new ClassVisitor(ASM9, cw) {
            @Override public void visit(int v, int a, String n, String s, String sn, String[] i) {
                owner[0] = n; super.visit(v, a, n, s, sn, i);
            }
            @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) {
                if (n.equals("transformInitialized") && d.equals("Z")) found[1] = true;
                return super.visitField(a, n, d, s, v);
            }
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                if (n.equals(M) && d.equals(D) && (a & ACC_STATIC) == 0) {
                    found[0] = true;
                    return super.visitMethod(a, ORIG, d, s, e);
                }
                return super.visitMethod(a, n, d, s, e);
            }
            @Override public void visitEnd() {
                if (found[0] && found[1]) emit(cv, owner[0]);
                super.visitEnd();
            }
        }, 0);
        if (!found[0] || !found[1]) throw new IllegalStateException("KnotClassDelegate layout differs, not patched");
        return cw.toByteArray();
    }

    private static void emit(ClassVisitor cv, String o) {
        MethodVisitor m = cv.visitMethod(ACC_PRIVATE, M, D, null, null);
        m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, o, "transformInitialized", "Z");
        m.visitVarInsn(ISTORE, 3);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ILOAD, 3);
        m.visitMethodInsn(INVOKESTATIC, HOOKS, "lookup", "(Ljava/lang/Object;Ljava/lang/String;Z)[B", false);
        m.visitVarInsn(ASTORE, 4);
        m.visitVarInsn(ALOAD, 4);
        Label l = new Label();
        m.visitJumpInsn(IFNULL, l);
        m.visitVarInsn(ALOAD, 4); m.visitInsn(ARETURN);
        m.visitLabel(l);
        m.visitFrame(F_APPEND, 2, new Object[] {INTEGER, "[B"}, 0, null);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ILOAD, 2);
        m.visitMethodInsn(INVOKESPECIAL, o, ORIG, D, false);
        m.visitVarInsn(ASTORE, 5);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 5); m.visitVarInsn(ILOAD, 3);
        m.visitMethodInsn(INVOKESTATIC, HOOKS, "store", "(Ljava/lang/Object;Ljava/lang/String;[BZ)V", false);
        m.visitVarInsn(ALOAD, 5); m.visitInsn(ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }
}

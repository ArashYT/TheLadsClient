package com.thelads.core.perf;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * Jasione, remade: every {@code SomeEnum.values()} call copies the enum's constants into a new array. A call whose array is
 * only read (its length, its elements, a null check) is switched to one shared array at class load, so hot loops stop
 * allocating. Any other use keeps the original call: stored, passed to a method, returned, written, compared by identity,
 * locked on. Calls inside the enum itself are left alone too (its static init may still be building the constants). Any
 * doubt or error leaves the class exactly as it was.
 *
 * <p>Only ASM 5 API is used outside {@link Condy}: Minecraft 1.8.9 runs ASM 5.0.3.
 */
public final class EnumValuesRewriter {
    private static final AtomicInteger SCANNED = new AtomicInteger(), CLASSES = new AtomicInteger(),
        SITES = new AtomicInteger(), FAILED = new AtomicInteger();
    private static final AtomicLong NANOS = new AtomicLong();
    // A class loaded while this thread rewrites another one (ASM itself, a lookup) is passed through unchanged.
    private static final ThreadLocal<Boolean> BUSY = new ThreadLocal<>();

    private EnumValuesRewriter() {}

    /**
     * The class with its read-only {@code values()} calls switched to a shared array, or null when nothing changed.
     * {@code condy}: Java 11+ classes load it as a constant (26.x); otherwise from the JDK 8 shared enum cache (1.8.9).
     */
    public static byte[] rewrite(byte[] bytes, Predicate<String> isEnum, boolean condy) {
        if (bytes == null || BUSY.get() != null) return null;
        BUSY.set(Boolean.TRUE);
        long start = System.nanoTime();
        try {
            SCANNED.incrementAndGet();
            ClassReader reader = new ClassReader(bytes);
            if (!callsValues(reader)) return null;
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            if ((node.version & 0xFFFF) < (condy ? Opcodes.V11 : Opcodes.V1_5)) return null;
            int sites = 0;
            for (MethodNode method : node.methods) sites += rewrite(node.name, method, isEnum, condy);
            if (sites == 0) return null;
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            byte[] result = writer.toByteArray();
            CLASSES.incrementAndGet();
            SITES.addAndGet(sites);
            return result;
        } catch (Throwable e) {
            FAILED.incrementAndGet();
            return null;
        } finally {
            NANOS.addAndGet(System.nanoTime() - start);
            BUSY.remove();
        }
    }

    /** True when the class is an enum, from its class file (null: unknown, so not optimized). Cached per name. */
    public static Predicate<String> enumCheck(Function<String, byte[]> classBytes) {
        Map<String, Boolean> known = new ConcurrentHashMap<>();
        return name -> known.computeIfAbsent(name, n -> {
            try {
                byte[] bytes = classBytes.apply(n);
                if (bytes == null) return false;
                ClassReader reader = new ClassReader(bytes);
                return (reader.getAccess() & Opcodes.ACC_ENUM) != 0 && "java/lang/Enum".equals(reader.getSuperName());
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    /** One line for the log: what was optimized and what it cost. */
    public static String summary() {
        return String.format("Jasione: %d Enum#values() call sites in %d classes now share one array (%d classes scanned in %d ms, %d left unchanged after an error)",
            SITES.get(), CLASSES.get(), SCANNED.get(), NANOS.get() / 1_000_000, FAILED.get());
    }

    /** Cheap pre-check on the constant pool: does any method reference look like {@code X.values()[LX;}? */
    static boolean callsValues(ClassReader reader) {
        char[] buffer = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            int at = reader.getItem(i);
            if (at == 0 || reader.readByte(at - 1) != 10) continue; // CONSTANT_Methodref
            int nameAndType = reader.getItem(reader.readUnsignedShort(at + 2));
            if ("values".equals(reader.readUTF8(nameAndType, buffer)) && reader.readUTF8(nameAndType + 2, buffer).startsWith("()[L")) return true;
        }
        return false;
    }

    private static int rewrite(String owner, MethodNode method, Predicate<String> isEnum, boolean condy) throws Exception {
        Set<AbstractInsnNode> calls = new HashSet<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.name.equals("values") && call.desc.equals("()[L" + call.owner + ";") && !call.owner.equals(owner)
                && isEnum.test(call.owner)) calls.add(call);
        }
        if (calls.isEmpty()) return 0;
        ReadOnlyUses uses = new ReadOnlyUses(calls);
        Frame<SourceValue>[] frames = new Analyzer<>(uses).analyze(owner, method);
        // Unreachable code (no frame) is left as it is. Decided before any edit shifts the instruction indexes.
        calls.removeIf(insn -> uses.escaped.contains(insn) || frames[method.instructions.indexOf(insn)] == null);
        int sites = 0;
        for (AbstractInsnNode insn : calls) {
            String enumName = ((MethodInsnNode) insn).owner;
            if (condy) {
                method.instructions.set(insn, Condy.load(enumName));
            } else {
                // sun.misc.SharedSecrets.getJavaLangAccess().getEnumConstantsShared(Enum.class): the JDK's own unshared copy,
                // which Forge's EnumHelper resets when it adds a constant.
                InsnList load = new InsnList();
                load.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "sun/misc/SharedSecrets", "getJavaLangAccess", "()Lsun/misc/JavaLangAccess;", false));
                load.add(new LdcInsnNode(Type.getObjectType(enumName)));
                load.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "sun/misc/JavaLangAccess", "getEnumConstantsShared", "(Ljava/lang/Class;)[Ljava/lang/Enum;", true));
                load.add(new TypeInsnNode(Opcodes.CHECKCAST, "[L" + enumName + ";"));
                method.instructions.insertBefore(insn, load);
                method.instructions.remove(insn);
            }
            sites++;
        }
        if (!condy && sites > 0) method.maxStack += 1; // the access object and the class are on the stack together
        return sites;
    }

    /**
     * Follows each candidate array through locals, casts and stack copies; any use other than reading it marks that call
     * as escaped. Values merged at a branch carry every call they may come from.
     */
    static final class ReadOnlyUses extends SourceInterpreter {
        final Set<AbstractInsnNode> calls;
        final Set<AbstractInsnNode> escaped = new HashSet<>();

        ReadOnlyUses(Set<AbstractInsnNode> calls) {
            super(Opcodes.ASM5);
            this.calls = calls;
        }

        private void use(SourceValue value, boolean readOnly) {
            if (readOnly) return;
            for (Object source : value.insns) if (calls.contains(source)) escaped.add((AbstractInsnNode) source);
        }

        @Override public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
            return value; // a load, store or dup is the same array
        }

        @Override public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
            int op = insn.getOpcode();
            use(value, op == Opcodes.ARRAYLENGTH || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL
                || op == Opcodes.INSTANCEOF || op == Opcodes.CHECKCAST);
            return op == Opcodes.CHECKCAST ? value : super.unaryOperation(insn, value);
        }

        @Override public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue array, SourceValue other) {
            int op = insn.getOpcode();
            use(array, op >= Opcodes.IALOAD && op <= Opcodes.SALOAD); // element reads; everything else (putfield, ==) escapes
            use(other, false);
            return super.binaryOperation(insn, array, other);
        }

        @Override public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue a, SourceValue b, SourceValue c) {
            use(a, false); // element writes
            use(b, false);
            use(c, false);
            return super.ternaryOperation(insn, a, b, c);
        }

        @Override public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            for (SourceValue value : values) use(value, false); // method arguments, receivers, lambda captures
            return super.naryOperation(insn, values);
        }

        @Override public void returnOperation(AbstractInsnNode insn, SourceValue value, SourceValue expected) {
            use(value, false);
        }
    }

    /** Java 11+ only (ASM 7+): a class constant resolved once by calling {@code values()}, shared by the class's call sites. */
    static final class Condy {
        private static final Handle INVOKE = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/ConstantBootstraps", "invoke",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;", false);

        static AbstractInsnNode load(String enumName) {
            String array = "[L" + enumName + ";";
            return new LdcInsnNode(new ConstantDynamic("values", array, INVOKE,
                new Handle(Opcodes.H_INVOKESTATIC, enumName, "values", "()" + array, false)));
        }
    }
}

package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.ConfigManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

class EnumValuesRewriterTest {
    private static final String SAMPLES = EnumValuesSamples.class.getName();
    private static final Predicate<String> IS_ENUM = EnumValuesRewriter.enumCheck(EnumValuesRewriterTest::bytes);

    @AfterEach void resetConfig() { ConfigManager.setTestConfigFile(null); }

    private static byte[] bytes(String internalName) {
        try (InputStream in = EnumValuesRewriterTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, int[]> calls(byte[] classBytes) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, 0);
        Map<String, int[]> found = new HashMap<>(); // method -> {values() calls left, condy loads, shared-cache loads}
        for (MethodNode method : node.methods) {
            int[] counts = new int[3];
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.equals("values")) counts[0]++;
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof ConstantDynamic) counts[1]++;
                if (insn instanceof MethodInsnNode call && call.name.equals("getEnumConstantsShared")) counts[2]++;
            }
            found.put(method.name, counts);
        }
        return found;
    }

    @Test void onlyReadOnlyCallsAreRewritten() {
        byte[] rewritten = EnumValuesRewriter.rewrite(bytes(SAMPLES.replace('.', '/')), IS_ENUM, true);
        assertNotNull(rewritten);
        Map<String, int[]> found = calls(rewritten);
        for (String reader : new String[] {"readLoop", "readIndexed", "readLength", "readAlias"}) {
            assertEquals(0, found.get(reader)[0], reader + " still calls values()");
            assertEquals(1, found.get(reader)[1], reader + " loads the shared array");
        }
        for (String escape : new String[] {"returned", "storedInField", "written", "writtenThroughAlias", "passed",
                "writtenAfterBranch", "comparedAcrossIterations", "captured", "cloned", "locked", "notAnEnum"}) {
            assertTrue(found.get(escape)[0] >= 1, escape + " must keep values()");
            assertEquals(0, found.get(escape)[1], escape + " must not share the array");
        }
        assertEquals(2, found.get("sameArray")[0], "an identity comparison keeps both calls");
    }

    @Test void rewrittenClassBehavesTheSame() throws Exception {
        byte[] rewritten = EnumValuesRewriter.rewrite(bytes(SAMPLES.replace('.', '/')), IS_ENUM, true);
        Class<?> copy = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(SAMPLES)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    return loaded != null ? loaded : defineClass(name, rewritten, 0, rewritten.length);
                }
            }
        }.loadClass(SAMPLES);
        assertNotSame(EnumValuesSamples.class, copy);
        for (Method original : EnumValuesSamples.class.getDeclaredMethods()) {
            if (original.isSynthetic()) continue;
            Object[] args = original.getParameterCount() == 0 ? new Object[0]
                : new Object[] {original.getParameterTypes()[0] == int.class ? (Object) 2 : (Object) true};
            Object expected = original.invoke(null, args), actual = copy.getMethod(original.getName(), original.getParameterTypes()).invoke(null, args);
            if (expected instanceof Object[] array) assertArrayEquals(array, (Object[]) actual, original.getName());
            else assertEquals(expected, actual, original.getName());
        }
        // A caller that writes into its own copy never reaches the shared array.
        copy.getMethod("written").invoke(null);
        copy.getMethod("writtenThroughAlias").invoke(null);
        assertEquals(6, copy.getMethod("readLoop").invoke(null));
        assertEquals(2, copy.getMethod("readIndexed", int.class).invoke(null, 2));
    }

    @Test void sharedCacheModeKeepsTheStackValid() throws Exception {
        byte[] rewritten = EnumValuesRewriter.rewrite(bytes(SAMPLES.replace('.', '/')), IS_ENUM, false);
        assertNotNull(rewritten);
        Map<String, int[]> found = calls(rewritten);
        assertEquals(1, found.get("readLoop")[2]);
        assertEquals(0, found.get("written")[2]);
        ClassNode node = new ClassNode();
        new ClassReader(rewritten).accept(node, 0);
        // Fails on a too-small max stack or a broken stack shape.
        for (MethodNode method : node.methods) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
    }

    @Test void enumOwnCallsAndOtherClassesAreLeftAlone() {
        assertNull(EnumValuesRewriter.rewrite(bytes(SampleColor.class.getName().replace('.', '/')), IS_ENUM, true));
        assertNull(EnumValuesRewriter.rewrite(bytes(EnumValuesSamples.NotAnEnum.class.getName().replace('.', '/')), IS_ENUM, true));
        assertNull(EnumValuesRewriter.rewrite(new byte[] {1, 2, 3}, IS_ENUM, true), "unreadable bytes are passed through");
        assertNull(EnumValuesRewriter.rewrite(bytes(SAMPLES.replace('.', '/')), name -> false, true));
        assertTrue(IS_ENUM.test(SampleColor.class.getName().replace('.', '/')));
        assertFalse(IS_ENUM.test("com/thelads/core/perf/Missing"));
        assertTrue(EnumValuesRewriter.summary().startsWith("Jasione: "));
    }

    @Test void savedModuleStateIsReadBeforeModulesLoad(@TempDir Path dir) throws IOException {
        File config = dir.resolve("thelads_config.json").toFile();
        ConfigManager.setTestConfigFile(config);
        assertTrue(ConfigManager.savedEnabled("Jasione", true), "no config file: default");
        Files.writeString(config.toPath(), "{\"modules\":{\"Jasione\":{\"enabled\":false}}}", StandardCharsets.UTF_8);
        assertFalse(ConfigManager.savedEnabled("Jasione", true));
        assertTrue(ConfigManager.savedEnabled("Other", true));
        Files.writeString(config.toPath(), "not json", StandardCharsets.UTF_8);
        assertTrue(ConfigManager.savedEnabled("Jasione", true), "unreadable config: default");
    }
}

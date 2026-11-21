package lads.boost;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Java agent entry point. Rewrites only Fabric's KnotClassDelegate. */
public final class BoostAgent {
    static final String TARGET = "net/fabricmc/loader/impl/launch/knot/KnotClassDelegate";

    public static void premain(String args, Instrumentation inst) {
        try {
            BoostHooks.init();
            inst.addTransformer(new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader l, String name, Class<?> c, ProtectionDomain pd, byte[] b) {
                    if (!TARGET.equals(name)) return null;
                    try {
                        return KnotPatcher.patch(b);
                    } catch (Throwable t) {
                        BoostHooks.fail("patch", t);
                        return null; // original class stays untouched
                    }
                }
            }, false);
        } catch (Throwable t) {
            BoostHooks.fail("premain", t);
        }
    }
}

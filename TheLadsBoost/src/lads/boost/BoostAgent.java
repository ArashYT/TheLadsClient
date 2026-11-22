package lads.boost;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Java agent entry point. Rewrites only Fabric's KnotClassDelegate (26.3, 1.20.1) or RetroFuturaBootstrap's
 *  LaunchClassLoader (1.8.9 Forge). Any error leaves the class untouched and the game loads the normal way. */
public final class BoostAgent {
    static final String KNOT = "net/fabricmc/loader/impl/launch/knot/KnotClassDelegate";
    static final String LCL = "net/minecraft/launchwrapper/LaunchClassLoader";

    public static void premain(String args, Instrumentation inst) {
        try {
            if (BoostHooks.killed()) return;
            BoostHooks.init();
            if (BoostHooks.disabled) return;
            inst.addTransformer(new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader l, String name, Class<?> c, ProtectionDomain pd, byte[] b) {
                    boolean knot = KNOT.equals(name);
                    if (!knot && !LCL.equals(name)) return null;
                    try {
                        return knot ? KnotPatcher.patch(b) : RfbPatcher.patch(b);
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

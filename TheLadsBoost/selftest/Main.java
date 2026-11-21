import net.fabricmc.loader.impl.launch.knot.KnotClassDelegate;

import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/** args: preinit missing  (counts). Prints "RESULT orig=.. sha=.. cl=.." */
public class Main {
    public static void main(String[] a) throws Exception {
        int preinit = Integer.parseInt(a[0]), missing = Integer.parseInt(a[1]);
        AtomicInteger clCalls = new AtomicInteger();
        ClassLoader cl = new ClassLoader(null) {
            @Override public Class<?> loadClass(String n, boolean r) throws ClassNotFoundException {
                clCalls.incrementAndGet();
                throw new ClassNotFoundException(n);
            }
        };
        KnotClassDelegate d = new KnotClassDelegate(cl);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        int total = 2000;
        for (int i = 0; i < total; i++) {
            if (i == preinit) d.initializeTransformers();
            String n = i == 700 ? "net.minecraft.client.main.Main" : i < preinit ? "net.fabricmc.loader.Pre" + i
                    : i % 40 == 0 && i / 40 < missing ? "missing.M" + i : "net.minecraft.C" + i;
            byte[] b = d.tryLoad(n, false);
            md.update(b == null ? new byte[] {0} : b);
            if (i == 701) Thread.sleep(300); // give the preload thread time
        }
        StringBuilder h = new StringBuilder();
        for (byte x : md.digest()) h.append(String.format("%02x", x));
        System.out.println("RESULT orig=" + KnotClassDelegate.ORIG.get() + " sha=" + h + " cl=" + clCalls.get());
    }
}

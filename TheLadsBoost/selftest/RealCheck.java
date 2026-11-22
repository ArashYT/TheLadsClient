import lads.boost.KnotPatcher;
import lads.boost.RfbPatcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/** args: knot|rfb outDir jarWithTheClass [more jars for type resolution...]. Patches the REAL class and verifies it. */
public class RealCheck {
    public static void main(String[] a) throws Exception {
        boolean rfb = a[0].equals("rfb");
        String cls = rfb ? "net/minecraft/launchwrapper/LaunchClassLoader" : "net/fabricmc/loader/impl/launch/knot/KnotClassDelegate";
        Path out = Paths.get(a[1]);
        byte[] in;
        try (JarFile j = new JarFile(a[2])) {
            ZipEntry e = j.getEntry(cls + ".class");
            in = j.getInputStream(e).readAllBytes();
        }
        byte[] patched = rfb ? RfbPatcher.patch(in) : KnotPatcher.patch(in);
        Path f = out.resolve(cls + ".class");
        Files.createDirectories(f.getParent());
        Files.write(f, patched);
        System.out.println(a[0] + " original=" + in.length + " patched=" + patched.length);

        URL[] urls = new URL[a.length - 2];
        for (int i = 2; i < a.length; i++) urls[i - 2] = Paths.get(a[i]).toUri().toURL();
        ClassLoader cl = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        StringWriter sw = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(patched), cl, false, new PrintWriter(sw));
        boolean ok = sw.toString().isEmpty();
        System.out.println("CheckClassAdapter(dataflow, types resolved via jars): " + (ok ? "OK, no errors" : "ERRORS\n" + sw));
        if (!ok) System.exit(1);
    }
}

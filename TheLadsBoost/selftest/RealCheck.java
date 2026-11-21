import lads.boost.KnotPatcher;
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

/** args: outDir fabric-loader.jar [more jars for type resolution...]. Patches the REAL class and verifies it. */
public class RealCheck {
    public static void main(String[] a) throws Exception {
        Path out = Paths.get(a[0]);
        byte[] in;
        try (JarFile j = new JarFile(a[1])) {
            ZipEntry e = j.getEntry("net/fabricmc/loader/impl/launch/knot/KnotClassDelegate.class");
            in = j.getInputStream(e).readAllBytes();
        }
        byte[] patched = KnotPatcher.patch(in);
        Path f = out.resolve("net/fabricmc/loader/impl/launch/knot/KnotClassDelegate.class");
        Files.createDirectories(f.getParent());
        Files.write(f, patched);
        System.out.println("original=" + in.length + " patched=" + patched.length);

        URL[] urls = new URL[a.length - 1];
        for (int i = 1; i < a.length; i++) urls[i - 1] = Paths.get(a[i]).toUri().toURL();
        ClassLoader cl = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        StringWriter sw = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(patched), cl, false, new PrintWriter(sw));
        System.out.println("CheckClassAdapter(dataflow, types resolved via jars): " + (sw.toString().isEmpty() ? "OK, no errors" : "ERRORS\n" + sw));
    }
}

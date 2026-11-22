package lads.boost;

import org.objectweb.asm.ClassReader;

/** Supertype names (dotted) of a class file, or null when it does not parse. */
final class Supers {
    static String[] of(byte[] b) {
        try {
            ClassReader r = new ClassReader(b);
            String s = r.getSuperName();
            String[] i = r.getInterfaces();
            String[] out = new String[i.length + (s == null ? 0 : 1)];
            int k = 0;
            if (s != null) out[k++] = s.replace('/', '.');
            for (String x : i) out[k++] = x.replace('/', '.');
            return out;
        } catch (Throwable t) { return null; }
    }
}

package com.thelads.core.client.social.modsync;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.function.LongConsumer;

/**
 * ModSync on P2P stream 2. The guest asks in lines, the host answers each with one line and, after "ok", that many bytes:
 * <pre>
 * list            ->  ok &lt;bytes&gt; + the host's list (ModSyncList JSON)
 * get &lt;sha512&gt;    ->  ok &lt;bytes&gt; + the jar      |  no &lt;reason&gt;
 * bye relaunch    ->  (nothing) the guest restarts with the mods and comes back
 * </pre>
 */
final class ModSyncWire {
    static final int MAX_LINE = 256;

    private ModSyncWire() {}

    static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** One line without its newline; null at the end of the stream. A longer line than MAX_LINE is a broken peer. */
    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') return line.toString(StandardCharsets.UTF_8);
            if (line.size() >= MAX_LINE) throw new IOException("ModSync line too long");
            line.write(b);
        }
        if (line.size() > 0) throw new EOFException("ModSync stream ended inside a line");
        return null;
    }

    static byte[] readBytes(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(data, off, len - off);
            if (n < 0) throw new EOFException("ModSync stream ended early");
            off += n;
        }
        return data;
    }

    /**
     * Reads the entry's bytes from in into target, through target + ".part": never more than the entry's size, and target appears
     * only when size and SHA-512 are the entry's. Anything else throws and leaves no file. in is read to len bytes only when
     * len is the entry's size (a P2P answer); len -1 reads to the end (a download).
     */
    static void receive(InputStream in, long len, ModSyncEntry entry, Path target, LongConsumer progress) throws IOException {
        if (len >= 0 && len != entry.size()) throw new IOException(entry.file() + ": " + len + " bytes offered, " + entry.size() + " expected");
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.createDirectories(target.getParent());
        MessageDigest sha = ModSyncHash.sha512();
        long got = 0;
        boolean ok = false;
        try {
            try (OutputStream out = Files.newOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                while (len < 0 || got < len) {
                    int want = len < 0 ? buf.length : (int) Math.min(buf.length, len - got);
                    int n = in.read(buf, 0, want);
                    if (n < 0) break;
                    got += n;
                    if (got > entry.size()) throw new IOException(entry.file() + ": more than " + entry.size() + " bytes");
                    sha.update(buf, 0, n);
                    out.write(buf, 0, n);
                    if (progress != null) progress.accept(n);
                }
            }
            if (got != entry.size()) throw new IOException(entry.file() + ": " + got + " of " + entry.size() + " bytes");
            if (!ModSyncHash.hex(sha.digest()).equals(entry.sha512())) throw new IOException(entry.file() + ": hash check failed");
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            ok = true;
        } finally {
            if (!ok) Files.deleteIfExists(part);
        }
    }
}

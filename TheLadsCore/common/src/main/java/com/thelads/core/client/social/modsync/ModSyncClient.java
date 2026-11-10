package com.thelads.core.client.social.modsync;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongConsumer;

/** The guest's end of stream 2 (see ModSyncWire): one request at a time, from one thread. */
public final class ModSyncClient {
    private final InputStream in;
    private final OutputStream out;

    public ModSyncClient(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /** The host's list; IOException when the host does not answer with a valid one. */
    public List<ModSyncEntry> list() throws IOException {
        ModSyncWire.writeLine(out, "list");
        long len = ok(ModSyncWire.readLine(in));
        if (len > ModSyncList.MAX_JSON) throw new IOException("ModSync: the host's mod list is too large");
        try {
            return ModSyncList.parse(new String(ModSyncWire.readBytes(in, (int) len), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IOException("ModSync: " + e.getMessage());
        }
    }

    /** Fetches the entry's jar from the host into target; it is there only when its size and SHA-512 are the entry's. */
    public void fetch(ModSyncEntry entry, Path target, LongConsumer progress) throws IOException {
        ModSyncWire.writeLine(out, "get " + entry.sha512());
        ModSyncWire.receive(in, ok(ModSyncWire.readLine(in)), entry, target, progress);
    }

    /** Tells the host this guest restarts with the mods and comes back. */
    public void byeRelaunch() throws IOException {
        ModSyncWire.writeLine(out, "bye relaunch");
    }

    private static long ok(String answer) throws IOException {
        if (answer == null) throw new IOException("ModSync: the host closed the link");
        if (!answer.startsWith("ok ")) throw new IOException("ModSync: the host refused (" + answer + ")");
        try {
            long len = Long.parseLong(answer.substring(3));
            if (len < 0) throw new NumberFormatException();
            return len;
        } catch (NumberFormatException e) {
            throw new IOException("ModSync: bad answer from the host");
        }
    }
}

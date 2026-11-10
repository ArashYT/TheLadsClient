package com.thelads.core.client.social.modsync;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The host's end of stream 2 for one guest (see ModSyncWire). It writes only in answer to a request, and serves nothing but its
 * published list and the jars on that list, each at most MAX_SENDS times: a guest can neither read another file nor drain the
 * host's upload (or the relay budget) by asking again and again.
 */
public final class ModSyncServer {
    static final int MAX_SENDS = 2, MAX_LISTS = 4;

    /** The host's published jars: entry and where it is on disk. */
    public record Shared(ModSyncEntry entry, Path path) {}

    private final List<Shared> shared;
    private final Map<String, Integer> sends = new HashMap<>();
    private int lists;
    private volatile boolean guestRelaunches;

    public ModSyncServer(List<Shared> shared) {
        this.shared = List.copyOf(shared);
    }

    /** The guest said it restarts with the mods: the host lets it back in without asking again. */
    public boolean guestRelaunches() {
        return guestRelaunches;
    }

    /** Answers requests until the stream ends or the guest breaks the protocol (IOException). */
    public void serve(InputStream in, OutputStream out) throws IOException {
        String line;
        while ((line = ModSyncWire.readLine(in)) != null) {
            if (line.equals("list")) {
                if (++lists > MAX_LISTS) throw new IOException("ModSync: list asked too often");
                byte[] json = ModSyncList.toJson(shared.stream().map(Shared::entry).toList()).getBytes(StandardCharsets.UTF_8);
                ModSyncWire.writeLine(out, "ok " + json.length);
                out.write(json);
                out.flush();
            } else if (line.startsWith("get ")) {
                send(line.substring(4), out);
            } else if (line.equals("bye relaunch")) {
                guestRelaunches = true;
            } else {
                throw new IOException("ModSync: unknown request");
            }
        }
    }

    private void send(String sha512, OutputStream out) throws IOException {
        Shared found = null;
        for (Shared s : shared) if (s.entry().sha512().equals(sha512)) found = s;
        if (found == null) {
            ModSyncWire.writeLine(out, "no not shared");
            return;
        }
        if (sends.merge(sha512, 1, Integer::sum) > MAX_SENDS) throw new IOException("ModSync: jar asked too often");
        long size = found.entry().size();
        // The launcher hashed the jar before the game started; a jar that changed size since is not the listed one.
        if (!Files.isRegularFile(found.path()) || Files.size(found.path()) != size) {
            ModSyncWire.writeLine(out, "no changed on disk");
            return;
        }
        try (InputStream file = Files.newInputStream(found.path())) {
            ModSyncWire.writeLine(out, "ok " + size);
            byte[] buf = new byte[16 * 1024];
            long left = size;
            while (left > 0) {
                int n = file.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("ModSync: jar shrank while it was sent"); // the stream is out of step: end it
                out.write(buf, 0, n);
                left -= n;
            }
            out.flush();
        }
    }
}

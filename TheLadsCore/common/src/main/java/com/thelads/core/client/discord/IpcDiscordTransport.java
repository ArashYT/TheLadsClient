package com.thelads.core.client.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discord's local RPC over the desktop app's named pipe (discord-ipc-0..9), in plain Java so every game version can use it,
 * 1.8.9's Java 8 included. Frames are opcode and length (little-endian ints) then UTF-8 JSON. No tokens, no HTTP.
 * ponytail: Windows pipes only (the launcher is Windows-only); macOS/Linux need a Unix socket at $XDG_RUNTIME_DIR/discord-ipc-0.
 */
public final class IpcDiscordTransport implements DiscordRpcService.Transport {
    static final String SITE = "https://ladsclient.arashyt.ca";
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/Discord");
    private static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2, PING = 3, PONG = 4;
    record Pipe(DataInput in, DataOutput out, Closeable closer) {}
    interface Opener { Pipe open() throws IOException; }

    private final Opener opener;
    private Pipe pipe;
    private DiscordRpcService.Events events;

    public IpcDiscordTransport() { this(IpcDiscordTransport::windowsPipe); }
    IpcDiscordTransport(Opener opener) { this.opener = opener; }

    private static Pipe windowsPipe() throws IOException {
        IOException missing = new IOException("Discord is not running");
        for (int i = 0; i < 10; i++) {
            try {
                RandomAccessFile file = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                return new Pipe(file, file, file);
            } catch (IOException absent) { missing = absent; }
        }
        throw missing;
    }

    @Override public void initialize(String applicationId, DiscordRpcService.Events events) {
        this.events = events;
        try {
            pipe = opener.open();
            JsonObject hello = new JsonObject();
            hello.addProperty("v", 1);
            hello.addProperty("client_id", applicationId);
            write(HANDSHAKE, hello);
            JsonObject ready = reply(null);
            if (ready != null && "READY".equals(string(ready, "evt"))) events.ready();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    // Discord sends nothing unasked over IPC except pings, which are answered while waiting for a reply.
    @Override public void callbacks() {}
    @Override public void update(DiscordPresence presence) { setActivity(activity(presence)); }
    @Override public void clear() { if (pipe != null) setActivity(null); }
    @Override public void shutdown() {
        Pipe current = pipe;
        pipe = null;
        if (current != null) try { current.closer().close(); } catch (IOException ignored) {}
    }

    static JsonObject activity(DiscordPresence presence) {
        JsonObject activity = new JsonObject();
        if (!presence.details().isEmpty()) activity.addProperty("details", presence.details());
        if (!presence.state().isEmpty()) activity.addProperty("state", presence.state());
        if (presence.startTimestamp() > 0) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", presence.startTimestamp() * 1000);
            activity.add("timestamps", timestamps);
        }
        JsonObject button = new JsonObject();
        button.addProperty("label", "Get The Lads Client");
        button.addProperty("url", SITE);
        JsonArray buttons = new JsonArray();
        buttons.add(button);
        activity.add("buttons", buttons);
        return activity;
    }

    private void setActivity(JsonObject activity) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", PID);
        if (activity != null) args.add("activity", activity);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        try {
            write(FRAME, command);
            JsonObject answer = reply("SET_ACTIVITY");
            if (answer == null) return;
            if ("ERROR".equals(string(answer, "evt"))) {
                int code = code(answer);
                LOGGER.warn("Discord refused the activity ({}): {}", code, answer.get("data"));
                events.error(code);
            } else if (activity != null) {
                LOGGER.info("Discord presence: {} / {}", string(activity, "details"), string(activity, "state"));
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** The next answer to {@code command} (null: the handshake's READY), answering pings; null when Discord closed the pipe. */
    private JsonObject reply(String command) throws IOException {
        while (true) {
            int op = Integer.reverseBytes(pipe.in().readInt());
            int length = Integer.reverseBytes(pipe.in().readInt());
            if (length < 0 || length > 1 << 20) throw new IOException("Bad Discord frame length " + length);
            byte[] body = new byte[length];
            pipe.in().readFully(body);
            JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            JsonObject json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            if (op == PING) { writeRaw(PONG, body); continue; }
            if (op == CLOSE) {
                int code = json.has("code") ? json.get("code").getAsInt() : code(json);
                LOGGER.info("Discord closed the connection ({}): {}", code, string(json, "message"));
                events.disconnected(code);
                return null;
            }
            if (op != FRAME) continue;
            if (command == null ? json.has("evt") : command.equals(string(json, "cmd"))) return json;
        }
    }

    private void write(int op, JsonObject json) throws IOException { writeRaw(op, json.toString().getBytes(StandardCharsets.UTF_8)); }
    private void writeRaw(int op, byte[] body) throws IOException {
        // One write per frame: a named pipe in message mode would split a header written on its own.
        byte[] frame = new byte[8 + body.length];
        putInt(frame, 0, op);
        putInt(frame, 4, body.length);
        System.arraycopy(body, 0, frame, 8, body.length);
        pipe.out().write(frame);
    }
    private static void putInt(byte[] into, int at, int value) {
        for (int i = 0; i < 4; i++) into[at + i] = (byte) (value >>> (8 * i));
    }
    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
    private static int code(JsonObject answer) {
        JsonElement data = answer.get("data");
        return data != null && data.isJsonObject() && data.getAsJsonObject().has("code") ? data.getAsJsonObject().get("code").getAsInt() : 0;
    }

    private static final long PID = pid();
    private static long pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        try { return Long.parseLong(name.substring(0, name.indexOf('@'))); } catch (RuntimeException unknown) { return 0; }
    }
}

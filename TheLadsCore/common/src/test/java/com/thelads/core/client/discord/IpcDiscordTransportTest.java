package com.thelads.core.client.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IpcDiscordTransportTest {
    /** A scripted Discord: its replies are queued up front, and every frame the transport writes is kept. */
    static class FakeDiscord implements DiscordRpcService.Events {
        final ByteArrayOutputStream replies = new ByteArrayOutputStream(), written = new ByteArrayOutputStream();
        final List<String> events = new ArrayList<>();
        FakeDiscord reply(int op, String json) {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            replies.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.length).array());
            replies.writeBytes(body);
            return this;
        }
        IpcDiscordTransport transport() {
            return new IpcDiscordTransport(() -> new IpcDiscordTransport.Pipe(new DataInputStream(new ByteArrayInputStream(replies.toByteArray())),
                new DataOutputStream(written), () -> events.add("closed")));
        }
        List<int[]> ops = new ArrayList<>();
        List<JsonObject> frames() {
            ByteBuffer buffer = ByteBuffer.wrap(written.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
            List<JsonObject> frames = new ArrayList<>();
            ops.clear();
            while (buffer.hasRemaining()) {
                int op = buffer.getInt(); byte[] body = new byte[buffer.getInt()]; buffer.get(body);
                ops.add(new int[] {op});
                frames.add(JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject());
            }
            return frames;
        }
        public void ready() { events.add("ready"); }
        public void disconnected(int code) { events.add("disconnected:" + code); }
        public void error(int code) { events.add("error:" + code); }
    }

    @Test void handshakeThenActivityWithPingAnsweredAndLadsButton() {
        var discord = new FakeDiscord()
            .reply(1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{\"v\":1}}")
            .reply(3, "{\"ping\":1}")
            .reply(1, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":null,\"data\":{}}")
            .reply(1, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":null,\"data\":null}");
        var transport = discord.transport();
        transport.initialize("1556191716925640744", discord);
        transport.update(new DiscordPresence("Browsing servers", "Minecraft 26.2", 1_700_000_000));
        transport.clear();
        transport.shutdown();
        assertEquals(List.of("ready", "closed"), discord.events);
        var frames = discord.frames();
        assertEquals(List.of(0, 1, 4, 1), discord.ops.stream().map(op -> op[0]).toList());
        assertEquals("1556191716925640744", frames.get(0).get("client_id").getAsString());
        var activity = frames.get(1).getAsJsonObject("args").getAsJsonObject("activity");
        assertEquals("SET_ACTIVITY", frames.get(1).get("cmd").getAsString());
        assertTrue(frames.get(1).getAsJsonObject("args").get("pid").getAsLong() > 0);
        assertEquals("Browsing servers", activity.get("details").getAsString());
        assertEquals("Minecraft 26.2", activity.get("state").getAsString());
        assertEquals(1_700_000_000_000L, activity.getAsJsonObject("timestamps").get("start").getAsLong());
        assertEquals(IpcDiscordTransport.SITE, activity.getAsJsonArray("buttons").get(0).getAsJsonObject().get("url").getAsString());
        assertEquals("{\"ping\":1}", frames.get(2).toString());
        assertFalse(frames.get(3).getAsJsonObject("args").has("activity"), "clearing sends no activity");
    }

    @Test void refusedActivityAndClosedPipeReachTheService() {
        var refused = new FakeDiscord()
            .reply(1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\"}")
            .reply(1, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":\"ERROR\",\"data\":{\"code\":4000,\"message\":\"bad\"}}");
        var transport = refused.transport();
        transport.initialize("1556191716925640744", refused);
        transport.update(new DiscordPresence("In the menus", "Minecraft 1.8.9", 0));
        assertEquals(List.of("ready", "error:4000"), refused.events);
        var activity = refused.frames().get(1).getAsJsonObject("args").getAsJsonObject("activity");
        assertFalse(activity.has("timestamps"), "elapsed time off sends no start");

        var closed = new FakeDiscord().reply(2, "{\"code\":4000,\"message\":\"Invalid Client ID\"}");
        closed.transport().initialize("1556191716925640744", closed);
        assertEquals(List.of("disconnected:4000"), closed.events);

        var gone = new FakeDiscord();
        assertThrows(UncheckedIOException.class, () -> gone.transport().initialize("1556191716925640744", gone));
    }
}

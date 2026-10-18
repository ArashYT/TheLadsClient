package com.thelads.core.client.cosmetics.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.ladsapi.LadsPresence;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CosmeticsLinkTest {
    private static final byte[] MASK = {0x11, 0x22, 0x33, 0x44};

    private static byte[] payload(int n) {
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) bytes[i] = (byte) (i * 7);
        return bytes;
    }

    private static byte[] unmasked(byte[] frame, int head, int n) {
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) (frame[head + 4 + i] ^ frame[head + (i & 3)]);
        return out;
    }

    @Test
    void encodesMaskedFramesWithAllThreeLengthForms() {
        byte[] small = WebSocket.encode(WebSocket.TEXT, payload(5), MASK);
        assertEquals((byte) 0x81, small[0]);
        assertEquals((byte) (0x80 | 5), small[1]);
        assertArrayEquals(MASK, Arrays.copyOfRange(small, 2, 6));
        assertArrayEquals(payload(5), unmasked(small, 2, 5));

        byte[] medium = WebSocket.encode(WebSocket.TEXT, payload(300), MASK);
        assertEquals((byte) (0x80 | 126), medium[1]);
        assertEquals(300, ((medium[2] & 0xFF) << 8) | (medium[3] & 0xFF));
        assertArrayEquals(payload(300), unmasked(medium, 4, 300));

        byte[] large = WebSocket.encode(WebSocket.PONG, payload(70_000), MASK);
        assertEquals((byte) 0x8A, large[0]);
        assertEquals((byte) (0x80 | 127), large[1]);
        long length = 0;
        for (int i = 0; i < 8; i++) length = (length << 8) | (large[2 + i] & 0xFF);
        assertEquals(70_000, length);
        assertArrayEquals(payload(70_000), unmasked(large, 10, 70_000));
    }

    private static DataInputStream server(int b0, int lengthForm, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(b0);
        if (lengthForm == 126) { out.writeByte(126); out.writeShort(payload.length); }
        else if (lengthForm == 127) { out.writeByte(127); out.writeLong(payload.length); }
        else out.writeByte(payload.length);
        out.write(payload);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void decodesServerFramesOfEveryLength() throws IOException {
        for (int[] form : new int[][]{{0, 100}, {126, 300}, {127, 70_000}}) {
            WebSocket.Frame frame = WebSocket.decode(server(0x81, form[0], payload(form[1])), WebSocket.MAX_IN);
            assertEquals(WebSocket.TEXT, frame.opcode());
            assertArrayEquals(payload(form[1]), frame.payload());
        }
        assertEquals(WebSocket.PING, WebSocket.decode(server(0x89, 0, payload(4)), WebSocket.MAX_IN).opcode());
    }

    @Test
    void rejectsFragmentsMaskingReservedBitsAndOversizedFrames() {
        assertThrows(ProtocolException.class, () -> WebSocket.decode(server(0x01, 0, payload(3)), WebSocket.MAX_IN)); // FIN clear
        assertThrows(ProtocolException.class, () -> WebSocket.decode(server(0x80, 0, payload(3)), WebSocket.MAX_IN)); // continuation
        assertThrows(ProtocolException.class, () -> WebSocket.decode(server(0xC1, 0, payload(3)), WebSocket.MAX_IN)); // RSV1
        assertThrows(ProtocolException.class, () -> WebSocket.decode(server(0x81, 127, payload(2000)), 1024));
        assertThrows(ProtocolException.class, () -> WebSocket.decode(server(0x89, 126, payload(200)), WebSocket.MAX_IN)); // control > 125
        byte[] masked = WebSocket.encode(WebSocket.TEXT, payload(3), MASK); // a server must never mask
        assertThrows(ProtocolException.class, () -> WebSocket.decode(new DataInputStream(new ByteArrayInputStream(masked)), WebSocket.MAX_IN));
    }

    @Test
    void handshakeAcceptMatchesRfc6455Example() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocket.accept("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void serverIdMatchesTheBackend() {
        // Same vectors as cosmetics-server/test_cosmetics.py.
        assertEquals("7344bf77af790171ed8daccf461e5258dc0d3f54", CosmeticsLink.serverId("00000000000000000000000000000000"));
        assertEquals("-3e76dc0d4181c87686d0e839cfbe74994ab51a04", CosmeticsLink.serverId("00000000000000000000000000000001"));
    }

    @Test
    void roomIsTheHashOfLowercasedHostAndPort() {
        String hypixel = "73e11bb8b9e5e76c8dfe3801e05168eb3975a3e008dade48b658218433c05b0e";
        assertEquals(hypixel, CosmeticsLink.room("mc.hypixel.net"));
        assertEquals(hypixel, CosmeticsLink.room(" MC.Hypixel.NET:25565 "));
        assertTrue(!hypixel.equals(CosmeticsLink.room("mc.hypixel.net:25566")));
        assertNull(CosmeticsLink.room("Singleplayer"));
        assertNull(CosmeticsLink.room("Unknown"));
        assertNull(CosmeticsLink.room(""));
        assertNull(CosmeticsLink.room(null));
    }

    @Test
    void backoffDoublesFromFiveSecondsToFiveMinutes() {
        assertEquals(5_000, CosmeticsLink.backoffMs(1, 0));
        assertEquals(10_000, CosmeticsLink.backoffMs(2, 0));
        assertEquals(160_000, CosmeticsLink.backoffMs(6, 0));
        assertEquals(300_000, CosmeticsLink.backoffMs(7, 0));
        assertEquals(300_000, CosmeticsLink.backoffMs(1000, 0));
        assertEquals(4_000, CosmeticsLink.backoffMs(1, 1)); // at most 20 % jitter, always down
    }

    @Test
    void endpointOverridesAllowWssAnywhereButWsOnlyToThisPc() {
        assertEquals(CosmeticsLink.DEFAULT_URL, CosmeticsLink.endpoint(null).toString());
        assertEquals(CosmeticsLink.DEFAULT_URL, CosmeticsLink.endpoint(" ").toString());
        assertNotNull(CosmeticsLink.endpoint("ws://127.0.0.1:8095/cosmetics/ws"));
        assertNotNull(CosmeticsLink.endpoint("ws://localhost:8095/cosmetics/ws"));
        assertNotNull(CosmeticsLink.endpoint("wss://example.org/cosmetics/ws"));
        assertNull(CosmeticsLink.endpoint("ws://example.org/cosmetics/ws"));
        assertNull(CosmeticsLink.endpoint("http://127.0.0.1/cosmetics/ws"));
        assertNull(CosmeticsLink.endpoint("not a url"));
    }

    @Test
    void uuidsTravelWithoutDashes() {
        UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertEquals("069a79f444e94726a5befca90e38aaf5", CosmeticsLink.plain(id));
        assertEquals(id, CosmeticsLink.uuid("069a79f444e94726a5befca90e38aaf5"));
        assertNull(CosmeticsLink.uuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertNull(CosmeticsLink.uuid("zz9a79f444e94726a5befca90e38aaf5"));
        assertEquals("{\"t\":\"emote\",\"id\":null}", CosmeticsLink.json("t", "emote", "id", null));
    }

    @Test
    void messagesUpdateEmotesForOthersOnly() {
        UUID self = UUID.randomUUID(), other = UUID.randomUUID(), third = UUID.randomUUID();
        CosmeticsLink link = new CosmeticsLink("ws://127.0.0.1:1/cosmetics/ws");
        link.handle("{\"t\":\"snapshot\",\"players\":[{\"uuid\":\"" + CosmeticsLink.plain(other) + "\",\"emote\":\"lads:groove\",\"age\":2000},"
            + "{\"uuid\":\"" + CosmeticsLink.plain(self) + "\",\"emote\":\"lads:groove\",\"age\":0},"
            + "{\"uuid\":\"" + CosmeticsLink.plain(third) + "\",\"emote\":null,\"age\":0}]}", self);
        Emotes.Playing playing = Emotes.get(other);
        assertNotNull(playing);
        assertEquals("lads:groove", playing.id());
        assertTrue(System.currentTimeMillis() - playing.startMs() >= 2000, "late joiner starts the loop where it is");
        assertNull(Emotes.get(self), "our own echo is ignored");
        assertNull(Emotes.get(third));

        link.handle("{\"t\":\"state\",\"uuid\":\"" + CosmeticsLink.plain(other) + "\",\"emote\":null,\"age\":0}", self);
        assertTrue(Emotes.get(other).stopping(), "a stop blends out");
        link.handle("{\"t\":\"leave\",\"uuid\":\"" + CosmeticsLink.plain(other) + "\"}", self);
        assertNull(Emotes.get(other));

        link.handle("not json", self); // ignored, never thrown
        link.handle("{\"t\":\"state\",\"uuid\":42}", self);
    }

    @Test
    void messagesUpdateRelayPresence() {
        UUID self = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        LadsPresence.relayClear();
        assertFalse(LadsPresence.isLads(p1));
        assertFalse(LadsPresence.isLads(p2));

        CosmeticsLink link = new CosmeticsLink("ws://127.0.0.1:1/cosmetics/ws");

        // 1. Snapshot adds all players in the room
        link.handle("{\"t\":\"snapshot\",\"players\":["
            + "{\"uuid\":\"" + CosmeticsLink.plain(p1) + "\",\"emote\":null,\"age\":0},"
            + "{\"uuid\":\"" + CosmeticsLink.plain(p2) + "\",\"emote\":\"lads:wave\",\"age\":1000}"
            + "]}", self);

        assertTrue(LadsPresence.isLads(p1));
        assertTrue(LadsPresence.isLads(p2));
        assertFalse(LadsPresence.isLads(p3));

        // 2. Join adds a newly joined player
        link.handle("{\"t\":\"join\",\"uuid\":\"" + CosmeticsLink.plain(p3) + "\"}", self);
        assertTrue(LadsPresence.isLads(p3));

        // 3. Leave removes the player
        link.handle("{\"t\":\"leave\",\"uuid\":\"" + CosmeticsLink.plain(p1) + "\"}", self);
        assertFalse(LadsPresence.isLads(p1));
        assertTrue(LadsPresence.isLads(p2));
        assertTrue(LadsPresence.isLads(p3));

        // 4. Another snapshot replaces/clears previous relay presence
        UUID p4 = UUID.randomUUID();
        link.handle("{\"t\":\"snapshot\",\"players\":["
            + "{\"uuid\":\"" + CosmeticsLink.plain(p4) + "\",\"emote\":null,\"age\":0}"
            + "]}", self);
        assertFalse(LadsPresence.isLads(p2));
        assertFalse(LadsPresence.isLads(p3));
        assertTrue(LadsPresence.isLads(p4));

        LadsPresence.relayClear();
        assertFalse(LadsPresence.isLads(p4));
    }
}

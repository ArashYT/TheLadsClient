package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.cosmetics.net.WebSocket;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SocialClientTest {

    @Test
    public void backoffCalculationFollowsSpecification() {
        // 5s doubling up to 5 min, minus up to 20% jitter
        long b1_min = SocialClient.backoffMs(1, 1.0);
        long b1_max = SocialClient.backoffMs(1, 0.0);
        assertEquals(4000L, b1_min);
        assertEquals(5000L, b1_max);

        long b2_min = SocialClient.backoffMs(2, 1.0);
        long b2_max = SocialClient.backoffMs(2, 0.0);
        assertEquals(8000L, b2_min);
        assertEquals(10000L, b2_max);

        long b7_min = SocialClient.backoffMs(7, 1.0);
        long b7_max = SocialClient.backoffMs(7, 0.0);
        assertEquals(240000L, b7_min);
        assertEquals(300000L, b7_max);

        long b10_max = SocialClient.backoffMs(10, 0.0);
        assertEquals(300000L, b10_max, "Must cap at 5 minutes");
    }

    @Test
    public void endpointOverrideValidation() {
        // Valid default
        URI def = SocialClient.resolveEndpoint(null);
        assertEquals("wss", def.getScheme());

        // Local ws allowed
        URI local = SocialClient.resolveEndpoint("ws://127.0.0.1:8080/social/ws");
        assertNotNull(local);
        assertEquals("ws", local.getScheme());

        // Remote insecure ws rejected
        URI remoteInsecure = SocialClient.resolveEndpoint("ws://malicious.example.com/social/ws");
        assertNull(remoteInsecure, "Remote plain ws must be rejected");

        // Remote wss allowed
        URI remoteSecure = SocialClient.resolveEndpoint("wss://custom.example.com/social/ws");
        assertNotNull(remoteSecure);
    }

    @Test
    public void boundedQueueDropsOldestPresenceFirst() {
        SocialClient client = new SocialClient("ws://127.0.0.1:1234/social/ws");
        // Client is not connected, so all sends queue in outbound
        for (int i = 0; i < 500; i++) {
            client.send(SocialProtocol.DM, "to", UUID.randomUUID().toString(), "text", "msg" + i);
        }
        assertEquals(500, client.outboundQueueSize());

        // Add 10 presence messages
        for (int i = 0; i < 10; i++) {
            client.send(SocialProtocol.PRESENCE, "state", "presence" + i);
        }
        assertEquals(510, client.outboundQueueSize());

        // Add 2 more DMs: fills queue to 512
        client.send(SocialProtocol.DM, "to", UUID.randomUUID().toString(), "text", "fill1");
        client.send(SocialProtocol.DM, "to", UUID.randomUUID().toString(), "text", "fill2");
        assertEquals(512, client.outboundQueueSize());

        // Now adding 1 more DM should drop the oldest presence message, keeping total size at 512
        client.send(SocialProtocol.DM, "to", UUID.randomUUID().toString(), "text", "overflow");
        assertEquals(512, client.outboundQueueSize());

        List<JsonObject> queued = client.outboundSnapshot();
        long remainingPresences = queued.stream().filter(m -> "presence".equals(m.get("t").getAsString())).count();
        assertEquals(9, remainingPresences, "One presence message should have been dropped");
    }

    @Test
    public void clientCloseStopsRunningThreadAndSocket() {
        SocialClient client = new SocialClient("ws://127.0.0.1:1234/social/ws");
        client.start();
        client.close();
        assertFalse(client.isConnected());
        assertEquals("Disconnected", client.connectionStatus());
    }

    @Test
    public void reconnectsWithBackoffAndRestoresPresence() throws Exception {
        try (FakeWsServer server = new FakeWsServer()) {
            UUID testUuid = UUID.randomUUID();
            LadsGameBridge.set(new DefaultGameBridge() {
                @Override
                public Session session() {
                    return new Session(testUuid, "TestPlayer", "fake-token");
                }
            });

            SocialClient client = new SocialClient("ws://127.0.0.1:" + server.port() + "/social/ws");
            client.setFastBackoff(true);

            SocialBridge.GameInfo gameInfo = new SocialBridge.GameInfo("1.20.1", "fabric", "plain", "", "", "", "1.9.18");
            Presence presence = new Presence("singleplayer", gameInfo, null, null);
            client.setPresence(presence);

            client.start();

            try {
                // 1. Wait for first presence to be received on connection 1 (which then immediately drops)
                assertTrue(server.firstPresenceLatch.await(5, TimeUnit.SECONDS), "First presence must be delivered on initial connect");

                // 2. Wait for client to reconnect and deliver presence on connection 2
                assertTrue(server.secondPresenceLatch.await(5, TimeUnit.SECONDS), "Presence must be restored after reconnection");

                assertEquals(2, server.receivedPresences.size());
                assertTrue(server.receivedPresences.get(0).contains("\"state\":\"singleplayer\""));
                assertTrue(server.receivedPresences.get(1).contains("\"state\":\"singleplayer\""));
            } finally {
                client.close();
                LadsGameBridge.set(new DefaultGameBridge());
            }
            assertFalse(client.isConnected());
        }
    }

    private static class FakeWsServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<String> receivedPresences = new CopyOnWriteArrayList<>();
        private final CountDownLatch firstPresenceLatch = new CountDownLatch(1);
        private final CountDownLatch secondPresenceLatch = new CountDownLatch(1);
        private volatile boolean running = true;
        private final Thread thread;

        FakeWsServer() throws IOException {
            serverSocket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            thread = new Thread(this::run, "FakeWsServer");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void run() {
            int connectionCount = 0;
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    connectionCount++;
                    int connIdx = connectionCount;
                    handleConnection(socket, connIdx);
                } catch (IOException e) {
                    break;
                }
            }
        }

        private void handleConnection(Socket socket, int connIdx) {
            try {
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                OutputStream out = socket.getOutputStream();

                // Read HTTP upgrade request headers
                String key = null;
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    if (line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) {
                        key = line.substring("sec-websocket-key:".length()).trim();
                    }
                }
                if (key == null) {
                    socket.close();
                    return;
                }

                // Send 101 Switching Protocols
                String accept = computeWsAccept(key);
                String resp = "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
                out.write(resp.getBytes(StandardCharsets.ISO_8859_1));
                out.flush();

                // Send challenge
                sendServerText(out, "{\"t\":\"challenge\",\"nonce\":\"fake-nonce-" + connIdx + "\"}");

                // Read auth message from client
                String authJson = readClientText(in);

                // Send hello
                sendServerText(out, "{\"t\":\"hello\",\"friends\":[],\"requests\":{\"in\":[],\"out\":[]},\"blocked\":[],\"dms\":[]}");

                // Read presence message
                String presenceJson = readClientText(in);
                if (presenceJson != null && presenceJson.contains("\"t\":\"presence\"")) {
                    receivedPresences.add(presenceJson);
                    if (connIdx == 1) {
                        firstPresenceLatch.countDown();
                        // Drop first connection immediately to trigger client reconnect
                        socket.close();
                        return;
                    } else if (connIdx == 2) {
                        secondPresenceLatch.countDown();
                    }
                }

                // Keep connection 2 open until closed
                while (running && !socket.isClosed()) {
                    try {
                        String msg = readClientText(in);
                        if (msg == null) break;
                    } catch (Exception e) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }

        private static String readLine(DataInputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int c; (c = in.read()) != -1; ) {
                if (c == '\n') break;
                if (c != '\r') sb.append((char) c);
            }
            return sb.toString();
        }

        private static void sendServerText(OutputStream out, String text) throws IOException {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            int n = payload.length;
            if (n < 126) {
                out.write(new byte[]{(byte) 0x81, (byte) n});
            } else if (n <= 0xFFFF) {
                out.write(new byte[]{(byte) 0x81, 126, (byte) (n >>> 8), (byte) n});
            } else {
                byte[] header = new byte[10];
                header[0] = (byte) 0x81;
                header[1] = 127;
                for (int i = 0; i < 8; i++) header[2 + i] = (byte) ((long) n >>> (56 - 8 * i));
                out.write(header);
            }
            out.write(payload);
            out.flush();
        }

        private static String readClientText(DataInputStream in) throws IOException {
            int b0 = in.readUnsignedByte();
            int b1 = in.readUnsignedByte();
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) len = in.readUnsignedShort();
            else if (len == 127) len = in.readLong();

            byte[] mask = new byte[4];
            if (masked) in.readFully(mask);

            byte[] payload = new byte[(int) len];
            in.readFully(payload);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i & 3];
                }
            }
            return new String(payload, StandardCharsets.UTF_8);
        }

        private static String computeWsAccept(String key) throws Exception {
            String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((key + guid).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        }

        @Override
        public void close() {
            running = false;
            try { serverSocket.close(); } catch (Exception ignored) {}
            thread.interrupt();
        }
    }
}

package com.thelads.core.client.social.p2p;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TurnClientTest {
    private FakeTurnServer fakeServer;
    private int fakeServerPort;

    public static class FakeTurnServer implements Runnable {
        private final DatagramSocket serverSocket;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Thread thread;

        public final AtomicInteger packetsReceived = new AtomicInteger(0);
        public final AtomicInteger unboundChannelDrops = new AtomicInteger(0);
        public final AtomicInteger unpermittedDrops = new AtomicInteger(0);
        public final AtomicInteger validChannelDataCount = new AtomicInteger(0);
        public final AtomicInteger validSendIndicationCount = new AtomicInteger(0);

        public volatile byte[] lastChannelData = null;
        public volatile byte[] lastSendIndicationData = null;

        public final java.util.Set<InetAddress> permittedIps = ConcurrentHashMap.newKeySet();
        public final ConcurrentHashMap<Integer, InetSocketAddress> boundChannels = new ConcurrentHashMap<>();

        public FakeTurnServer() throws IOException {
            this.serverSocket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        public void start() {
            thread = new Thread(this, "FakeTurnServer");
            thread.setDaemon(true);
            thread.start();
        }

        public void stop() {
            running.set(false);
            serverSocket.close();
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void run() {
            byte[] buf = new byte[2048];
            while (running.get() && !serverSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    serverSocket.receive(packet);
                    packetsReceived.incrementAndGet();

                    // Check for ChannelData (RFC 8656 section 12)
                    if (TurnClient.isChannelData(buf, 0, packet.getLength())) {
                        int chNum = TurnClient.getChannelNumber(buf, 0);
                        if (!boundChannels.containsKey(chNum)) {
                            // REJECT: Channel is not bound!
                            unboundChannelDrops.incrementAndGet();
                            continue;
                        }
                        int len = TurnClient.getChannelDataLength(buf, 0);
                        byte[] payload = new byte[len];
                        System.arraycopy(buf, 4, payload, 0, len);
                        lastChannelData = payload;
                        validChannelDataCount.incrementAndGet();
                        continue;
                    }

                    StunMessage msg = StunMessage.decode(buf, 0, packet.getLength());

                    // Check for Send indication (RFC 8656 section 11.1)
                    if (msg.getType() == StunMessage.SEND_INDICATION) {
                        InetSocketAddress peer = msg.getXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS);
                        if (peer == null || !permittedIps.contains(peer.getAddress())) {
                            // REJECT: Peer does not have permission!
                            unpermittedDrops.incrementAndGet();
                            continue;
                        }
                        lastSendIndicationData = msg.getDataAttribute();
                        validSendIndicationCount.incrementAndGet();
                        continue;
                    }

                    if (msg.getType() == StunMessage.ALLOCATE_REQUEST) {
                        String user = msg.getStringAttribute(StunMessage.ATTR_USERNAME);
                        if (user == null) {
                            // Send 401 challenge
                            StunMessage err = new StunMessage(StunMessage.ALLOCATE_ERROR, msg.getTransactionId());
                            err.addErrorCode(401, "Unauthorized");
                            err.addStringAttribute(StunMessage.ATTR_REALM, "test.realm");
                            err.addStringAttribute(StunMessage.ATTR_NONCE, "nonce-xyz-123");
                            byte[] respBytes = err.encode(null, true);
                            serverSocket.send(new DatagramPacket(respBytes, respBytes.length, packet.getSocketAddress()));
                        } else {
                            // Verify credentials
                            byte[] key = StunMessage.deriveLongTermKey("testuser", "test.realm", "testpass");
                            assertTrue(StunMessage.validateMessageIntegrity(buf, 0, packet.getLength(), key));

                            // Send 201 Success
                            StunMessage ok = new StunMessage(StunMessage.ALLOCATE_RESPONSE, msg.getTransactionId());
                            ok.addXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS, new InetSocketAddress("127.0.0.1", 45678));
                            ok.addIntAttribute(StunMessage.ATTR_LIFETIME, 600);
                            byte[] respBytes = ok.encode(key, true);
                            serverSocket.send(new DatagramPacket(respBytes, respBytes.length, packet.getSocketAddress()));
                        }
                    } else if (msg.getType() == StunMessage.CREATE_PERMISSION_REQUEST) {
                        byte[] key = StunMessage.deriveLongTermKey("testuser", "test.realm", "testpass");
                        InetSocketAddress peer = msg.getXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS);
                        if (peer != null) {
                            permittedIps.add(peer.getAddress());
                        }
                        StunMessage ok = new StunMessage(StunMessage.CREATE_PERMISSION_RESPONSE, msg.getTransactionId());
                        byte[] respBytes = ok.encode(key, true);
                        serverSocket.send(new DatagramPacket(respBytes, respBytes.length, packet.getSocketAddress()));
                    } else if (msg.getType() == StunMessage.CHANNEL_BIND_REQUEST) {
                        byte[] key = StunMessage.deriveLongTermKey("testuser", "test.realm", "testpass");
                        Integer chAttr = msg.getIntAttribute(StunMessage.ATTR_CHANNEL_NUMBER);
                        InetSocketAddress peer = msg.getXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS);
                        int ch = (chAttr != null) ? ((chAttr >>> 16) & 0xFFFF) : 0;
                        if (ch == 0 && chAttr != null) ch = chAttr & 0xFFFF;
                        if (ch >= 0x4000 && ch <= 0x7FFE && peer != null) {
                            boundChannels.put(ch, peer);
                        }
                        StunMessage ok = new StunMessage(StunMessage.CHANNEL_BIND_RESPONSE, msg.getTransactionId());
                        byte[] respBytes = ok.encode(key, true);
                        serverSocket.send(new DatagramPacket(respBytes, respBytes.length, packet.getSocketAddress()));
                    } else if (msg.getType() == StunMessage.REFRESH_REQUEST) {
                        byte[] key = StunMessage.deriveLongTermKey("testuser", "test.realm", "testpass");
                        Integer reqLt = msg.getIntAttribute(StunMessage.ATTR_LIFETIME);
                        int lt = (reqLt != null) ? reqLt : 300;
                        StunMessage ok = new StunMessage(StunMessage.REFRESH_RESPONSE, msg.getTransactionId());
                        ok.addIntAttribute(StunMessage.ATTR_LIFETIME, lt);
                        byte[] respBytes = ok.encode(key, true);
                        serverSocket.send(new DatagramPacket(respBytes, respBytes.length, packet.getSocketAddress()));
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        fakeServer = new FakeTurnServer();
        fakeServerPort = fakeServer.getPort();
        fakeServer.start();
    }

    @AfterEach
    public void tearDown() {
        if (fakeServer != null) {
            fakeServer.stop();
        }
    }

    @Test
    public void allocateAgainstFakeTurnServer() throws Exception {
        String turnUrl = "turn:127.0.0.1:" + fakeServerPort;
        try (TurnClient client = new TurnClient(turnUrl, "testuser", "testpass")) {
            InetSocketAddress relayed = client.allocate();
            assertNotNull(relayed);
            assertEquals("127.0.0.1", relayed.getAddress().getHostAddress());
            assertEquals(45678, relayed.getPort());
            assertEquals(600, client.getLifetimeSeconds());

            InetSocketAddress peer = new InetSocketAddress("127.0.0.1", 12345);

            // 1. REJECTION TEST: ChannelData on unbound channel MUST be rejected/dropped
            byte[] unboundPkt = "Unbound channel data".getBytes(StandardCharsets.UTF_8);
            client.sendChannelData(0x4000, unboundPkt, 0, unboundPkt.length);
            Thread.sleep(100);
            assertTrue(fakeServer.unboundChannelDrops.get() > 0, "Server must reject ChannelData on unbound channel");
            assertNull(fakeServer.lastChannelData, "Server must not deliver data from unbound channel");

            // 2. REJECTION TEST: Send indication to peer without permission MUST be rejected/dropped
            byte[] unpermittedPkt = "Unpermitted peer data".getBytes(StandardCharsets.UTF_8);
            client.sendIndication(peer, unpermittedPkt, 0, unpermittedPkt.length);
            Thread.sleep(100);
            assertTrue(fakeServer.unpermittedDrops.get() > 0, "Server must reject Send indication without permission");
            assertNull(fakeServer.lastSendIndicationData, "Server must not deliver data to unpermitted peer");

            // 3. SUCCESS TEST: CreatePermission allows Send indications
            assertDoesNotThrow(() -> client.createPermission(peer));
            assertTrue(client.hasPermission(peer.getAddress()));

            byte[] permittedPkt = "Permitted peer data".getBytes(StandardCharsets.UTF_8);
            client.sendIndication(peer, permittedPkt, 0, permittedPkt.length);
            Thread.sleep(100);
            assertTrue(fakeServer.validSendIndicationCount.get() > 0, "Server must accept Send indication with permission");
            assertArrayEquals(permittedPkt, fakeServer.lastSendIndicationData);

            // 4. SUCCESS TEST: ChannelBind allows ChannelData
            int channelNum = 0x4000;
            assertDoesNotThrow(() -> client.channelBind(channelNum, peer));
            assertTrue(client.isChannelBound(channelNum));
            assertEquals(channelNum, client.getBoundChannel(peer));

            byte[] boundPkt = "Bound channel payload".getBytes(StandardCharsets.UTF_8);
            client.sendChannelData(channelNum, boundPkt, 0, boundPkt.length);
            Thread.sleep(100);
            assertTrue(fakeServer.validChannelDataCount.get() > 0, "Server must accept ChannelData on bound channel");
            assertArrayEquals(boundPkt, fakeServer.lastChannelData);

            // 5. SUCCESS TEST: Refresh
            int newLifetime = client.refresh(300);
            assertEquals(300, newLifetime);
        }
    }

    /** A fake TURN server: 401 then signed (or, with sign=false, unsigned) answers to Allocate and ChannelBind. */
    private static DatagramSocket fakeTurn(boolean sign) throws IOException {
        DatagramSocket turn = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        byte[] key = StunMessage.deriveLongTermKey("u", "r", "c");
        Thread t = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (!turn.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    turn.receive(p);
                    StunMessage m = StunMessage.decode(buf, 0, p.getLength());
                    StunMessage r;
                    if (m.getType() == StunMessage.ALLOCATE_REQUEST && !m.hasAttribute(StunMessage.ATTR_USERNAME)) {
                        r = new StunMessage(StunMessage.ALLOCATE_ERROR, m.getTransactionId());
                        r.addErrorCode(401, "Unauthorized");
                        r.addStringAttribute(StunMessage.ATTR_REALM, "r");
                        r.addStringAttribute(StunMessage.ATTR_NONCE, "n");
                        byte[] out = r.encode(null, true);
                        turn.send(new DatagramPacket(out, out.length, p.getSocketAddress()));
                        continue;
                    } else if (m.getType() == StunMessage.ALLOCATE_REQUEST) {
                        r = new StunMessage(StunMessage.ALLOCATE_RESPONSE, m.getTransactionId());
                        r.addXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS, new InetSocketAddress("203.0.113.5", 5000));
                        r.addIntAttribute(StunMessage.ATTR_LIFETIME, 600);
                    } else if (m.getType() == StunMessage.CHANNEL_BIND_REQUEST) {
                        r = new StunMessage(StunMessage.CHANNEL_BIND_RESPONSE, m.getTransactionId());
                    } else {
                        continue;
                    }
                    byte[] out = r.encode(sign ? key : null, true);
                    turn.send(new DatagramPacket(out, out.length, p.getSocketAddress()));
                } catch (Exception e) {
                    if (turn.isClosed()) return;
                }
            }
        }, "fake-turn-src");
        t.setDaemon(true);
        t.start();
        return turn;
    }

    /** F6 (PocTurnSrc): ChannelData from anyone but the TURN server used to arrive as data from the bound peer (a blind FIN). */
    @Test
    public void datagramsFromOtherSourcesAreIgnored() throws Exception {
        try (DatagramSocket turn = fakeTurn(true); TurnClient tc = new TurnClient("turn:127.0.0.1:" + turn.getLocalPort(), "u", "c");
             DatagramSocket attacker = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Integer> got = new CompletableFuture<>();
            tc.setDataHandler((peer, data, off, len) -> got.complete(len));
            tc.allocate();
            int ch = tc.channelBind(new InetSocketAddress("198.51.100.7", 4444));
            byte[] fin = new byte[16];
            fin[0] = (byte) (0x80 | 0x10 | 0x02);
            byte[] cd = TurnClient.encodeChannelData(ch, fin, 0, fin.length);
            attacker.send(new DatagramPacket(cd, cd.length, InetAddress.getLoopbackAddress(), tc.getSocket().getLocalPort()));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> got.get(500, java.util.concurrent.TimeUnit.MILLISECONDS),
                    "ChannelData from a stranger must not reach the data handler");
            turn.send(new DatagramPacket(cd, cd.length, InetAddress.getLoopbackAddress(), tc.getSocket().getLocalPort()));
            assertEquals(16, got.get(2, java.util.concurrent.TimeUnit.SECONDS), "the same ChannelData from the TURN server is delivered");
        }
    }

    /** F6: an authenticated request's success answer without a valid MESSAGE-INTEGRITY (a spoofed relayed address) is refused. */
    @Test
    public void unsignedTurnResponseIsRefused() throws Exception {
        try (DatagramSocket turn = fakeTurn(false); TurnClient tc = new TurnClient("turn:127.0.0.1:" + turn.getLocalPort(), "u", "c")) {
            assertThrows(IOException.class, tc::allocate);
        }
    }
}

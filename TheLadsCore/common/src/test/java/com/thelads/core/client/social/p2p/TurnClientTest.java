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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class TurnClientTest {
    private FakeTurnServer fakeServer;
    private int fakeServerPort;

    private static class FakeTurnServer implements Runnable {
        private final DatagramSocket serverSocket;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Thread thread;
        public volatile int packetsReceived = 0;
        public volatile byte[] lastChannelData = null;

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
                    packetsReceived++;

                    if (TurnClient.isChannelData(buf, 0, packet.getLength())) {
                        int chNum = TurnClient.getChannelNumber(buf, 0);
                        int len = TurnClient.getChannelDataLength(buf, 0);
                        byte[] payload = new byte[len];
                        System.arraycopy(buf, 4, payload, 0, len);
                        lastChannelData = payload;
                        continue;
                    }

                    StunMessage msg = StunMessage.decode(buf, 0, packet.getLength());
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
                        StunMessage ok = new StunMessage(StunMessage.CREATE_PERMISSION_RESPONSE, msg.getTransactionId());
                        byte[] respBytes = ok.encode(key, true);
                        serverSocket.send(new DatagramPacket(respBytes, respBytes.length, packet.getSocketAddress()));
                    } else if (msg.getType() == StunMessage.CHANNEL_BIND_REQUEST) {
                        byte[] key = StunMessage.deriveLongTermKey("testuser", "test.realm", "testpass");
                        StunMessage ok = new StunMessage(StunMessage.CHANNEL_BIND_RESPONSE, msg.getTransactionId());
                        byte[] respBytes = ok.encode(key, true);
                        serverSocket.send(new DatagramPacket(respBytes, respBytes.length, packet.getSocketAddress()));
                    } else if (msg.getType() == StunMessage.REFRESH_REQUEST) {
                        byte[] key = StunMessage.deriveLongTermKey("testuser", "test.realm", "testpass");
                        StunMessage ok = new StunMessage(StunMessage.REFRESH_RESPONSE, msg.getTransactionId());
                        ok.addIntAttribute(StunMessage.ATTR_LIFETIME, 300);
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

            // Test CreatePermission
            InetSocketAddress peer = new InetSocketAddress("127.0.0.1", 12345);
            assertDoesNotThrow(() -> client.createPermission(peer));

            // Test ChannelBind
            int channelNum = 0x4000;
            assertDoesNotThrow(() -> client.channelBind(channelNum, peer));

            // Test ChannelData send
            byte[] testPayload = "Hello TURN Relay!".getBytes(StandardCharsets.UTF_8);
            client.sendChannelData(channelNum, testPayload, 0, testPayload.length);

            // Wait for fake server to receive channel data
            Thread.sleep(100);
            assertNotNull(fakeServer.lastChannelData);
            assertArrayEquals(testPayload, fakeServer.lastChannelData);

            // Test Refresh
            int newLifetime = client.refresh(300);
            assertEquals(300, newLifetime);
        }
    }
}

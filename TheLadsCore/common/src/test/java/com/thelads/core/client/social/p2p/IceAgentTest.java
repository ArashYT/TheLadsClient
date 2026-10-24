package com.thelads.core.client.social.p2p;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class IceAgentTest {

    @Test
    public void loopbackHostCandidatesConnect() throws Exception {
        AtomicReference<IceAgent> agentARef = new AtomicReference<>();
        AtomicReference<IceAgent> agentBRef = new AtomicReference<>();

        IceAgent agentA = new IceAgent("test-sid", true, null, sig -> {
            IceAgent b = agentBRef.get();
            if (b != null) b.onSignal(sig);
        });
        agentARef.set(agentA);

        IceAgent agentB = new IceAgent("test-sid", false, null, sig -> {
            IceAgent a = agentARef.get();
            if (a != null) a.onSignal(sig);
        });
        agentBRef.set(agentB);

        try {
            CompletableFuture.allOf(agentA.gatherAndSendSignal(), agentB.gatherAndSendSignal()).join();

            agentA.getConnectedFuture().get(5, TimeUnit.SECONDS);
            agentB.getConnectedFuture().get(5, TimeUnit.SECONDS);

            assertFalse(agentA.isRelayed(), "Host-to-host should not be relayed");
            assertFalse(agentB.isRelayed(), "Host-to-host should not be relayed");

            // Test payload transmission
            CountDownLatch aReceived = new CountDownLatch(1);
            CountDownLatch bReceived = new CountDownLatch(1);
            AtomicReference<String> msgForA = new AtomicReference<>();
            AtomicReference<String> msgForB = new AtomicReference<>();

            agentA.setPacketConsumer(bytes -> {
                msgForA.set(new String(bytes, StandardCharsets.UTF_8));
                aReceived.countDown();
            });

            agentB.setPacketConsumer(bytes -> {
                msgForB.set(new String(bytes, StandardCharsets.UTF_8));
                bReceived.countDown();
            });

            byte[] helloB = "Hello B!".getBytes(StandardCharsets.UTF_8);
            agentA.send(helloB, 0, helloB.length);

            byte[] helloA = "Hello A!".getBytes(StandardCharsets.UTF_8);
            agentB.send(helloA, 0, helloA.length);

            assertTrue(bReceived.await(2, TimeUnit.SECONDS), "Agent B should receive message from A");
            assertTrue(aReceived.await(2, TimeUnit.SECONDS), "Agent A should receive message from B");

            assertEquals("Hello B!", msgForB.get());
            assertEquals("Hello A!", msgForA.get());
        } finally {
            agentA.close();
            agentB.close();
        }
    }

    @Test
    public void relayOnlyUsesRelayCandidate() throws Exception {
        // Start fake TURN server
        DatagramSocket fakeServer = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        int fakePort = fakeServer.getLocalPort();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread serverThread = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (running.get() && !fakeServer.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    fakeServer.receive(p);
                    StunMessage msg = StunMessage.decode(buf, 0, p.getLength());
                    if (msg.getType() == StunMessage.ALLOCATE_REQUEST) {
                        String user = msg.getStringAttribute(StunMessage.ATTR_USERNAME);
                        if (user == null) {
                            StunMessage err = new StunMessage(StunMessage.ALLOCATE_ERROR, msg.getTransactionId());
                            err.addErrorCode(401, "Unauthorized");
                            err.addStringAttribute(StunMessage.ATTR_REALM, "realm");
                            err.addStringAttribute(StunMessage.ATTR_NONCE, "nonce");
                            byte[] resp = err.encode(null, true);
                            fakeServer.send(new DatagramPacket(resp, resp.length, p.getSocketAddress()));
                        } else {
                            byte[] key = StunMessage.deriveLongTermKey("u", "realm", "p");
                            StunMessage ok = new StunMessage(StunMessage.ALLOCATE_RESPONSE, msg.getTransactionId());
                            ok.addXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS, new InetSocketAddress("127.0.0.1", 54321));
                            ok.addIntAttribute(StunMessage.ATTR_LIFETIME, 600);
                            byte[] resp = ok.encode(key, true);
                            fakeServer.send(new DatagramPacket(resp, resp.length, p.getSocketAddress()));
                        }
                    } else if (msg.getType() == StunMessage.CREATE_PERMISSION_REQUEST) {
                        byte[] key = StunMessage.deriveLongTermKey("u", "realm", "p");
                        StunMessage ok = new StunMessage(StunMessage.CREATE_PERMISSION_RESPONSE, msg.getTransactionId());
                        byte[] resp = ok.encode(key, true);
                        fakeServer.send(new DatagramPacket(resp, resp.length, p.getSocketAddress()));
                    } else if (msg.getType() == StunMessage.CHANNEL_BIND_REQUEST) {
                        byte[] key = StunMessage.deriveLongTermKey("u", "realm", "p");
                        StunMessage ok = new StunMessage(StunMessage.CHANNEL_BIND_RESPONSE, msg.getTransactionId());
                        byte[] resp = ok.encode(key, true);
                        fakeServer.send(new DatagramPacket(resp, resp.length, p.getSocketAddress()));
                    }
                } catch (Exception ignored) {
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            P2PSession.TurnServer ts = new P2PSession.TurnServer("turn:127.0.0.1:" + fakePort, "u", "p");
            P2PSession.IceConfig cfg = new P2PSession.IceConfig(
                    Collections.emptyList(),
                    Collections.singletonList(ts),
                    true // relayOnly = true!
            );

            List<JsonObject> sentSignals = new ArrayList<>();
            IceAgent agent = new IceAgent("test-relay", true, cfg, sentSignals::add);
            try {
                agent.gatherAndSendSignal().join();

                assertFalse(sentSignals.isEmpty());
                JsonObject sig = sentSignals.get(0);
                JsonArray candArr = sig.getAsJsonArray("candidates");
                assertFalse(candArr.isEmpty(), "Should have gathered relay candidate");

                for (int i = 0; i < candArr.size(); i++) {
                    JsonObject c = candArr.get(i).getAsJsonObject();
                    assertEquals("relay", c.get("type").getAsString(), "relayOnly must contain only relay candidates");
                }
            } finally {
                agent.close();
            }
        } finally {
            running.set(false);
            fakeServer.close();
        }
    }
}

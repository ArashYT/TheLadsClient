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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        // Start fake strict TURN server (rejects unbound ChannelData and unpermitted indications)
        DatagramSocket fakeServer = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        int fakePort = fakeServer.getLocalPort();
        AtomicBoolean running = new AtomicBoolean(true);

        java.util.Set<InetAddress> permittedIps = ConcurrentHashMap.newKeySet();
        ConcurrentHashMap<Integer, InetSocketAddress> boundChannels = new ConcurrentHashMap<>();
        AtomicInteger unboundDrops = new AtomicInteger(0);
        AtomicInteger unpermittedDrops = new AtomicInteger(0);

        Thread serverThread = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (running.get() && !fakeServer.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    fakeServer.receive(p);

                    // Reject unbound ChannelData
                    if (TurnClient.isChannelData(buf, 0, p.getLength())) {
                        int ch = TurnClient.getChannelNumber(buf, 0);
                        if (!boundChannels.containsKey(ch)) {
                            unboundDrops.incrementAndGet();
                            continue;
                        }
                    }

                    StunMessage msg = StunMessage.decode(buf, 0, p.getLength());

                    // Reject unpermitted Send indications
                    if (msg.getType() == StunMessage.SEND_INDICATION) {
                        InetSocketAddress peer = msg.getXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS);
                        if (peer == null || !permittedIps.contains(peer.getAddress())) {
                            unpermittedDrops.incrementAndGet();
                            continue;
                        }
                    }

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
                        InetSocketAddress peer = msg.getXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS);
                        if (peer != null) {
                            permittedIps.add(peer.getAddress());
                        }
                        byte[] key = StunMessage.deriveLongTermKey("u", "realm", "p");
                        StunMessage ok = new StunMessage(StunMessage.CREATE_PERMISSION_RESPONSE, msg.getTransactionId());
                        byte[] resp = ok.encode(key, true);
                        fakeServer.send(new DatagramPacket(resp, resp.length, p.getSocketAddress()));
                    } else if (msg.getType() == StunMessage.CHANNEL_BIND_REQUEST) {
                        Integer chAttr = msg.getIntAttribute(StunMessage.ATTR_CHANNEL_NUMBER);
                        InetSocketAddress peer = msg.getXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS);
                        int ch = (chAttr != null) ? ((chAttr >>> 16) & 0xFFFF) : 0;
                        if (ch == 0 && chAttr != null) ch = chAttr & 0xFFFF;
                        if (ch >= 0x4000 && ch <= 0x7FFE && peer != null) {
                            boundChannels.put(ch, peer);
                        }
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

    @Test
    public void forgedRequestWithoutIntegrityIsIgnored() throws Exception {
        IceAgent agent = new IceAgent("test-forged", true, null, sig -> {});
        try {
            agent.gatherAndSendSignal().join();
            int port = agent.getLocalCandidates().get(0).port;

            DatagramSocket attacker = new DatagramSocket();
            try {
                // Forged STUN request with ATTR_USE_CANDIDATE, but NO message-integrity
                StunMessage forged = new StunMessage(StunMessage.BINDING_REQUEST);
                forged.addStringAttribute(StunMessage.ATTR_USERNAME, agent.getLocalUfrag() + ":attacker");
                forged.addAttribute(StunMessage.ATTR_USE_CANDIDATE, new byte[0]);
                byte[] bytes = forged.encode(null, true);

                attacker.send(new DatagramPacket(bytes, bytes.length, new InetSocketAddress("127.0.0.1", port)));
                Thread.sleep(100);

                assertFalse(agent.getConnectedFuture().isDone(), "Forged request without integrity must be ignored");
            } finally {
                attacker.close();
            }
        } finally {
            agent.close();
        }
    }

    @Test
    public void responseWithUnknownTransactionIdIsIgnored() throws Exception {
        IceAgent agent = new IceAgent("test-unknown-tid", true, null, sig -> {});
        try {
            agent.gatherAndSendSignal().join();
            int port = agent.getLocalCandidates().get(0).port;

            DatagramSocket attacker = new DatagramSocket();
            try {
                // STUN response with arbitrary transaction ID not in agent's pending checks
                StunMessage resp = new StunMessage(StunMessage.BINDING_RESPONSE);
                resp.addXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS, new InetSocketAddress("127.0.0.1", attacker.getLocalPort()));
                byte[] key = StunMessage.deriveShortTermKey("somepwd");
                byte[] bytes = resp.encode(key, true);

                attacker.send(new DatagramPacket(bytes, bytes.length, new InetSocketAddress("127.0.0.1", port)));
                Thread.sleep(100);

                assertFalse(agent.getConnectedFuture().isDone(), "Response with unknown TID must be ignored");
            } finally {
                attacker.close();
            }
        } finally {
            agent.close();
        }
    }

    @Test
    public void dataFromNonNominatedAddressIsIgnored() throws Exception {
        AtomicReference<IceAgent> agentARef = new AtomicReference<>();
        AtomicReference<IceAgent> agentBRef = new AtomicReference<>();

        IceAgent agentA = new IceAgent("test-data-nom", true, null, sig -> {
            IceAgent b = agentBRef.get();
            if (b != null) b.onSignal(sig);
        });
        agentARef.set(agentA);

        IceAgent agentB = new IceAgent("test-data-nom", false, null, sig -> {
            IceAgent a = agentARef.get();
            if (a != null) a.onSignal(sig);
        });
        agentBRef.set(agentB);

        try {
            CompletableFuture.allOf(agentA.gatherAndSendSignal(), agentB.gatherAndSendSignal()).join();
            agentA.getConnectedFuture().get(5, TimeUnit.SECONDS);
            agentB.getConnectedFuture().get(5, TimeUnit.SECONDS);

            AtomicBoolean rogueReceived = new AtomicBoolean(false);
            agentA.setPacketConsumer(data -> {
                String str = new String(data, StandardCharsets.UTF_8);
                if ("ROGUE".equals(str)) {
                    rogueReceived.set(true);
                }
            });

            // Send packet from third party socket (not nominated)
            DatagramSocket rogue = new DatagramSocket();
            try {
                int aPort = agentA.getLocalCandidates().get(0).port;
                byte[] rogueData = "ROGUE".getBytes(StandardCharsets.UTF_8);
                rogue.send(new DatagramPacket(rogueData, rogueData.length, new InetSocketAddress("127.0.0.1", aPort)));

                Thread.sleep(100);
                assertFalse(rogueReceived.get(), "Data from non-nominated address must be ignored");
            } finally {
                rogue.close();
            }
        } finally {
            agentA.close();
            agentB.close();
        }
    }

    @Test
    public void twoTurnServersFirstFailsSecondSucceeds() throws Exception {
        DatagramSocket workingServer = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        int workingPort = workingServer.getLocalPort();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread serverThread = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (running.get() && !workingServer.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    workingServer.receive(p);
                    StunMessage msg = StunMessage.decode(buf, 0, p.getLength());
                    if (msg.getType() == StunMessage.ALLOCATE_REQUEST) {
                        String user = msg.getStringAttribute(StunMessage.ATTR_USERNAME);
                        if (user == null) {
                            StunMessage err = new StunMessage(StunMessage.ALLOCATE_ERROR, msg.getTransactionId());
                            err.addErrorCode(401, "Unauthorized");
                            err.addStringAttribute(StunMessage.ATTR_REALM, "realm");
                            err.addStringAttribute(StunMessage.ATTR_NONCE, "nonce");
                            byte[] resp = err.encode(null, true);
                            workingServer.send(new DatagramPacket(resp, resp.length, p.getSocketAddress()));
                        } else {
                            byte[] key = StunMessage.deriveLongTermKey("u", "realm", "p");
                            StunMessage ok = new StunMessage(StunMessage.ALLOCATE_RESPONSE, msg.getTransactionId());
                            ok.addXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS, new InetSocketAddress("127.0.0.1", 54322));
                            ok.addIntAttribute(StunMessage.ATTR_LIFETIME, 600);
                            byte[] resp = ok.encode(key, true);
                            workingServer.send(new DatagramPacket(resp, resp.length, p.getSocketAddress()));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            // First server points to closed port (fail fast), second server works
            DatagramSocket dummy = new DatagramSocket();
            int deadPort = dummy.getLocalPort();
            dummy.close(); // port is closed

            P2PSession.TurnServer ts1 = new P2PSession.TurnServer("turn:127.0.0.1:" + deadPort, "bad", "bad");
            P2PSession.TurnServer ts2 = new P2PSession.TurnServer("turn:127.0.0.1:" + workingPort, "u", "p");
            List<P2PSession.TurnServer> turnServers = new ArrayList<>();
            turnServers.add(ts1);
            turnServers.add(ts2);

            P2PSession.IceConfig cfg = new P2PSession.IceConfig(
                    Collections.emptyList(),
                    turnServers,
                    true // relayOnly
            );

            List<JsonObject> sentSignals = new ArrayList<>();
            IceAgent agent = new IceAgent("test-2turn", true, cfg, sentSignals::add);
            try {
                agent.gatherAndSendSignal().join();

                assertFalse(sentSignals.isEmpty());
                JsonObject sig = sentSignals.get(0);
                JsonArray candArr = sig.getAsJsonArray("candidates");
                assertEquals(1, candArr.size(), "Should gather exactly 1 relay candidate (from working server)");
                JsonObject cand = candArr.get(0).getAsJsonObject();
                assertEquals(54322, cand.get("port").getAsInt());
            } finally {
                agent.close();
            }
        } finally {
            running.set(false);
            workingServer.close();
        }
    }

    @Test
    public void gathersServerReflexiveCandidateFromStunServer() throws Exception {
        // Fake STUN server: answers every Binding request with a fixed public mapping; a second server never answers.
        InetSocketAddress mapped = new InetSocketAddress(InetAddress.getByName("203.0.113.7"), 40000);
        DatagramSocket stun = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket silent = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        Thread server = new Thread(() -> {
            byte[] buf = new byte[1500];
            while (!stun.isClosed()) {
                try {
                    DatagramPacket in = new DatagramPacket(buf, buf.length);
                    stun.receive(in);
                    StunMessage req = StunMessage.decode(in.getData(), 0, in.getLength());
                    if (req.getType() != StunMessage.BINDING_REQUEST) continue;
                    StunMessage resp = new StunMessage(StunMessage.BINDING_RESPONSE, req.getTransactionId());
                    resp.addXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS, mapped);
                    byte[] out = resp.encode(); // no FINGERPRINT: optional for STUN servers
                    stun.send(new DatagramPacket(out, out.length, in.getSocketAddress()));
                } catch (IOException e) {
                    return;
                }
            }
        }, "FakeStun");
        server.setDaemon(true);
        server.start();

        List<JsonObject> signals = new ArrayList<>();
        P2PSession.IceConfig cfg = new P2PSession.IceConfig(
                List.of("stun:127.0.0.1:" + silent.getLocalPort(), "stun:127.0.0.1:" + stun.getLocalPort()), Collections.emptyList(), false);
        IceAgent agent = new IceAgent("test-srflx", true, cfg, signals::add);
        try {
            long start = System.currentTimeMillis();
            agent.gatherAndSendSignal().join();
            long ms = System.currentTimeMillis() - start;
            assertTrue(ms < 4000, "gathering must give up on a silent STUN server after ~3 s, took " + ms + " ms");

            JsonArray cands = signals.get(0).getAsJsonArray("candidates");
            boolean srflx = false, loopbackHost = false;
            for (int i = 0; i < cands.size(); i++) {
                JsonObject c = cands.get(i).getAsJsonObject();
                if ("srflx".equals(c.get("type").getAsString()) && "203.0.113.7".equals(c.get("ip").getAsString())
                        && c.get("port").getAsInt() == 40000) srflx = true;
                if ("host".equals(c.get("type").getAsString()) && "127.0.0.1".equals(c.get("ip").getAsString())) loopbackHost = true;
            }
            assertTrue(srflx, "signal must carry the srflx candidate the STUN server mapped: " + cands);
            assertTrue(loopbackHost, "host candidates are kept: " + cands);
        } finally {
            agent.close();
            stun.close();
            silent.close();
        }
    }
}

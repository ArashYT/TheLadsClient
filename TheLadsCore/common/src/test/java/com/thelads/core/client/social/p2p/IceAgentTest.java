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

    /** QA blocker: the peer was not listening when our signal went out; it goes again until a check proves the peer has it. */
    @Test
    public void lostFirstSignalIsResentAndStillConnects() throws Exception {
        AtomicReference<IceAgent> bRef = new AtomicReference<>();
        AtomicInteger sentByA = new AtomicInteger();
        IceAgent a = new IceAgent("lost-sid", true, null, sig -> {
            if (sentByA.incrementAndGet() > 1) bRef.get().onSignal(sig); // the first one is lost
        });
        IceAgent b = new IceAgent("lost-sid", false, null, a::onSignal);
        bRef.set(b);
        try {
            CompletableFuture.allOf(a.gatherAndSendSignal(), b.gatherAndSendSignal()).join();
            a.getConnectedFuture().get(8, TimeUnit.SECONDS);
            b.getConnectedFuture().get(8, TimeUnit.SECONDS);
            int sent = sentByA.get();
            assertTrue(sent >= 2, "re-sent after the loss");
            Thread.sleep(IceAgent.SIGNAL_RESEND_MS + 500);
            assertEquals(sent, sentByA.get(), "no more re-sends once connected");
        } finally {
            a.close();
            b.close();
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

    @Test
    public void controlledAdoptsExactlyTheControllersNominatedPair() throws Exception {
        // A fake controlling peer with two adapters (two sockets). B answers checks on both, its own checks succeed on adapter 1, yet it
        // selects only the pair the controller nominates (USE-CANDIDATE from adapter 2).
        List<JsonObject> bSignals = new ArrayList<>();
        IceAgent b = new IceAgent("test-nominate", false, null, bSignals::add);
        DatagramSocket adapter1 = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket adapter2 = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        String ctrlUfrag = "ctrlufrg", ctrlPwd = "ctrlpasswordctrlpasswd";
        adapter1.setSoTimeout(2000);
        adapter2.setSoTimeout(2000);
        try {
            b.gatherAndSendSignal().join();
            JsonObject remote = new JsonObject();
            remote.addProperty("ufrag", ctrlUfrag);
            remote.addProperty("pwd", ctrlPwd);
            JsonArray cands = new JsonArray();
            for (DatagramSocket a : new DatagramSocket[] {adapter1, adapter2}) {
                JsonObject c = new JsonObject();
                c.addProperty("type", "host");
                c.addProperty("ip", "127.0.0.1");
                c.addProperty("port", a.getLocalPort());
                c.addProperty("prio", IceAgent.PRIORITY_HOST);
                cands.add(c);
            }
            remote.add("candidates", cands);
            b.onSignal(remote);

            // Answer B's own checks on adapter 1: before, that alone made the controlled side pick a pair
            InetSocketAddress bAddr = null;
            for (int i = 0; i < 3; i++) {
                DatagramPacket in = new DatagramPacket(new byte[1500], 1500);
                adapter1.receive(in);
                StunMessage req = StunMessage.decode(in.getData(), 0, in.getLength());
                if (req.getType() != StunMessage.BINDING_REQUEST) continue;
                bAddr = (InetSocketAddress) in.getSocketAddress();
                StunMessage resp = new StunMessage(StunMessage.BINDING_RESPONSE, req.getTransactionId());
                resp.addXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS, bAddr);
                byte[] out = resp.encode(StunMessage.deriveShortTermKey(ctrlPwd), true);
                adapter1.send(new DatagramPacket(out, out.length, bAddr));
            }
            assertNotNull(bAddr);
            // A plain check from adapter 1 is answered but selects nothing
            assertNotNull(check(adapter1, bAddr, b, ctrlUfrag, false));
            Thread.sleep(300);
            assertFalse(b.getConnectedFuture().isDone(), "controlled must not select a pair the controller did not nominate");

            // The nomination from adapter 2 selects exactly adapter 2
            assertNotNull(check(adapter2, bAddr, b, ctrlUfrag, true));
            b.getConnectedFuture().get(2, TimeUnit.SECONDS);
            assertEquals(adapter2.getLocalSocketAddress(), b.getSelectedRemoteAddress());
        } finally {
            b.close();
            adapter1.close();
            adapter2.close();
        }
    }

    /** Sends a controlling check from a to the agent and returns its validated response (null: none). */
    private static StunMessage check(DatagramSocket a, InetSocketAddress to, IceAgent agent, String ctrlUfrag, boolean useCandidate) throws Exception {
        StunMessage req = new StunMessage(StunMessage.BINDING_REQUEST);
        req.addStringAttribute(StunMessage.ATTR_USERNAME, agent.getLocalUfrag() + ":" + ctrlUfrag);
        req.addLongAttribute(StunMessage.ATTR_PRIORITY, IceAgent.PRIORITY_HOST);
        req.addLongAttribute(StunMessage.ATTR_ICE_CONTROLLING, 1L);
        if (useCandidate) req.addAttribute(StunMessage.ATTR_USE_CANDIDATE, new byte[0]);
        byte[] out = req.encode(StunMessage.deriveShortTermKey(agent.getLocalPwd()), true);
        a.send(new DatagramPacket(out, out.length, to));
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            DatagramPacket in = new DatagramPacket(new byte[1500], 1500);
            a.receive(in); // B's own checks arrive here too: skip them
            StunMessage msg = StunMessage.decode(in.getData(), 0, in.getLength());
            if (msg.getType() == StunMessage.BINDING_RESPONSE && java.util.Arrays.equals(msg.getTransactionId(), req.getTransactionId())) return msg;
        }
        return null;
    }

    @Test
    public void bothSidesEndOnTheSamePairWithTwoAddressesEach() throws Exception {
        // Each side advertises two host addresses (a second loopback address and an unreachable one first, as a multi-adapter PC would)
        AtomicReference<IceAgent> aRef = new AtomicReference<>(), bRef = new AtomicReference<>();
        IceAgent a = new IceAgent("test-pair", true, null, sig -> bRef.get().onSignal(twoAdapters(sig)));
        IceAgent b = new IceAgent("test-pair", false, null, sig -> aRef.get().onSignal(twoAdapters(sig)));
        aRef.set(a);
        bRef.set(b);
        try {
            CompletableFuture.allOf(a.gatherAndSendSignal(), b.gatherAndSendSignal()).join();
            a.getConnectedFuture().get(5, TimeUnit.SECONDS);
            b.getConnectedFuture().get(5, TimeUnit.SECONDS);
            int aPort = port(a), bPort = port(b);
            assertEquals(bPort, a.getSelectedRemoteAddress().getPort(), "controller selected B's socket");
            assertEquals(aPort, b.getSelectedRemoteAddress().getPort(), "controlled selected A's socket");

            // Data only flows when each side's selected address is where the other's packets really come from
            CountDownLatch both = new CountDownLatch(2);
            a.setPacketConsumer(bytes -> both.countDown());
            b.setPacketConsumer(bytes -> both.countDown());
            byte[] ping = "ping".getBytes(StandardCharsets.UTF_8);
            a.send(ping, 0, ping.length);
            b.send(ping, 0, ping.length);
            assertTrue(both.await(2, TimeUnit.SECONDS), "both directions must deliver on the selected pair");
        } finally {
            a.close();
            b.close();
        }
    }

    private static int port(IceAgent agent) {
        return agent.getLocalCandidates().get(0).port;
    }

    /** The signal with its candidates replaced by 192.0.2.1 (unreachable), 127.0.0.2 and 127.0.0.1 on the agent's port. */
    private static JsonObject twoAdapters(JsonObject sig) {
        JsonObject copy = sig.deepCopy();
        int port = sig.getAsJsonArray("candidates").get(0).getAsJsonObject().get("port").getAsInt();
        JsonArray cands = new JsonArray();
        long prio = IceAgent.PRIORITY_HOST;
        for (String ip : new String[] {"192.0.2.1", "127.0.0.2", "127.0.0.1"}) {
            JsonObject c = new JsonObject();
            c.addProperty("type", "host");
            c.addProperty("ip", ip);
            c.addProperty("port", port);
            c.addProperty("prio", prio--);
            cands.add(c);
        }
        copy.add("candidates", cands);
        return copy;
    }

    private static JsonObject signal(String ufrag, String pwd, int count, String ip, int basePort) {
        JsonObject sig = new JsonObject();
        sig.addProperty("ufrag", ufrag);
        sig.addProperty("pwd", pwd);
        JsonArray c = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "host");
            o.addProperty("ip", ip);
            o.addProperty("port", basePort + i);
            o.addProperty("prio", 1000 - i);
            c.add(o);
        }
        sig.add("candidates", c);
        return sig;
    }

    private static int remoteCount(IceAgent agent) throws Exception {
        java.lang.reflect.Field f = IceAgent.class.getDeclaredField("remoteCandidates");
        f.setAccessible(true);
        return ((List<?>) f.get(agent)).size();
    }

    /** F5 (PocIce 2): thousands of candidates per signal used to flood checks and fill pendingChecks; now signals are bounded. */
    @Test
    public void remoteCandidatesAndChecksAreBounded() throws Exception {
        IceAgent host = new IceAgent("test-flood", true, null, j -> {});
        try {
            host.gatherAndSendSignal().join();
            String ufrag = "ATTUFRAG", pwd = "ATTACKERPWD1234567890ab";
            for (int s = 0; s < 4; s++) host.onSignal(signal(ufrag, pwd, 6000, "127.0.0.1", 1024 + s * 6000));
            assertEquals(0, remoteCount(host), "a signal with more than 16 candidates is dropped whole");
            for (int s = 0; s < 4; s++) host.onSignal(signal(ufrag, pwd, IceAgent.MAX_SIGNAL_CANDIDATES, "127.0.0.1", 2000 + s * 100));
            assertEquals(IceAgent.MAX_REMOTE_CANDIDATES, remoteCount(host), "32 remote candidates in all");
            Thread.sleep(1000);
            java.lang.reflect.Field f = IceAgent.class.getDeclaredField("pendingChecks");
            f.setAccessible(true);
            int pending = ((java.util.Map<?, ?>) f.get(host)).size();
            assertTrue(pending <= 1000 / IceAgent.CHECK_PACE_MS + 2, "paced checks: " + pending + " in 1 s");
        } finally {
            host.close();
        }
    }

    /** F5/F8 (PocIce 3): hostnames, bad addresses, ports and credentials are refused without throwing; the agent keeps working. */
    @Test
    public void invalidSignalsAreIgnoredWithoutThrowing() throws Exception {
        IceAgent v = new IceAgent("test-bad", true, null, j -> {});
        try {
            String ok = "GOODPASSWORD1234567890";
            for (String bad : new String[] {
                    "{\"ufrag\":\"a\",\"pwd\":\"b\",\"candidates\":[{}]}",
                    "{\"candidates\":[{\"type\":\"host\",\"ip\":\"1.2.3.4\",\"port\":\"x\"}]}",
                    "{\"ufrag\":\"abc\",\"pwd\":\"" + ok + "\"}",
                    "{\"ufrag\":\"abcd\",\"pwd\":\"short\"}",
                    "{\"ufrag\":\"ab cd\",\"pwd\":\"" + ok + "\"}",
                    "{\"candidates\":[{\"type\":\"host\",\"ip\":\"example.com\",\"port\":5000}]}",
                    "{\"candidates\":[{\"type\":\"host\",\"ip\":\"0.0.0.0\",\"port\":5000}]}",
                    "{\"candidates\":[{\"type\":\"host\",\"ip\":\"224.0.0.1\",\"port\":5000}]}",
                    "{\"candidates\":[{\"type\":\"host\",\"ip\":\"255.255.255.255\",\"port\":5000}]}",
                    "{\"candidates\":[{\"type\":\"host\",\"ip\":\"1.2.3.4\",\"port\":0}]}",
                    "{\"candidates\":[{\"type\":\"host\",\"ip\":\"1.2.3.4\",\"port\":70000}]}",
                    "{\"candidates\":[{\"type\":\"prflx\",\"ip\":\"1.2.3.4\",\"port\":5000}]}"}) {
                assertDoesNotThrow(() -> v.onSignal(com.google.gson.JsonParser.parseString(bad).getAsJsonObject()), bad);
            }
            assertEquals(0, remoteCount(v), "nothing from a bad signal is kept");
            v.onSignal(signal("GOODUFRG", ok, 1, "192.0.2.1", 5000));
            assertEquals(1, remoteCount(v), "a valid signal still works afterwards");
        } finally {
            v.close();
        }
    }

    /** F4 (PocIce 1): a sniffed check with USE-CANDIDATE appended after MESSAGE-INTEGRITY (FINGERPRINT recomputed) nominates nothing. */
    @Test
    public void useCandidateAppendedAfterIntegrityIsIgnored() throws Exception {
        IceAgent guest = new IceAgent("test-mi", false, null, j -> {});
        try (DatagramSocket attacker = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            guest.gatherAndSendSignal().join();
            String peerUfrag = "PEERUFRG", peerPwd = "PEERPASSWORD1234567890";
            guest.onSignal(signal(peerUfrag, peerPwd, 1, "127.0.0.1", 40000));
            int port = port(guest);
            StunMessage req = new StunMessage(StunMessage.BINDING_REQUEST);
            req.addStringAttribute(StunMessage.ATTR_USERNAME, guest.getLocalUfrag() + ":" + peerUfrag);
            req.addLongAttribute(StunMessage.ATTR_PRIORITY, 1L);
            req.addLongAttribute(StunMessage.ATTR_ICE_CONTROLLING, 7L);
            byte[] sniffed = req.encode(StunMessage.deriveShortTermKey(guest.getLocalPwd()), true);
            int upToMi = sniffed.length - 8; // without FINGERPRINT
            byte[] forged = java.util.Arrays.copyOf(sniffed, upToMi + 4 + 8);
            forged[upToMi] = 0x00;
            forged[upToMi + 1] = 0x25; // USE-CANDIDATE, length 0
            forged[upToMi + 2] = 0;
            forged[upToMi + 3] = 0;
            int msgLen = forged.length - 20;
            forged[2] = (byte) (msgLen >> 8);
            forged[3] = (byte) msgLen;
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(forged, 0, upToMi + 4);
            long fp = (crc.getValue() ^ 0x5354554EL) & 0xFFFFFFFFL;
            forged[upToMi + 4] = (byte) 0x80;
            forged[upToMi + 5] = 0x28;
            forged[upToMi + 6] = 0;
            forged[upToMi + 7] = 4;
            forged[upToMi + 8] = (byte) (fp >> 24);
            forged[upToMi + 9] = (byte) (fp >> 16);
            forged[upToMi + 10] = (byte) (fp >> 8);
            forged[upToMi + 11] = (byte) fp;
            assertTrue(StunMessage.validateFingerprint(forged, 0, forged.length), "the forgery itself is well-formed");
            attacker.send(new DatagramPacket(forged, forged.length, InetAddress.getLoopbackAddress(), port));
            Thread.sleep(300);
            assertFalse(guest.getConnectedFuture().isDone(), "an unauthenticated USE-CANDIDATE must not select the attacker's address");
        } finally {
            guest.close();
        }
    }

    private static long turnThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("TurnClient-") && t.isAlive()).count();
    }

    /** F11 (PocLeak): an agent closed while its TURN allocation is in flight used to leave the TurnClient (socket, threads) running. */
    @Test
    public void closingMidAllocationReleasesTheTurnClient() throws Exception {
        DatagramSocket turn = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        byte[] key = StunMessage.deriveLongTermKey("u", "r", "c");
        Thread fake = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (!turn.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    turn.receive(p);
                    StunMessage m = StunMessage.decode(buf, 0, p.getLength());
                    if (m.getType() != StunMessage.ALLOCATE_REQUEST) continue;
                    byte[] out;
                    if (!m.hasAttribute(StunMessage.ATTR_USERNAME)) {
                        StunMessage r = new StunMessage(StunMessage.ALLOCATE_ERROR, m.getTransactionId());
                        r.addErrorCode(401, "Unauthorized");
                        r.addStringAttribute(StunMessage.ATTR_REALM, "r");
                        r.addStringAttribute(StunMessage.ATTR_NONCE, "n");
                        out = r.encode(null, true);
                    } else {
                        Thread.sleep(1500); // a slow TURN server
                        StunMessage r = new StunMessage(StunMessage.ALLOCATE_RESPONSE, m.getTransactionId());
                        r.addXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS, new InetSocketAddress("203.0.113.5", 5000));
                        r.addIntAttribute(StunMessage.ATTR_LIFETIME, 600);
                        out = r.encode(key, true);
                    }
                    turn.send(new DatagramPacket(out, out.length, p.getSocketAddress()));
                } catch (Exception e) {
                    if (turn.isClosed()) return;
                }
            }
        }, "fake-slow-turn");
        fake.setDaemon(true);
        fake.start();
        try {
            P2PSession.IceConfig cfg = new P2PSession.IceConfig(Collections.emptyList(),
                    List.of(new P2PSession.TurnServer("turn:127.0.0.1:" + turn.getLocalPort(), "u", "c")), true);
            long before = turnThreads();
            for (int i = 0; i < 5; i++) {
                IceAgent agent = new IceAgent("leak" + i, true, cfg, j -> {});
                agent.gatherAndSendSignal();
                Thread.sleep(300); // cancelled while allocating
                agent.close();
            }
            long deadline = System.currentTimeMillis() + 4000;
            while (turnThreads() > before && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertEquals(before, turnThreads(), "every TurnClient thread ends after its agent closed mid-allocation");
        } finally {
            turn.close();
        }
    }
}

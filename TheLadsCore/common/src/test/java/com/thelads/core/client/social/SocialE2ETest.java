package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End to end against the real backend: cosmetics-server/cosmetics.py --dev-insecure-auth on a free loopback port (py -3.12), two
 * players' SocialStates with fake bridges. Friend add + accept, presence, join request + accept, P2P over loopback host candidates,
 * 1 MB through the guest's proxy to an echo server standing in for the integrated server, host closes (guest stream ends in 1 s),
 * DM round trip, block hides presence. The forced-relay variants go through real Cloudflare TURN with the owner's key file (its
 * values go to the backend process only, never printed) and are skipped without it.
 * Skipped, saying why, when py -3.12 with aiohttp or the backend source is not available.
 */
public class SocialE2ETest {
    private static final long WAIT_MS = 20_000;
    private static final Path TURN_KEY_FILE = Paths.get(System.getProperty("user.home"), "Documents", "lads_turn_credentials.json");

    @Test
    void friendsJoinP2PEchoCloseDmAndBlock() throws Exception {
        run(null, 0);
    }

    /** "Always use relay": the host offers only its Cloudflare relay; both sides must end on a relay pair, 1 MB echo, host close. */
    @Test
    void forcedRelayThroughCloudflareTurn() throws Exception {
        JsonObject key = turnKey();
        assumeTrue(key != null, TURN_KEY_FILE + " not found: skipping the Cloudflare TURN end-to-end test");
        run(key, 0);
    }

    /** TURN permissions last 5 min: after 6 idle minutes (refreshes only) the relay still carries the echo. LADS_TURN_LONG=1 only. */
    @Test
    void forcedRelaySurvivesSixIdleMinutes() throws Exception {
        assumeTrue("1".equals(System.getenv("LADS_TURN_LONG")), "LADS_TURN_LONG=1 not set: skipping the 6 minute TURN refresh test");
        JsonObject key = turnKey();
        assumeTrue(key != null, TURN_KEY_FILE + " not found: skipping the Cloudflare TURN refresh test");
        run(key, 6 * 60_000L);
    }

    private static JsonObject turnKey() throws IOException {
        if (!Files.isRegularFile(TURN_KEY_FILE)) return null;
        JsonObject key = com.google.gson.JsonParser.parseString(Files.readString(TURN_KEY_FILE)).getAsJsonObject();
        return key.has("CF_TURN_KEY_ID") && key.has("CF_TURN_API_TOKEN") ? key : null;
    }

    /** turnKey null: direct over loopback, then DM and block. Else forced relay through Cloudflare, idling idleMs before the echo. */
    private void run(JsonObject turnKey, long idleMs) throws Exception {
        boolean relay = turnKey != null;
        Path backendDir = findBackend();
        assumeTrue(backendDir != null, "cosmetics-server/cosmetics.py not found above " + Paths.get("").toAbsolutePath() + ": skipping the social end-to-end test");
        assumeTrue(pythonReady(backendDir), "py -3.12 with aiohttp is not available: skipping the social end-to-end test");

        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = s.getLocalPort();
        }
        Path tmp = Files.createTempDirectory("lads-social-e2e");
        Path log = tmp.resolve("backend.log");
        ProcessBuilder pb = new ProcessBuilder("py", "-3.12", "cosmetics.py", "--dev-insecure-auth", "--host", "127.0.0.1",
                "--port", String.valueOf(port), "--db", tmp.resolve("cosmetics.db").toString());
        pb.directory(backendDir.toFile());
        pb.environment().put("SOCIAL_DB", tmp.resolve("social.db").toString());
        if (relay) { // to the backend process only: never printed or logged
            pb.environment().put("CF_TURN_KEY_ID", turnKey.get("CF_TURN_KEY_ID").getAsString());
            pb.environment().put("CF_TURN_API_TOKEN", turnKey.get("CF_TURN_API_TOKEN").getAsString());
        } else { // direct only: no relay credentials
            pb.environment().remove("CF_TURN_KEY_ID");
            pb.environment().remove("CF_TURN_API_TOKEN");
        }
        pb.redirectErrorStream(true).redirectOutput(log.toFile());
        Process backend = pb.start();

        String url = "ws://127.0.0.1:" + port + "/social/ws";
        int n = new Random().nextInt(10_000);
        UUID hostId = UUID.randomUUID(), guestId = UUID.randomUUID();
        String guestName = "E2EGuest" + n;
        FakeBridge hostBridge = new FakeBridge(true), guestBridge = new FakeBridge(false);
        LadsGameBridge.Session hostSession = new LadsGameBridge.Session(hostId, "E2EHost" + n, "t"),
                guestSession = new LadsGameBridge.Session(guestId, guestName, "t");
        List<JsonObject> guestInbox = new CopyOnWriteArrayList<>();
        SocialState host = new SocialState(() -> hostBridge, () -> hostSession, url);
        SocialState guest = new SocialState(() -> guestBridge, () -> guestSession, url) {
            @Override public void onMessage(JsonObject msg) {
                guestInbox.add(msg);
                super.onMessage(msg);
            }
        };
        List<JsonObject> p2pEnds = new CopyOnWriteArrayList<>(); // each side's p2p_end says whether its session was relayed
        host.setClientSender(m -> { if ("p2p_end".equals(str(m, "t"))) p2pEnds.add(m); host.client().send(m); });
        guest.setClientSender(m -> { if ("p2p_end".equals(str(m, "t"))) p2pEnds.add(m); guest.client().send(m); });

        try (EchoServer echo = new EchoServer()) {
            hostBridge.echoPort = echo.port();
            waitForBackend(port, backend, log);
            host.client().start();
            guest.client().start();
            await("both clients connected", () -> host.client().isConnected() && guest.client().isConnected());

            // Friend request (toast action on the guest) and accept
            host.addFriend(guestName);
            String friendAccept = guestBridge.actions.poll(WAIT_MS, TimeUnit.MILLISECONDS);
            assertEquals("friend_accept:" + hostId, friendAccept);
            guest.handleAction(friendAccept);
            await("friends on both sides", () -> host.isFriend(guestId) && guest.isFriend(hostId));

            // Host opens the world to friends; the guest sees it as joinable
            HostOptions options = new HostOptions();
            options.scope = HostOptions.Scope.FRIENDS;
            options.approval = HostOptions.Approval.ASK;
            options.backupOnOpen = false;
            options.relayOnly = relay; // "Always use relay (hide my IP)": the host sends no host or srflx candidate
            host.startHosting(options);
            assertTrue(host.hostSession().isHosting());
            await("guest sees the host hosting", () -> friend(guest, hostId).presence().world() != null);
            assertTrue(guest.canJoin(friend(guest, hostId)).ok());

            // Join request -> host accepts through its toast action -> P2P -> guest's proxy
            guest.requestJoin(hostId);
            String joinAccept = hostBridge.actions.poll(WAIT_MS, TimeUnit.MILLISECONDS);
            assertNotNull(joinAccept, "host got no join request toast");
            assertTrue(joinAccept.startsWith("join_accept:"), joinAccept);
            long joinAt = System.nanoTime();
            host.handleAction(joinAccept);
            await("guest connects to its proxy", () -> guestBridge.proxyPort > 0);
            System.out.println("[SocialE2E] link up " + (System.nanoTime() - joinAt) / 1_000_000 + " ms after the accept: " + guestBridge.proxyName);
            if (relay) assertTrue(guestBridge.proxyName.endsWith("(relayed)"), "guest must be on a relay pair: " + guestBridge.proxyName);
            assertEquals("p2p:" + hostId + "/world-e2e", HypixelSafetyManager.getP2PSession(), "P2P session must count as an unknown server");

            byte[] payload = new byte[1 << 20];
            new Random(42).nextBytes(payload);
            try (Socket mc = new Socket(InetAddress.getLoopbackAddress(), guestBridge.proxyPort)) {
                mc.setSoTimeout((int) WAIT_MS);
                if (idleMs > 0) {
                    System.out.println("[SocialE2E] idle " + idleMs / 1000 + " s on the relay (keepalive pings only)");
                    Thread.sleep(idleMs);
                }
                long echoAt = System.nanoTime();
                OutputStream out = mc.getOutputStream();
                CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
                    try {
                        out.write(payload);
                        out.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                byte[] back = mc.getInputStream().readNBytes(payload.length);
                writer.get(WAIT_MS, TimeUnit.MILLISECONDS);
                assertArrayEquals(payload, back, "1 MB through guest proxy -> P2P -> host -> echo and back must be identical");
                System.out.println("[SocialE2E] 1 MB echo took " + (System.nanoTime() - echoAt) / 1_000_000 + " ms" + (relay ? " through Cloudflare TURN" : ""));

                // Host closes: the guest's stream ends within 1 s
                long closedAt = System.nanoTime();
                host.stopHosting();
                mc.setSoTimeout(1000);
                try {
                    assertEquals(-1, mc.getInputStream().read(), "guest stream must end, not carry data, after the host closed");
                } catch (SocketTimeoutException e) {
                    fail("guest stream still open 1 s after the host closed the world");
                } catch (IOException reset) {
                    // a reset also ends the stream
                }
                long ms = (System.nanoTime() - closedAt) / 1_000_000;
                assertTrue(ms <= 1000, "guest stream ended after " + ms + " ms");
                System.out.println("[SocialE2E] 1 MB echoed through P2P; guest stream ended " + ms + " ms after the host closed");
            }
            await("guest session closed and P2P key cleared", () -> !guest.isGuestSessionActive() && HypixelSafetyManager.getP2PSession() == null);
            await("both sides reported the session end", () -> p2pEnds.size() >= 2);
            for (JsonObject end : p2pEnds) {
                System.out.println("[SocialE2E] p2p_end relayed=" + end.get("relayed") + " bytes=" + end.get("bytes") + " reason=" + str(end, "reason"));
                assertEquals(relay, end.get("relayed").getAsBoolean(), "each side's session.relayed(): " + end);
            }
            if (relay) return; // DM and block do not depend on the transport

            // DM round trip
            host.sendDm(guestId, "hello from host");
            await("guest got the DM", () -> guest.dms(hostId).stream().anyMatch(d -> d.text().equals("hello from host") && d.from().equals(hostId)));
            guest.sendDm(hostId, "hi back");
            await("host got the reply", () -> host.dms(guestId).stream().anyMatch(d -> d.text().equals("hi back") && d.from().equals(guestId)));

            // Block hides presence: after the guest blocks the host, the host's presence changes never reach the guest
            guest.block(hostId);
            await("block processed", () -> guestInbox.stream().anyMatch(m -> "friend_removed".equals(str(m, "t"))));
            guestInbox.clear();
            host.startHosting(options);
            assertTrue(host.hostSession().isHosting());
            Thread.sleep(1500);
            assertFalse(guestInbox.stream().anyMatch(m -> "presence".equals(str(m, "t")) && SocialClient.plain(hostId).equals(str(m, "uuid"))),
                    "a blocked host's presence must not reach the guest: " + guestInbox);
            assertFalse(guest.isFriend(hostId));
        } catch (AssertionError | Exception e) {
            System.out.println("[SocialE2E] backend log tail:\n" + tail(log));
            throw e;
        } finally {
            host.stopHosting();
            guest.closeGuestSession("test end");
            host.client().close();
            guest.client().close();
            HypixelSafetyManager.setP2PSession(null, null);
            backend.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            try (var files = Files.walk(tmp)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static FriendEntry friend(SocialState state, UUID id) {
        return state.friends().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < end) {
            try {
                if (condition.getAsBoolean()) return;
            } catch (RuntimeException notYet) {
                // e.g. the friend is not in the list yet
            }
            Thread.sleep(25);
        }
        fail("timed out waiting for: " + what);
    }

    private static Path findBackend() {
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("cosmetics-server");
            if (Files.isRegularFile(candidate.resolve("cosmetics.py")) && Files.isRegularFile(candidate.resolve("social.py"))) return candidate;
        }
        return null;
    }

    private static boolean pythonReady(Path dir) {
        try {
            Process p = new ProcessBuilder("py", "-3.12", "-c", "import aiohttp").directory(dir.toFile()).redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static void waitForBackend(int port, Process backend, Path log) throws Exception {
        long end = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < end) {
            if (!backend.isAlive()) fail("backend exited (" + backend.exitValue() + "):\n" + tail(log));
            try (Socket ignored = new Socket(InetAddress.getLoopbackAddress(), port)) {
                return;
            } catch (IOException notYet) {
                Thread.sleep(100);
            }
        }
        fail("backend did not listen on " + port + ":\n" + tail(log));
    }

    private static String tail(Path log) {
        try {
            String text = Files.readString(log, StandardCharsets.UTF_8);
            return text.length() > 4000 ? text.substring(text.length() - 4000) : text;
        } catch (IOException e) {
            return "(no log: " + e.getMessage() + ")";
        }
    }

    /** One player's game: hosting opens the echo server's port; the guest's connect is recorded; toast accept ids are queued. */
    private static final class FakeBridge implements SocialBridge {
        private final boolean host;
        final BlockingQueue<String> actions = new LinkedBlockingQueue<>();
        volatile int echoPort = -1, proxyPort = -1;
        volatile String proxyName = "";

        FakeBridge(boolean host) {
            this.host = host;
        }

        @Override public boolean canHost() { return host; }
        @Override public int openToFriends(HostOptions options) { return echoPort; }
        @Override public boolean inWorldOrServer() { return host; }
        @Override public String worldId() { return host ? "world-e2e" : null; }
        @Override public String worldName() { return host ? "E2E World" : null; }
        @Override public GameInfo gameInfo() { return new GameInfo("1.20.1", "fabric", "plain", "", "", "", "e2e"); }
        @Override public void connectToLocal(int port, String displayName) {
            proxyName = displayName;
            proxyPort = port;
        }
        @Override public void toast(String title, String body, String acceptAction, String denyAction) {
            if (acceptAction != null) actions.add(acceptAction);
        }
    }

    /** Stands in for the integrated server on loopback: echoes every connection's bytes back. */
    private static final class EchoServer implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());

        EchoServer() throws IOException {
            Thread t = new Thread(() -> {
                while (!socket.isClosed()) {
                    try {
                        Socket c = socket.accept();
                        Thread pipe = new Thread(() -> {
                            try (c) {
                                c.getInputStream().transferTo(c.getOutputStream());
                            } catch (IOException ignored) {
                            }
                        }, "E2E-Echo");
                        pipe.setDaemon(true);
                        pipe.start();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "E2E-EchoAccept");
            t.setDaemon(true);
            t.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}

package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.social.p2p.P2PSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Host-side session manager: tracks accepted guest UUIDs, enforces max players and login whitelist,
 * and relays P2P streams to the loopback integrated server port.
 */
public final class HostSession {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/HostSession");

    public interface P2PStarter {
        java.util.concurrent.CompletableFuture<P2PSession> start(String sid, boolean host, P2PSession.IceConfig cfg, Consumer<JsonObject> sendSignal);
    }

    private static volatile P2PStarter p2pStarter = P2PSession::start;
    public static volatile boolean skipLoopbackForTest = false;

    public static void setP2PStarter(P2PStarter starter) {
        p2pStarter = starter != null ? starter : P2PSession::start;
    }

    private volatile boolean hosting;
    private volatile HostOptions options = new HostOptions();
    private volatile int port = -1;

    private final SocialState owner;
    private final Set<UUID> acceptedUuids = ConcurrentHashMap.newKeySet();
    private final Map<String, P2PSession> activeP2PSessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> guestToSid = new ConcurrentHashMap<>();
    private final Map<String, UUID> sidToGuest = new ConcurrentHashMap<>();
    private final Set<String> startingSids = ConcurrentHashMap.newKeySet();
    private final Map<String, Queue<JsonObject>> pendingSignals = new ConcurrentHashMap<>();
    private final Set<String> endedSids = ConcurrentHashMap.newKeySet();
    private final Object signalLock = new Object();

    private final Predicate<UUID> loginFilter = this::canLogin;
    /** After Friends hosting stops, nobody else logs in to this world until it closes (the bridge clears it then) or LAN opens it. */
    private static final Predicate<UUID> NOBODY = id -> false;
    static final long KICK_GRACE_MS = 2_000; // the disconnect packet reaches the guest before the tunnel goes

    HostSession(SocialState owner) {
        this.owner = owner;
    }

    public boolean isHosting() {
        return hosting;
    }

    public HostOptions options() {
        return options;
    }

    public int port() {
        return port;
    }

    public Set<UUID> acceptedUuids() {
        return Collections.unmodifiableSet(acceptedUuids);
    }

    public Predicate<UUID> loginFilter() {
        return loginFilter;
    }

    /** Guests in the world now (the integrated server's player list, host excluded; maxPlayers counts guests too). */
    public int activeGuestCount() {
        return owner.bridge().guests().size();
    }

    private boolean isOnline(UUID id) {
        for (SocialBridge.GuestInfo g : owner.bridge().guests()) if (id.equals(g.id())) return true;
        return false;
    }

    public boolean ownsSid(String sid) {
        if (sid == null) return false;
        return activeP2PSessions.containsKey(sid) || startingSids.contains(sid) || pendingSignals.containsKey(sid);
    }

    public void onSignal(String sid, JsonObject data) {
        if (sid == null || data == null) return;
        synchronized (signalLock) {
            P2PSession s = activeP2PSessions.get(sid);
            if (s != null) {
                s.onSignal(data);
            } else {
                pendingSignals.computeIfAbsent(sid, k -> new ConcurrentLinkedQueue<>()).offer(data);
            }
        }
    }

    /**
     * Whitelist predicate for the integrated server. Rejects unknown players with "You weren't invited to this world".
     */
    public boolean canLogin(UUID id) {
        if (!hosting || id == null || options.isBanned(id)) return false;

        // Only accepted guests (join request answered, invite sent, or a friend auto-accepted at its join request): a kick takes the
        // acceptance back, so coming back needs a new request (F7)
        if (!acceptedUuids.contains(id)) {
            LOGGER.info("HostSession: rejected login for uninvited UUID {}", id);
            return false;
        }

        // Max players enforcement: if not already connected and at cap, reject
        int online = activeGuestCount();
        if (online >= options.maxPlayers && !isOnline(id)) {
            LOGGER.info("HostSession: rejected login for UUID {}: world full ({}/{})", id, online, options.maxPlayers);
            return false;
        }

        return true;
    }

    public void accept(UUID guest) {
        if (guest != null && !options.isBanned(guest)) {
            acceptedUuids.add(guest);
        }
    }

    /** Takes back the acceptance and kicks: the vanilla disconnect ("Kicked by host") goes through the tunnel, which closes after it. */
    public void revoke(UUID guest) {
        if (guest == null) return;
        acceptedUuids.remove(guest); // the login filter refuses them from now on
        owner.bridge().guestAction(guest, SocialBridge.GuestAction.KICK);
        String sid = guestToSid.get(guest);
        if (sid != null) {
            CompletableFuture.delayedExecutor(KICK_GRACE_MS, TimeUnit.MILLISECONDS).execute(() -> {
                if (sid.equals(guestToSid.get(guest))) closeGuest(sid, "Revoked"); // not already ended (guest left, hosting stopped)
            });
        }
    }

    public void closeGuest(String sid, String reason) {
        if (sid != null) {
            UUID guest = sidToGuest.remove(sid);
            if (guest != null) guestToSid.remove(guest, sid); // never a newer session of the same guest
            startingSids.remove(sid);
            pendingSignals.remove(sid);
            P2PSession p2p = activeP2PSessions.remove(sid);
            if (p2p != null) {
                endSession(sid, reason, p2p);
                try { p2p.close(reason); } catch (Exception ignored) {}
            } else {
                endSession(sid, reason, null);
            }
        }
    }

    private void endSession(String sid, String reason, P2PSession p2p) {
        if (sid == null) return;
        if (endedSids.add(sid)) {
            boolean relayed = p2p != null && p2p.relayed();
            long bytes = p2p != null ? p2p.bytes() : 0L;
            owner.sendRaw(SocialProtocol.msg(SocialProtocol.P2P_END,
                    "sid", sid,
                    "reason", reason != null ? reason : "Session ended",
                    "relayed", relayed,
                    "bytes", bytes));
        }
    }

    /** Test helper to initialize hosting state without starting real networking. */
    public void initForTest(HostOptions options, int port) {
        this.options = options != null ? options : new HostOptions();
        this.port = port;
        this.hosting = true;
    }

    public synchronized boolean start(HostOptions options) {
        if (options == null || options.scope == HostOptions.Scope.OFF) {
            stop("Scope OFF");
            return false;
        }

        this.options = options;
        SocialBridge bridge = owner.bridge();
        if (options.scope == HostOptions.Scope.LAN) {
            bridge.setLoginFilter(null); // the host chose LAN explicitly: vanilla rules
            bridge.openToLan(options);
            this.hosting = true;
            return true;
        }

        bridge.setLoginFilter(loginFilter); // before the port opens: canLogin refuses everyone until hosting is on
        int p = bridge.openToFriends(options);
        if (p <= 0) {
            bridge.setLoginFilter(null);
            LOGGER.warn("HostSession: failed to open to friends (port={})", p);
            return false;
        }

        this.port = p;
        this.hosting = true;
        this.endedSids.clear();
        bridge.applyHostOptions(options);
        LOGGER.info("HostSession: hosting started on loopback port {}, scope={}, maxPlayers={}", p, options.scope, options.maxPlayers);
        return true;
    }

    public synchronized void stop(String reason) {
        if (!hosting) return;
        hosting = false;
        for (Map.Entry<String, P2PSession> entry : activeP2PSessions.entrySet()) {
            String sid = entry.getKey();
            P2PSession session = entry.getValue();
            endSession(sid, reason, session);
            try {
                session.close(reason != null ? reason : "Host closed the world");
            } catch (Exception ignored) {}
        }
        for (String sid : startingSids) {
            endSession(sid, reason, null);
        }
        activeP2PSessions.clear();
        guestToSid.clear();
        sidToGuest.clear();
        startingSids.clear();
        pendingSignals.clear();
        acceptedUuids.clear();

        SocialBridge bridge = owner.bridge();
        // not null (= anyone): refuses everyone until the world closes; once it has closed, nothing is left to guard
        bridge.setLoginFilter(bridge.worldId() != null ? NOBODY : null);
        bridge.closeHosting(reason != null ? reason : "Host closed the world");
        port = -1;
        LOGGER.info("HostSession: hosting stopped ({})", reason);
    }

    /**
     * Bridges an accepted peer's P2P session to the local loopback integrated server.
     */
    public void handleP2PStart(String sid, UUID peer, P2PSession.IceConfig iceConfig, Consumer<JsonObject> sendSignal) {
        if (!hosting || port <= 0) {
            LOGGER.warn("HostSession: cannot handle P2P start for sid {}, not hosting", sid);
            return;
        }
        // F9: a tunnel only for a guest we accepted (join request, invite, auto-accepted friend), one per guest, a few beyond the slots
        if (peer == null || !acceptedUuids.contains(peer)) {
            LOGGER.warn("HostSession: ignored P2P start for {}: not accepted", peer);
            return;
        }
        String old = guestToSid.get(peer);
        if (old != null) closeGuest(old, "Replaced");
        if (activeP2PSessions.size() + startingSids.size() >= options.maxPlayers + 2) {
            LOGGER.warn("HostSession: ignored P2P start for {}: too many sessions", peer);
            return;
        }

        guestToSid.put(peer, sid);
        sidToGuest.put(sid, peer);
        startingSids.add(sid);

        // "Always use relay (hide my IP)" holds even if the backend's config did not say so: no host/srflx candidate is ever sent.
        P2PSession.IceConfig ice = options.relayOnly && !iceConfig.relayOnly()
                ? new P2PSession.IceConfig(iceConfig.stun(), iceConfig.turn(), true) : iceConfig;
        CompletableFuture<P2PSession> started = p2pStarter.start(sid, true, ice, sendSignal);
        // The future is the session itself (P2PSession.start): the guest's candidates must reach it while it connects.
        if (started instanceof P2PSession connecting) attach(sid, connecting);
        started.thenAccept(p2p -> {
            if (!attach(sid, p2p)) return;

            p2p.onClose(reason -> {
                UUID guest = sidToGuest.remove(sid);
                if (guest != null) guestToSid.remove(guest);
                activeP2PSessions.remove(sid);
                endSession(sid, reason, p2p);
                LOGGER.info("HostSession: guest P2P session {} closed: {}", sid, reason);
            });

            if (!skipLoopbackForTest) {
                Thread pipeThread = new Thread(() -> bridgeToLoopback(p2p, port), "Lads-HostBridge-" + sid);
                pipeThread.setDaemon(true);
                pipeThread.start();
            }
        }).exceptionally(ex -> {
            startingSids.remove(sid);
            pendingSignals.remove(sid);
            activeP2PSessions.remove(sid);
            UUID guest = sidToGuest.remove(sid);
            if (guest != null) guestToSid.remove(guest);
            endSession(sid, "P2P session start failed", null);
            LOGGER.warn("HostSession: P2P session start failed for sid {}: {}", sid, ex.getMessage());
            return null;
        });
    }

    /** From now on p2p gets this sid's signals (queued ones first); false, closing it, when the sid ended meanwhile. */
    private boolean attach(String sid, P2PSession p2p) {
        synchronized (signalLock) {
            if (!hosting || !sidToGuest.containsKey(sid)) {
                p2p.close("Session ended");
                return false;
            }
            startingSids.remove(sid);
            activeP2PSessions.put(sid, p2p);
            Queue<JsonObject> queued = pendingSignals.remove(sid);
            if (queued != null) {
                JsonObject sig;
                while ((sig = queued.poll()) != null) {
                    p2p.onSignal(sig);
                }
            }
            return true;
        }
    }

    private void bridgeToLoopback(P2PSession p2p, int targetPort) {
        Socket socket = null;
        try {
            socket = new Socket("127.0.0.1", targetPort);
            socket.setTcpNoDelay(true);
            Socket finalSocket = socket;

            // Stream 0 from guest P2P -> loopback TCP socket, then FIN after the guest's last byte: the server reads it and closes its side
            Thread inPipe = new Thread(() -> {
                GuestSession.copy(p2p.in(P2PSession.MINECRAFT), getOut(finalSocket));
                try { finalSocket.shutdownOutput(); } catch (IOException ignored) {}
            }, "Lads-HostPipeIn");
            inPipe.setDaemon(true);
            inPipe.start();

            // Loopback TCP socket -> Stream 0 to guest P2P, to the server's last byte (a kick): p2p.close delivers it before the session ends
            GuestSession.copy(finalSocket.getInputStream(), p2p.out(P2PSession.MINECRAFT));
        } catch (IOException e) {
            LOGGER.warn("HostSession: bridge loopback connection error: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
            p2p.close("Loopback socket closed");
        }
    }

    private static OutputStream getOut(Socket s) {
        try { return s.getOutputStream(); } catch (IOException e) { return OutputStream.nullOutputStream(); }
    }
}

package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.social.p2p.P2PSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    public static void setP2PStarter(P2PStarter starter) {
        p2pStarter = starter != null ? starter : P2PSession::start;
    }

    private volatile boolean hosting;
    private volatile HostOptions options = new HostOptions();
    private volatile int port = -1;

    private final Set<UUID> acceptedUuids = ConcurrentHashMap.newKeySet();
    private final Set<UUID> connectedGuests = ConcurrentHashMap.newKeySet();
    private final Map<String, P2PSession> activeP2PSessions = new ConcurrentHashMap<>();

    private final Predicate<UUID> loginFilter = this::canLogin;

    public HostSession() {}

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

    public int activeGuestCount() {
        return connectedGuests.size();
    }

    /**
     * Whitelist predicate for the integrated server. Rejects unknown players with "You weren't invited to this world".
     */
    public boolean canLogin(UUID id) {
        if (!hosting || id == null) return false;

        // Auto-accept friends in FRIENDS scope
        if (options.scope == HostOptions.Scope.FRIENDS && options.approval == HostOptions.Approval.AUTO_FRIENDS) {
            SocialState state = SocialState.get();
            if (state != null && state.isFriend(id)) {
                acceptedUuids.add(id);
            }
        }

        if (!acceptedUuids.contains(id)) {
            LOGGER.info("HostSession: rejected login for uninvited UUID {}", id);
            return false;
        }

        // Max players enforcement: if not already connected and at cap, reject
        if (!connectedGuests.contains(id) && connectedGuests.size() >= options.maxPlayers) {
            LOGGER.info("HostSession: rejected login for UUID {}: world full ({}/{})", id, connectedGuests.size(), options.maxPlayers);
            return false;
        }

        return true;
    }

    public void accept(UUID guest) {
        if (guest != null) {
            acceptedUuids.add(guest);
        }
    }

    public void revoke(UUID guest) {
        if (guest != null) {
            acceptedUuids.remove(guest);
            connectedGuests.remove(guest);
            SocialBridge.get().guestAction(guest, SocialBridge.GuestAction.KICK);
        }
    }

    public void onGuestConnected(UUID guest) {
        if (guest != null) {
            connectedGuests.add(guest);
        }
    }

    public void onGuestDisconnected(UUID guest) {
        if (guest != null) {
            connectedGuests.remove(guest);
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
        if (options.scope == HostOptions.Scope.LAN) {
            SocialBridge.get().openToLan(options);
            this.hosting = true;
            return true;
        }

        int p = SocialBridge.get().openToFriends(options);
        if (p <= 0) {
            LOGGER.warn("HostSession: failed to open to friends (port={})", p);
            return false;
        }

        this.port = p;
        this.hosting = true;
        SocialBridge.get().setLoginFilter(loginFilter);
        SocialBridge.get().applyHostOptions(options);
        LOGGER.info("HostSession: hosting started on loopback port {}, scope={}, maxPlayers={}", p, options.scope, options.maxPlayers);
        return true;
    }

    public synchronized void stop(String reason) {
        if (!hosting) return;
        hosting = false;
        for (P2PSession session : activeP2PSessions.values()) {
            try {
                session.close(reason != null ? reason : "Host closed the world");
            } catch (Exception ignored) {}
        }
        activeP2PSessions.clear();
        acceptedUuids.clear();
        connectedGuests.clear();

        SocialBridge.get().setLoginFilter(null);
        SocialBridge.get().closeHosting(reason != null ? reason : "Host closed the world");
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

        p2pStarter.start(sid, true, iceConfig, sendSignal).thenAccept(p2p -> {
            activeP2PSessions.put(sid, p2p);
            p2p.onClose(reason -> {
                activeP2PSessions.remove(sid);
                LOGGER.info("HostSession: guest P2P session {} closed: {}", sid, reason);
            });

            Thread pipeThread = new Thread(() -> bridgeToLoopback(p2p, port), "Lads-HostBridge-" + sid);
            pipeThread.setDaemon(true);
            pipeThread.start();
        }).exceptionally(ex -> {
            LOGGER.warn("HostSession: P2P session start failed for sid {}: {}", sid, ex.getMessage());
            return null;
        });
    }

    private void bridgeToLoopback(P2PSession p2p, int targetPort) {
        Socket socket = null;
        try {
            socket = new Socket("127.0.0.1", targetPort);
            socket.setTcpNoDelay(true);
            Socket finalSocket = socket;

            // Stream 0 from guest P2P -> loopback TCP socket
            Thread inPipe = new Thread(() -> pipe(p2p.in(P2PSession.MINECRAFT), getOut(finalSocket)), "Lads-HostPipeIn");
            inPipe.setDaemon(true);
            inPipe.start();

            // Loopback TCP socket -> Stream 0 to guest P2P
            pipe(finalSocket.getInputStream(), p2p.out(P2PSession.MINECRAFT));
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

    private static void pipe(InputStream in, OutputStream out) {
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try { out.close(); } catch (IOException ignored) {}
        }
    }
}

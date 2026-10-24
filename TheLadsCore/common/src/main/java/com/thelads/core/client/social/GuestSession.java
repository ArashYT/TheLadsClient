package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.social.p2p.P2PSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Guest-side session manager: opens a loopback proxy port, activates HypixelSafetyManager's
 * P2P bypass key, starts the P2PSession and connects the Minecraft client to the proxy.
 */
public final class GuestSession {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/GuestSession");

    public interface P2PStarter {
        java.util.concurrent.CompletableFuture<P2PSession> start(String sid, boolean host, P2PSession.IceConfig cfg, Consumer<JsonObject> sendSignal);
    }

    private static volatile P2PStarter p2pStarter = P2PSession::start;

    public static void setP2PStarter(P2PStarter starter) {
        p2pStarter = starter != null ? starter : P2PSession::start;
    }

    private volatile boolean active;
    private volatile UUID hostUuid;
    private volatile String hostDisplayName;
    private volatile String sid;
    private volatile ServerSocket proxyServer;
    private volatile Socket activeSocket;
    private volatile P2PSession p2pSession;

    public GuestSession() {}

    public boolean isActive() {
        return active;
    }

    public UUID hostUuid() {
        return hostUuid;
    }

    public String sid() {
        return sid;
    }

    public synchronized void start(String sid, UUID hostUuid, String hostDisplayName, String worldId,
                                   P2PSession.IceConfig iceConfig, Consumer<JsonObject> sendSignal) {
        close("Starting new session");

        this.sid = sid;
        this.hostUuid = hostUuid;
        this.hostDisplayName = hostDisplayName != null ? hostDisplayName : "Friend World";
        this.active = true;

        String safetyKey = "p2p:" + hostUuid + "/" + (worldId != null ? worldId : "world");
        HypixelSafetyManager.setP2PSession(safetyKey);

        try {
            proxyServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            proxyServer.setReuseAddress(true);
            int localPort = proxyServer.getLocalPort();
            LOGGER.info("GuestSession: local proxy listening on port {} for sid {}", localPort, sid);

            // Connect vanilla client to the loopback proxy
            SocialBridge.get().connectToLocal(localPort, this.hostDisplayName);

            // Start P2P session
            p2pStarter.start(sid, false, iceConfig, sendSignal).thenAccept(session -> {
                this.p2pSession = session;
                session.onClose(reason -> {
                    LOGGER.info("GuestSession: P2P session closed ({})", reason);
                    close(reason);
                });

                // Listen for Minecraft client TCP connection to proxy
                Thread acceptThread = new Thread(() -> acceptAndBridge(session), "Lads-GuestProxy-" + sid);
                acceptThread.setDaemon(true);
                acceptThread.start();
            }).exceptionally(ex -> {
                LOGGER.warn("GuestSession: P2P session failed to start: {}", ex.getMessage());
                close("P2P session failed");
                return null;
            });
        } catch (IOException e) {
            LOGGER.error("GuestSession: failed to bind proxy server socket: {}", e.getMessage());
            close("Proxy socket bind failure");
        }
    }

    private void acceptAndBridge(P2PSession session) {
        try {
            if (proxyServer == null || proxyServer.isClosed()) return;
            Socket socket = proxyServer.accept();
            socket.setTcpNoDelay(true);
            this.activeSocket = socket;

            // Minecraft client TCP -> P2P stream 0
            Thread clientToP2P = new Thread(() -> pipe(getIn(socket), session.out(P2PSession.MINECRAFT)), "Lads-GuestPipeOut");
            clientToP2P.setDaemon(true);
            clientToP2P.start();

            // P2P stream 0 -> Minecraft client TCP
            pipe(session.in(P2PSession.MINECRAFT), socket.getOutputStream());
        } catch (IOException e) {
            if (active) LOGGER.warn("GuestSession: proxy connection ended: {}", e.getMessage());
        } finally {
            close("Proxy connection finished");
        }
    }

    public synchronized void close(String reason) {
        if (!active && proxyServer == null && p2pSession == null) return;
        active = false;
        HypixelSafetyManager.setP2PSession(null);

        if (proxyServer != null) {
            try { proxyServer.close(); } catch (IOException ignored) {}
            proxyServer = null;
        }
        if (activeSocket != null) {
            try { activeSocket.close(); } catch (IOException ignored) {}
            activeSocket = null;
        }
        P2PSession sessionToClose = p2pSession;
        p2pSession = null;
        if (sessionToClose != null) {
            try { sessionToClose.close(reason); } catch (Exception ignored) {}
        }

        sid = null;
        hostUuid = null;
        hostDisplayName = null;
        LOGGER.info("GuestSession: closed ({})", reason);
    }

    public void onSignal(JsonObject data) {
        P2PSession s = p2pSession;
        if (s != null && data != null) {
            s.onSignal(data);
        }
    }

    private static InputStream getIn(Socket s) {
        try { return s.getInputStream(); } catch (IOException e) { return InputStream.nullInputStream(); }
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

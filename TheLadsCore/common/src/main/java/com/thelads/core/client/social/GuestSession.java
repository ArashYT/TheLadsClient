package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.social.modsync.ModSyncDownloader;
import com.thelads.core.client.social.modsync.ModSyncEntry;
import com.thelads.core.client.social.modsync.ModSyncFlow;
import com.thelads.core.client.social.p2p.P2PSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
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

    private final SocialState owner;
    private volatile boolean active;
    private volatile boolean linkUp; // the P2P stream is up: the game was told to connect
    private volatile UUID hostUuid;
    private volatile String hostDisplayName;
    private volatile String sid;
    private volatile ServerSocket proxyServer;
    private volatile String proxyAddress;
    private volatile Socket activeSocket;
    private volatile P2PSession p2pSession;
    private volatile String expectedSid; // p2p_start came while the game was still in a world: start(sid) follows once it left
    private volatile String status; // ModSync: what it is doing, under "Connecting to ..."
    private volatile SocialState.TrustQuestion trustQuestion; // ModSync: waiting for the player's answer

    private static final int MAX_PENDING_SIGNALS = 64;
    private final Queue<JsonObject> pendingSignals = new ConcurrentLinkedQueue<>();
    private final Object signalLock = new Object();
    private final AtomicBoolean ended = new AtomicBoolean(false);

    GuestSession(SocialState owner) {
        this.owner = owner;
    }

    public boolean isActive() {
        return active;
    }

    String hostName() {
        return hostDisplayName;
    }

    public UUID hostUuid() {
        return hostUuid;
    }

    public String sid() {
        return sid;
    }

    /** The game's connection is our proxy ("127.0.0.1:<port>", the address connectToLocal used). */
    boolean isProxy(String serverAddress) {
        return serverAddress != null && serverAddress.equals(proxyAddress);
    }

    /** The friend whose world we are connecting to while the P2P link is not up yet (the Friends screen shows it), else null. */
    String connectingTo() {
        return active && !linkUp ? hostDisplayName : null;
    }

    /** What the connection is doing while the link is up but the game was not told to connect yet (ModSync), else null. */
    String connectingStatus() {
        return active && !linkUp ? status : null;
    }

    /** ModSync's "trust this host's mods?" while it waits for an answer, else null. */
    SocialState.TrustQuestion trustQuestion() {
        SocialState.TrustQuestion q = trustQuestion;
        return q != null && active && !q.answer().isDone() ? q : null;
    }

    public boolean ownsSid(String sid) {
        return sid != null && (active && Objects.equals(this.sid, sid) || Objects.equals(expectedSid, sid));
    }

    /** Ends the current session and holds sid's signals until start(sid): the host's candidates may arrive while the game leaves its world. */
    public synchronized void expect(String sid) {
        close("Switching world");
        expectedSid = sid;
    }

    /** Still waiting for start(sid): not cancelled or replaced while the game left its world. */
    public boolean isExpected(String sid) {
        return sid != null && Objects.equals(expectedSid, sid);
    }

    public synchronized void start(String sid, UUID hostUuid, String hostDisplayName, String worldId,
                                   P2PSession.IceConfig iceConfig, Consumer<JsonObject> sendSignal) {
        boolean expected = isExpected(sid);
        if (!expected) close("Starting new session"); // expect(sid) already ended the old one; sid's early signals stay queued

        this.sid = sid;
        this.hostUuid = hostUuid;
        this.hostDisplayName = hostDisplayName != null ? hostDisplayName : "Friend World";
        this.active = true;
        this.linkUp = false;
        this.status = null;
        this.ended.set(false);
        expectedSid = null; // after sid/active: ownsSid has no gap for the host's signals

        synchronized (signalLock) {
            if (!expected) pendingSignals.clear();
            p2pSession = null;
        }

        String safetyKey = "p2p:" + hostUuid + "/" + (worldId != null ? worldId : "world");

        try {
            ServerSocket proxy = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[] {127, 0, 0, 1})); // never ::1: proxyAddress says 127.0.0.1
            proxyServer = proxy;
            int localPort = proxy.getLocalPort();
            proxyAddress = "127.0.0.1:" + localPort;
            HypixelSafetyManager.setP2PSession(safetyKey, proxyAddress); // an unknown server while connected to this proxy
            LOGGER.info("GuestSession: local proxy listening on port {} for sid {}", localPort, sid);

            // Start P2P session; the game connects to the proxy only once it is up (F13: nothing else can take the port meanwhile)
            CompletableFuture<P2PSession> started = p2pStarter.start(sid, false, iceConfig, sendSignal);
            // The future is the session itself (P2PSession.start): the host's candidates must reach it while it connects.
            if (started instanceof P2PSession connecting) attach(sid, connecting);
            started.thenAccept(session -> {
                if (!attach(sid, session)) return;

                session.onClose(reason -> {
                    LOGGER.info("GuestSession: P2P session closed ({})", reason);
                    close(sid, reason);
                });

                // Listen for Minecraft client TCP connection to proxy, then let the game connect
                String shown = this.hostDisplayName + (session.relayed() ? " (relayed)" : " (direct)");
                Runnable connect = () -> {
                    Thread acceptThread = new Thread(() -> acceptAndBridge(sid, proxy, session), "Lads-GuestProxy-" + sid);
                    acceptThread.setDaemon(true);
                    acceptThread.start();
                    linkUp = true;
                    owner.bridge().connectToLocal(localPort, shown);
                };
                if (!owner.needsModSync(hostUuid)) {
                    connect.run();
                    return;
                }
                // The host runs jars we do not: they are fetched first; the game connects only when nothing is missing
                String hostName = this.hostDisplayName;
                Thread modSync = new Thread(() -> modSync(sid, hostUuid, hostName, session, connect), "Lads-GuestModSync-" + sid);
                modSync.setDaemon(true);
                modSync.start();
            }).exceptionally(ex -> {
                LOGGER.warn("GuestSession: P2P session failed to start: {}", ex.getMessage());
                if (Objects.equals(this.sid, sid) && active) owner.bridge().toast("Friend's world", "Could not reach " + hostDisplayName + "'s world", null, null);
                close(sid, "P2P session failed");
                return null;
            });
        } catch (IOException e) {
            LOGGER.error("GuestSession: failed to bind proxy server socket: {}", e.getMessage());
            close("Proxy socket bind failure");
        }
    }

    private boolean isSession(String sid) {
        return active && Objects.equals(this.sid, sid);
    }

    /**
     * ModSync before the game connects: nothing missing connects as usual; fetched jars end in a restart through the launcher (the
     * host lets us back in, SocialState rejoins); Cancel or a refused trust question ends the session. A sync that fails joins
     * without the host's mods, as before ModSync: a world that needs them refuses the login and says which are missing.
     */
    private void modSync(String sid, UUID host, String hostName, P2PSession session, Runnable connect) {
        SocialBridge bridge = owner.bridge();
        java.nio.file.Path gameDir = bridge.gameDir();
        ModSyncFlow.Ui ui = new ModSyncFlow.Ui() {
            @Override public void status(String text) {
                status = text;
            }

            @Override public boolean trust(List<ModSyncEntry> jars) {
                long bytes = 0;
                for (ModSyncEntry m : jars) bytes += m.size();
                String size = bytes >= 1024 * 1024 ? (bytes / (1024 * 1024)) + " MB" : Math.max(1, bytes / 1024) + " KB";
                SocialState.TrustQuestion q = new SocialState.TrustQuestion("Trust " + hostName + "'s mods?",
                        jars.size() + (jars.size() == 1 ? " mod file (" : " mod files (") + size + ") in " + hostName + "'s world "
                                + (jars.size() == 1 ? "is" : "are") + " not on Modrinth or CurseForge and would come straight from "
                                + hostName + "'s PC. A mod is a program: it can do anything on your PC. Continue only if you trust "
                                + hostName + ". The game restarts with these mods; your own mods and settings stay as they are.",
                        new CompletableFuture<>());
                status = "Waiting for your answer";
                trustQuestion = q;
                try {
                    return q.answer().get(3, TimeUnit.MINUTES);
                } catch (Exception e) { // no answer: no
                    return false;
                } finally {
                    trustQuestion = null;
                }
            }

            @Override public boolean cancelled() {
                return !isSession(sid);
            }
        };
        ModSyncFlow.Result result;
        try {
            result = ModSyncFlow.guest(gameDir, host, session.in(P2PSession.MODSYNC), session.out(P2PSession.MODSYNC),
                    owner.loadedMods(), ModSyncDownloader.http(), ModSyncFlow.relaunchedFor(host), ui);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("GuestSession: ModSync failed: {}", e.toString());
            ModSyncFlow.dropRelaunch(gameDir); // never a restart the player was not told about
            if (isSession(sid)) bridge.toast("Friend's world", "Could not get " + hostName + "'s mods: joining without them", null, null);
            result = ModSyncFlow.Result.CONNECT;
        }
        if (!isSession(sid)) { // cancelled, or the link ended, while it ran
            if (result == ModSyncFlow.Result.RELAUNCH) ModSyncFlow.dropRelaunch(gameDir);
            return;
        }
        switch (result) {
            case CONNECT -> connect.run();
            case CANCELLED -> close(sid, "Cancelled");
            case RELAUNCH -> {
                try {
                    Thread.sleep(1500); // the player reads "Restarting the game with N mods"
                } catch (InterruptedException ignored) {
                }
                if (!isSession(sid)) {
                    ModSyncFlow.dropRelaunch(gameDir);
                    return;
                }
                LOGGER.info("GuestSession: restarting with {}'s mods", host);
                close(sid, "Restarting with the host's mods"); // after "bye relaunch": close delivers what was written
                bridge.quitGame();
            }
        }
    }

    /** From now on session gets the signals (queued ones first); false, closing it, when this session was closed or replaced. */
    private boolean attach(String sid, P2PSession session) {
        synchronized (signalLock) {
            if (!active || !Objects.equals(this.sid, sid)) {
                session.close("Session ended");
                return false;
            }
            this.p2pSession = session;
            JsonObject sig;
            while ((sig = pendingSignals.poll()) != null) {
                session.onSignal(sig);
            }
            return true;
        }
    }

    /**
     * The game's socket is never closed with bytes unread: Windows then resets the connection and the game drops what it was sent last
     * (the host's "Kicked by host"). The host's end is written out, then FIN (half-close); the game reads it and closes its side, and
     * the socket closes once that side is read to the end (close() bounds it to 5 s).
     */
    private void acceptAndBridge(String sid, ServerSocket proxy, P2PSession session) {
        try {
            Socket socket = proxy.accept();
            proxy.close(); // one connection: no other local process can get a second pipe to the friend's world
            socket.setTcpNoDelay(true);
            this.activeSocket = socket;

            // Minecraft client TCP -> P2P stream 0; the game closing its connection (leaving the world) ends the session at once
            Thread clientToP2P = new Thread(() -> {
                copy(getIn(socket), session.out(P2PSession.MINECRAFT));
                try { socket.close(); } catch (IOException ignored) {}
                close(sid, "Left the friend's world");
            }, "Lads-GuestPipeOut");
            clientToP2P.setDaemon(true);
            clientToP2P.start();

            // P2P stream 0 -> Minecraft client TCP, to the host's last byte (the session's buffer outlives its close), then FIN
            copy(session.in(P2PSession.MINECRAFT), socket.getOutputStream());
            socket.shutdownOutput();
        } catch (IOException e) {
            if (active) LOGGER.warn("GuestSession: proxy connection ended: {}", e.getMessage());
        } finally {
            close(sid, "Proxy connection finished");
        }
    }

    /** Ends the session only while it is still sid: a late callback of an older session never ends a newer one. */
    private synchronized void close(String sid, String reason) {
        if (Objects.equals(this.sid, sid)) close(reason);
    }

    public synchronized void close(String reason) {
        expectedSid = null;
        if (!active && proxyServer == null && p2pSession == null && sid == null) return;
        active = false;
        SocialState.TrustQuestion question = trustQuestion;
        if (question != null) question.answer().complete(false);
        HypixelSafetyManager.setP2PSession(null, null);

        if (proxyServer != null) {
            try { proxyServer.close(); } catch (IOException ignored) {}
            proxyServer = null;
        }
        proxyAddress = null;
        Socket game = activeSocket;
        activeSocket = null;
        if (game != null) { // the pipes end it gracefully (acceptAndBridge); this only bounds a game that never closes its side
            CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
                try { game.close(); } catch (IOException ignored) {}
            });
        }

        String endingSid = this.sid;
        P2PSession sessionToClose;
        synchronized (signalLock) {
            pendingSignals.clear();
            sessionToClose = p2pSession;
            p2pSession = null;
        }

        if (endingSid != null && ended.compareAndSet(false, true)) {
            boolean relayed = sessionToClose != null && sessionToClose.relayed();
            long bytes = sessionToClose != null ? sessionToClose.bytes() : 0L;
            owner.sendRaw(SocialProtocol.msg(SocialProtocol.P2P_END,
                    "sid", endingSid,
                    "reason", reason != null ? reason : "Session ended",
                    "relayed", relayed,
                    "bytes", bytes));
        }

        if (sessionToClose != null) {
            try { sessionToClose.close(reason); } catch (Exception ignored) {}
        }

        sid = null;
        hostUuid = null;
        hostDisplayName = null;
        LOGGER.info("GuestSession: closed ({})", reason);
    }

    public void onSignal(JsonObject data) {
        if (data == null) return;
        synchronized (signalLock) {
            P2PSession s = p2pSession;
            if (s != null) {
                s.onSignal(data);
            } else if (pendingSignals.size() < MAX_PENDING_SIGNALS) {
                pendingSignals.offer(data);
            }
        }
    }

    private static InputStream getIn(Socket s) {
        try { return s.getInputStream(); } catch (IOException e) { return InputStream.nullInputStream(); }
    }

    /** in -> out until in ends; once out fails the rest of in is read and dropped (never left unread). Closes neither. */
    static void copy(InputStream in, OutputStream out) {
        byte[] buf = new byte[8192];
        boolean sink = false;
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (sink) continue;
                try {
                    out.write(buf, 0, n);
                    out.flush();
                } catch (IOException e) {
                    sink = true;
                }
            }
        } catch (IOException ignored) {
        }
    }
}

package com.thelads.core.client.social.p2p;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Implementation of P2PSession.
 * Integrates IceAgent, ReliableStream, and Mux to establish and manage
 * an end-to-end reliable multiplexed peer-to-peer connection.
 */
final class P2PSessionImpl extends CompletableFuture<P2PSession> implements P2PSession {
    private static final java.util.concurrent.ConcurrentHashMap<String, P2PSessionImpl> ACTIVE_SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private final String sid;
    private final boolean host;
    private final IceConfig config;
    private final Consumer<JsonObject> sendSignal;

    private final IceAgent iceAgent;
    private final ReliableStream reliableStream;
    private final Mux mux;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<Consumer<String>> closeListeners = new CopyOnWriteArrayList<>();
    private volatile String closeReason = "Session closed";

    private P2PSessionImpl(String sid, boolean host, IceConfig config, Consumer<JsonObject> sendSignal) throws Exception {
        this.sid = sid;
        this.host = host;
        this.config = config;
        this.sendSignal = sendSignal;

        this.iceAgent = new IceAgent(sid, host, config, sendSignal);
        this.reliableStream = new ReliableStream(iceAgent::send);
        this.iceAgent.setPacketConsumer((bytes) -> {
            reliableStream.onDatagramReceived(bytes, 0, bytes.length);
        });

        this.mux = new Mux(reliableStream.getInputStream(), reliableStream.getOutputStream());
    }

    static CompletableFuture<P2PSession> start(String sid, boolean host, IceConfig cfg, Consumer<JsonObject> sendSignal) {
        try {
            P2PSessionImpl session = new P2PSessionImpl(sid, host, cfg, sendSignal);
            ACTIVE_SESSIONS.put(sid, session);

            // Timeout after 20 seconds
            CompletableFuture<Void> connFuture = session.iceAgent.getConnectedFuture();
            session.iceAgent.gatherAndSendSignal();

            connFuture.orTimeout(20, TimeUnit.SECONDS).whenComplete((v, err) -> {
                if (err != null) {
                    session.close("Connection failed or timed out: " + err.getMessage());
                    session.completeExceptionally(err);
                } else {
                    session.startControlListener();
                    session.complete(session);
                }
            });

            return session;
        } catch (Exception e) {
            CompletableFuture<P2PSession> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public static void deliverSignal(String sid, JsonObject data) {
        P2PSessionImpl session = ACTIVE_SESSIONS.get(sid);
        if (session != null) {
            session.onSignal(data);
        }
    }

    private void startControlListener() {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mux.in(CONTROL), StandardCharsets.UTF_8))) {
                String line;
                while (!closed.get() && (line = reader.readLine()) != null) {
                    try {
                        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                        if (obj.has("action") && "close".equals(obj.get("action").getAsString())) {
                            String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Peer closed session";
                            close(reason);
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            if (!closed.get()) {
                close("Underlying connection closed");
            }
        }, "P2P-ControlListener-" + sid);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    @Override
    public void onSignal(JsonObject data) {
        iceAgent.onSignal(data);
    }

    @Override
    public InputStream in(int stream) {
        return mux.in(stream);
    }

    @Override
    public OutputStream out(int stream) {
        return mux.out(stream);
    }

    @Override
    public boolean relayed() {
        return iceAgent.isRelayed();
    }

    @Override
    public long bytes() {
        return iceAgent.getBytesSent() + iceAgent.getBytesReceived();
    }

    @Override
    public void close(String reason) {
        if (closed.compareAndSet(false, true)) {
            ACTIVE_SESSIONS.remove(sid, this);
            this.closeReason = reason;

            // Send close control message to peer
            try {
                OutputStream controlOut = mux.out(CONTROL);
                JsonObject msg = new JsonObject();
                msg.addProperty("action", "close");
                msg.addProperty("reason", reason);
                controlOut.write((msg.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                controlOut.flush();
            } catch (Exception ignored) {
            }

            try {
                mux.close();
            } catch (Exception ignored) {
            }
            try {
                reliableStream.close();
            } catch (Exception ignored) {
            }

            // Close iceAgent after a brief moment (20ms) so final datagram can exit the socket buffer
            Thread cleanup = new Thread(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {}
                try {
                    iceAgent.close();
                } catch (Exception ignored) {}
            }, "P2P-Cleanup-" + sid);
            cleanup.setDaemon(true);
            cleanup.start();

            for (Consumer<String> listener : closeListeners) {
                try {
                    listener.accept(reason);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onClose(Consumer<String> reasonConsumer) {
        if (closed.get()) {
            reasonConsumer.accept(closeReason);
        } else {
            closeListeners.add(reasonConsumer);
        }
    }
}

package com.thelads.core.client.social.p2p;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    static final int MAX_CONTROL_LINE = 64 * 1024;
    private static final int MAX_REASON = 128;

    private void startControlListener() {
        Thread t = new Thread(() -> {
            try (InputStream in = new BufferedInputStream(mux.in(CONTROL))) {
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                String text;
                while (!closed.get() && (text = readControlLine(in, line)) != null) {
                    try {
                        JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                        if (obj.has("action") && "close".equals(obj.get("action").getAsString())) {
                            String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Peer closed session";
                            close(reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
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

    /** One control line without its newline; null at the end of the stream. Longer than MAX_CONTROL_LINE: IOException (the session ends). */
    static String readControlLine(InputStream in, ByteArrayOutputStream line) throws IOException {
        line.reset();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') return new String(line.toByteArray(), StandardCharsets.UTF_8);
            if (line.size() >= MAX_CONTROL_LINE) throw new IOException("Control line over " + MAX_CONTROL_LINE + " bytes");
            line.write(b);
        }
        return line.size() == 0 ? null : new String(line.toByteArray(), StandardCharsets.UTF_8);
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

    /**
     * Never blocks the caller (the game thread kicks, stops hosting, leaves a world): a writer stuck on a full send window holds the mux
     * output, so the polite "close" line (then FIN) gets at most 1 s on its own thread; then the streams and the ICE socket are shut on a
     * worker, which wakes any stuck writer.
     */
    @Override
    public void close(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        ACTIVE_SESSIONS.remove(sid, this);
        this.closeReason = reason;

        Thread polite = new Thread(() -> {
            try {
                JsonObject msg = new JsonObject();
                msg.addProperty("action", "close");
                msg.addProperty("reason", reason);
                OutputStream controlOut = mux.out(CONTROL);
                controlOut.write((msg.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                controlOut.flush();
            } catch (Exception ignored) {
            }
            mux.close(); // FIN
        }, "P2P-CloseLine-" + sid);
        polite.setDaemon(true);
        Thread closer = new Thread(() -> {
            polite.start();
            try {
                polite.join(1000);
            } catch (InterruptedException ignored) {
            }
            reliableStream.close(); // a writer still stuck on the window throws now
            mux.close();
            try {
                Thread.sleep(20); // the last datagrams leave the socket
            } catch (InterruptedException ignored) {
            }
            iceAgent.close();
        }, "P2P-Close-" + sid);
        closer.setDaemon(true);
        closer.start();

        for (Consumer<String> listener : closeListeners) {
            try {
                listener.accept(reason);
            } catch (Exception ignored) {
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

package com.thelads.core.client.social.p2p;

import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One peer-to-peer link between a host and one guest (contract): ICE over UDP (direct or Cloudflare TURN relay), a reliable
 * stream on top, multiplexed: stream 0 = the Minecraft connection, 1 = control, 2+ = later (pack transfers).
 * The implementation (P2PSessionImpl, lane S2) has no library dependencies.
 */
public interface P2PSession {
    int MINECRAFT = 0, CONTROL = 1;

    record TurnServer(String url, String user, String credential) {}

    record IceConfig(List<String> stun, List<TurnServer> turn, boolean relayOnly) {}

    /**
     * Gathers candidates, sends them through sendSignal (relayed by the backend to the peer), connects; completes when the reliable
     * stream is up, exceptionally on failure or after 20 s. The returned future is the session itself: the peer's signals go to its
     * onSignal while it connects (they arrive before the stream is up; HostSession and GuestSession rely on this).
     */
    static CompletableFuture<P2PSession> start(String sid, boolean host, IceConfig cfg, Consumer<JsonObject> sendSignal) {
        return P2PSessionImpl.start(sid, host, cfg, sendSignal);
    }

    /** A signal payload from the peer (the backend's "signal" message data). */
    void onSignal(JsonObject data);

    InputStream in(int stream);

    OutputStream out(int stream);

    /** True when the selected path goes through TURN. */
    boolean relayed();

    /** Bytes sent + received so far (reported in p2p_end for the relay budget). */
    long bytes();

    void close(String reason);

    /** Called once, on any thread, when the session ends (peer closed, timeout, close()). */
    void onClose(Consumer<String> reason);
}

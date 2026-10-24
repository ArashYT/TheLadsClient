package com.thelads.core.client.social.p2p;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Lane S2 replaces this stub with the real transport. */
final class P2PSessionImpl {
    private P2PSessionImpl() {}

    static CompletableFuture<P2PSession> start(String sid, boolean host, P2PSession.IceConfig cfg, Consumer<JsonObject> sendSignal) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("P2P transport not built yet"));
    }
}

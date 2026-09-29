package com.thelads.core.client.discord;

import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRichPresence;
import java.util.function.Supplier;

/** Lazy adapter around the already bundled Discord IPC runtime; no HTTP or account tokens. */
public final class NativeDiscordTransport implements DiscordRpcService.Transport {
    private final Supplier<DiscordRPC> library;
    private DiscordRPC api;
    // Strong reference keeps JNA callbacks alive until native shutdown finishes.
    private DiscordEventHandlers handlers;
    public NativeDiscordTransport() { this(() -> DiscordRPC.INSTANCE); }
    public NativeDiscordTransport(Supplier<DiscordRPC> library) { this.library = library; }
    @Override public void initialize(String applicationId, DiscordRpcService.Events events) {
        api = library.get();
        handlers = new DiscordEventHandlers();
        handlers.ready = user -> events.ready(); // Do not retain or log Discord's user payload.
        handlers.disconnected = (code, message) -> events.disconnected(code);
        handlers.errored = (code, message) -> events.error(code);
        api.Discord_Initialize(applicationId, handlers, false, null);
    }
    @Override public void callbacks() { api.Discord_RunCallbacks(); }
    @Override public void update(DiscordPresence activity) {
        DiscordRichPresence presence = new DiscordRichPresence();
        presence.details = DiscordPresence.text(activity.details()); presence.state = DiscordPresence.text(activity.state());
        presence.startTimestamp = Math.max(0, activity.startTimestamp());
        // No user identifiers, party IDs, join secrets or unregistered artwork keys.
        api.Discord_UpdatePresence(presence);
    }
    @Override public void clear() { if (api != null) api.Discord_ClearPresence(); }
    @Override public void shutdown() {
        try { if (api != null) api.Discord_Shutdown(); }
        finally { api = null; handlers = null; }
    }
}

package com.thelads.core.client.social;

/**
 * What a friend is doing (contract). state: menu | singleplayer | hosting | server | offline.
 * server is null unless the friend shares it; world is set only while hosting a joinable world.
 */
public record Presence(String state, SocialBridge.GameInfo game, String server, WorldAd world) {
    public static final Presence OFFLINE = new Presence("offline", null, null, null);

    /** A hosted world as friends see it. scope: friends | invite. */
    public record WorldAd(String id, String name, String scope, int slots, int used) {}

    public boolean online() {
        return !"offline".equals(state);
    }
}

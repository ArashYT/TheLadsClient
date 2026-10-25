package com.thelads.core.client.social;

import java.util.UUID;

/** One Lads friend (contract). nick is the player's private note name or null; since = epoch ms. */
public record FriendEntry(UUID id, String name, String nick, Presence presence, long since) {
    public String displayName() {
        return nick == null || nick.isEmpty() ? name : nick;
    }
}

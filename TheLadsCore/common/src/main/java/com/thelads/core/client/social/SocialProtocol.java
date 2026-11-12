package com.thelads.core.client.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** /social/ws message types (contract; server side: cosmetics-server/social.py). Every message is {"t": type, ...}. */
public final class SocialProtocol {
    public static final String URL_DEFAULT = "wss://bot.arashyt.ca/social/ws";
    public static final String STUN_DEFAULT = "stun:stun.cloudflare.com:3478";

    // handshake (same as cosmetics)
    public static final String CHALLENGE = "challenge", AUTH = "auth", HELLO = "hello";
    // client -> server
    public static final String PRESENCE = "presence", FRIEND_ADD = "friend_add", FRIEND_ACCEPT = "friend_accept",
            FRIEND_DECLINE = "friend_decline", FRIEND_REMOVE = "friend_remove", BLOCK = "block", UNBLOCK = "unblock",
            NICK = "nick", DM = "dm", DM_IMG = "dm_img", INVITE = "invite", INVITE_ACCEPT = "invite_accept", INVITE_DECLINE = "invite_decline",
            JOIN_REQUEST = "join_request", JOIN_ACCEPT = "join_accept", JOIN_DENY = "join_deny", SIGNAL = "signal",
            P2P_END = "p2p_end", SETTINGS = "settings";
    // party, client -> server (party_warp also server -> client; party_state, party_invite_in, party_ended are server -> client)
    public static final String PARTY_INVITE = "party_invite", PARTY_ACCEPT = "party_accept", PARTY_DECLINE = "party_decline",
            PARTY_LEAVE = "party_leave", PARTY_KICK = "party_kick", PARTY_PROMOTE = "party_promote", PARTY_WARP = "party_warp",
            PARTY_STATE = "party_state", PARTY_INVITE_IN = "party_invite_in", PARTY_ENDED = "party_ended";
    public static final int PARTY_MAX_MEMBERS = 8, PARTY_SERVER_MAX = 255;
    /** What the backend accepts as a party warp address (host, optional :port). */
    public static final java.util.regex.Pattern PARTY_SERVER = java.util.regex.Pattern.compile("[A-Za-z0-9.-]{1,253}(?::[0-9]{1,5})?");
    // DM pictures: DM carries "img" (a screenshot id, uploaded as DM_IMG parts; see DmMedia) or "gif" (a Tenor/Giphy link) next to "text".
    // server -> client (plus PRESENCE, DM, INVITE, JOIN_REQUEST, SIGNAL, P2P_END above)
    public static final String REQUEST_IN = "request_in", REQUEST_SENT = "request_sent", FRIEND_ADDED = "friend_added",
            FRIEND_REMOVED = "friend_removed", JOIN_DENIED = "join_denied", P2P_START = "p2p_start", ERROR = "error";
    // error codes
    public static final String E_NOT_FOUND = "not_found", E_SELF = "self", E_ALREADY = "already", E_BLOCKED = "blocked",
            E_LIMIT = "limit", E_RATE = "rate", E_NOT_FRIEND = "not_friend", E_EXPIRED = "expired",
            E_INVITE_ONLY = "invite_only", E_FULL = "full", E_INCOMPATIBLE = "incompatible", E_OFFLINE = "offline",
            E_NOT_LEADER = "not_leader";

    public static final int MAX_DM = 256;

    private SocialProtocol() {}

    /** msg("dm", "to", uuid, "text", text): string, number and boolean values; a JsonObject value is nested as-is. */
    public static JsonObject msg(String t, Object... kv) {
        if ((kv.length & 1) != 0) throw new IllegalArgumentException("key/value pairs");
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v == null) continue;
            if (v instanceof JsonObject j) o.add(k, j);
            else if (v instanceof Number n) o.add(k, new JsonPrimitive(n));
            else if (v instanceof Boolean b) o.add(k, new JsonPrimitive(b));
            else o.addProperty(k, v.toString());
        }
        return o;
    }
}

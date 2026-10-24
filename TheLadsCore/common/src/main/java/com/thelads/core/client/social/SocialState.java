package com.thelads.core.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.social.p2p.P2PSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Client-side friends, presence, DMs, invites and P2P orchestration state.
 * Shared state used by the Friends and Host menus across all Minecraft versions.
 */
public class SocialState {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/SocialState");

    public record Dm(UUID from, String text, long ts) {}
    public record JoinCheck(boolean ok, String reason) {}
    public record Invite(String inviteId, UUID from, String worldName, boolean isServer, String serverAddress) {}
    public record JoinRequest(String reqId, UUID from, SocialBridge.GameInfo game) {}

    private static volatile SocialState instance;

    public static SocialState get() {
        if (instance == null) {
            synchronized (SocialState.class) {
                if (instance == null) {
                    instance = new SocialState();
                }
            }
        }
        return instance;
    }

    private final Map<UUID, FriendEntry> friendsMap = new ConcurrentHashMap<>();
    private final Map<UUID, FriendEntry> incomingMap = new ConcurrentHashMap<>();
    private final Map<UUID, FriendEntry> outgoingMap = new ConcurrentHashMap<>();
    private final Map<UUID, FriendEntry> blockedMap = new ConcurrentHashMap<>();
    private final Set<UUID> blockedUuids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Dm>> dmsMap = new ConcurrentHashMap<>();

    private final Map<String, Invite> activeInvites = new ConcurrentHashMap<>();
    private final Map<String, JoinRequest> activeJoinRequests = new ConcurrentHashMap<>();

    private final HostSession hostSession = new HostSession();
    private final GuestSession guestSession = new GuestSession();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private Consumer<JsonObject> clientSender;

    public SocialState() {
        this.clientSender = obj -> SocialClient.get().send(obj);
    }

    public void setClientSender(Consumer<JsonObject> sender) {
        this.clientSender = sender != null ? sender : obj -> SocialClient.get().send(obj);
    }

    public HostSession hostSession() {
        return hostSession;
    }

    public GuestSession guestSession() {
        return guestSession;
    }

    public boolean isGuestSessionActive() {
        return guestSession.isActive();
    }

    public UUID activeGuestHostUuid() {
        return guestSession.hostUuid();
    }

    public String activeGuestSid() {
        return guestSession.sid();
    }

    public void closeGuestSession(String reason) {
        guestSession.close(reason);
    }

    public List<FriendEntry> friends() {
        List<FriendEntry> list = new ArrayList<>(friendsMap.values());
        list.sort((a, b) -> {
            boolean aOnline = a.presence() != null && a.presence().online();
            boolean bOnline = b.presence() != null && b.presence().online();
            if (aOnline != bOnline) return aOnline ? -1 : 1;
            return a.displayName().compareToIgnoreCase(b.displayName());
        });
        return Collections.unmodifiableList(list);
    }

    public List<FriendEntry> incoming() {
        return Collections.unmodifiableList(new ArrayList<>(incomingMap.values()));
    }

    public List<FriendEntry> outgoing() {
        return Collections.unmodifiableList(new ArrayList<>(outgoingMap.values()));
    }

    public List<FriendEntry> blocked() {
        return Collections.unmodifiableList(new ArrayList<>(blockedMap.values()));
    }

    public Set<UUID> blockedUuids() {
        return Collections.unmodifiableSet(blockedUuids);
    }

    public boolean isFriend(UUID id) {
        return id != null && friendsMap.containsKey(id);
    }

    public void addListener(Runnable l) {
        if (l != null) listeners.add(l);
    }

    public void removeListener(Runnable l) {
        listeners.remove(l);
    }

    public void notifyListeners() {
        for (Runnable l : listeners) {
            try { l.run(); } catch (Exception e) { LOGGER.warn("SocialState: listener failed", e); }
        }
    }

    public String connectionStatus() {
        return SocialClient.get().connectionStatus();
    }

    // --- Outbound actions ---

    private void send(String type, Object... kv) {
        clientSender.accept(SocialProtocol.msg(type, kv));
    }

    public void addFriend(String name) {
        if (name != null && !name.trim().isEmpty()) {
            send(SocialProtocol.FRIEND_ADD, "name", name.trim());
        }
    }

    public void accept(UUID uuid) {
        if (uuid != null) {
            send(SocialProtocol.FRIEND_ACCEPT, "uuid", uuid.toString());
            incomingMap.remove(uuid);
            notifyListeners();
        }
    }

    public void decline(UUID uuid) {
        if (uuid != null) {
            send(SocialProtocol.FRIEND_DECLINE, "uuid", uuid.toString());
            incomingMap.remove(uuid);
            notifyListeners();
        }
    }

    public void remove(UUID uuid) {
        if (uuid != null) {
            send(SocialProtocol.FRIEND_REMOVE, "uuid", uuid.toString());
            friendsMap.remove(uuid);
            notifyListeners();
        }
    }

    public void block(UUID uuid) {
        if (uuid != null) {
            send(SocialProtocol.BLOCK, "uuid", uuid.toString());
            blockedUuids.add(uuid);
            FriendEntry f = friendsMap.remove(uuid);
            if (f != null) blockedMap.put(uuid, f);
            else blockedMap.put(uuid, new FriendEntry(uuid, uuid.toString().substring(0, 8), null, Presence.OFFLINE, 0));
            incomingMap.remove(uuid);
            outgoingMap.remove(uuid);
            notifyListeners();
        }
    }

    public void unblock(UUID uuid) {
        if (uuid != null) {
            send(SocialProtocol.UNBLOCK, "uuid", uuid.toString());
            blockedUuids.remove(uuid);
            blockedMap.remove(uuid);
            notifyListeners();
        }
    }

    public void nick(UUID uuid, String nick) {
        if (uuid != null) {
            send(SocialProtocol.NICK, "uuid", uuid.toString(), "nick", nick != null ? nick : "");
            FriendEntry old = friendsMap.get(uuid);
            if (old != null) {
                friendsMap.put(uuid, new FriendEntry(uuid, old.name(), nick, old.presence(), old.since()));
                notifyListeners();
            }
        }
    }

    public void sendDm(UUID to, String text) {
        if (to == null || text == null || text.trim().isEmpty()) return;
        String trimmed = text.length() > SocialProtocol.MAX_DM ? text.substring(0, SocialProtocol.MAX_DM) : text;
        send(SocialProtocol.DM, "to", to.toString(), "text", trimmed);

        LadsGameBridge.Session session = LadsGameBridge.get().session();
        UUID self = session != null ? session.uuid() : null;
        if (self != null) {
            recordDm(to, new Dm(self, trimmed, System.currentTimeMillis()));
        }
    }

    public List<Dm> dms(UUID friendUuid) {
        if (friendUuid == null) return Collections.emptyList();
        List<Dm> list = dmsMap.get(friendUuid);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    private void recordDm(UUID friendUuid, Dm dm) {
        dmsMap.computeIfAbsent(friendUuid, k -> new CopyOnWriteArrayList<>()).add(dm);
        notifyListeners();
    }

    public void invite(UUID friendUuid) {
        if (friendUuid == null) return;
        if (hostSession.isHosting()) {
            HostOptions opt = hostSession.options();
            JsonObject worldAd = new JsonObject();
            worldAd.addProperty("id", SocialBridge.get().worldId() != null ? SocialBridge.get().worldId() : "world");
            worldAd.addProperty("name", SocialBridge.get().worldName() != null ? SocialBridge.get().worldName() : "My World");
            worldAd.addProperty("scope", opt.scope.name().toLowerCase());
            worldAd.addProperty("slots", opt.maxPlayers);
            worldAd.addProperty("used", hostSession.activeGuestCount() + 1);
            hostSession.accept(friendUuid);
            send(SocialProtocol.INVITE, "to", friendUuid.toString(), "world", worldAd);
        } else if (SocialBridge.get().inWorldOrServer()) {
            String addr = LadsGameBridge.get().getServerAddress();
            if (addr != null && !addr.isEmpty() && !"Singleplayer".equalsIgnoreCase(addr)) {
                send(SocialProtocol.INVITE, "to", friendUuid.toString(), "server", addr);
            }
        }
    }

    public void acceptInvite(String inviteId) {
        if (inviteId == null) return;
        if (SocialBridge.get().inWorldOrServer() || guestSession.isActive()) {
            guestSession.close("Switching world");
            SocialBridge.get().leaveWorld(() -> send(SocialProtocol.INVITE_ACCEPT, "inviteId", inviteId));
        } else {
            send(SocialProtocol.INVITE_ACCEPT, "inviteId", inviteId);
        }
        activeInvites.remove(inviteId);
    }

    public void declineInvite(String inviteId) {
        if (inviteId == null) return;
        send(SocialProtocol.INVITE_DECLINE, "inviteId", inviteId);
        activeInvites.remove(inviteId);
    }

    public void requestJoin(UUID host) {
        if (host == null) return;
        if (SocialBridge.get().inWorldOrServer() || guestSession.isActive()) {
            guestSession.close("Switching world");
            SocialBridge.get().leaveWorld(() -> send(SocialProtocol.JOIN_REQUEST, "host", host.toString()));
        } else {
            send(SocialProtocol.JOIN_REQUEST, "host", host.toString());
        }
    }

    public void answerJoin(String reqId, boolean yes) {
        if (reqId == null) return;
        JoinRequest req = activeJoinRequests.remove(reqId);
        if (yes) {
            if (req != null) hostSession.accept(req.from());
            send(SocialProtocol.JOIN_ACCEPT, "reqId", reqId);
        } else {
            send(SocialProtocol.JOIN_DENY, "reqId", reqId);
        }
    }

    public void startHosting(HostOptions options) {
        hostSession.start(options);
        publishUpdatedPresence();
        notifyListeners();
    }

    public void stopHosting() {
        hostSession.stop("Host closed the world");
        publishUpdatedPresence();
        notifyListeners();
    }

    public HostOptions hostOptions() {
        return hostSession.options();
    }

    private void publishUpdatedPresence() {
        SocialBridge bridge = SocialBridge.get();
        if (hostSession.isHosting()) {
            HostOptions opt = hostSession.options();
            Presence.WorldAd ad = new Presence.WorldAd(
                    bridge.worldId() != null ? bridge.worldId() : "world",
                    bridge.worldName() != null ? bridge.worldName() : "My World",
                    opt.scope.name().toLowerCase(),
                    opt.maxPlayers,
                    hostSession.activeGuestCount() + 1
            );
            SocialClient.get().setPresence(new Presence("hosting", bridge.gameInfo(), null, ad));
        } else if (bridge.inWorldOrServer()) {
            String addr = LadsGameBridge.get().getServerAddress();
            boolean isSp = "Singleplayer".equalsIgnoreCase(addr);
            SocialClient.get().setPresence(new Presence(isSp ? "singleplayer" : "server", bridge.gameInfo(), isSp ? null : addr, null));
        } else {
            SocialClient.get().setPresence(new Presence("menu", bridge.gameInfo(), null, null));
        }
    }

    public JoinCheck canJoin(FriendEntry friend) {
        if (friend == null) return new JoinCheck(false, "Unknown player");
        Presence p = friend.presence();
        if (p == null || !p.online()) return new JoinCheck(false, "Offline");
        Presence.WorldAd ad = p.world();
        if (ad == null) return new JoinCheck(false, "Not hosting a world");
        if (ad.slots() > 0 && ad.used() >= ad.slots()) return new JoinCheck(false, "World is full");

        SocialBridge.GameInfo hostGame = p.game();
        if (hostGame == null) return new JoinCheck(false, "Unknown game version");
        SocialBridge.GameInfo ourGame = SocialBridge.get().gameInfo();

        if (ourGame.mc() != null && !ourGame.mc().isEmpty() && hostGame.mc() != null && !hostGame.mc().isEmpty()) {
            if (!Objects.equals(ourGame.mc(), hostGame.mc())) {
                return new JoinCheck(false, "Needs Minecraft " + hostGame.mc());
            }
        }

        if (ourGame.loader() != null && !ourGame.loader().isEmpty() && hostGame.loader() != null && !hostGame.loader().isEmpty()) {
            if (!Objects.equals(ourGame.loader(), hostGame.loader())) {
                return new JoinCheck(false, "Needs " + hostGame.loader());
            }
        }

        if ("pack".equalsIgnoreCase(hostGame.profileKind())) {
            String neededPack = hostGame.packId() != null ? hostGame.packId() : "";
            String neededVer = hostGame.packVersion() != null ? hostGame.packVersion() : "";
            if (!Objects.equals(ourGame.packId(), hostGame.packId()) || !Objects.equals(ourGame.packVersion(), hostGame.packVersion())) {
                return new JoinCheck(false, "Needs " + neededPack + " " + neededVer);
            }
        }

        return new JoinCheck(true, null);
    }

    // --- Inbound message dispatching ---

    public void onHello(JsonObject hello) {
        friendsMap.clear();
        incomingMap.clear();
        outgoingMap.clear();
        blockedMap.clear();
        blockedUuids.clear();
        dmsMap.clear();

        if (hello.has("friends") && hello.get("friends").isJsonArray()) {
            for (JsonElement e : hello.getAsJsonArray("friends")) {
                if (e.isJsonObject()) {
                    FriendEntry f = parseFriendEntry(e.getAsJsonObject());
                    if (f != null) friendsMap.put(f.id(), f);
                }
            }
        }

        if (hello.has("requests") && hello.get("requests").isJsonObject()) {
            JsonObject reqs = hello.getAsJsonObject("requests");
            if (reqs.has("in") && reqs.get("in").isJsonArray()) {
                for (JsonElement e : reqs.getAsJsonArray("in")) {
                    if (e.isJsonObject()) {
                        FriendEntry req = parseFriendEntry(e.getAsJsonObject());
                        if (req != null) incomingMap.put(req.id(), req);
                    }
                }
            }
            if (reqs.has("out") && reqs.get("out").isJsonArray()) {
                for (JsonElement e : reqs.getAsJsonArray("out")) {
                    if (e.isJsonObject()) {
                        FriendEntry req = parseFriendEntry(e.getAsJsonObject());
                        if (req != null) outgoingMap.put(req.id(), req);
                    }
                }
            }
        }

        if (hello.has("blocked") && hello.get("blocked").isJsonArray()) {
            for (JsonElement e : hello.getAsJsonArray("blocked")) {
                UUID b = parseUuid(e.getAsString());
                if (b != null) {
                    blockedUuids.add(b);
                    blockedMap.put(b, new FriendEntry(b, b.toString().substring(0, 8), null, Presence.OFFLINE, 0));
                }
            }
        }

        if (hello.has("dms") && hello.get("dms").isJsonArray()) {
            for (JsonElement e : hello.getAsJsonArray("dms")) {
                if (e.isJsonObject()) {
                    JsonObject d = e.getAsJsonObject();
                    UUID from = parseUuid(str(d, "from"));
                    String text = str(d, "text");
                    long ts = d.has("ts") ? d.get("ts").getAsLong() : System.currentTimeMillis();
                    if (from != null && text != null) {
                        recordDm(from, new Dm(from, text, ts));
                    }
                }
            }
        }

        notifyListeners();
    }

    public void onMessage(JsonObject msg) {
        String t = str(msg, "t");
        if (t == null) return;

        switch (t) {
            case SocialProtocol.PRESENCE -> {
                UUID id = parseUuid(str(msg, "uuid"));
                if (id != null) {
                    FriendEntry existing = friendsMap.get(id);
                    if (existing != null) {
                        Presence p = parsePresence(msg);
                        friendsMap.put(id, new FriendEntry(id, existing.name(), existing.nick(), p, existing.since()));
                        notifyListeners();
                    }
                }
            }
            case SocialProtocol.REQUEST_IN -> {
                FriendEntry entry = parseFriendEntry(msg);
                if (entry != null) {
                    incomingMap.put(entry.id(), entry);
                    SocialBridge.get().toast("Friend Request", entry.name() + " sent you a friend request",
                            "friend_accept:" + entry.id(), "friend_decline:" + entry.id());
                    notifyListeners();
                }
            }
            case SocialProtocol.REQUEST_SENT -> {
                FriendEntry entry = parseFriendEntry(msg);
                if (entry != null) {
                    outgoingMap.put(entry.id(), entry);
                    notifyListeners();
                }
            }
            case SocialProtocol.FRIEND_ADDED -> {
                FriendEntry entry = parseFriendEntry(msg);
                if (entry != null) {
                    incomingMap.remove(entry.id());
                    outgoingMap.remove(entry.id());
                    friendsMap.put(entry.id(), entry);
                    SocialBridge.get().toast("New Friend", entry.name() + " is now your friend!", null, null);
                    notifyListeners();
                }
            }
            case SocialProtocol.FRIEND_REMOVED -> {
                UUID id = parseUuid(str(msg, "uuid"));
                if (id != null) {
                    friendsMap.remove(id);
                    notifyListeners();
                }
            }
            case SocialProtocol.DM -> {
                UUID from = parseUuid(str(msg, "from"));
                String text = str(msg, "text");
                long ts = msg.has("ts") ? msg.get("ts").getAsLong() : System.currentTimeMillis();
                if (from != null && text != null) {
                    recordDm(from, new Dm(from, text, ts));
                    FriendEntry sender = friendsMap.get(from);
                    String name = sender != null ? sender.displayName() : "Friend";
                    SocialBridge.get().toast("Message from " + name, text, null, null);
                }
            }
            case SocialProtocol.INVITE -> {
                String inviteId = str(msg, "inviteId");
                UUID from = parseUuid(str(msg, "from"));
                String server = str(msg, "server");
                JsonObject world = msg.getAsJsonObject("world");
                String worldName = world != null ? str(world, "name") : "their world";

                FriendEntry sender = from != null ? friendsMap.get(from) : null;
                String name = sender != null ? sender.displayName() : "A friend";

                if (server != null) {
                    activeInvites.put(inviteId, new Invite(inviteId, from, server, true, server));
                    SocialBridge.get().toast("Server Invite", name + " invited you to " + server,
                            "invite_accept:" + inviteId, "invite_decline:" + inviteId);
                } else {
                    activeInvites.put(inviteId, new Invite(inviteId, from, worldName, false, null));
                    SocialBridge.get().toast("Game Invite", name + " invited you to " + worldName,
                            "invite_accept:" + inviteId, "invite_decline:" + inviteId);
                }
                notifyListeners();
            }
            case SocialProtocol.JOIN_REQUEST -> {
                String reqId = str(msg, "reqId");
                UUID from = parseUuid(str(msg, "from"));
                SocialBridge.GameInfo game = parseGameInfo(msg.getAsJsonObject("game"));
                handleJoinRequest(reqId, from, game);
            }
            case SocialProtocol.JOIN_DENIED -> {
                SocialBridge.get().toast("Join Denied", "Host denied your join request", null, null);
            }
            case SocialProtocol.P2P_START -> {
                String sid = str(msg, "sid");
                UUID peer = parseUuid(str(msg, "peer"));
                String role = str(msg, "role");
                JsonObject ice = msg.getAsJsonObject("ice");
                handleP2PStart(sid, peer, role, ice);
            }
            case SocialProtocol.SIGNAL -> {
                String sid = str(msg, "sid");
                JsonObject data = msg.getAsJsonObject("data");
                if (guestSession.isActive() && Objects.equals(sid, guestSession.sid())) {
                    guestSession.onSignal(data);
                }
            }
            case SocialProtocol.P2P_END -> {
                String sid = str(msg, "sid");
                String reason = str(msg, "reason");
                if (guestSession.isActive() && Objects.equals(sid, guestSession.sid())) {
                    guestSession.close(reason);
                }
            }
            case SocialProtocol.ERROR -> {
                String code = str(msg, "code");
                LOGGER.warn("SocialState: received error from server: {}", code);
            }
        }
    }

    public void handleJoinRequest(String reqId, UUID from, SocialBridge.GameInfo game) {
        HostOptions options = hostSession.options();
        if (hostSession.isHosting() && options.scope == HostOptions.Scope.FRIENDS
                && options.approval == HostOptions.Approval.AUTO_FRIENDS
                && isFriend(from)) {
            hostSession.accept(from);
            send(SocialProtocol.JOIN_ACCEPT, "reqId", reqId);
            return;
        }

        activeJoinRequests.put(reqId, new JoinRequest(reqId, from, game));
        FriendEntry friend = friendsMap.get(from);
        String name = friend != null ? friend.displayName() : (from != null ? from.toString().substring(0, 8) : "Player");
        SocialBridge.get().toast("Join Request", name + " wants to join your world",
                "join_accept:" + reqId, "join_deny:" + reqId);
        notifyListeners();
    }

    public void handleP2PStart(String sid, UUID peer, String role, JsonObject iceJson) {
        FriendEntry friend = friendsMap.get(peer);
        String name = friend != null ? friend.displayName() : "Friend World";
        String worldId = friend != null && friend.presence() != null && friend.presence().world() != null
                ? friend.presence().world().id() : "world";
        handleP2PStart(sid, peer, name, worldId, role, iceJson);
    }

    public void handleP2PStart(String sid, UUID peer, String peerName, String worldId, String role, JsonObject iceJson) {
        P2PSession.IceConfig iceConfig = parseIceConfig(iceJson);
        Consumer<JsonObject> signalSender = data -> send(SocialProtocol.SIGNAL, "sid", sid, "data", data);

        if ("host".equalsIgnoreCase(role)) {
            hostSession.handleP2PStart(sid, peer, iceConfig, signalSender);
        } else {
            guestSession.start(sid, peer, peerName, worldId, iceConfig, signalSender);
        }
    }

    public void addFriendLocally(FriendEntry friend) {
        if (friend != null) {
            friendsMap.put(friend.id(), friend);
        }
    }

    private static P2PSession.IceConfig parseIceConfig(JsonObject ice) {
        if (ice == null) return new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false);
        List<String> stuns = new ArrayList<>();
        if (ice.has("stun") && ice.get("stun").isJsonArray()) {
            for (JsonElement e : ice.getAsJsonArray("stun")) stuns.add(e.getAsString());
        }
        List<P2PSession.TurnServer> turns = new ArrayList<>();
        if (ice.has("turn") && ice.get("turn").isJsonArray()) {
            for (JsonElement e : ice.getAsJsonArray("turn")) {
                if (e.isJsonObject()) {
                    JsonObject t = e.getAsJsonObject();
                    turns.add(new P2PSession.TurnServer(str(t, "url"), str(t, "user"), str(t, "credential")));
                }
            }
        }
        boolean relayOnly = ice.has("relayOnly") && ice.get("relayOnly").getAsBoolean();
        return new P2PSession.IceConfig(stuns, turns, relayOnly);
    }

    private static FriendEntry parseFriendEntry(JsonObject o) {
        UUID id = parseUuid(str(o, "uuid"));
        if (id == null) return null;
        String name = str(o, "name");
        String nick = str(o, "nick");
        long since = o.has("since") ? o.get("since").getAsLong() : 0;
        Presence presence = o.has("presence") && o.get("presence").isJsonObject()
                ? parsePresence(o.getAsJsonObject("presence")) : Presence.OFFLINE;
        return new FriendEntry(id, name != null ? name : id.toString().substring(0, 8), nick, presence, since);
    }

    private static Presence parsePresence(JsonObject o) {
        String state = str(o, "state");
        if (state == null) state = "menu";
        String server = str(o, "server");
        SocialBridge.GameInfo game = o.has("game") && o.get("game").isJsonObject()
                ? parseGameInfo(o.getAsJsonObject("game")) : null;
        Presence.WorldAd world = o.has("world") && o.get("world").isJsonObject()
                ? parseWorldAd(o.getAsJsonObject("world")) : null;
        return new Presence(state, game, server, world);
    }

    private static SocialBridge.GameInfo parseGameInfo(JsonObject g) {
        if (g == null) return new SocialBridge.GameInfo("", "", "plain", "", "", "", "");
        return new SocialBridge.GameInfo(
                str(g, "mc"), str(g, "loader"), str(g, "profileKind"),
                str(g, "packId"), str(g, "packVersion"), str(g, "modsHash"), str(g, "lads")
        );
    }

    private static Presence.WorldAd parseWorldAd(JsonObject w) {
        if (w == null) return null;
        String id = str(w, "id");
        String name = str(w, "name");
        String scope = str(w, "scope");
        int slots = w.has("slots") ? w.get("slots").getAsInt() : 8;
        int used = w.has("used") ? w.get("used").getAsInt() : 1;
        return new Presence.WorldAd(id, name, scope, slots, used);
    }

    private static String str(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static UUID parseUuid(String s) {
        return SocialClient.parseUuid(s);
    }
}

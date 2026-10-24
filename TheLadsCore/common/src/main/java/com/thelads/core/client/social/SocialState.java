package com.thelads.core.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.social.p2p.P2PSession;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
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
import java.util.function.Supplier;

/**
 * Client-side friends, presence, DMs, invites and P2P orchestration state.
 * Shared state used by the Friends and Host menus across all Minecraft versions.
 */
public class SocialState {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/SocialState");
    /** The Lads setting (default on); off = no connection to the social service at all. */
    public static final String MODULE = "Friends & Social";

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

    private final HostSession hostSession = new HostSession(this);
    private final GuestSession guestSession = new GuestSession(this);

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Supplier<SocialBridge> bridges;
    private final Supplier<LadsGameBridge.Session> sessions;
    private final SocialClient client;
    private volatile Consumer<JsonObject> clientSender;
    private volatile SocialBridge.GameInfo game;
    private volatile boolean showServer = true, appearOffline;
    private volatile Presence lastPresence;
    private volatile UUID dmTarget;
    private int ticks; // game thread only

    /** The game's own state: SocialBridge.get(), the logged-in session, the -Dlads.social.url / LADS_SOCIAL_URL endpoint. */
    public SocialState() {
        this(SocialBridge::get, () -> LadsGameBridge.get().session(), System.getProperty("lads.social.url", System.getenv("LADS_SOCIAL_URL")));
    }

    /** One player's state: its bridge, who it is and its endpoint (tests run two of these side by side). */
    public SocialState(Supplier<SocialBridge> bridges, Supplier<LadsGameBridge.Session> sessions, String url) {
        this.bridges = bridges;
        this.sessions = sessions;
        this.client = new SocialClient(url, this);
        this.clientSender = client::send;
    }

    public void setClientSender(Consumer<JsonObject> sender) {
        this.clientSender = sender != null ? sender : client::send;
    }

    public SocialClient client() {
        return client;
    }

    SocialBridge bridge() {
        return bridges.get();
    }

    LadsGameBridge.Session session() {
        return sessions.get();
    }

    /** Ours, read once: it does not change while the game runs (1.20.1 reads pack.json for it). */
    SocialBridge.GameInfo gameInfo() {
        SocialBridge.GameInfo g = game;
        if (g == null) game = g = bridge().gameInfo();
        return g;
    }

    void sendRaw(JsonObject message) {
        clientSender.accept(message);
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
        return client.connectionStatus();
    }

    public int onlineFriendCount() {
        int n = 0;
        for (FriendEntry f : friendsMap.values()) if (f.presence() != null && f.presence().online()) n++;
        return n;
    }

    public boolean showServer() {
        return showServer;
    }

    public boolean appearOffline() {
        return appearOffline;
    }

    public void setShowServer(boolean show) {
        showServer = show;
        send(SocialProtocol.SETTINGS, "showServer", show);
        refreshPresence();
        notifyListeners();
    }

    public void setAppearOffline(boolean offline) {
        appearOffline = offline;
        send(SocialProtocol.SETTINGS, "appearOffline", offline);
        notifyListeners();
    }

    /** The friend a "dm:<uuid>" action asked to open, once (the Friends screen opens on that conversation). */
    public FriendEntry takeDmTarget() {
        UUID id = dmTarget;
        dmTarget = null;
        return id == null ? null : friendsMap.get(id);
    }

    // --- Lifecycle (game thread) ---

    /** Every client tick, next to CosmeticsLink.tick(): connection on/off with the setting, presence from the game, session cleanup. */
    public static void tick() {
        get().sample();
    }

    private void sample() {
        SocialBridge bridge = bridge();
        // Anti-ban: the P2P key (an unknown server for HypixelSafetyManager) never outlives the tunnel: any other connection ends it.
        if (guestSession.isActive() && bridge.inWorldOrServer() && !guestSession.isProxy(LadsGameBridge.get().getServerAddress())) {
            guestSession.close("Left the friend's world");
        }
        if (++ticks % 20 != 0) return;
        if (!enabled()) {
            client.close();
            return;
        }
        client.start();
        if (hostSession.isHosting() && bridge.worldId() == null) stopHosting();
        refreshPresence();
    }

    private static boolean enabled() {
        Module module = ModuleManager.getInstance().getModule(MODULE);
        return module == null || module.isEnabled();
    }

    /** Publishes what we are doing when it changed (SocialClient repeats it every 60 s and after a reconnect). */
    public void refreshPresence() {
        Presence p = currentPresence();
        if (p.equals(lastPresence)) return;
        lastPresence = p;
        client.setPresence(p);
    }

    private Presence currentPresence() {
        SocialBridge bridge = bridge();
        SocialBridge.GameInfo g = gameInfo();
        if (hostSession.isHosting() && hostSession.options().scope != HostOptions.Scope.LAN) {
            HostOptions o = hostSession.options();
            // Not "joinable": friends can't ask to join (backend: scope invite), invites still work.
            String scope = o.scope == HostOptions.Scope.FRIENDS && o.showJoinable ? "friends" : "invite";
            return new Presence("hosting", g, null, new Presence.WorldAd(Objects.requireNonNullElse(bridge.worldId(), "world"),
                    Objects.requireNonNullElse(bridge.worldName(), "My World"), scope, o.maxPlayers, hostSession.activeGuestCount() + 1));
        }
        if (guestSession.isActive()) return new Presence("server", g, null, null);
        if (!bridge.inWorldOrServer()) return new Presence("menu", g, null, null);
        String addr = LadsGameBridge.get().getServerAddress();
        if ("Singleplayer".equalsIgnoreCase(addr)) return new Presence("singleplayer", g, null, null);
        return new Presence("server", g, showServer ? addr : null, null);
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

        LadsGameBridge.Session session = session();
        UUID self = session != null && session.uuid() != null ? session.uuid() : new UUID(0, 0);
        recordDm(to, new Dm(self, trimmed, System.currentTimeMillis()));
    }

    public List<Dm> dms(UUID friendUuid) {
        if (friendUuid == null) return Collections.emptyList();
        List<Dm> list = dmsMap.get(friendUuid);
        if (list == null) return Collections.emptyList();
        synchronized (list) {
            return Collections.unmodifiableList(new ArrayList<>(list));
        }
    }

    private void recordDm(UUID friendUuid, Dm dm) {
        List<Dm> list = dmsMap.computeIfAbsent(friendUuid, k -> new ArrayList<>());
        synchronized (list) {
            list.add(dm);
            while (list.size() > 200) {
                list.remove(0);
            }
        }
        notifyListeners();
    }

    public void invite(UUID friendUuid) {
        if (friendUuid == null) return;
        if (hostSession.isHosting()) {
            Presence.WorldAd ad = currentPresence().world();
            if (ad == null) return; // LAN scope: friends join through the LAN, not an invite
            JsonObject worldAd = new JsonObject();
            worldAd.addProperty("id", ad.id());
            worldAd.addProperty("name", ad.name());
            worldAd.addProperty("scope", ad.scope());
            worldAd.addProperty("slots", ad.slots());
            worldAd.addProperty("used", ad.used());
            hostSession.accept(friendUuid);
            send(SocialProtocol.INVITE, "to", friendUuid.toString(), "world", worldAd);
        } else if (bridge().inWorldOrServer() && showServer && !guestSession.isActive()) {
            String addr = LadsGameBridge.get().getServerAddress();
            if (addr != null && !addr.isEmpty() && !"Singleplayer".equalsIgnoreCase(addr)) {
                send(SocialProtocol.INVITE, "to", friendUuid.toString(), "server", addr);
            }
        }
    }

    public void acceptInvite(String inviteId) {
        if (inviteId == null) return;
        Invite invite = activeInvites.remove(inviteId);
        if (invite != null && invite.isServer() && invite.serverAddress() != null) {
            String address = invite.serverAddress();
            send(SocialProtocol.INVITE_ACCEPT, "inviteId", inviteId);
            SocialBridge bridge = bridge();
            if (bridge.inWorldOrServer() || guestSession.isActive()) {
                guestSession.close("Switching to server");
                bridge.leaveWorld(() -> bridge.connectToServer(address));
            } else {
                bridge.connectToServer(address);
            }
            return;
        }

        send(SocialProtocol.INVITE_ACCEPT, "inviteId", inviteId);
    }

    public void declineInvite(String inviteId) {
        if (inviteId == null) return;
        send(SocialProtocol.INVITE_DECLINE, "inviteId", inviteId);
        activeInvites.remove(inviteId);
    }

    public void requestJoin(UUID host) {
        if (host == null) return;
        send(SocialProtocol.JOIN_REQUEST, "host", host.toString());
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

    /** Host screen: opens the world (LAN, or loopback + login filter for friends) and shows it to friends. */
    public boolean startHosting(HostOptions options) {
        boolean ok = hostSession.start(options);
        refreshPresence();
        notifyListeners();
        return ok;
    }

    public void stopHosting() {
        hostSession.stop("Host closed the world");
        refreshPresence();
        notifyListeners();
    }

    public HostOptions hostOptions() {
        return hostSession.options();
    }

    /** The host's per-player menu: kick and ban also take back the guest's acceptance and end their P2P session. */
    public void guestAction(UUID id, SocialBridge.GuestAction action) {
        if (action == SocialBridge.GuestAction.BAN) bridge().guestAction(id, action);
        if (action == SocialBridge.GuestAction.KICK || action == SocialBridge.GuestAction.BAN) hostSession.revoke(id); // revoke kicks
        else bridge().guestAction(id, action);
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
        SocialBridge.GameInfo ourGame = gameInfo();

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

    public void handleAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        int colon = actionId.indexOf(':');
        String prefix = colon == -1 ? actionId : actionId.substring(0, colon);
        String arg = colon == -1 ? "" : actionId.substring(colon + 1);

        switch (prefix) {
            case "invite_accept" -> acceptInvite(arg);
            case "invite_decline" -> declineInvite(arg);
            case "join_accept" -> answerJoin(arg, true);
            case "join_deny" -> answerJoin(arg, false);
            case "friend_accept" -> {
                UUID id = parseUuid(arg);
                if (id != null) accept(id);
            }
            case "friend_decline" -> {
                UUID id = parseUuid(arg);
                if (id != null) decline(id);
            }
            case "open_friends" -> bridge().openFriendsScreen();
            case "dm" -> {
                dmTarget = parseUuid(arg);
                bridge().openFriendsScreen();
            }
            default -> LOGGER.debug("SocialState: ignored unknown action id: {}", actionId);
        }
    }

    // --- Inbound message dispatching ---

    public void onHello(JsonObject hello) {
        friendsMap.clear();
        incomingMap.clear();
        outgoingMap.clear();
        blockedMap.clear();
        blockedUuids.clear();

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

        if (hello.get("settings") instanceof JsonObject settings) readSettings(settings);

        if (hello.has("dms") && hello.get("dms").isJsonArray()) {
            for (JsonElement e : hello.getAsJsonArray("dms")) {
                if (e.isJsonObject()) {
                    JsonObject d = e.getAsJsonObject();
                    UUID from = parseUuid(str(d, "from"));
                    String text = str(d, "text");
                    long ts = d.has("ts") ? d.get("ts").getAsLong() : System.currentTimeMillis();
                    if (from != null && text != null) {
                        List<Dm> list = dmsMap.computeIfAbsent(from, k -> new ArrayList<>());
                        synchronized (list) {
                            boolean exists = false;
                            for (Dm existing : list) {
                                if (existing.ts() == ts && Objects.equals(existing.text(), text)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                list.add(new Dm(from, text, ts));
                            }
                            while (list.size() > 200) {
                                list.remove(0);
                            }
                        }
                    }
                }
            }
        }

        notifyListeners();
    }

    private void readSettings(JsonObject settings) {
        if (settings.get("showServer") instanceof JsonPrimitive p && p.isBoolean()) showServer = p.getAsBoolean();
        if (settings.get("appearOffline") instanceof JsonPrimitive p && p.isBoolean()) appearOffline = p.getAsBoolean();
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
                    bridge().toast("Friend Request", entry.name() + " sent you a friend request",
                            "friend_accept:" + entry.id(), "friend_decline:" + entry.id());
                    bridge().chatAction(entry.name() + " sent you a friend request [Accept]",
                            "friend_accept:" + entry.id());
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
                    bridge().toast("New Friend", entry.name() + " is now your friend!", null, null);
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
                    bridge().toast("Message from " + name, text, "dm:" + from, null);
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
                    bridge().toast("Server Invite", name + " invited you to " + server,
                            "invite_accept:" + inviteId, "invite_decline:" + inviteId);
                    bridge().chatAction(name + " invited you to " + server + " [Accept]",
                            "invite_accept:" + inviteId);
                } else {
                    activeInvites.put(inviteId, new Invite(inviteId, from, worldName, false, null));
                    bridge().toast("Game Invite", name + " invited you to " + worldName,
                            "invite_accept:" + inviteId, "invite_decline:" + inviteId);
                    bridge().chatAction(name + " invited you to " + worldName + " [Accept]",
                            "invite_accept:" + inviteId);
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
                bridge().toast("Join Denied", "Host denied your join request", null, null);
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
                if (sid != null && data != null) {
                    if (guestSession.ownsSid(sid)) {
                        guestSession.onSignal(data);
                    } else if (hostSession.ownsSid(sid)) {
                        hostSession.onSignal(sid, data);
                    }
                }
            }
            case SocialProtocol.P2P_END -> {
                String sid = str(msg, "sid");
                String reason = str(msg, "reason");
                if (sid != null) {
                    if (guestSession.ownsSid(sid)) {
                        guestSession.close(reason);
                    } else if (hostSession.ownsSid(sid)) {
                        hostSession.closeGuest(sid, reason);
                    }
                }
            }
            case SocialProtocol.SETTINGS -> {
                readSettings(msg);
                notifyListeners();
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
        bridge().toast("Join Request", name + " wants to join your world",
                "join_accept:" + reqId, "join_deny:" + reqId);
        bridge().chatAction(name + " wants to join your world [Accept]",
                "join_accept:" + reqId);
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
            SocialBridge bridge = bridge();
            if (bridge.inWorldOrServer() || guestSession.isActive()) {
                guestSession.close("Switching world");
                bridge.leaveWorld(() -> guestSession.start(sid, peer, peerName, worldId, iceConfig, signalSender));
            } else {
                guestSession.start(sid, peer, peerName, worldId, iceConfig, signalSender);
            }
        }
    }

    public void addFriendLocally(FriendEntry friend) {
        if (friend != null) {
            friendsMap.put(friend.id(), friend);
        }
    }

    private static P2PSession.IceConfig parseIceConfig(JsonObject ice) {
        if (ice == null) return new P2PSession.IceConfig(List.of(SocialProtocol.STUN_DEFAULT), Collections.emptyList(), false);
        List<String> stuns = new ArrayList<>();
        if (ice.has("stun") && ice.get("stun").isJsonArray()) {
            for (JsonElement e : ice.getAsJsonArray("stun")) stuns.add(e.getAsString());
        }
        if (stuns.isEmpty()) stuns.add(SocialProtocol.STUN_DEFAULT); // srflx candidates even when the backend names no STUN server
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

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
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
    private static final String BACKUP = "World Backup";
    /** Each backup's zip on its own low-priority daemon thread: the game thread never waits for it. */
    private static final Executor BACKUP_THREAD = task -> {
        Thread t = new Thread(task, "Lads-WorldBackup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    };

    public record Dm(UUID from, String text, long ts) {}
    public record JoinCheck(boolean ok, String reason) {}
    public record Invite(String inviteId, UUID from, String worldName, boolean isServer, String serverAddress) {}
    public record JoinRequest(String reqId, UUID from, SocialBridge.GameInfo game) {}
    /** "Leave X and join Y?" and what Confirm does. */
    public record SwitchConfirm(String question, Runnable go) {}

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
    private volatile SwitchConfirm pendingSwitch;
    private volatile boolean backingUp;
    private volatile long lastBadMessageLog;
    /** host -> until when its p2p_start is expected: we asked to join it or accepted its invite (F9; the backend's 5 min expiry). */
    private final Map<UUID, Long> joinIntents = new ConcurrentHashMap<>();
    private static final long INTENT_MS = 5 * 60_000;
    /** Backend invite / join request ids. */
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** Chat link token -> action id (F10): a click runs only a link we printed, once, within 10 minutes. */
    private final Map<String, ChatLink> chatLinks = new ConcurrentHashMap<>();
    private static final long CHAT_LINK_MS = 10 * 60_000;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private record ChatLink(String actionId, long expiresAt) {}
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

    /** An invite accepted while in a world, to confirm in the Friends screen before leaving (once). */
    public SwitchConfirm takePendingSwitch() {
        SwitchConfirm s = pendingSwitch;
        pendingSwitch = null;
        return s;
    }

    /** What to ask before leaving for this friend's world (Join), or null when we are in no world or server: nothing to leave. */
    public String joinQuestion(UUID friend) {
        return switchQuestion(nameOf(friend) + "'s world");
    }

    private String switchQuestion(String target) {
        if (!bridge().inWorldOrServer() && !guestSession.isActive()) return null;
        String here;
        String addr = LadsGameBridge.get().getServerAddress();
        if (guestSession.isActive()) here = guestSession.hostName() + "'s world";
        else if (!"Singleplayer".equalsIgnoreCase(addr)) here = addr;
        else here = Objects.requireNonNullElse(bridge().worldName(), "this world");
        return "Leave " + here + " and join " + target + "?";
    }

    private String nameOf(UUID id) {
        FriendEntry f = id == null ? null : friendsMap.get(id);
        return f != null ? f.displayName() : "your friend";
    }

    /** The friend whose world we are connecting to (the P2P link is not up yet), else null: the Friends screen shows progress. */
    public String connectingTo() {
        return guestSession.connectingTo();
    }

    /** Cancel on that progress: the P2P session ends, nothing connects. */
    public void cancelConnecting() {
        guestSession.close("Cancelled");
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
                    Objects.requireNonNullElse(bridge.worldName(), "My World"), scope, o.maxPlayers, hostSession.activeGuestCount())); // guests/max guests
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
        if (dmsMap.size() >= 500 && !dmsMap.containsKey(friendUuid)) return; // the 500-friend cap: no more conversations than that
        List<Dm> list = dmsMap.computeIfAbsent(friendUuid, k -> new ArrayList<>());
        synchronized (list) {
            list.add(dm);
            while (list.size() > 200) {
                list.remove(0);
            }
        }
        notifyListeners();
    }

    /** Banned from this world for good (lads-host.json "banned"), online or not. */
    private void banFromWorld(UUID id) {
        HostOptions options = hostSession.options();
        if (id != null && options.banned.add(id.toString())) saveWorldOptions(options);
    }

    /** This world's bans (UUID strings): the live options while hosting, else its lads-host.json (none without a world). */
    public List<String> worldBans() {
        HostOptions options = worldOptions();
        return options == null ? Collections.emptyList() : new ArrayList<>(options.banned);
    }

    /** Host screen's Unban: the player may ask to join this world again. */
    public void unban(String uuid) {
        HostOptions options = worldOptions();
        if (options != null && options.banned.remove(uuid)) saveWorldOptions(options);
    }

    private HostOptions worldOptions() {
        if (hostSession.isHosting()) return hostSession.options();
        Path world = bridge().worldDir();
        return world == null ? null : HostOptions.load(world);
    }

    private void saveWorldOptions(HostOptions options) {
        Path world = bridge().worldDir();
        if (world == null) return;
        try {
            options.save(world);
        } catch (IOException e) {
            LOGGER.warn("SocialState: could not save lads-host.json: {}", e.getMessage());
        }
    }

    public void invite(UUID friendUuid) {
        if (friendUuid == null || hostSession.isHosting() && hostSession.options().isBanned(friendUuid)) return;
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

        if (invite != null && invite.from() != null) joinIntents.put(invite.from(), System.currentTimeMillis() + INTENT_MS);
        send(SocialProtocol.INVITE_ACCEPT, "inviteId", inviteId);
    }

    public void declineInvite(String inviteId) {
        if (inviteId == null) return;
        send(SocialProtocol.INVITE_DECLINE, "inviteId", inviteId);
        activeInvites.remove(inviteId);
    }

    public void requestJoin(UUID host) {
        if (host == null) return;
        joinIntents.put(host, System.currentTimeMillis() + INTENT_MS);
        send(SocialProtocol.JOIN_REQUEST, "host", host.toString());
    }

    /** Join requests waiting for an answer (QA: P2PQa answers the toast's request). */
    public List<JoinRequest> joinRequests() {
        return new ArrayList<>(activeJoinRequests.values());
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

    /**
     * Host screen: opens the world (LAN, or loopback + login filter for friends) and shows it to friends. With "Backup the world before
     * opening", the server saves on its thread, the zip runs on a worker with a progress toast, then the world opens on the game thread:
     * the game never waits. A failed backup warns and still opens.
     */
    public void startHosting(HostOptions options) {
        SocialBridge bridge = bridge();
        Path world = bridge.worldDir();
        if (options == null || !options.backupOnOpen || options.scope == HostOptions.Scope.OFF || world == null) {
            openHosting(options);
            return;
        }
        if (backingUp) return; // a second click while the first backup runs
        backingUp = true;
        bridge.toast(BACKUP, "Saving the world...", null, null);
        bridge.saveWorld().thenRunAsync(() -> {
            try {
                WorldBackup.zip(world, pct -> bridge.toast(BACKUP, "Backing up the world: " + pct + "%", null, null));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, BACKUP_THREAD).whenComplete((done, err) -> {
            backingUp = false;
            if (err != null) LOGGER.warn("SocialState: world backup failed, opening anyway", err);
            bridge.toast(BACKUP, err == null ? "World backed up" : "Backup failed, opening the world anyway", null, null);
            bridge.runOnGameThread(() -> openHosting(options));
        });
    }

    private void openHosting(HostOptions options) {
        hostSession.start(options);
        refreshPresence();
        notifyListeners();
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
        if (action == SocialBridge.GuestAction.BAN) banFromWorld(id); // per world; revoke below kicks (not the game-wide ban list)
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

    /** A chat line with a link: the click value is a one-time random token, never the action itself (any server can print links). */
    private void chatAction(String text, String actionId) {
        long now = System.currentTimeMillis();
        chatLinks.values().removeIf(link -> link.expiresAt() < now);
        if (chatLinks.size() >= 64) return; // a flood of requests: the toast and the Friends screen still offer them
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        StringBuilder token = new StringBuilder(32);
        for (byte b : raw) token.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        chatLinks.put(token.toString(), new ChatLink(actionId, now + CHAT_LINK_MS));
        bridge().chatAction(text, token.toString());
    }

    /** A clicked "/lads:social <token>" (never sent to the server): runs the action it was printed for, once; anything else does nothing. */
    public void handleChatClick(String token) {
        ChatLink link = token == null ? null : chatLinks.remove(token);
        if (link != null && link.expiresAt() >= System.currentTimeMillis()) handleAction(link.actionId());
    }

    /**
     * Toast buttons, keys and chat links (frozen ids). Each acts only on something this client holds: a pending friend request, a join
     * request or invite it received; anything else is ignored (F10).
     */
    public void handleAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        int colon = actionId.indexOf(':');
        String prefix = colon == -1 ? actionId : actionId.substring(0, colon);
        String arg = colon == -1 ? "" : actionId.substring(colon + 1);

        switch (prefix) {
            case "invite_accept" -> {
                Invite invite = activeInvites.get(arg);
                if (invite == null) return;
                String question = switchQuestion(invite.isServer() ? invite.serverAddress() : nameOf(invite.from()) + "'s world");
                if (question == null) {
                    acceptInvite(arg);
                } else { // in a world or on a server: the Friends screen asks first; Cancel keeps everything
                    pendingSwitch = new SwitchConfirm(question, () -> acceptInvite(arg));
                    bridge().openFriendsScreen();
                }
            }
            case "invite_decline" -> {
                if (activeInvites.containsKey(arg)) declineInvite(arg);
            }
            case "join_accept" -> {
                if (activeJoinRequests.containsKey(arg)) answerJoin(arg, true);
            }
            case "join_deny" -> {
                if (activeJoinRequests.containsKey(arg)) answerJoin(arg, false);
            }
            case "friend_accept" -> {
                UUID id = parseUuid(arg);
                if (id != null && incomingMap.containsKey(id)) accept(id);
            }
            case "friend_decline" -> {
                UUID id = parseUuid(arg);
                if (id != null && incomingMap.containsKey(id)) decline(id);
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

    /** A backend message (or a friend's signal it relays): a malformed one is logged (at most every 10 s) and ignored, the link stays up (F8). */
    public void onMessage(JsonObject msg) {
        try {
            dispatch(msg);
        } catch (RuntimeException bad) {
            long now = System.currentTimeMillis();
            if (now - lastBadMessageLog < 10_000) return;
            lastBadMessageLog = now;
            LOGGER.warn("SocialState: ignored a malformed {} message ({})", str(msg, "t"), bad.toString());
        }
    }

    private void dispatch(JsonObject msg) {
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
                    chatAction(entry.name() + " sent you a friend request [Accept]",
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
                if (inviteId == null || from == null || !ID.matcher(inviteId).matches()) return;
                String server = str(msg, "server");
                JsonObject world = msg.getAsJsonObject("world");
                String worldName = world != null ? str(world, "name") : "their world";

                FriendEntry sender = from != null ? friendsMap.get(from) : null;
                String name = sender != null ? sender.displayName() : "A friend";

                if (server != null) {
                    activeInvites.put(inviteId, new Invite(inviteId, from, server, true, server));
                    bridge().toast("Server Invite", name + " invited you to " + server,
                            "invite_accept:" + inviteId, "invite_decline:" + inviteId);
                    chatAction(name + " invited you to " + server + " [Accept]",
                            "invite_accept:" + inviteId);
                } else {
                    activeInvites.put(inviteId, new Invite(inviteId, from, worldName, false, null));
                    bridge().toast("Game Invite", name + " invited you to " + worldName,
                            "invite_accept:" + inviteId, "invite_decline:" + inviteId);
                    chatAction(name + " invited you to " + worldName + " [Accept]",
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
                    } else if (P2PQa.ACTIVE) { // QA only: a signal no session owns (yet) is dropped here
                        LOGGER.warn("Lads p2p QA: signal for sid {} dropped: no guest or host session owns it", sid);
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
        if (reqId == null || from == null || !ID.matcher(reqId).matches()) return;
        HostOptions options = hostSession.options();
        if (options.isBanned(from)) { // banned from this world: denied without asking
            send(SocialProtocol.JOIN_DENY, "reqId", reqId);
            return;
        }
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
        chatAction(name + " wants to join your world [Accept]",
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
        if (sid == null || peer == null) return;
        P2PSession.IceConfig iceConfig = parseIceConfig(iceJson);
        Consumer<JsonObject> signalSender = data -> send(SocialProtocol.SIGNAL, "sid", sid, "data", data);

        if ("host".equalsIgnoreCase(role)) {
            hostSession.handleP2PStart(sid, peer, iceConfig, signalSender); // only for a guest we accepted (HostSession)
        } else {
            // F9: only for a host we asked to join or whose invite we accepted; never "leave your world now" on the backend's word alone
            Long until = joinIntents.remove(peer);
            if (until == null || until < System.currentTimeMillis()) {
                LOGGER.warn("SocialState: ignored p2p_start from {}: no join request or accepted invite", peer);
                return;
            }
            SocialBridge bridge = bridge();
            // The Friends screen shows "Connecting to <friend>'s world..." with Cancel until the link is up (then the game connects)
            if (bridge.inWorldOrServer() || guestSession.isActive()) {
                guestSession.expect(sid); // the host's ICE signal can arrive while the game is leaving: it waits for start
                bridge.leaveWorld(() -> {
                    if (!guestSession.isExpected(sid)) return; // cancelled or replaced meanwhile
                    guestSession.start(sid, peer, peerName, worldId, iceConfig, signalSender);
                    bridge.openFriendsScreen();
                });
            } else {
                guestSession.start(sid, peer, peerName, worldId, iceConfig, signalSender);
                bridge.runOnGameThread(bridge::openFriendsScreen);
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

package com.thelads.core.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.social.modsync.ModSyncFlow;
import com.thelads.core.client.social.modsync.ModSyncServer;
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

    /** img: a screenshot's backend id, or our sent copy ("file:...", DmMedia.image); gif: a Tenor/Giphy GIF link; null when none. */
    public record Dm(UUID from, String text, long ts, String img, String gif) {
        public Dm(UUID from, String text, long ts) { this(from, text, ts, null, null); }
    }
    public record JoinCheck(boolean ok, String reason) {}
    public record Invite(String inviteId, UUID from, String worldName, boolean isServer, String serverAddress) {}
    public record JoinRequest(String reqId, UUID from, SocialBridge.GameInfo game) {}
    /** "Leave X and join Y?" and what Confirm does. */
    public record SwitchConfirm(String question, Runnable go) {}
    /** ModSync's "trust this host's mods?": the Friends screen asks it and completes answer (false = cancel the join). */
    public record TrustQuestion(String title, String message, java.util.concurrent.CompletableFuture<Boolean> answer) {}

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
    /** Party (the backend keeps it in memory and ends it when we disconnect). */
    public record PartyMember(UUID id, String name) {}
    public record Party(UUID leader, List<PartyMember> members) {}
    public record PartyInvite(UUID from, String name, long at) {}
    /** The leader is going to a server: the UI offers Join / Ignore; nothing connects until the player chooses Join. */
    public record PartyWarp(String token, UUID from, String leaderName, String server, String serverName, long at) {}
    static final long PARTY_INVITE_MS = 120_000, PARTY_WARP_MS = 60_000;
    private volatile Party party;
    private final Map<UUID, PartyInvite> partyInvites = new ConcurrentHashMap<>();
    private volatile PartyWarp pendingWarp;
    private volatile Consumer<PartyWarp> partyWarpListener;
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
    /** host -> when and where we asked to join it or accepted its invite: its p2p_start is expected for 5 min (F9; the backend's expiry). */
    private final Map<UUID, Intent> joinIntents = new ConcurrentHashMap<>();
    private record Intent(long at, String here) {}
    private static final long INTENT_MS = 5 * 60_000;
    /** An accept older than this, or from elsewhere, asks again before leaving (the player may be in a match by now). Tests change it. */
    static long reconfirmMs = 20_000;
    /** A late p2p_start waiting for the player's "Join" (toast or chat link). */
    private record PendingJoin(String sid, Runnable go) {}
    /** ModSync: this profile's own jars as the launcher listed them, read once (the hash in our presence, what a guest may fetch). */
    private volatile List<ModSyncServer.Shared> modSyncShared;
    /** ModSync, host: guests that left to restart with our mods and whether they went offline since; back online they get a new invite. */
    private final Map<UUID, Relaunching> relaunching = new ConcurrentHashMap<>();
    private record Relaunching(long at, boolean wentOffline) {}
    static final long RELAUNCH_MS = 10 * 60_000;
    /** ModSync, guest: the launcher started this game with that host's mods: its next invite within RELAUNCH_MS is accepted, once. */
    private volatile UUID rejoinHost = parseUuid(System.getProperty(ModSyncFlow.HOST_PROPERTY));
    private final long startedAt = System.currentTimeMillis();
    private boolean leavingServer; // game thread only
    private TrustQuestion trustShown; // game thread only
    private volatile PendingJoin pendingJoin;
    /** The last Add Friend: its name and the backend's answer for the Friends screen (null while waiting). */
    private volatile String addingName, addResult;
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

    /** The game's session is now another account (AccountSwitcherScreen): nothing of the old one's stays, and the link logs in again. */
    public void onAccountChanged() {
        friendsMap.clear();
        incomingMap.clear();
        outgoingMap.clear();
        blockedMap.clear();
        blockedUuids.clear();
        dmsMap.clear();
        activeInvites.clear();
        activeJoinRequests.clear();
        resetParty();
        client.reconnect();
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
        if (g == null) {
            g = bridge().gameInfo();
            // ModSync: a plain Fabric profile says which jars it runs; packs and 1.8.9 publish nothing and sync nothing
            if ("plain".equals(g.profileKind()) && "fabric".equals(g.loader()) && (g.modsHash() == null || g.modsHash().isEmpty())) {
                String hash = ModSyncFlow.hash(modSyncShared());
                if (!hash.isEmpty()) g = new SocialBridge.GameInfo(g.mc(), g.loader(), g.profileKind(), g.packId(), g.packVersion(), hash, g.lads());
            }
            game = g;
        }
        return g;
    }

    List<ModSyncServer.Shared> modSyncShared() {
        List<ModSyncServer.Shared> shared = modSyncShared;
        if (shared == null) modSyncShared = shared = List.copyOf(ModSyncFlow.shared(bridge().gameDir()));
        return shared;
    }

    /**
     * The host runs jars this game does not: before the game connects, its list is asked for on stream 2. Never from a host that
     * published no modsHash (nothing to sync, or an old client whose Mux ends the session on a stream-2 frame).
     */
    boolean needsModSync(UUID host) {
        FriendEntry f = host == null ? null : friendsMap.get(host);
        SocialBridge.GameInfo theirs = f == null || f.presence() == null ? null : f.presence().game(), ours = gameInfo();
        String hash = theirs == null || theirs.modsHash() == null ? "" : theirs.modsHash();
        return MODS_HASH.matcher(hash).matches() && !hash.equals(ours.modsHash()) && ModSyncFlow.launcherReady(bridge().gameDir())
                && "plain".equals(ours.profileKind()) && "fabric".equals(ours.loader());
    }

    private static final java.util.regex.Pattern MODS_HASH = java.util.regex.Pattern.compile("[0-9a-f]{32}");

    /** Mod id to version of everything this game loaded (ModSync: such a mod is not fetched again). */
    Map<String, String> loadedMods() {
        Map<String, String> loaded = new java.util.HashMap<>();
        LadsGameBridge game = LadsGameBridge.get();
        if (game != null) for (com.thelads.core.mods.LoadedMod m : game.loadedMods()) loaded.put(m.id(), m.version());
        return loaded;
    }

    /** HostSession: this guest fetched our mods and restarts its game; it is let back in without a new question. */
    void guestRelaunches(UUID guest) {
        relaunching.put(guest, new Relaunching(System.currentTimeMillis(), false));
    }

    /** The restarted guest is back (offline, then online again) while we still host: the invite it accepted before is sent again. */
    private void relaunchPresence(UUID id, Presence p) {
        Relaunching r = relaunching.get(id);
        if (r == null) return;
        if (System.currentTimeMillis() - r.at() > RELAUNCH_MS || !hostSession.isHosting()) {
            relaunching.remove(id);
        } else if (!p.online()) {
            relaunching.put(id, new Relaunching(r.at(), true));
        } else if (r.wentOffline()) {
            relaunching.remove(id);
            invite(id);
        }
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
        return switchQuestion(onNetwork(friend) ? com.thelads.core.client.LadsNetwork.NAME : nameOf(friend) + "'s world");
    }

    private String switchQuestion(String target) {
        String here = here();
        return here == null ? null : "Leave " + here + " and join " + target + "?";
    }

    /** What joining would leave (a friend's world, a server address, this world's name), or null in the menus. */
    private String here() {
        if (!bridge().inWorldOrServer() && !guestSession.isActive()) return null;
        String addr = LadsGameBridge.get().getServerAddress();
        if (guestSession.isActive()) return guestSession.hostName() + "'s world";
        if (!"Singleplayer".equalsIgnoreCase(addr)) return addr;
        return Objects.requireNonNullElse(bridge().worldName(), "this world");
    }

    private String nameOf(UUID id) {
        FriendEntry f = id == null ? null : friendsMap.get(id);
        return f != null ? f.displayName() : "your friend";
    }

    /** The friend whose world we are connecting to (the P2P link is not up yet), else null: the Friends screen shows progress. */
    public String connectingTo() {
        return guestSession.connectingTo();
    }

    /** What that connection is doing once the link is up (ModSync: "Downloading mod 2 of 5: ..."), else null. */
    public String connectingStatus() {
        return guestSession.connectingStatus();
    }

    /** ModSync's "trust this host's mods?" while it waits for the player, else null. */
    public TrustQuestion trustQuestion() {
        return guestSession.trustQuestion();
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

    /**
     * Anti-ban, before the game connects: a game the launcher started with a host's mods (ModSync) joins only that host's world
     * (our proxy on this PC), the local network and our own servers. It says why when it refuses. sample() still leaves any other
     * server (Realms do not connect through here).
     */
    public boolean refusesJoin(String address) {
        if (System.getProperty(ModSyncFlow.HOST_PROPERTY) == null || HypixelSafetyManager.isOwnOrLocal(address)) return false;
        bridge().toast("Restart to play here", "This game runs your friend's mods. Start it again from the launcher to join servers.", null, null);
        return true;
    }

    private void sample() {
        SocialBridge bridge = bridge();
        // Anti-ban: the P2P key (an unknown server for HypixelSafetyManager) never outlives the tunnel: any other connection ends it.
        if (guestSession.isActive() && bridge.inWorldOrServer() && !guestSession.isProxy(LadsGameBridge.get().getServerAddress())) {
            guestSession.close("Left the friend's world");
        }
        // Anti-ban: a game the launcher started with a host's mods (ModSync) plays that host's world, singleplayer, LAN and our own
        // servers only. On any other server those mods could get the player banned: it leaves at once and says why.
        String address = System.getProperty(ModSyncFlow.HOST_PROPERTY) == null || !bridge.inWorldOrServer() ? null : LadsGameBridge.get().getServerAddress();
        if (address == null || guestSession.isActive() || address.startsWith("127.0.0.1:") || !HypixelSafetyManager.isMultiplayer()
                || HypixelSafetyManager.isTrustedServer()) {
            leavingServer = false;
        } else if (!leavingServer) {
            leavingServer = true;
            bridge.leaveWorld(() -> bridge.toast("Restart to play here", "This game runs your friend's mods. Start it again from the launcher to join servers.", null, null));
        }
        // ModSync: the trust question is asked on the Friends screen, and an invite accepted from a toast or the chat has not opened it
        TrustQuestion question = guestSession.trustQuestion();
        if (question != trustShown) {
            trustShown = question;
            if (question != null) bridge.openFriendsScreen();
        }
        if (++ticks % 20 != 0) return;
        if (!enabled()) {
            client.close();
            return;
        }
        client.start();
        ModSyncQa.second(this, bridge, activeInvites.values());
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
            addingName = name.trim();
            addResult = null;
            send(SocialProtocol.FRIEND_ADD, "name", name.trim());
        }
    }

    /** The backend's answer to the last Add Friend ("Request sent to X!" or why not), or null while it is on its way. */
    public String addFriendResult() {
        return addResult;
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
        String link = DmMedia.gifLink(trimmed);
        if (link != null) { // a Tenor/Giphy link goes as a GIF (never an upload); old clients see the link
            DmMedia.background(() -> {
                String gif = DmMedia.resolveGif(link); // a Tenor page is fetched for its GIF
                send(SocialProtocol.DM, "to", to.toString(), "text", link, "gif", gif);
                recordDm(to, new Dm(selfId(), link, System.currentTimeMillis(), null, gif));
            });
            return;
        }
        send(SocialProtocol.DM, "to", to.toString(), "text", trimmed);
        recordDm(to, new Dm(selfId(), trimmed, System.currentTimeMillis()));
    }

    /**
     * Sends a screenshot, only one straight from the game's screenshots folder: made a JPEG under 1 MB off the render thread,
     * then sent as "dm_img" parts the backend joins, keeps and passes on by id.
     */
    public void sendScreenshot(UUID to, Path shot) {
        if (to == null || !DmMedia.isScreenshot(gameDir(), shot)) return;
        DmMedia.background(() -> {
            try {
                byte[] jpeg = DmMedia.encodeScreenshot(shot);
                List<String> parts = DmMedia.parts(jpeg);
                for (int i = 0; i < parts.size(); i++) {
                    send(SocialProtocol.DM_IMG, "to", to.toString(), "text", DmMedia.SCREENSHOT_TEXT, "part", i, "parts", parts.size(), "data", parts.get(i));
                }
                recordDm(to, new Dm(selfId(), DmMedia.SCREENSHOT_TEXT, System.currentTimeMillis(), DmMedia.keepSent(jpeg), null));
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("SocialState: screenshot {} not sent: {}", shot.getFileName(), e.toString());
                bridge().toast("Friends", "Couldn't send that screenshot", null, null);
            }
        });
    }

    /** The game folder whose screenshots folder DMs may send from. */
    public Path gameDir() {
        return bridge().gameDir();
    }

    private UUID selfId() {
        LadsGameBridge.Session session = session();
        return session != null && session.uuid() != null ? session.uuid() : new UUID(0, 0);
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

    /** An invite to the same friend goes out at most once per 10 s. */
    public static final long INVITE_COOLDOWN_MS = 10_000L;
    private final Map<UUID, Long> lastInviteAt = new java.util.concurrent.ConcurrentHashMap<>();

    /** True (and starts the cooldown) when an invite to this friend may go out now. */
    boolean inviteAllowed(UUID friendUuid, long nowMs) {
        Long last = lastInviteAt.get(friendUuid);
        if (last != null && nowMs - last < INVITE_COOLDOWN_MS) return false;
        lastInviteAt.put(friendUuid, nowMs);
        return true;
    }

    public void invite(UUID friendUuid) {
        if (friendUuid == null || hostSession.isHosting() && hostSession.options().isBanned(friendUuid)) return;
        if (!inviteAllowed(friendUuid, System.currentTimeMillis())) return;
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

    /** Invites received and not yet answered (Friends screen, Invites tab). */
    public List<Invite> invites() {
        return new ArrayList<>(activeInvites.values());
    }

    public void acceptInvite(String inviteId) {
        if (inviteId == null) return;
        Invite invite = activeInvites.remove(inviteId);
        if (invite != null && invite.isServer() && invite.serverAddress() != null) {
            String address = invite.serverAddress();
            send(SocialProtocol.INVITE_ACCEPT, "inviteId", inviteId);
            switchToServer(address);
            return;
        }

        if (invite != null && invite.from() != null) {
            joinIntents.put(invite.from(), new Intent(System.currentTimeMillis(), here()));
            // Until the host's p2p_start (or an error) arrives the player sees the join is on its way
            bridge().toast(JOINING, "Waiting for " + nameOf(invite.from()) + " to let you in...", null, null);
        }
        send(SocialProtocol.INVITE_ACCEPT, "inviteId", inviteId);
    }

    /** The button-less toast an accepted invite shows until the join goes on or fails. */
    static final String JOINING = "Joining";

    /** A soft, short chime for an invite (UI sound, follows the master volume). */
    private void inviteSound() {
        bridge().runOnGameThread(() -> {
            try { LadsGameBridge.get().playComboSound(1.4f, 0.25f); } catch (Throwable ignored) {}
        });
    }

    public void declineInvite(String inviteId) {
        if (inviteId == null) return;
        send(SocialProtocol.INVITE_DECLINE, "inviteId", inviteId);
        activeInvites.remove(inviteId);
    }

    private void switchToServer(String address) {
        SocialBridge bridge = bridge();
        if (bridge.inWorldOrServer() || guestSession.isActive()) {
            guestSession.close("Switching to server");
            bridge.leaveWorld(() -> bridge.connectToServer(address));
        } else {
            bridge.connectToServer(address);
        }
    }

    /** This friend shares that they are on The Lads Network: Join goes there, with no host to ask. */
    private boolean onNetwork(UUID friend) {
        FriendEntry f = friend == null ? null : friendsMap.get(friend);
        return f != null && f.presence() != null && com.thelads.core.client.LadsNetwork.isAddress(f.presence().server());
    }

    public void requestJoin(UUID host) {
        if (host == null) return;
        if (onNetwork(host)) { // never the friend's own text: the network's address
            switchToServer(com.thelads.core.client.LadsNetwork.ADDRESS);
            return;
        }
        joinIntents.put(host, new Intent(System.currentTimeMillis(), here()));
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
        if (!hostSession.start(options) && options != null && options.scope != HostOptions.Scope.OFF) // it used to fail without a word
            bridge().toast("Hosting", bridge().canHost() ? "Could not open the world, try again" : "Open a singleplayer world that is not already open to LAN", null, null);
        refreshPresence();
        notifyListeners();
    }

    public void stopHosting() {
        hostSession.stop("Host closed the world");
        relaunching.clear();
        refreshPresence();
        notifyListeners();
    }

    public HostOptions hostOptions() {
        return hostSession.options();
    }

    /** Host screen closed: the world takes its settings at once, as vanilla's World Options did, hosting or not. */
    public void applyHostSettings(HostOptions options) {
        if (options == null) return;
        if (hostSession.isHosting()) {
            hostSession.update(options);
            refreshPresence();
        }
        bridge().applyHostOptions(options);
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
        if (com.thelads.core.client.LadsNetwork.isAddress(p.server())) {
            return com.thelads.core.client.LadsNetwork.connected() ? new JoinCheck(false, "You are on the network too") : new JoinCheck(true, null);
        }
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
            case "p2p_join", "p2p_skip" -> {
                PendingJoin p = pendingJoin;
                if (p == null || !p.sid().equals(arg)) return;
                pendingJoin = null;
                if (prefix.equals("p2p_join")) {
                    p.go().run();
                } else {
                    if (guestSession.isExpected(arg)) guestSession.close("Declined");
                    send(SocialProtocol.P2P_END, "sid", arg, "reason", "Declined"); // the host stops waiting
                }
            }
            case "party_accept", "party_decline" -> {
                UUID id = parseUuid(arg);
                if (id == null || !partyInvites.containsKey(id)) return;
                if (prefix.equals("party_accept")) acceptPartyInvite(id);
                else declinePartyInvite(id);
            }
            case "party_warp_join" -> joinPartyWarp(arg);
            case "party_warp_ignore" -> ignorePartyWarp(arg);
            case "open_friends" -> bridge().openFriendsScreen();
            case "dm" -> {
                dmTarget = parseUuid(arg);
                bridge().openFriendsScreen();
            }
            default -> LOGGER.debug("SocialState: ignored unknown action id: {}", actionId);
        }
    }

    /** The request behind a toast/chat action is still open here (answered anywhere or expired: its toast goes away). */
    public boolean actionPending(String actionId) {
        int colon = actionId == null ? -1 : actionId.indexOf(':');
        if (colon == -1) return true;
        String arg = actionId.substring(colon + 1);
        return switch (actionId.substring(0, colon)) {
            case "invite_accept", "invite_decline" -> activeInvites.containsKey(arg);
            case "join_accept", "join_deny" -> activeJoinRequests.containsKey(arg);
            case "p2p_join", "p2p_skip" -> {
                PendingJoin p = pendingJoin;
                yield p != null && p.sid().equals(arg);
            }
            case "friend_accept", "friend_decline" -> {
                UUID id = parseUuid(arg);
                yield id != null && incomingMap.containsKey(id);
            }
            case "party_accept", "party_decline" -> {
                UUID id = parseUuid(arg);
                yield id != null && partyInvite(id) != null;
            }
            case "party_warp_join", "party_warp_ignore" -> {
                PartyWarp w = pendingPartyWarp();
                yield w != null && w.token().equals(arg);
            }
            default -> true;
        };
    }

    /** The player behind a request action id (their uuid string, for the toast's skin head), or null. */
    public String actionSender(String actionId) {
        int colon = actionId == null ? -1 : actionId.indexOf(':');
        if (colon == -1) return null;
        String arg = actionId.substring(colon + 1);
        UUID id = switch (actionId.substring(0, colon)) {
            case "invite_accept", "invite_decline" -> { Invite i = activeInvites.get(arg); yield i != null ? i.from() : null; }
            case "join_accept", "join_deny" -> { JoinRequest r = activeJoinRequests.get(arg); yield r != null ? r.from() : null; }
            case "friend_accept", "friend_decline", "party_accept", "party_decline", "dm" -> parseUuid(arg);
            default -> null;
        };
        return id != null ? id.toString() : null;
    }

    // --- Party ---

    /** Our party, or null when we are in none. */
    public Party party() {
        return party;
    }

    public boolean isPartyLeader() {
        Party p = party;
        return p != null && p.leader().equals(selfId());
    }

    /** Party invites received and not yet answered or expired (the backend drops them after 2 minutes). */
    public List<PartyInvite> partyInvites() {
        long now = System.currentTimeMillis();
        partyInvites.values().removeIf(i -> now - i.at() > PARTY_INVITE_MS);
        return new ArrayList<>(partyInvites.values());
    }

    private PartyInvite partyInvite(UUID from) {
        PartyInvite i = partyInvites.get(from);
        return i != null && System.currentTimeMillis() - i.at() <= PARTY_INVITE_MS ? i : null;
    }

    /** The leader's server the UI offers ("Leader is going to X": Join / Ignore), or null. */
    public PartyWarp pendingPartyWarp() {
        PartyWarp w = pendingWarp;
        if (w != null && System.currentTimeMillis() - w.at() > PARTY_WARP_MS) pendingWarp = w = null;
        return w;
    }

    /** UI hook: called (on the socket thread) when a warp request arrives; the argument is also pendingPartyWarp(). Null removes it. */
    public void setPartyWarpListener(Consumer<PartyWarp> listener) {
        partyWarpListener = listener;
    }

    /** Only a friend, and only when we are in no party or lead it; the backend checks again. */
    public void inviteToParty(UUID friend) {
        if (friend == null || !isFriend(friend) || party != null && !isPartyLeader()) return;
        if (party != null && party.members().size() >= SocialProtocol.PARTY_MAX_MEMBERS) return;
        send(SocialProtocol.PARTY_INVITE, "to", friend.toString());
    }

    public void acceptPartyInvite(UUID from) {
        if (from == null || partyInvite(from) == null) return;
        partyInvites.remove(from);
        send(SocialProtocol.PARTY_ACCEPT, "from", from.toString());
        notifyListeners();
    }

    public void declinePartyInvite(UUID from) {
        if (from == null || partyInvites.remove(from) == null) return;
        send(SocialProtocol.PARTY_DECLINE, "from", from.toString());
        notifyListeners();
    }

    public void leaveParty() {
        if (party == null) return;
        send(SocialProtocol.PARTY_LEAVE);
    }

    public void kickFromParty(UUID member) {
        if (member != null && isPartyLeader() && !member.equals(selfId())) send(SocialProtocol.PARTY_KICK, "to", member.toString());
    }

    public void promoteInParty(UUID member) {
        if (member != null && isPartyLeader() && !member.equals(selfId())) send(SocialProtocol.PARTY_PROMOTE, "to", member.toString());
    }

    /** Leader: asks every other member to join this server. They only get a Join / Ignore question; no one connects by itself. */
    public void warp(String server) {
        warp(server, null);
    }

    public void warp(String server, String displayName) {
        if (!isPartyLeader() || server == null || server.length() > SocialProtocol.PARTY_SERVER_MAX
                || !SocialProtocol.PARTY_SERVER.matcher(server).matches()) return;
        send(SocialProtocol.PARTY_WARP, "server", server, "name", displayName != null && !displayName.isBlank() && displayName.length() <= 48 ? displayName : null);
    }

    /** Leader: warp to the server we are on now (not singleplayer, not a friend's world, and only when we share our server). False if not possible. */
    public boolean warpHere() {
        if (!isPartyLeader() || !showServer || !bridge().inWorldOrServer() || guestSession.isActive()) return false;
        String addr = LadsGameBridge.get().getServerAddress();
        if (addr == null || addr.isEmpty() || "Singleplayer".equalsIgnoreCase(addr)) return false;
        warp(addr);
        return true;
    }

    /** The player chose Join: the normal connect (HypixelSafetyManager and the leave-this-world question apply as for a server invite). */
    public void joinPartyWarp() {
        PartyWarp w = pendingPartyWarp();
        if (w != null) joinPartyWarp(w.token());
    }

    private void joinPartyWarp(String token) {
        PartyWarp w = pendingPartyWarp();
        if (w == null || token == null || !w.token().equals(token)) return;
        pendingWarp = null;
        Party p = party;
        String address = w.server();
        if (p != null && p.leader().equals(w.from()) && !refusesJoin(address)) {
            String question = switchQuestion(address);
            if (question == null) {
                switchToServer(address);
            } else if (!address.equalsIgnoreCase(here())) {
                pendingSwitch = new SwitchConfirm(question, () -> switchToServer(address));
                bridge().openFriendsScreen();
            }
        }
        notifyListeners();
    }

    public void ignorePartyWarp() {
        PartyWarp w = pendingPartyWarp();
        if (w != null) ignorePartyWarp(w.token());
    }

    private void ignorePartyWarp(String token) {
        PartyWarp w = pendingWarp;
        if (w == null || token == null || !w.token().equals(token)) return;
        pendingWarp = null;
        notifyListeners();
    }

    private void resetParty() {
        party = null;
        partyInvites.clear();
        pendingWarp = null;
    }

    private void onPartyState(JsonObject msg) {
        UUID leader = parseUuid(str(msg, "leader"));
        if (leader == null || !(msg.get("members") instanceof JsonArray arr)) return;
        List<PartyMember> members = new ArrayList<>();
        for (JsonElement e : arr) {
            if (members.size() >= SocialProtocol.PARTY_MAX_MEMBERS || !e.isJsonObject()) continue;
            UUID id = parseUuid(str(e.getAsJsonObject(), "id"));
            String name = str(e.getAsJsonObject(), "name");
            if (id != null) members.add(new PartyMember(id, name != null && name.length() <= 32 ? name : nameOf(id)));
        }
        if (members.isEmpty()) return;
        boolean joined = party == null;
        party = new Party(leader, List.copyOf(members));
        for (PartyMember m : members) partyInvites.remove(m.id());
        PartyWarp w = pendingWarp;
        if (w != null && !w.from().equals(leader)) pendingWarp = null;
        if (joined) { // the leader made the party by inviting: someone joined theirs; a member joined the leader's
            boolean lead = leader.equals(selfId());
            String others = members.stream().filter(m -> !m.id().equals(selfId())).map(PartyMember::name).collect(java.util.stream.Collectors.joining(", "));
            String leaderName = members.stream().filter(m -> m.id().equals(leader)).map(PartyMember::name).findFirst().orElse(null);
            bridge().toast("Party", lead ? others + " joined your party" : leaderName == null ? "You joined a party" : "You joined " + leaderName + "'s party", null, null);
        }
        notifyListeners();
    }

    private void onPartyInvite(JsonObject msg) {
        UUID from = parseUuid(str(msg, "from"));
        if (from == null || !friendsMap.containsKey(from) || blockedUuids.contains(from) || party != null) return;
        String name = nameOf(from);
        partyInvites.put(from, new PartyInvite(from, name, System.currentTimeMillis()));
        while (partyInvites.size() > 20) partyInvites.remove(partyInvites.keySet().iterator().next());
        bridge().toast("Party Invite", name + " invited you to a party", "party_accept:" + from, "party_decline:" + from);
        inviteSound();
        chatAction(name + " invited you to a party [Accept]", "party_accept:" + from);
        notifyListeners();
    }

    private void onPartyWarp(JsonObject msg) {
        UUID from = parseUuid(str(msg, "from"));
        String server = str(msg, "server");
        Party p = party;
        if (p == null || from == null || !p.leader().equals(from) || from.equals(selfId()) || server == null
                || server.length() > SocialProtocol.PARTY_SERVER_MAX || !SocialProtocol.PARTY_SERVER.matcher(server).matches()) return;
        String serverName = str(msg, "name");
        if (serverName != null && (serverName.isBlank() || serverName.length() > 48)) serverName = null;
        String leaderName = p.members().stream().filter(m -> m.id().equals(from)).map(PartyMember::name).findFirst().orElse(nameOf(from));
        byte[] raw = new byte[8];
        RANDOM.nextBytes(raw);
        StringBuilder token = new StringBuilder(16);
        for (byte b : raw) token.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        PartyWarp w = new PartyWarp(token.toString(), from, leaderName, server, serverName, System.currentTimeMillis());
        pendingWarp = w;
        String target = serverName != null ? serverName : server;
        bridge().toast("Party", leaderName + " is going to " + target, "party_warp_join:" + w.token(), "party_warp_ignore:" + w.token());
        chatAction(leaderName + " is going to " + target + " [Join]", "party_warp_join:" + w.token());
        Consumer<PartyWarp> l = partyWarpListener;
        if (l != null) {
            try { l.accept(w); } catch (RuntimeException e) { LOGGER.warn("SocialState: party warp listener failed", e); }
        }
        notifyListeners();
    }

    // --- Inbound message dispatching ---

    public void onHello(JsonObject hello) {
        resetParty(); // a new link: the backend dropped our party and invites with the old one
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
                    String img = str(d, "img"), gif = str(d, "gif");
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
                                list.add(new Dm(from, text, ts, img, gif));
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
                        relaunchPresence(id, p);
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
                    if (entry.name().equalsIgnoreCase(addingName)) addResult = "Request sent to " + entry.name() + "!";
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
                    if (entry.name().equalsIgnoreCase(addingName)) addResult = entry.name() + " is now your friend!"; // they had asked us
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
                String img = str(msg, "img"), gif = str(msg, "gif");
                long ts = msg.has("ts") ? msg.get("ts").getAsLong() : System.currentTimeMillis();
                if (from != null && text != null) {
                    recordDm(from, new Dm(from, text, ts, img, gif));
                    FriendEntry sender = friendsMap.get(from);
                    String name = sender != null ? sender.displayName() : "Friend";
                    bridge().toast("Message from " + name, img != null ? "Sent a screenshot" : gif != null ? "Sent a GIF" : text, "dm:" + from, null);
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
                    inviteSound();
                    chatAction(name + " invited you to " + server + " [Accept]",
                            "invite_accept:" + inviteId);
                } else if (from.equals(rejoinHost) && System.currentTimeMillis() - startedAt < RELAUNCH_MS) {
                    // ModSync: the player accepted this host before the restart with its mods; the host's new invite continues that join
                    rejoinHost = null;
                    activeInvites.put(inviteId, new Invite(inviteId, from, worldName, false, null));
                    acceptInvite(inviteId);
                } else {
                    activeInvites.put(inviteId, new Invite(inviteId, from, worldName, false, null));
                    bridge().toast("Game Invite", name + " invited you to " + worldName,
                            "invite_accept:" + inviteId, "invite_decline:" + inviteId);
                    inviteSound();
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
                    PendingJoin p = pendingJoin;
                    if (p != null && p.sid().equals(sid)) pendingJoin = null; // the host gave up waiting for our "Join"
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
            case SocialProtocol.PARTY_STATE -> onPartyState(msg);
            case SocialProtocol.PARTY_INVITE_IN -> onPartyInvite(msg);
            case SocialProtocol.PARTY_WARP -> onPartyWarp(msg);
            case SocialProtocol.PARTY_ENDED -> {
                boolean had = party != null;
                party = null;
                pendingWarp = null;
                if (had) bridge().toast("Party", "The party has ended", null, null);
                notifyListeners();
            }
            case SocialProtocol.ERROR -> {
                String code = str(msg, "code");
                LOGGER.warn("SocialState: received error from server: {}", code);
                String ref = str(msg, "ref");
                if (SocialProtocol.E_EXPIRED.equals(code) && ref != null) { // an invite or join request timed out on the backend
                    activeInvites.remove(ref);
                    activeJoinRequests.remove(ref);
                }
                // B4: the player sees why (ref: the name typed in Add Friend, a player's UUID, or an invite/request id)
                UUID refId = parseUuid(ref);
                if (refId != null) joinIntents.remove(refId); // a join request that failed: no p2p_start follows
                com.thelads.core.client.social.ui.SocialToasts.dismiss(JOINING); // a failed join: its "Joining" note goes
                boolean adding = ref != null && ref.equalsIgnoreCase(addingName);
                String text = SocialProtocol.E_NOT_FOUND.equals(code) && !adding ? null // an id already answered or gone
                        : errorText(code, ref, adding ? ref : refId != null ? nameOf(refId) : null);
                if (adding) addResult = text != null ? text : "Could not send the request";
                if (text != null) bridge().toast("Friends", text, null, null);
            }
        }
    }

    /** A backend error code as one line for the player (who: a name, or null), or null for codes they can't act on ("bad"). */
    static String errorText(String code, String ref, String who) {
        if (code == null) return null;
        String them = who != null && !who.isEmpty() ? who : "that player";
        return switch (code) {
            case SocialProtocol.E_NOT_FOUND -> "No Minecraft player called " + them;
            case SocialProtocol.E_SELF -> "You can't add yourself";
            case SocialProtocol.E_ALREADY -> "Already friends with " + them + ", or already asked";
            case SocialProtocol.E_BLOCKED -> "Blocked: you can't reach " + them;
            case SocialProtocol.E_LIMIT -> "invites".equals(ref) ? "Too many open invites: wait for answers"
                    : "join_requests".equals(ref) ? "That world has too many join requests waiting" : "Limit reached (500 friends)";
            case SocialProtocol.E_RATE -> "Slow down: too many requests, try again later";
            case SocialProtocol.E_NOT_FRIEND -> "You're not friends with " + them;
            case SocialProtocol.E_EXPIRED -> "That invite or join request expired";
            case SocialProtocol.E_INVITE_ONLY -> them + "'s world only takes invited friends";
            case SocialProtocol.E_FULL -> them + "'s world is full";
            case SocialProtocol.E_INCOMPATIBLE -> them + "'s world needs another Minecraft version or modpack";
            case SocialProtocol.E_OFFLINE -> "Can't reach " + them + ": offline or not hosting";
            case SocialProtocol.E_NOT_LEADER -> "Only the party leader can do that";
            case "bad" -> "server".equals(ref) ? "That server address can't be shared with the party" : "img".equals(ref) ? "Couldn't send that screenshot" : "gif".equals(ref) ? "Only Tenor or Giphy GIF links can be sent" : null;
            default -> null;
        };
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
            Intent intent = joinIntents.remove(peer);
            com.thelads.core.client.social.ui.SocialToasts.dismiss(JOINING);
            long now = System.currentTimeMillis();
            if (intent == null || now - intent.at() > INTENT_MS) {
                LOGGER.warn("SocialState: ignored p2p_start from {}: no join request or accepted invite", peer);
                return;
            }
            String here = here();
            if (here != null && (!here.equals(intent.here()) || now - intent.at() > reconfirmMs)) {
                // F12: the "leave?" answer was for another place or a while ago (a match may have started since): ask again, leave nothing yet
                pendingJoin = new PendingJoin(sid, () -> joinNow(sid, peer, peerName, worldId, iceConfig, signalSender));
                if (!guestSession.isActive()) guestSession.expect(sid); // holds the host's first signals; ends nothing (no friend's world open)
                bridge().toast(peerName + " accepted", "Leave " + here + " and join?", "p2p_join:" + sid, "p2p_skip:" + sid);
                chatAction(peerName + " accepted your join request: leave " + here + " and join? [Join]", "p2p_join:" + sid);
                return;
            }
            joinNow(sid, peer, peerName, worldId, iceConfig, signalSender);
        }
    }

    private void joinNow(String sid, UUID peer, String peerName, String worldId, P2PSession.IceConfig iceConfig,
                         Consumer<JsonObject> signalSender) {
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

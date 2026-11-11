package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.gui.MenuGraphics;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.SocialState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;

/**
 * Host screen (replaces vanilla's Open to LAN / World Options): a sidebar of sections, each a list of settings with a label, a short
 * explanation and one control (choice, switch, slider, text box). It holds everything vanilla's LAN screen had (game modes, cheats,
 * port), the guests and their actions, game rules and resource pack sharing. Done saves lads-host.json and applies it to the world.
 */
public final class HostScreenLogic {

    public record Box(int x, int y, int w, int h) {
        public boolean has(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    public enum Section {
        GENERAL("Access"),
        GUESTS("Players"),
        GAMEPLAY("World"),
        GAMERULES("Game Rules"),
        PACKS("Resource Packs"),
        ADVANCED("Advanced");

        public final String title;
        Section(String title) { this.title = title; }
    }

    /** Vanilla game rules the host can set for the world, by their pre-26 names (26.x bridges map them to the renamed rules). */
    static final String[] RULES = {
            "keepInventory", "doDaylightCycle", "doWeatherCycle", "mobGriefing", "doFireTick", "doMobSpawning",
            "naturalRegeneration", "playersSleepingPercentage", "doImmediateRespawn", "showDeathMessages",
            "announceAdvancements", "doInsomnia", "fallDamage", "drowningDamage", "fireDamage", "freezeDamage",
            "doTileDrops", "doEntityDrops", "doMobLoot", "forgiveDeadPlayers", "universalAnger", "disableElytraMovementCheck"
    };
    /** The one number among RULES (0-100); the rest are on/off. */
    static final String SLEEP_RULE = "playersSleepingPercentage";
    private static final Set<String> OFF_BY_DEFAULT = Set.of("keepInventory", "doImmediateRespawn", "universalAnger", "disableElytraMovementCheck");

    /** "doDaylightCycle" -> "Daylight Cycle": drops the "do" prefix and splits camelCase into words. */
    public static String ruleLabel(String rule) {
        String r = rule.length() > 2 && rule.startsWith("do") && Character.isUpperCase(rule.charAt(2)) ? rule.substring(2) : rule;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (i == 0) b.append(Character.toUpperCase(c));
            else if (Character.isUpperCase(c)) b.append(' ').append(c);
            else b.append(c);
        }
        return b.toString();
    }

    private static final Section[] SECTIONS = Section.values();
    /** Never OFF: Start / Stop Hosting is the switch. With OFF picked (and saved), Start Hosting sent "stop" and did nothing. */
    private static final String[] SCOPES = {"FRIENDS", "INVITE", "LAN"};
    private static final String[] SCOPE_LABELS = {"Friends", "Invited Only", "LAN"};
    private static final String[] APPROVALS = {"ASK", "AUTO_FRIENDS", "INVITE_ONLY"};
    private static final String[] APPROVAL_LABELS = {"Ask Me", "Let Friends In", "Invites Only"};
    private static final String[] MODES = {"survival", "creative", "adventure", "spectator"};
    private static final String[] MODE_LABELS = {"Survival", "Creative", "Adventure", "Spectator"};
    private static final String[] DIFFS = {"keep", "peaceful", "easy", "normal", "hard"};
    private static final String[] DIFF_LABELS = {"Keep", "Peaceful", "Easy", "Normal", "Hard"};

    // Spacing scale (rule 5: one set of values)
    private static final int PAD = 10, GAP = 8, CONTROL_H = 16;

    /** A banned player: UUID string and the name to show. */
    public record Banned(String uuid, String name) {}

    public interface Callbacks {
        HostOptions currentOptions();
        default void saveOptions(HostOptions options) {}
        /** Done: the world takes the settings now (hosting or not). */
        default void apply(HostOptions options) {}
        default boolean isHosting() { return false; }
        default void startHosting(HostOptions options) {}
        default void stopHosting() {}
        default List<SocialBridge.GuestInfo> guests() { return List.of(); }
        default void guestAction(UUID id, SocialBridge.GuestAction action) {}
        /** This world's banned players (kept in lads-host.json) and how to lift a ban. */
        default List<Banned> banned() { return List.of(); }
        default void unban(String uuid) {}
        /** The world's current game rules by RULES name. */
        default Map<String, String> liveGamerules() { return Map.of(); }
        default boolean canSharePacks() { return false; }
        /** The host's enabled packs that can be shared: id to name. */
        default Map<String, String> shareablePacks() { return Map.of(); }
        default void onClose() {}
    }

    private record Slider(Box track, int min, int max) {}

    private final Callbacks callbacks;
    private Section currentSection = Section.GENERAL;

    // Working options state
    private HostOptions.Scope scope = HostOptions.Scope.FRIENDS;
    private HostOptions.Approval approval = HostOptions.Approval.ASK;
    private int maxPlayers = 8;
    private String guestGameMode = "survival";
    private int guestOpLevel = 0;
    private boolean pvp = true;
    private String difficulty = null;
    private final Map<String, String> gamerules = new LinkedHashMap<>();
    private int viewDistanceCap = 0;
    private boolean relayOnly = false;
    private boolean backupOnOpen = true;
    private boolean showJoinable = true;
    private String welcome = "";
    private int port = 0;
    private String portText = "";
    private boolean portInvalid = false;
    private Boolean allowCommands = null;
    private String hostGameMode = null;
    private boolean forceGameMode = false;
    private boolean sharePacks = true;
    private boolean shareAllPacks = true;
    private final Set<String> sharedPacks = new LinkedHashSet<>();

    private final Map<String, String> liveRules;
    private final Map<String, String> packs;

    private boolean typingPort = false;
    private String draggingSlider = null;

    // Scrolling
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Layout of the last frame (hit-boxes)
    private int scissorY = 0;
    private int scissorH = Integer.MAX_VALUE;
    private int contentX, contentW;
    private final Map<Section, Box> sectionBoxes = new HashMap<>();
    private final Map<String, Box> clickables = new HashMap<>();
    private final Map<String, Slider> sliders = new HashMap<>();
    private final Map<UUID, Map<SocialBridge.GuestAction, Box>> guestActionBoxes = new HashMap<>();

    private Box hostToggleBtn;
    private Box doneBtn;
    private Box portBox;

    public HostScreenLogic(Callbacks callbacks) {
        this.callbacks = callbacks != null ? callbacks : new Callbacks() {
            @Override public HostOptions currentOptions() { return new HostOptions(); }
        };
        loadFrom(this.callbacks.currentOptions());
        Map<String, String> live = this.callbacks.liveGamerules();
        this.liveRules = live != null ? live : Map.of();
        Map<String, String> p = this.callbacks.canSharePacks() ? this.callbacks.shareablePacks() : null;
        this.packs = p != null ? p : Map.of();
    }

    /** Multiplayer Options for the open world (null = none: defaults, not saved); callbacks go to SocialState.get() and its bridge. */
    public static HostScreenLogic forWorld(Path worldDir, Runnable onClose) {
        SocialState s = SocialState.get();
        return new HostScreenLogic(new Callbacks() {
            @Override public HostOptions currentOptions() {
                HostOptions o = s.hostSession().isHosting() || worldDir == null ? s.hostOptions() : HostOptions.load(worldDir);
                SocialBridge.get().fillLiveOptions(o);
                return o;
            }
            @Override public void saveOptions(HostOptions options) {
                if (worldDir == null) return;
                try {
                    options.save(worldDir);
                } catch (IOException e) {
                    LoggerFactory.getLogger("TheLadsCore/HostScreen").warn("Could not save lads-host.json: {}", e.getMessage());
                }
            }
            @Override public void apply(HostOptions options) { s.applyHostSettings(options); }
            @Override public boolean isHosting() { return s.hostSession().isHosting(); }
            @Override public void startHosting(HostOptions options) {
                saveOptions(options);
                s.startHosting(options);
            }
            @Override public void stopHosting() { s.stopHosting(); }
            @Override public List<SocialBridge.GuestInfo> guests() { return SocialBridge.get().guests(); }
            private List<Banned> bannedCache; // asked every frame; worldBans() reads lads-host.json when not hosting: re-read after Ban/Unban only

            @Override public void guestAction(UUID id, SocialBridge.GuestAction action) {
                s.guestAction(id, action);
                bannedCache = null;
            }
            @Override public List<Banned> banned() {
                if (bannedCache != null) return bannedCache;
                List<com.thelads.core.client.social.FriendEntry> friends = s.friends();
                List<Banned> out = new ArrayList<>();
                for (String id : s.worldBans()) {
                    String name = id.length() > 8 ? id.substring(0, 8) : id;
                    for (com.thelads.core.client.social.FriendEntry f : friends) if (f.id().toString().equals(id)) name = f.displayName();
                    out.add(new Banned(id, name));
                }
                return bannedCache = out;
            }
            @Override public void unban(String uuid) {
                s.unban(uuid);
                bannedCache = null;
            }
            @Override public Map<String, String> liveGamerules() { return SocialBridge.get().liveGamerules(); }
            @Override public boolean canSharePacks() { return SocialBridge.get().canSharePacks(); }
            @Override public Map<String, String> shareablePacks() { return SocialBridge.get().shareablePacks(); }
            @Override public void onClose() { onClose.run(); }
        });
    }

    public void loadFrom(HostOptions o) {
        if (o == null) o = new HostOptions();
        setScope(o.scope); // a saved OFF opens to friends
        this.approval = o.approval != null ? o.approval : HostOptions.Approval.ASK;
        this.maxPlayers = Math.max(1, Math.min(16, o.maxPlayers));
        this.guestGameMode = o.guestGameMode != null ? o.guestGameMode : "survival";
        this.guestOpLevel = Math.max(0, Math.min(4, o.guestOpLevel));
        this.pvp = o.pvp;
        this.difficulty = o.difficulty;
        this.gamerules.clear();
        if (o.gamerules != null) this.gamerules.putAll(o.gamerules);
        this.viewDistanceCap = Math.max(0, Math.min(32, o.viewDistanceCap));
        this.relayOnly = o.relayOnly;
        this.backupOnOpen = o.backupOnOpen;
        this.showJoinable = o.showJoinable;
        this.welcome = o.welcome != null ? o.welcome : "";
        setPort(o.port);
        this.allowCommands = o.allowCommands;
        this.hostGameMode = o.hostGameMode;
        this.forceGameMode = o.forceGameMode;
        this.sharePacks = o.sharePacks;
        this.shareAllPacks = o.shareAllPacks;
        this.sharedPacks.clear();
        if (o.sharedPacks != null) this.sharedPacks.addAll(o.sharedPacks);
    }

    public HostOptions buildOptions() {
        HostOptions o = new HostOptions();
        o.scope = this.scope;
        o.approval = this.approval;
        o.maxPlayers = this.maxPlayers;
        o.guestGameMode = this.guestGameMode;
        o.guestOpLevel = this.guestOpLevel;
        o.pvp = this.pvp;
        o.difficulty = this.difficulty;
        o.gamerules = new LinkedHashMap<>(this.gamerules);
        o.viewDistanceCap = this.viewDistanceCap;
        o.relayOnly = this.relayOnly;
        o.backupOnOpen = this.backupOnOpen;
        o.showJoinable = this.showJoinable;
        o.welcome = this.welcome;
        o.port = this.port;
        o.allowCommands = this.allowCommands;
        o.hostGameMode = this.hostGameMode;
        o.forceGameMode = this.forceGameMode;
        o.sharePacks = this.sharePacks;
        o.shareAllPacks = this.shareAllPacks;
        o.sharedPacks = new ArrayList<>(this.sharedPacks);
        o.banned = new java.util.LinkedHashSet<>(); // bans are edited by Ban / Unban (saved at once), kept as they are now
        for (Banned b : safeBanned()) o.banned.add(b.uuid());
        return o;
    }

    public Section section() { return currentSection; }
    public void setSection(Section section) {
        this.currentSection = section != null ? section : Section.GENERAL;
        this.scrollOffset = 0;
    }

    public int scrollOffset() { return scrollOffset; }
    public int maxScroll() { return maxScroll; }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int prev = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (amount * 20)));
        return prev != scrollOffset;
    }

    public void setScope(HostOptions.Scope scope) { this.scope = scope != null && scope != HostOptions.Scope.OFF ? scope : HostOptions.Scope.FRIENDS; }
    public void setApproval(HostOptions.Approval approval) { this.approval = approval != null ? approval : HostOptions.Approval.ASK; }
    public void setMaxPlayers(int max) { this.maxPlayers = Math.max(1, Math.min(16, max)); }
    public void setGuestGameMode(String mode) { this.guestGameMode = mode != null ? mode : "survival"; }
    public void setGuestOpLevel(int level) { this.guestOpLevel = Math.max(0, Math.min(4, level)); }
    public void setPvp(boolean pvp) { this.pvp = pvp; }
    public void setDifficulty(String diff) { this.difficulty = diff; }
    /** 0 = the host's own render distance; otherwise 2-32 chunks. */
    public void setViewDistanceCap(int cap) { this.viewDistanceCap = cap <= 0 ? 0 : Math.max(2, Math.min(32, cap)); }
    public void setRelayOnly(boolean relay) { this.relayOnly = relay; }
    public void setBackupOnOpen(boolean backup) { this.backupOnOpen = backup; }
    public void setShowJoinable(boolean show) { this.showJoinable = show; }
    public void setWelcome(String welcome) {
        if (welcome != null && welcome.length() > 256) welcome = welcome.substring(0, 256);
        this.welcome = welcome != null ? welcome : "";
    }
    public void setGamerule(String rule, String value) {
        if (value == null) this.gamerules.remove(rule);
        else this.gamerules.put(rule, value);
    }
    /** 0 = any free port; otherwise 1024-65535 (vanilla's range). */
    public void setPort(int port) {
        this.port = port >= 1024 && port <= 65535 ? port : 0;
        this.portText = this.port == 0 ? "" : String.valueOf(this.port);
        this.portInvalid = false;
    }
    public void setAllowCommands(Boolean allow) { this.allowCommands = allow; }
    public void setHostGameMode(String mode) { this.hostGameMode = mode; }
    public void setForceGameMode(boolean force) { this.forceGameMode = force; }
    public void setSharePacks(boolean share) { this.sharePacks = share; }
    public void setShareAllPacks(boolean all) { this.shareAllPacks = all; }
    public void toggleSharedPack(String id) {
        if (!sharedPacks.remove(id)) sharedPacks.add(id);
    }

    public void triggerGuestAction(UUID guestId, SocialBridge.GuestAction action) {
        callbacks.guestAction(guestId, action);
    }

    public void toggleHosting() {
        commitPort();
        if (callbacks.isHosting()) {
            callbacks.stopHosting();
        } else {
            callbacks.startHosting(buildOptions());
        }
    }

    private List<SocialBridge.GuestInfo> safeGuests() {
        List<SocialBridge.GuestInfo> g = callbacks.guests();
        return g != null ? g : List.of();
    }

    /** The rule's value as shown: set here, else the world's, else vanilla's default. */
    String ruleValue(String rule) {
        String v = gamerules.get(rule);
        if (v == null) v = liveRules.get(rule);
        if (v == null) v = SLEEP_RULE.equals(rule) ? "100" : OFF_BY_DEFAULT.contains(rule) ? "false" : "true";
        return v;
    }

    private String startLabel() {
        return scope == HostOptions.Scope.LAN ? "Open to LAN" : scope == HostOptions.Scope.INVITE ? "Open to Invited" : "Open to Friends";
    }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();

        sectionBoxes.clear();
        clickables.clear();
        sliders.clear();
        guestActionBoxes.clear();
        portBox = null;

        g.fill(0, 0, width, height, 0xD0070709);

        int panelW = Math.min(width - 16, 600), panelH = height - 16;
        int px = (width - panelW) / 2, py = 8;
        MenuGraphics.round(g, px, py, panelW, panelH, LadsPalette.PANEL);

        // Header: what this screen does, and whether the world is open right now
        boolean hosting = callbacks.isHosting();
        g.drawText("Host This World", px + PAD + 2, py + 10, LadsPalette.TEXT, true);
        int online = safeGuests().size();
        String status = hosting ? "Open, " + online + " of " + maxPlayers + " guests" : "Not open yet";
        int statusColor = hosting ? LadsPalette.CARD_ON_GLOW : LadsPalette.MUTED;
        int statusX = px + panelW - PAD - g.textWidth(status);
        MenuGraphics.round(g, statusX - 9, py + 11, 5, 5, statusColor);
        g.drawText(status, statusX, py + 10, statusColor, false);

        int bodyY = py + 28, footerH = 34;
        int bodyH = panelH - (bodyY - py) - footerH;
        SocialToasts.reserveTop(bodyY);

        // Sidebar
        int navW = Math.max(96, Math.min(112, panelW / 5));
        int navX = px + PAD, navY = bodyY;
        for (Section s : SECTIONS) {
            Box b = new Box(navX, navY, navW, 20);
            sectionBoxes.put(s, b);
            boolean active = s == currentSection, hover = b.has(mouseX, mouseY);
            if (active || hover) MenuGraphics.round(g, b.x, b.y, b.w, b.h, active ? LadsPalette.PRIMARY : LadsPalette.HOVER);
            String count = s == Section.GUESTS && online > 0 ? String.valueOf(online) : "";
            int countW = count.isEmpty() ? 0 : g.textWidth(count) + 6;
            g.drawText(MenuGraphics.fit(g, s.title, navW - 14 - countW), b.x + 7, b.y + 6, active ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            if (!count.isEmpty()) g.drawText(count, b.x + b.w - 7 - g.textWidth(count), b.y + 6, active ? LadsPalette.TEXT : LadsPalette.ACCENT, false);
            navY += 22;
        }

        // Content
        int cx = navX + navW + GAP, cw = px + panelW - PAD - cx;
        MenuGraphics.round(g, cx, bodyY, cw, bodyH, LadsPalette.CARD);
        this.contentX = cx + PAD;
        this.contentW = cw - PAD * 2 - 4; // room for the scroll bar
        this.scissorY = bodyY + 2;
        this.scissorH = bodyH - 4;
        g.enableScissor(cx, scissorY, cx + cw, scissorY + scissorH);
        int top = bodyY + PAD - scrollOffset;
        int end = switch (currentSection) {
            case GENERAL -> renderAccess(g, top, mouseX, mouseY);
            case GUESTS -> renderPlayers(g, top, mouseX, mouseY);
            case GAMEPLAY -> renderWorld(g, top, mouseX, mouseY);
            case GAMERULES -> renderRules(g, top, mouseX, mouseY);
            case PACKS -> renderPacks(g, top, mouseX, mouseY);
            case ADVANCED -> renderAdvanced(g, top, mouseX, mouseY);
        };
        g.disableScissor();
        maxScroll = Math.max(0, end - top + PAD - bodyH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        if (maxScroll > 0) {
            int trackH = bodyH - 8, thumbH = Math.max(16, trackH * bodyH / (bodyH + maxScroll));
            int thumbY = bodyY + 4 + (trackH - thumbH) * scrollOffset / maxScroll;
            g.fill(cx + cw - 5, thumbY, cx + cw - 3, thumbY + thumbH, LadsPalette.BORDER);
        }

        // Footer: one primary action (open the world; Done once it is open), the other one secondary
        int bh = 20, fy = py + panelH - footerH + (footerH - bh) / 2;
        String primary = hosting ? "Done" : startLabel(), secondary = hosting ? "Stop Hosting" : "Done";
        int pw = Math.max(100, g.textWidth(primary) + 24), sw = Math.max(90, g.textWidth(secondary) + 24);
        Box primaryBtn = new Box(px + panelW - PAD - pw, fy, pw, bh);
        Box secondaryBtn = new Box(primaryBtn.x - GAP - sw, fy, sw, bh);
        MenuGraphics.round(g, primaryBtn.x, primaryBtn.y, primaryBtn.w, primaryBtn.h,
                primaryBtn.has(mouseX, mouseY) ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText(primary, primaryBtn.x + pw / 2, fy + 6, LadsPalette.TEXT, false);
        MenuGraphics.round(g, secondaryBtn.x, secondaryBtn.y, secondaryBtn.w, secondaryBtn.h,
                secondaryBtn.has(mouseX, mouseY) ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText(secondary, secondaryBtn.x + sw / 2, fy + 6, hosting ? LadsPalette.ACCENT : LadsPalette.TEXT, false);
        doneBtn = hosting ? primaryBtn : secondaryBtn;
        hostToggleBtn = hosting ? secondaryBtn : primaryBtn;
        String hint = hosting ? "Changes apply when you click Done" : "Settings are saved for this world";
        if (secondaryBtn.x - px - PAD * 2 > g.textWidth(hint)) g.drawText(hint, px + PAD + 2, fy + 6, LadsPalette.MUTED, false);
    }

    // ---- Sections ----

    private int renderAccess(LadsGraphics g, int y, int mx, int my) {
        y = group(g, "Who can join", y);
        int scopeIdx = scope == HostOptions.Scope.LAN ? 2 : scope == HostOptions.Scope.INVITE ? 1 : 0;
        String scopeDesc = scopeIdx == 2 ? "Anyone on your home network, like vanilla LAN"
                : scopeIdx == 1 ? "Only friends you invite from the Friends screen" : "Your friends can join or ask to join";
        y = choiceRow(g, "scope", "Open to", scopeDesc, SCOPES, SCOPE_LABELS, SCOPES[scopeIdx], y, mx, my);
        String approvalDesc = approval == HostOptions.Approval.AUTO_FRIENDS ? "Friends get in without asking you"
                : approval == HostOptions.Approval.INVITE_ONLY ? "Join requests are turned down" : "You accept each join request";
        y = choiceRow(g, "approval", "Join requests", approvalDesc, APPROVALS, APPROVAL_LABELS, approval.name(), y, mx, my);
        y = sliderRow(g, "maxPlayers", "Max guests", "At most this many friends in the world at once",
                String.valueOf(maxPlayers), maxPlayers, 1, 16, y, mx, my);
        y = toggleRow(g, "showJoinable", "Show as joinable", "Friends see a Join button next to your name", showJoinable, y, mx, my);

        y = group(g, "LAN", y + GAP);
        int rowH = textBlock(g, "Port", portInvalid ? "Not a valid port: a free one is picked" : "Empty picks a free port (1024 to 65535)",
                y, 84, portInvalid ? LadsPalette.ACCENT : LadsPalette.MUTED);
        portBox = new Box(contentX + contentW - 76, y + (rowH - CONTROL_H) / 2, 76, CONTROL_H);
        MenuGraphics.round(g, portBox.x, portBox.y, portBox.w, portBox.h,
                typingPort ? LadsPalette.PRIMARY : portInvalid ? LadsPalette.ACCENT : LadsPalette.BORDER);
        MenuGraphics.round(g, portBox.x + 1, portBox.y + 1, portBox.w - 2, portBox.h - 2,
                typingPort || portBox.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
        String shown = portText.isEmpty() && !typingPort ? "Auto" : portText + (typingPort ? "_" : "");
        g.drawText(shown, portBox.x + 6, portBox.y + 4, portText.isEmpty() && !typingPort ? LadsPalette.MUTED : LadsPalette.TEXT, false);
        if (!visible(portBox)) portBox = null;
        return y + rowH + GAP;
    }

    private int renderWorld(LadsGraphics g, int y, int mx, int my) {
        y = group(g, "Guests", y);
        y = choiceRow(g, "gamemode", "Game mode", "What guests play in", MODES, MODE_LABELS, guestGameMode.toLowerCase(java.util.Locale.ROOT), y, mx, my);
        y = toggleRow(g, "force", "Force game mode", "Guests switch to it every time they join", forceGameMode, y, mx, my);
        y = toggleRow(g, "guestCommands", "Guests can use commands", "Like /gamemode and /tp. Operators always can", guestOpLevel > 0, y, mx, my);
        y = sliderRow(g, "viewDistance", "Guest render distance", "Guests load at most this far. Yours is not changed",
                viewDistanceCap == 0 ? "Same as yours" : viewDistanceCap + " chunks", viewDistanceCap, 0, 32, y, mx, my);
        y = toggleRow(g, "pvp", "PvP", "Players can hurt each other", pvp, y, mx, my);

        y = group(g, "You", y + GAP);
        y = choiceRow(g, "hostmode", "Your game mode", null, MODES, MODE_LABELS,
                hostGameMode == null ? "" : hostGameMode.toLowerCase(java.util.Locale.ROOT), y, mx, my);
        y = toggleRow(g, "cheats", "Allow cheats", "Commands for you in this world", Boolean.TRUE.equals(allowCommands), y, mx, my);

        y = group(g, "World", y + GAP);
        y = choiceRow(g, "diff", "Difficulty", null, DIFFS, DIFF_LABELS,
                difficulty == null ? "keep" : difficulty.toLowerCase(java.util.Locale.ROOT), y, mx, my);
        return y;
    }

    private int renderRules(LadsGraphics g, int y, int mx, int my) {
        for (String r : RULES) {
            if (SLEEP_RULE.equals(r)) {
                int v = parseInt(ruleValue(r), 100);
                y = sliderRow(g, "rule:" + r, ruleLabel(r), null, v + "%", v, 0, 100, y, mx, my);
            } else {
                y = toggleRow(g, "rule:" + r, ruleLabel(r), null, "true".equalsIgnoreCase(ruleValue(r)), y, mx, my);
            }
        }
        return y;
    }

    private int renderPacks(LadsGraphics g, int y, int mx, int my) {
        if (!callbacks.canSharePacks()) {
            g.drawText(MenuGraphics.fit(g, "Sharing packs works when you host on Minecraft 26.x.", contentW), contentX, y + 2, LadsPalette.MUTED, false);
            return y + 14;
        }
        y = toggleRow(g, "sharePacks", "Share my resource packs", "Guests use them while they play here", sharePacks, y, mx, my);
        if (!sharePacks) return y;
        y = choiceRow(g, "packmode", "Which packs", shareAllPacks ? "Every pack you have turned on" : "Only the packs ticked below",
                new String[]{"all", "choose"}, new String[]{"All Turned On", "Choose"}, shareAllPacks ? "all" : "choose", y, mx, my);
        if (packs.isEmpty()) {
            g.drawText(MenuGraphics.fit(g, "You have no resource packs turned on (Options > Resource Packs).", contentW), contentX, y + 2, LadsPalette.MUTED, false);
            return y + 14;
        }
        if (shareAllPacks) {
            for (String name : packs.values()) {
                g.drawText(MenuGraphics.fit(g, name, contentW - 8), contentX + 8, y + 2, LadsPalette.TEXT, false);
                y += 14;
            }
            return y;
        }
        for (Map.Entry<String, String> p : packs.entrySet()) {
            Box row = new Box(contentX - 4, y, contentW + 8, 18);
            if (row.has(mx, my)) MenuGraphics.round(g, row.x, row.y, row.w, row.h, LadsPalette.HOVER);
            boolean on = sharedPacks.contains(p.getKey());
            MenuGraphics.round(g, contentX, y + 4, 10, 10, on ? LadsPalette.PRIMARY : LadsPalette.BORDER);
            if (!on) MenuGraphics.round(g, contentX + 1, y + 5, 8, 8, LadsPalette.PANEL);
            else g.drawText("x", contentX + 2, y + 5, LadsPalette.TEXT, false);
            g.drawText(MenuGraphics.fit(g, p.getValue(), contentW - 18), contentX + 16, y + 5, LadsPalette.TEXT, false);
            click("pack:" + p.getKey(), row);
            y += 20;
        }
        return y;
    }

    private int renderAdvanced(LadsGraphics g, int y, int mx, int my) {
        y = group(g, "Connection", y);
        y = toggleRow(g, "relayOnly", "Always use the relay", "Hides your IP from guests; can add a little lag", relayOnly, y, mx, my);
        y = group(g, "Safety", y + GAP);
        y = toggleRow(g, "backupOnOpen", "Back up before opening", "Zips the world each time you open it", backupOnOpen, y, mx, my);
        return y;
    }

    /** Where a banned player's Unban button was last drawn (null: not on screen). */
    public Box unbanButtonBox(String uuid) {
        return clickables.get("unban:" + uuid);
    }

    /** Where a guest's Make Operator / Remove Operator button was last drawn (null: not on screen). */
    public Box operatorButtonBox(UUID guest) {
        Map<SocialBridge.GuestAction, Box> actions = guestActionBoxes.get(guest);
        if (actions == null) return null;
        Box b = actions.get(SocialBridge.GuestAction.OP);
        return b != null ? b : actions.get(SocialBridge.GuestAction.DEOP);
    }

    private List<Banned> safeBanned() {
        List<Banned> b = callbacks.banned();
        return b != null ? b : List.of();
    }

    private int renderPlayers(LadsGraphics g, int y, int mx, int my) {
        List<SocialBridge.GuestInfo> guests = safeGuests();
        List<Banned> banned = safeBanned();
        y = group(g, "In this world (" + guests.size() + ")", y);
        if (guests.isEmpty()) {
            g.drawText("Nobody has joined yet.", contentX, y + 2, LadsPalette.TEXT, false);
            g.drawText(MenuGraphics.fit(g, callbacks.isHosting() ? "Invite friends from the Friends screen."
                    : "Open the world first, then invite friends.", contentW), contentX, y + 14, LadsPalette.MUTED, false);
            y += 30;
        }
        for (SocialBridge.GuestInfo guest : guests) {
            int cardH = 46;
            if (y + cardH > scissorY && y < scissorY + scissorH) {
                int x = contentX - 4, w = contentW + 8;
                MenuGraphics.round(g, x, y, w, cardH, LadsPalette.PANEL);
                g.drawHead(guest.name(), guest.id().toString(), x + 6, y + 5, 16);
                Map<SocialBridge.GuestAction, Box> actions = guestActionBoxes.computeIfAbsent(guest.id(), k -> new HashMap<>());

                // The operator button is the obvious one: green while it grants, plain while it takes away
                String opLabel = guest.op() ? "Remove Operator" : "Make Operator";
                int opW = g.textWidth(opLabel) + 16;
                Box opB = new Box(x + w - 6 - opW, y + 5, opW, CONTROL_H);
                actions.put(guest.op() ? SocialBridge.GuestAction.DEOP : SocialBridge.GuestAction.OP, opB);
                int opBg = guest.op() ? (opB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD)
                        : (opB.has(mx, my) ? MenuGraphics.mix(LadsPalette.CARD_ON, LadsPalette.CARD_ON_GLOW, 0.25f) : LadsPalette.CARD_ON);
                MenuGraphics.round(g, opB.x, opB.y, opB.w, opB.h, opBg);
                g.drawCenteredText(opLabel, opB.x + opB.w / 2, opB.y + 4, LadsPalette.TEXT, false);

                int nameX = x + 28, nameMax = opB.x - GAP - nameX;
                String badge = guest.op() ? "Operator" : "";
                int badgeW = badge.isEmpty() ? 0 : g.textWidth(badge) + 8;
                String name = MenuGraphics.fit(g, guest.name(), nameMax - (badgeW > 0 ? badgeW + 4 : 0));
                g.drawText(name, nameX, y + 5, LadsPalette.TEXT, false);
                if (badgeW > 0) {
                    int bx = nameX + g.textWidth(name) + 4;
                    MenuGraphics.round(g, bx, y + 3, badgeW, 12, LadsPalette.CARD_ON);
                    g.drawText(badge, bx + 4, y + 5, LadsPalette.CARD_ON_GLOW, false);
                }
                String mode = guest.gameMode() == null ? "" : guest.gameMode();
                String modeLabel = mode.isEmpty() ? "Unknown" : Character.toUpperCase(mode.charAt(0)) + mode.substring(1).toLowerCase(java.util.Locale.ROOT);
                g.drawText(MenuGraphics.fit(g, modeLabel + ", " + guest.ping() + " ms", nameMax), nameX, y + 15, LadsPalette.MUTED, false);

                // Second line: the other actions, small and plain; Kick and Ban in the warning colour
                int bx = nameX, by = y + 27;
                bx = action(g, actions, getNextGameModeAction(mode), "Mode: " + modeLabel, LadsPalette.TEXT, bx, by, mx, my);
                bx = action(g, actions, SocialBridge.GuestAction.TP_TO_ME, "TP Here", LadsPalette.TEXT, bx, by, mx, my);
                bx = action(g, actions, SocialBridge.GuestAction.MUTE, "Mute", LadsPalette.TEXT, bx, by, mx, my);
                bx = action(g, actions, SocialBridge.GuestAction.KICK, "Kick", LadsPalette.ACCENT, bx, by, mx, my);
                action(g, actions, SocialBridge.GuestAction.BAN, "Ban", LadsPalette.ACCENT, bx, by, mx, my);
                actions.values().removeIf(b -> !visible(b) || b.x + b.w > x + w);
            }
            y += cardH + 4;
        }

        // Banned from this world (lads-host.json): Unban lets them ask to join again
        y = group(g, "Banned (" + banned.size() + ")", y + GAP);
        if (banned.isEmpty()) {
            g.drawText("Nobody is banned from this world.", contentX, y + 2, LadsPalette.MUTED, false);
            return y + 14;
        }
        for (Banned b : banned) {
            MenuGraphics.round(g, contentX - 4, y, contentW + 8, 22, LadsPalette.PANEL);
            g.drawText(MenuGraphics.fit(g, b.name(), contentW - 70), contentX + 2, y + 7, LadsPalette.TEXT, false);
            Box unbanB = new Box(contentX + contentW - 54, y + 3, 54, CONTROL_H);
            MenuGraphics.round(g, unbanB.x, unbanB.y, unbanB.w, unbanB.h, unbanB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
            g.drawCenteredText("Unban", unbanB.x + unbanB.w / 2, unbanB.y + 4, LadsPalette.TEXT, false);
            click("unban:" + b.uuid(), unbanB);
            y += 26;
        }
        return y;
    }

    private int action(LadsGraphics g, Map<SocialBridge.GuestAction, Box> actions, SocialBridge.GuestAction action, String label,
                       int color, int x, int y, int mx, int my) {
        int w = g.textWidth(label) + 12;
        Box b = new Box(x, y, w, 14);
        actions.put(action, b);
        MenuGraphics.round(g, b.x, b.y, b.w, b.h, b.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText(label, b.x + w / 2, b.y + 3, color, false);
        return x + w + 4;
    }

    private SocialBridge.GuestAction getNextGameModeAction(String currentMode) {
        if ("survival".equalsIgnoreCase(currentMode)) return SocialBridge.GuestAction.CREATIVE;
        if ("creative".equalsIgnoreCase(currentMode)) return SocialBridge.GuestAction.ADVENTURE;
        if ("adventure".equalsIgnoreCase(currentMode)) return SocialBridge.GuestAction.SPECTATOR;
        return SocialBridge.GuestAction.SURVIVAL;
    }

    // ---- Controls ----

    private boolean visible(Box b) {
        return b.y + b.h > scissorY && b.y < scissorY + scissorH;
    }

    private void click(String key, Box b) {
        if (visible(b)) clickables.put(key, b);
    }

    /** A small heading over a group of settings (capitals: it reads apart from the explanations under each setting). */
    private int group(LadsGraphics g, String title, int y) {
        g.drawText(title.toUpperCase(java.util.Locale.ROOT), contentX, y + 2, LadsPalette.MUTED, false);
        return y + 16;
    }

    /** Label (and its explanation under it) at the content's left, kept clear of a control controlW wide; returns the rows' height. */
    private int textBlock(LadsGraphics g, String label, String desc, int y, int controlW, int descColor) {
        int room = contentW - controlW - GAP;
        g.drawText(MenuGraphics.fit(g, label, room), contentX, y + 3, LadsPalette.TEXT, false);
        if (desc == null) return 18;
        g.drawText(MenuGraphics.fit(g, desc, room), contentX, y + 15, descColor, false);
        return 27;
    }

    private int toggleRow(LadsGraphics g, String key, String label, String desc, boolean on, int y, int mx, int my) {
        int rowH = textBlock(g, label, desc, y, 30, LadsPalette.MUTED);
        Box row = new Box(contentX - 4, y - 2, contentW + 8, rowH + 2);
        if (row.has(mx, my)) {
            MenuGraphics.round(g, row.x, row.y, row.w, row.h, LadsPalette.HOVER);
            textBlock(g, label, desc, y, 30, LadsPalette.MUTED); // over the hover fill
        }
        int sx = contentX + contentW - 24, sy = y + (rowH - 12) / 2;
        MenuGraphics.round(g, sx, sy, 24, 12, on ? LadsPalette.PRIMARY : LadsPalette.BORDER);
        MenuGraphics.round(g, on ? sx + 14 : sx + 2, sy + 2, 8, 8, on ? LadsPalette.TEXT : LadsPalette.MUTED);
        click("toggle:" + key, row);
        return y + rowH + 4;
    }

    private int choiceRow(LadsGraphics g, String key, String label, String desc, String[] values, String[] labels, String selected,
                          int y, int mx, int my) {
        int[] widths = new int[labels.length];
        int total = 0;
        for (int i = 0; i < labels.length; i++) total += widths[i] = g.textWidth(labels[i]) + 14;
        int textW = Math.max(g.textWidth(label), desc == null ? 0 : g.textWidth(desc));
        boolean inline = textW + GAP + total <= contentW;
        int rowH, cy, cx;
        if (inline) {
            rowH = textBlock(g, label, desc, y, total, LadsPalette.MUTED);
            cy = y + (rowH - CONTROL_H) / 2;
            cx = contentX + contentW - total;
        } else { // too narrow: the choices go under the text, across the content
            rowH = textBlock(g, label, desc, y, 0, LadsPalette.MUTED);
            cy = y + rowH;
            cx = contentX;
            if (total > contentW) for (int i = 0; i < widths.length; i++) widths[i] = contentW / widths.length;
            total = 0;
            for (int w : widths) total += w;
            rowH += CONTROL_H + 2;
        }
        MenuGraphics.round(g, cx, cy, total, CONTROL_H, LadsPalette.PANEL);
        for (int i = 0; i < values.length; i++) {
            Box b = new Box(cx, cy, widths[i], CONTROL_H);
            boolean sel = values[i].equalsIgnoreCase(selected);
            if (sel || b.has(mx, my)) MenuGraphics.round(g, b.x, b.y, b.w, b.h, sel ? LadsPalette.PRIMARY : LadsPalette.HOVER);
            g.drawCenteredText(MenuGraphics.fit(g, labels[i], b.w - 4), b.x + b.w / 2, b.y + 4, sel ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            click(key + ":" + values[i], b);
            cx += widths[i];
        }
        return y + rowH + 4;
    }

    private int sliderRow(LadsGraphics g, String key, String label, String desc, String valueText, int value, int min, int max,
                          int y, int mx, int my) {
        int valueW = g.textWidth(valueText), rowH = 18;
        g.drawText(MenuGraphics.fit(g, label, contentW - valueW - GAP), contentX, y + 3, LadsPalette.TEXT, false);
        if (desc != null) { // the value sits on the label's line only: the explanation gets the whole width
            g.drawText(MenuGraphics.fit(g, desc, contentW), contentX, y + 15, LadsPalette.MUTED, false);
            rowH = 27;
        }
        g.drawText(valueText, contentX + contentW - valueW, y + 3, LadsPalette.TEXT, false);
        int ty = y + rowH;
        Box track = new Box(contentX + 3, ty, contentW - 6, 12);
        float t = max == min ? 0 : (float) (Math.max(min, Math.min(max, value)) - min) / (max - min);
        int knobX = track.x + Math.round(t * track.w);
        boolean active = key.equals(draggingSlider) || track.has(mx, my) || new Box(track.x - 4, ty, track.w + 8, 12).has(mx, my);
        g.fill(track.x, ty + 5, track.x + track.w, ty + 7, LadsPalette.BORDER);
        g.fill(track.x, ty + 5, knobX, ty + 7, LadsPalette.PRIMARY);
        MenuGraphics.round(g, knobX - 3, ty, 6, 12, active ? LadsPalette.ACCENT : LadsPalette.TEXT);
        if (visible(track)) sliders.put(key, new Slider(track, min, max));
        return y + rowH + 16;
    }

    private void dragSlider(double x) {
        Slider s = draggingSlider == null ? null : sliders.get(draggingSlider);
        if (s == null) return;
        double t = Math.max(0, Math.min(1, (x - s.track().x) / (double) s.track().w));
        setSliderValue(draggingSlider, (int) Math.round(s.min() + t * (s.max() - s.min())));
    }

    private void setSliderValue(String key, int v) {
        switch (key) {
            case "maxPlayers" -> setMaxPlayers(v);
            case "viewDistance" -> setViewDistanceCap(v == 1 ? 2 : v);
            default -> {
                if (key.startsWith("rule:")) setGamerule(key.substring(5), String.valueOf(Math.max(0, Math.min(100, v))));
            }
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ---- Input ----

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        boolean wasTyping = typingPort;
        if (typingPort) {
            typingPort = false;
            commitPort();
        }

        if (doneBtn != null && doneBtn.has(x, y)) {
            close();
            return true;
        }
        if (hostToggleBtn != null && hostToggleBtn.has(x, y)) {
            toggleHosting();
            return true;
        }
        for (Map.Entry<Section, Box> entry : sectionBoxes.entrySet()) {
            if (entry.getValue().has(x, y)) {
                setSection(entry.getKey());
                return true;
            }
        }

        boolean inScissor = y >= scissorY && y <= scissorY + scissorH;
        if (inScissor) {
            if (portBox != null && portBox.has(x, y)) {
                typingPort = true;
                return true;
            }
            for (Map.Entry<String, Slider> s : sliders.entrySet()) {
                Box t = s.getValue().track();
                if (new Box(t.x - 4, t.y, t.w + 8, t.h).has(x, y)) {
                    draggingSlider = s.getKey();
                    dragSlider(x);
                    return true;
                }
            }
            for (Map.Entry<String, Box> entry : clickables.entrySet()) {
                if (entry.getValue().has(x, y)) {
                    handleClick(entry.getKey());
                    return true;
                }
            }
            for (Map.Entry<UUID, Map<SocialBridge.GuestAction, Box>> ge : guestActionBoxes.entrySet()) {
                for (Map.Entry<SocialBridge.GuestAction, Box> ae : ge.getValue().entrySet()) {
                    if (ae.getValue().has(x, y)) {
                        triggerGuestAction(ge.getKey(), ae.getKey());
                        return true;
                    }
                }
            }
        }
        return wasTyping;
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (draggingSlider == null) return false;
        dragSlider(x);
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        boolean was = draggingSlider != null;
        draggingSlider = null;
        return was;
    }

    private void handleClick(String key) {
        if (key.startsWith("unban:")) {
            callbacks.unban(key.substring("unban:".length()));
        } else if (key.startsWith("scope:")) {
            setScope(HostOptions.Scope.valueOf(key.substring("scope:".length())));
        } else if (key.startsWith("approval:")) {
            setApproval(HostOptions.Approval.valueOf(key.substring("approval:".length())));
        } else if (key.startsWith("gamemode:")) {
            setGuestGameMode(key.substring("gamemode:".length()));
        } else if (key.startsWith("hostmode:")) {
            setHostGameMode(key.substring("hostmode:".length()));
        } else if (key.startsWith("diff:")) {
            String d = key.substring("diff:".length());
            setDifficulty("keep".equals(d) ? null : d);
        } else if (key.startsWith("packmode:")) {
            setShareAllPacks("all".equals(key.substring("packmode:".length())));
        } else if (key.startsWith("pack:")) {
            toggleSharedPack(key.substring("pack:".length()));
        } else if (key.startsWith("toggle:rule:")) {
            String rule = key.substring("toggle:rule:".length());
            setGamerule(rule, "true".equalsIgnoreCase(ruleValue(rule)) ? "false" : "true");
        } else {
            switch (key) {
                case "toggle:showJoinable" -> setShowJoinable(!showJoinable);
                case "toggle:force" -> setForceGameMode(!forceGameMode);
                case "toggle:guestCommands" -> setGuestOpLevel(guestOpLevel > 0 ? 0 : 2);
                case "toggle:pvp" -> setPvp(!pvp);
                case "toggle:cheats" -> setAllowCommands(!Boolean.TRUE.equals(allowCommands));
                case "toggle:relayOnly" -> setRelayOnly(!relayOnly);
                case "toggle:backupOnOpen" -> setBackupOnOpen(!backupOnOpen);
                case "toggle:sharePacks" -> setSharePacks(!sharePacks);
                default -> {}
            }
        }
    }

    /** The port box has the keyboard: 26.3 delivers typed characters only while the screen asks for them. */
    public boolean isEditingText() { return typingPort; }

    public boolean charTyped(char c) {
        if (typingPort && c >= '0' && c <= '9' && portText.length() < 5) {
            portText += c;
            return true;
        }
        return false;
    }

    private void commitPort() {
        if (portText.isEmpty()) {
            port = 0;
            portInvalid = false;
            return;
        }
        int p = parseInt(portText, -1);
        portInvalid = p < 1024 || p > 65535;
        port = portInvalid ? 0 : p;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean escape = keyCode == 256 || keyCode == 1, enter = keyCode == 257 || keyCode == 28 || keyCode == 335;
        if (typingPort) {
            if ((keyCode == 259 || keyCode == 14) && !portText.isEmpty()) {
                portText = portText.substring(0, portText.length() - 1);
                return true;
            }
            if (escape || enter) {
                typingPort = false;
                commitPort();
                return true;
            }
            return false;
        }
        if (escape) {
            close();
            return true;
        }
        return false;
    }

    public void close() {
        typingPort = false;
        commitPort();
        HostOptions o = buildOptions();
        callbacks.saveOptions(o);
        callbacks.apply(o);
        callbacks.onClose();
    }
}

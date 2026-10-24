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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.LoggerFactory;

/**
 * Common GUI logic for Multiplayer Options (Host Screen):
 * Scope, approval, max players, guest game mode, op level, PvP, difficulty,
 * gamerules, view distance cap, relay only, backup, show joinable, welcome message,
 * guest list with per-player actions (Kick, Ban, Op/Deop, GameMode, Mute, TP),
 * mouse scrolling, and Open/Close hosting button.
 */
public final class HostScreenLogic {

    public record Box(int x, int y, int w, int h) {
        public boolean has(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    public enum Section {
        GENERAL("General"),
        GAMEPLAY("Gameplay"),
        GAMERULES("Game Rules"),
        ADVANCED("Advanced"),
        GUESTS("Guests");

        public final String title;
        Section(String title) { this.title = title; }
    }

    private static final Section[] SECTIONS = Section.values();
    private static final HostOptions.Scope[] SCOPES = HostOptions.Scope.values();
    private static final HostOptions.Approval[] APPROVALS = HostOptions.Approval.values();

    /** A banned player: UUID string and the name to show. */
    public record Banned(String uuid, String name) {}

    public interface Callbacks {
        HostOptions currentOptions();
        default void saveOptions(HostOptions options) {}
        default boolean isHosting() { return false; }
        default void startHosting(HostOptions options) {}
        default void stopHosting() {}
        default List<SocialBridge.GuestInfo> guests() { return List.of(); }
        default void guestAction(UUID id, SocialBridge.GuestAction action) {}
        /** This world's banned players (kept in lads-host.json) and how to lift a ban. */
        default List<Banned> banned() { return List.of(); }
        default void unban(String uuid) {}
        default void onClose() {}
    }

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

    // Welcome input
    private boolean typingWelcome = false;

    // Scrolling
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Hit-boxes
    private int scissorY = 0;
    private int scissorH = Integer.MAX_VALUE;
    private final Map<Section, Box> sectionBoxes = new HashMap<>();
    private final Map<String, Box> clickables = new HashMap<>();
    private final Map<UUID, Map<SocialBridge.GuestAction, Box>> guestActionBoxes = new HashMap<>();

    private Box hostToggleBtn;
    private Box doneBtn;
    private Box welcomeInputBox;

    public HostScreenLogic(Callbacks callbacks) {
        this.callbacks = callbacks != null ? callbacks : new Callbacks() {
            @Override public HostOptions currentOptions() { return new HostOptions(); }
        };
        loadFrom(this.callbacks.currentOptions());
    }

    /** Multiplayer Options for the open world (null = none: defaults, not saved); callbacks go to SocialState.get() and its bridge. */
    public static HostScreenLogic forWorld(Path worldDir, Runnable onClose) {
        SocialState s = SocialState.get();
        return new HostScreenLogic(new Callbacks() {
            @Override public HostOptions currentOptions() {
                return s.hostSession().isHosting() || worldDir == null ? s.hostOptions() : HostOptions.load(worldDir);
            }
            @Override public void saveOptions(HostOptions options) {
                if (worldDir == null) return;
                try {
                    options.save(worldDir);
                } catch (IOException e) {
                    LoggerFactory.getLogger("TheLadsCore/HostScreen").warn("Could not save lads-host.json: {}", e.getMessage());
                }
            }
            @Override public boolean isHosting() { return s.hostSession().isHosting(); }
            @Override public void startHosting(HostOptions options) {
                saveOptions(options);
                s.startHosting(options);
            }
            @Override public void stopHosting() { s.stopHosting(); }
            @Override public List<SocialBridge.GuestInfo> guests() { return SocialBridge.get().guests(); }
            @Override public void guestAction(UUID id, SocialBridge.GuestAction action) { s.guestAction(id, action); }
            @Override public List<Banned> banned() {
                List<Banned> out = new ArrayList<>();
                for (String id : s.worldBans()) {
                    String name = id.length() > 8 ? id.substring(0, 8) : id;
                    for (com.thelads.core.client.social.FriendEntry f : s.friends()) if (f.id().toString().equals(id)) name = f.displayName();
                    out.add(new Banned(id, name));
                }
                return out;
            }
            @Override public void unban(String uuid) { s.unban(uuid); }
            @Override public void onClose() { onClose.run(); }
        });
    }

    public void loadFrom(HostOptions o) {
        if (o == null) o = new HostOptions();
        this.scope = o.scope != null ? o.scope : HostOptions.Scope.FRIENDS;
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

    public void setScope(HostOptions.Scope scope) { this.scope = scope != null ? scope : HostOptions.Scope.FRIENDS; }
    public void setApproval(HostOptions.Approval approval) { this.approval = approval != null ? approval : HostOptions.Approval.ASK; }
    public void setMaxPlayers(int max) { this.maxPlayers = Math.max(1, Math.min(16, max)); }
    public void setGuestGameMode(String mode) { this.guestGameMode = mode != null ? mode : "survival"; }
    public void setGuestOpLevel(int level) { this.guestOpLevel = Math.max(0, Math.min(4, level)); }
    public void setPvp(boolean pvp) { this.pvp = pvp; }
    public void setDifficulty(String diff) { this.difficulty = diff; }
    public void setViewDistanceCap(int cap) { this.viewDistanceCap = Math.max(0, Math.min(32, cap)); }
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

    public void triggerGuestAction(UUID guestId, SocialBridge.GuestAction action) {
        callbacks.guestAction(guestId, action);
    }

    public void toggleHosting() {
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

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();

        // Clear layout maps
        sectionBoxes.clear();
        clickables.clear();
        guestActionBoxes.clear();

        // Dark background overlay
        g.fill(0, 0, width, height, 0xD0070709);

        int panelW = Math.min(width - 32, 540);
        int panelX = (width - panelW) / 2;
        int topY = 12;

        // Header Title
        g.drawText("Multiplayer Options", panelX, topY + 4, LadsPalette.TEXT, true);
        String hostStatus = callbacks.isHosting() ? "Hosting Active" : "Not Hosted";
        int statusCol = callbacks.isHosting() ? LadsPalette.CARD_ON_GLOW : LadsPalette.MUTED;
        g.drawText(hostStatus, panelX + 130, topY + 4, statusCol, false);

        // Section Tabs
        int tabY = topY + 24;
        int tabW = (panelW - (SECTIONS.length - 1) * 4) / SECTIONS.length;
        int tabH = 20;

        for (int i = 0; i < SECTIONS.length; i++) {
            Section s = SECTIONS[i];
            int tx = panelX + i * (tabW + 4);
            Box b = new Box(tx, tabY, tabW, tabH);
            sectionBoxes.put(s, b);

            boolean active = (s == currentSection);
            boolean hover = b.has(mouseX, mouseY);
            int bg = active ? LadsPalette.PRIMARY : (hover ? LadsPalette.HOVER : LadsPalette.CARD);
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, bg);

            String title = s.title;
            if (s == Section.GUESTS) {
                int gCount = safeGuests().size();
                title += " (" + gCount + ")";
            }
            g.drawCenteredText(title, b.x + b.w / 2, b.y + (b.h - g.fontHeight()) / 2 + 1,
                    active ? LadsPalette.TEXT : LadsPalette.MUTED, active);
        }

        SocialToasts.reserveTop(tabY + tabH + 6);

        // Main Settings Card
        int contentY = tabY + tabH + 8;
        int contentH = height - contentY - 36;
        MenuGraphics.round(g, panelX, contentY, panelW, contentH, LadsPalette.PANEL);

        this.scissorY = contentY + 4;
        this.scissorH = contentH - 8;
        g.enableScissor(panelX, scissorY, panelW, scissorH);

        switch (currentSection) {
            case GENERAL -> renderGeneralSection(g, panelX, contentY, panelW, contentH, mouseX, mouseY, scissorY, scissorH);
            case GAMEPLAY -> renderGameplaySection(g, panelX, contentY, panelW, contentH, mouseX, mouseY, scissorY, scissorH);
            case GAMERULES -> renderGamerulesSection(g, panelX, contentY, panelW, contentH, mouseX, mouseY, scissorY, scissorH);
            case ADVANCED -> renderAdvancedSection(g, panelX, contentY, panelW, contentH, mouseX, mouseY, scissorY, scissorH);
            case GUESTS -> renderGuestsSection(g, panelX, contentY, panelW, contentH, mouseX, mouseY, scissorY, scissorH);
        }

        g.disableScissor();

        // Bottom Action Buttons (outside scissor, never drawn over)
        int btnW = 120, btnH = 22;
        int bottomY = height - 28;

        hostToggleBtn = new Box(panelX, bottomY, btnW + 20, btnH);
        boolean hostHover = hostToggleBtn.has(mouseX, mouseY);
        boolean isHosting = callbacks.isHosting();
        int hostBtnCol = isHosting ? (hostHover ? LadsPalette.HOVER : LadsPalette.CARD)
                : (hostHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        MenuGraphics.round(g, hostToggleBtn.x, hostToggleBtn.y, hostToggleBtn.w, hostToggleBtn.h, hostBtnCol);
        String hostBtnText = isHosting ? "Stop Hosting" : "Start Hosting";
        g.drawCenteredText(hostBtnText, hostToggleBtn.x + hostToggleBtn.w / 2, hostToggleBtn.y + 6,
                isHosting ? LadsPalette.ACCENT : LadsPalette.TEXT, false);

        doneBtn = new Box(panelX + panelW - btnW, bottomY, btnW, btnH);
        boolean doneHover = doneBtn.has(mouseX, mouseY);
        MenuGraphics.round(g, doneBtn.x, doneBtn.y, doneBtn.w, doneBtn.h,
                doneHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Done", doneBtn.x + doneBtn.w / 2, doneBtn.y + 6, LadsPalette.TEXT, false);
    }

    private void renderGeneralSection(LadsGraphics g, int x, int y, int w, int h, int mx, int my, int sy, int sh) {
        int totalH = 176; // 4 rows x 28 + welcome label 20 + input 22 + padding: the last row scrolls fully into view
        maxScroll = Math.max(0, totalH - (h - 12));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int itemY = y + 10 - scrollOffset;

        // Scope
        g.drawText("Multiplayer Scope", x + 12, itemY + 6, LadsPalette.TEXT, false);
        int optW = 60, optH = 18;
        int optX = x + w - 4 * (optW + 4) - 12;
        for (HostOptions.Scope s : SCOPES) {
            Box b = new Box(optX, itemY + 2, optW, optH);
            if (b.y + b.h > sy && b.y < sy + sh) clickables.put("scope:" + s.name(), b);
            boolean sel = (this.scope == s);
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, sel ? LadsPalette.PRIMARY : (b.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD));
            g.drawCenteredText(s.name(), b.x + b.w / 2, b.y + 4, sel ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            optX += optW + 4;
        }

        itemY += 28;
        // Approval
        g.drawText("Join Approval", x + 12, itemY + 6, LadsPalette.TEXT, false);
        int appW = 80;
        int appX = x + w - 3 * (appW + 4) - 12;
        for (HostOptions.Approval a : APPROVALS) {
            Box b = new Box(appX, itemY + 2, appW, optH);
            if (b.y + b.h > sy && b.y < sy + sh) clickables.put("approval:" + a.name(), b);
            boolean sel = (this.approval == a);
            String label = a == HostOptions.Approval.AUTO_FRIENDS ? "Auto Friends" : a == HostOptions.Approval.ASK ? "Ask Me" : "Invite Only";
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, sel ? LadsPalette.PRIMARY : (b.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD));
            g.drawCenteredText(label, b.x + b.w / 2, b.y + 4, sel ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            appX += appW + 4;
        }

        itemY += 28;
        // Max players
        g.drawText("Max guests: " + maxPlayers, x + 12, itemY + 6, LadsPalette.TEXT, false);
        Box minusBtn = new Box(x + w - 80, itemY + 2, 24, optH);
        Box plusBtn = new Box(x + w - 40, itemY + 2, 24, optH);
        if (minusBtn.y + minusBtn.h > sy && minusBtn.y < sy + sh) {
            clickables.put("maxPlayers:minus", minusBtn);
            clickables.put("maxPlayers:plus", plusBtn);
        }
        MenuGraphics.round(g, minusBtn.x, minusBtn.y, minusBtn.w, minusBtn.h, minusBtn.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("-", minusBtn.x + minusBtn.w / 2, minusBtn.y + 4, LadsPalette.TEXT, false);
        MenuGraphics.round(g, plusBtn.x, plusBtn.y, plusBtn.w, plusBtn.h, plusBtn.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("+", plusBtn.x + plusBtn.w / 2, plusBtn.y + 4, LadsPalette.TEXT, false);

        itemY += 28;
        // Show joinable
        g.drawText("Show as Joinable in Friends List", x + 12, itemY + 6, LadsPalette.TEXT, false);
        Box joinableToggle = new Box(x + w - 70, itemY + 2, 58, optH);
        if (joinableToggle.y + joinableToggle.h > sy && joinableToggle.y < sy + sh) clickables.put("toggle:showJoinable", joinableToggle);
        MenuGraphics.round(g, joinableToggle.x, joinableToggle.y, joinableToggle.w, joinableToggle.h,
                showJoinable ? LadsPalette.PRIMARY : LadsPalette.CARD);
        g.drawCenteredText(showJoinable ? "ON" : "OFF", joinableToggle.x + joinableToggle.w / 2, joinableToggle.y + 4, LadsPalette.TEXT, false);

        itemY += 28;
        // Welcome message
        g.drawText("Welcome Message", x + 12, itemY + 6, LadsPalette.TEXT, false);
        itemY += 20;
        welcomeInputBox = new Box(x + 12, itemY, w - 24, 22);
        MenuGraphics.round(g, welcomeInputBox.x, welcomeInputBox.y, welcomeInputBox.w, welcomeInputBox.h,
                typingWelcome ? LadsPalette.HOVER : LadsPalette.CARD);
        String welDisp = welcome.isEmpty() ? (typingWelcome ? "_" : "Enter welcome message...") : welcome + (typingWelcome ? "_" : "");
        int welCol = welcome.isEmpty() && !typingWelcome ? LadsPalette.MUTED : LadsPalette.TEXT;
        g.drawText(MenuGraphics.fit(g, welDisp, w - 40), welcomeInputBox.x + 8, welcomeInputBox.y + 6, welCol, false);
    }

    private void renderGameplaySection(LadsGraphics g, int x, int y, int w, int h, int mx, int my, int sy, int sh) {
        int totalH = 140;
        maxScroll = Math.max(0, totalH - (h - 12));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int itemY = y + 10 - scrollOffset;
        int optW = 60, optH = 18;

        // Guest game mode
        g.drawText("Default Guest Game Mode", x + 12, itemY + 6, LadsPalette.TEXT, false);
        String[] modes = {"survival", "creative", "adventure", "spectator"};
        int gmX = x + w - 4 * (optW + 4) - 12;
        for (String m : modes) {
            Box b = new Box(gmX, itemY + 2, optW, optH);
            if (b.y + b.h > sy && b.y < sy + sh) clickables.put("gamemode:" + m, b);
            boolean sel = guestGameMode.equalsIgnoreCase(m);
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, sel ? LadsPalette.PRIMARY : (b.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD));
            g.drawCenteredText(m.substring(0, 1).toUpperCase() + m.substring(1), b.x + b.w / 2, b.y + 4, sel ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            gmX += optW + 4;
        }

        itemY += 28;
        // Guest Op Level (0-4)
        g.drawText("Guest Permission Level (Op: " + guestOpLevel + ")", x + 12, itemY + 6, LadsPalette.TEXT, false);
        int opX = x + w - 5 * (26 + 4) - 12;
        for (int i = 0; i <= 4; i++) {
            Box b = new Box(opX, itemY + 2, 26, optH);
            if (b.y + b.h > sy && b.y < sy + sh) clickables.put("op:" + i, b);
            boolean sel = (guestOpLevel == i);
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, sel ? LadsPalette.PRIMARY : (b.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD));
            g.drawCenteredText(String.valueOf(i), b.x + b.w / 2, b.y + 4, sel ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            opX += 30;
        }

        itemY += 28;
        // PvP Toggle
        g.drawText("Player vs Player (PvP)", x + 12, itemY + 6, LadsPalette.TEXT, false);
        Box pvpToggle = new Box(x + w - 70, itemY + 2, 58, optH);
        if (pvpToggle.y + pvpToggle.h > sy && pvpToggle.y < sy + sh) clickables.put("toggle:pvp", pvpToggle);
        MenuGraphics.round(g, pvpToggle.x, pvpToggle.y, pvpToggle.w, pvpToggle.h, pvp ? LadsPalette.PRIMARY : LadsPalette.CARD);
        g.drawCenteredText(pvp ? "ON" : "OFF", pvpToggle.x + pvpToggle.w / 2, pvpToggle.y + 4, LadsPalette.TEXT, false);

        itemY += 28;
        // Difficulty
        g.drawText("World Difficulty Override", x + 12, itemY + 6, LadsPalette.TEXT, false);
        String[] diffs = {null, "peaceful", "easy", "normal", "hard"};
        int dfW = 54;
        int dfX = x + w - diffs.length * (dfW + 4) - 12;
        for (String d : diffs) {
            String key = d == null ? "keep" : d;
            Box b = new Box(dfX, itemY + 2, dfW, optH);
            if (b.y + b.h > sy && b.y < sy + sh) clickables.put("diff:" + key, b);
            boolean sel = (d == null && difficulty == null) || (d != null && d.equalsIgnoreCase(difficulty));
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, sel ? LadsPalette.PRIMARY : (b.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD));
            String lbl = d == null ? "Keep" : d.substring(0, 1).toUpperCase() + d.substring(1);
            g.drawCenteredText(lbl, b.x + b.w / 2, b.y + 4, sel ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            dfX += dfW + 4;
        }
    }

    private void renderGamerulesSection(LadsGraphics g, int x, int y, int w, int h, int mx, int my, int sy, int sh) {
        String[] rules = {
                "keepInventory",
                "doDaylightCycle",
                "doWeatherCycle",
                "mobGriefing",
                "doFireTick",
                "doMobSpawning",
                "naturalRegeneration",
                "playersSleepingPercentage"
        };

        int totalH = rules.length * 24 + 16;
        maxScroll = Math.max(0, totalH - (h - 12));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int itemY = y + 8 - scrollOffset;

        for (String r : rules) {
            if (itemY + 20 > sy && itemY < sy + sh) {
                g.drawText(r, x + 14, itemY + 4, LadsPalette.TEXT, false);

                String val = gamerules.get(r);
                boolean isTrue = "true".equalsIgnoreCase(val);

                Box toggleB = new Box(x + w - 70, itemY, 56, 16);
                clickables.put("rule:" + r, toggleB);
                MenuGraphics.round(g, toggleB.x, toggleB.y, toggleB.w, toggleB.h, isTrue ? LadsPalette.PRIMARY : LadsPalette.CARD);
                g.drawCenteredText(isTrue ? "true" : (val == null ? "default" : "false"),
                        toggleB.x + toggleB.w / 2, toggleB.y + 4, isTrue ? LadsPalette.TEXT : LadsPalette.MUTED, false);
            }
            itemY += 24;
        }
    }

    private void renderAdvancedSection(LadsGraphics g, int x, int y, int w, int h, int mx, int my, int sy, int sh) {
        int totalH = 120;
        maxScroll = Math.max(0, totalH - (h - 12));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int itemY = y + 10 - scrollOffset;
        int optH = 18;

        // View Distance Cap
        String capText = viewDistanceCap == 0 ? "Host default" : viewDistanceCap + " chunks";
        g.drawText("Guest View Distance Cap: " + capText, x + 12, itemY + 6, LadsPalette.TEXT, false);
        Box minusVd = new Box(x + w - 80, itemY + 2, 24, optH);
        Box plusVd = new Box(x + w - 40, itemY + 2, 24, optH);
        if (minusVd.y + minusVd.h > sy && minusVd.y < sy + sh) {
            clickables.put("viewDistance:minus", minusVd);
            clickables.put("viewDistance:plus", plusVd);
        }
        MenuGraphics.round(g, minusVd.x, minusVd.y, minusVd.w, minusVd.h, minusVd.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("-", minusVd.x + minusVd.w / 2, minusVd.y + 4, LadsPalette.TEXT, false);
        MenuGraphics.round(g, plusVd.x, plusVd.y, plusVd.w, plusVd.h, plusVd.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("+", plusVd.x + plusVd.w / 2, plusVd.y + 4, LadsPalette.TEXT, false);

        itemY += 28;
        // Relay only
        g.drawText("Always Use Relay (Hide my IP - Cloudflare TURN)", x + 12, itemY + 6, LadsPalette.TEXT, false);
        Box relayToggle = new Box(x + w - 70, itemY + 2, 58, optH);
        if (relayToggle.y + relayToggle.h > sy && relayToggle.y < sy + sh) clickables.put("toggle:relayOnly", relayToggle);
        MenuGraphics.round(g, relayToggle.x, relayToggle.y, relayToggle.w, relayToggle.h,
                relayOnly ? LadsPalette.PRIMARY : LadsPalette.CARD);
        g.drawCenteredText(relayOnly ? "ON" : "OFF", relayToggle.x + relayToggle.w / 2, relayToggle.y + 4, LadsPalette.TEXT, false);

        itemY += 28;
        // Backup on open
        g.drawText("Backup World Before Opening", x + 12, itemY + 6, LadsPalette.TEXT, false);
        Box backupToggle = new Box(x + w - 70, itemY + 2, 58, optH);
        if (backupToggle.y + backupToggle.h > sy && backupToggle.y < sy + sh) clickables.put("toggle:backupOnOpen", backupToggle);
        MenuGraphics.round(g, backupToggle.x, backupToggle.y, backupToggle.w, backupToggle.h,
                backupOnOpen ? LadsPalette.PRIMARY : LadsPalette.CARD);
        g.drawCenteredText(backupOnOpen ? "ON" : "OFF", backupToggle.x + backupToggle.w / 2, backupToggle.y + 4, LadsPalette.TEXT, false);
    }

    /** Where a banned player's Unban button was last drawn (null: not on screen). */
    public Box unbanButtonBox(String uuid) {
        return clickables.get("unban:" + uuid);
    }

    private List<Banned> safeBanned() {
        List<Banned> b = callbacks.banned();
        return b != null ? b : List.of();
    }

    private void renderGuestsSection(LadsGraphics g, int x, int y, int w, int h, int mx, int my, int sy, int sh) {
        List<SocialBridge.GuestInfo> guests = safeGuests();
        List<Banned> banned = safeBanned();
        int totalH = 32 + Math.max(1, guests.size()) * 32 + 24 + Math.max(1, banned.size()) * 26;
        maxScroll = Math.max(0, totalH - (h - 12));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int itemY = y + 8 - scrollOffset;
        g.drawText("Connected Guests (" + guests.size() + ")", x + 12, itemY, LadsPalette.ACCENT, true);
        itemY += 16;

        if (guests.isEmpty()) {
            g.drawText("No guests currently connected.", x + 20, itemY + 10, LadsPalette.MUTED, false);
            itemY += 32;
        }

        for (SocialBridge.GuestInfo guest : guests) {
            if (itemY + 28 > sy && itemY < sy + sh) {
                Box rowB = new Box(x + 8, itemY, w - 16, 28);
                MenuGraphics.round(g, rowB.x, rowB.y, rowB.w, rowB.h, LadsPalette.CARD);

                String info = guest.name() + " (" + guest.gameMode() + ") " + guest.ping() + "ms" + (guest.op() ? " [OP]" : "");
                g.drawText(info, rowB.x + 8, itemY + 8, LadsPalette.TEXT, false);

                Map<SocialBridge.GuestAction, Box> actions = guestActionBoxes.computeIfAbsent(guest.id(), k -> new HashMap<>());
                int btnY = itemY + 4;
                int btnH = 20;
                int curX = rowB.x + rowB.w - 8;

                // Kick
                curX -= 36;
                Box kickB = new Box(curX, btnY, 34, btnH);
                actions.put(SocialBridge.GuestAction.KICK, kickB);
                MenuGraphics.round(g, kickB.x, kickB.y, kickB.w, kickB.h, kickB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                g.drawCenteredText("Kick", kickB.x + kickB.w / 2, kickB.y + 5, LadsPalette.ACCENT, false);

                // Ban
                curX -= 34;
                Box banB = new Box(curX, btnY, 32, btnH);
                actions.put(SocialBridge.GuestAction.BAN, banB);
                MenuGraphics.round(g, banB.x, banB.y, banB.w, banB.h, banB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                g.drawCenteredText("Ban", banB.x + banB.w / 2, banB.y + 5, LadsPalette.ACCENT, false);

                // Mute (Finding 8)
                curX -= 38;
                Box muteB = new Box(curX, btnY, 36, btnH);
                actions.put(SocialBridge.GuestAction.MUTE, muteB);
                MenuGraphics.round(g, muteB.x, muteB.y, muteB.w, muteB.h, muteB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                g.drawCenteredText("Mute", muteB.x + muteB.w / 2, muteB.y + 5, LadsPalette.TEXT, false);

                // Op / Deop
                curX -= 38;
                SocialBridge.GuestAction opAction = guest.op() ? SocialBridge.GuestAction.DEOP : SocialBridge.GuestAction.OP;
                Box opB = new Box(curX, btnY, 36, btnH);
                actions.put(opAction, opB);
                MenuGraphics.round(g, opB.x, opB.y, opB.w, opB.h, opB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                g.drawCenteredText(guest.op() ? "Deop" : "Op", opB.x + opB.w / 2, opB.y + 5, LadsPalette.TEXT, false);

                // GameMode cycle (Finding 8)
                curX -= 44;
                SocialBridge.GuestAction nextGm = getNextGameModeAction(guest.gameMode());
                Box gmB = new Box(curX, btnY, 42, btnH);
                actions.put(nextGm, gmB);
                MenuGraphics.round(g, gmB.x, gmB.y, gmB.w, gmB.h, gmB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                String gmLabel = guest.gameMode() != null && guest.gameMode().length() >= 4 ? guest.gameMode().substring(0, 4) : "Mode";
                g.drawCenteredText(gmLabel, gmB.x + gmB.w / 2, gmB.y + 5, LadsPalette.TEXT, false);

                // TP to me
                curX -= 30;
                Box tpB = new Box(curX, btnY, 28, btnH);
                actions.put(SocialBridge.GuestAction.TP_TO_ME, tpB);
                MenuGraphics.round(g, tpB.x, tpB.y, tpB.w, tpB.h, tpB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                g.drawCenteredText("TP", tpB.x + tpB.w / 2, tpB.y + 5, LadsPalette.TEXT, false);
            }

            itemY += 32;
        }

        // Banned from this world (lads-host.json): Unban lets them ask to join again
        itemY += 8;
        g.drawText("Banned (" + banned.size() + ")", x + 12, itemY, LadsPalette.ACCENT, true);
        itemY += 16;
        if (banned.isEmpty()) {
            g.drawText("Nobody is banned from this world.", x + 20, itemY + 6, LadsPalette.MUTED, false);
            return;
        }
        for (Banned b : banned) {
            if (itemY + 22 > sy && itemY < sy + sh) {
                MenuGraphics.round(g, x + 8, itemY, w - 16, 22, LadsPalette.CARD);
                g.drawText(b.name(), x + 16, itemY + 7, LadsPalette.TEXT, false);
                Box unbanB = new Box(x + w - 8 - 8 - 50, itemY + 2, 50, 18);
                clickables.put("unban:" + b.uuid(), unbanB);
                MenuGraphics.round(g, unbanB.x, unbanB.y, unbanB.w, unbanB.h, unbanB.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                g.drawCenteredText("Unban", unbanB.x + unbanB.w / 2, unbanB.y + 4, LadsPalette.TEXT, false);
            }
            itemY += 26;
        }
    }

    private SocialBridge.GuestAction getNextGameModeAction(String currentMode) {
        if ("survival".equalsIgnoreCase(currentMode)) return SocialBridge.GuestAction.CREATIVE;
        if ("creative".equalsIgnoreCase(currentMode)) return SocialBridge.GuestAction.ADVENTURE;
        if ("adventure".equalsIgnoreCase(currentMode)) return SocialBridge.GuestAction.SPECTATOR;
        return SocialBridge.GuestAction.SURVIVAL;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;

        // Done button
        if (doneBtn != null && doneBtn.has(x, y)) {
            close();
            return true;
        }

        // Host toggle button
        if (hostToggleBtn != null && hostToggleBtn.has(x, y)) {
            toggleHosting();
            return true;
        }

        // Section Tabs
        for (Map.Entry<Section, Box> entry : sectionBoxes.entrySet()) {
            if (entry.getValue().has(x, y)) {
                setSection(entry.getKey());
                return true;
            }
        }

        // Welcome input box
        if (welcomeInputBox != null && welcomeInputBox.has(x, y)) {
            typingWelcome = true;
            return true;
        } else {
            typingWelcome = false;
        }

        boolean inScissor = y >= scissorY && y <= scissorY + scissorH;
        if (inScissor) {
            // General / Gameplay / Advanced clickables
            for (Map.Entry<String, Box> entry : clickables.entrySet()) {
                if (entry.getValue().has(x, y)) {
                    handleClick(entry.getKey());
                    return true;
                }
            }

            // Guest actions
            for (Map.Entry<UUID, Map<SocialBridge.GuestAction, Box>> ge : guestActionBoxes.entrySet()) {
                UUID gId = ge.getKey();
                for (Map.Entry<SocialBridge.GuestAction, Box> ae : ge.getValue().entrySet()) {
                    if (ae.getValue().has(x, y)) {
                        triggerGuestAction(gId, ae.getKey());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void handleClick(String key) {
        if (key.startsWith("unban:")) {
            callbacks.unban(key.substring("unban:".length()));
        } else if (key.startsWith("scope:")) {
            setScope(HostOptions.Scope.valueOf(key.substring("scope:".length())));
        } else if (key.startsWith("approval:")) {
            setApproval(HostOptions.Approval.valueOf(key.substring("approval:".length())));
        } else if (key.equals("maxPlayers:minus")) {
            setMaxPlayers(maxPlayers - 1);
        } else if (key.equals("maxPlayers:plus")) {
            setMaxPlayers(maxPlayers + 1);
        } else if (key.equals("toggle:showJoinable")) {
            setShowJoinable(!showJoinable);
        } else if (key.startsWith("gamemode:")) {
            setGuestGameMode(key.substring("gamemode:".length()));
        } else if (key.startsWith("op:")) {
            setGuestOpLevel(Integer.parseInt(key.substring("op:".length())));
        } else if (key.equals("toggle:pvp")) {
            setPvp(!pvp);
        } else if (key.startsWith("diff:")) {
            String d = key.substring("diff:".length());
            setDifficulty("keep".equals(d) ? null : d);
        } else if (key.startsWith("rule:")) {
            String rule = key.substring("rule:".length());
            boolean current = "true".equalsIgnoreCase(gamerules.get(rule));
            setGamerule(rule, current ? "false" : "true");
        } else if (key.equals("viewDistance:minus")) {
            setViewDistanceCap(viewDistanceCap <= 2 ? 0 : viewDistanceCap - 2);
        } else if (key.equals("viewDistance:plus")) {
            setViewDistanceCap(viewDistanceCap == 0 ? 2 : Math.min(32, viewDistanceCap + 2));
        } else if (key.equals("toggle:relayOnly")) {
            setRelayOnly(!relayOnly);
        } else if (key.equals("toggle:backupOnOpen")) {
            setBackupOnOpen(!backupOnOpen);
        }
    }

    public boolean charTyped(char c) {
        if (typingWelcome) {
            if (c >= 32 && c != 127 && welcome.length() < 256) {
                welcome += c;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape
        if (keyCode == 256 || keyCode == 1) {
            close();
            return true;
        }

        // Backspace
        if ((keyCode == 259 || keyCode == 14) && typingWelcome && !welcome.isEmpty()) {
            welcome = welcome.substring(0, welcome.length() - 1);
            return true;
        }

        // Enter
        if (keyCode == 257 || keyCode == 28) {
            typingWelcome = false;
            return true;
        }

        return false;
    }

    public void close() {
        callbacks.saveOptions(buildOptions());
        callbacks.onClose();
    }
}

package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.ConfirmDialog;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.gui.MenuGraphics;
import com.thelads.core.client.social.FriendEntry;
import com.thelads.core.client.social.Presence;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.SocialProtocol;
import com.thelads.core.client.social.SocialState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Common GUI logic for the Friends screen (tabs Friends/Requests/Blocked/Add, status line,
 * version/loader/pack, Join/Invite/Message, DM pane, scrolling, real nickname editing),
 * drawn through MenuGraphics / LadsGraphics.
 */
public final class FriendsScreenLogic {

    public record Box(int x, int y, int w, int h) {
        public boolean has(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    public record JoinCheck(boolean ok, String reason) {}
    public record DmEntry(UUID from, String text, long timestamp) {}

    public enum Tab {
        FRIENDS("Friends"),
        REQUESTS("Requests"),
        BLOCKED("Blocked"),
        ADD("Add Friend");

        public final String title;
        Tab(String title) { this.title = title; }
    }

    private static final Tab[] TABS = Tab.values();

    public interface Callbacks {
        List<FriendEntry> friends();
        default List<FriendEntry> incomingRequests() { return List.of(); }
        default List<FriendEntry> outgoingRequests() { return List.of(); }
        default List<FriendEntry> blocked() { return List.of(); }
        default JoinCheck canJoin(FriendEntry friend) { return new JoinCheck(true, ""); }
        default void addFriend(String name) {}
        default void acceptRequest(UUID id) {}
        default void declineRequest(UUID id) {}
        default void removeFriend(UUID id) {}
        default void blockFriend(UUID id) {}
        default void unblockFriend(UUID id) {}
        default void setNick(UUID id, String nick) {}
        default void sendDm(UUID id, String text) {}
        default List<DmEntry> dms(UUID id) { return List.of(); }
        default void invite(UUID id) {}
        default void requestJoin(UUID id) {}
        /** "Leave X and join Y?" when joining would leave a world or server first; null = join at once. */
        default String joinQuestion(UUID id) { return null; }
        default String connectionStatus() { return "Connected"; }
        default boolean showServer() { return true; }
        default void setShowServer(boolean show) {}
        default boolean appearOffline() { return false; }
        default void setAppearOffline(boolean appearOffline) {}
        default void onClose() {}
    }

    private final Callbacks callbacks;
    private final ConfirmDialog confirm = new ConfirmDialog();
    private Tab currentTab = Tab.FRIENDS;

    // DM pane state
    private FriendEntry activeDmFriend = null;
    private String dmInput = "";
    private int prevDmCount = 0;

    // Add friend input
    private String addFriendInput = "";
    private String addNotice = "";

    // Nickname editing
    private UUID editingNickId = null;
    private String nickInput = "";
    private Box nickSaveBtn;
    private Box nickCancelBtn;

    // Scroll offsets and scissor bounds
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int dmScrollOffset = 0;
    private int maxDmScroll = 0;
    private boolean stickDmToBottom = true;
    private int scissorY = 0;
    private int scissorH = Integer.MAX_VALUE;

    // Cached sorted friends
    private List<FriendEntry> lastRawFriends = null;
    private List<FriendEntry> cachedSortedFriends = Collections.emptyList();

    // Layout hit-boxes
    private final Map<Tab, Box> tabBoxes = new HashMap<>();
    private final Map<UUID, Box> joinBoxes = new HashMap<>();
    private final Map<UUID, Box> inviteBoxes = new HashMap<>();
    private final Map<UUID, Box> messageBoxes = new HashMap<>();
    private final Map<UUID, Box> moreBoxes = new HashMap<>();
    private final Map<UUID, Box> acceptBoxes = new HashMap<>();
    private final Map<UUID, Box> declineBoxes = new HashMap<>();
    private final Map<UUID, Box> unblockBoxes = new HashMap<>();
    private final Map<UUID, String> joinReasons = new HashMap<>();
    private final Map<UUID, Boolean> joinEnabledMap = new HashMap<>();

    private Box doneBtn;
    private Box showServerBtn;
    private Box appearOfflineBtn;
    private Box addSendBtn;
    private Box dmSendBtn;
    private Box dmBackBtn;
    private Box addInputBox;
    private Box dmInputBox;
    private Box nickInputBox;

    private boolean typingAddInput = false;
    private boolean typingDmInput = false;
    private boolean typingNickInput = false;

    // More popup for a friend
    private UUID popupFriendId = null;
    private Box popupNickBtn;
    private Box popupRemoveBtn;
    private Box popupBlockBtn;

    // Deferred tooltip
    private Runnable deferredTooltip = null;

    public FriendsScreenLogic(Callbacks callbacks) {
        this.callbacks = callbacks != null ? callbacks : new Callbacks() {
            @Override public List<FriendEntry> friends() { return List.of(); }
        };
    }

    /** The game's Friends screen: every callback goes to SocialState.get(); per-version screens only forward render and input. */
    public static FriendsScreenLogic social(Runnable onClose) {
        SocialState s = SocialState.get();
        FriendsScreenLogic logic = new FriendsScreenLogic(new Callbacks() {
            @Override public List<FriendEntry> friends() { return s.friends(); }
            @Override public List<FriendEntry> incomingRequests() { return s.incoming(); }
            @Override public List<FriendEntry> outgoingRequests() { return s.outgoing(); }
            @Override public List<FriendEntry> blocked() { return s.blocked(); }
            @Override public JoinCheck canJoin(FriendEntry friend) {
                SocialState.JoinCheck check = s.canJoin(friend);
                return new JoinCheck(check.ok(), check.reason());
            }
            @Override public void addFriend(String name) { s.addFriend(name); }
            @Override public void acceptRequest(UUID id) { s.accept(id); }
            @Override public void declineRequest(UUID id) { s.decline(id); }
            @Override public void removeFriend(UUID id) { s.remove(id); }
            @Override public void blockFriend(UUID id) { s.block(id); }
            @Override public void unblockFriend(UUID id) { s.unblock(id); }
            @Override public void setNick(UUID id, String nick) { s.nick(id, nick); }
            @Override public void sendDm(UUID id, String text) { s.sendDm(id, text); }
            @Override public List<DmEntry> dms(UUID id) {
                List<SocialState.Dm> dms = s.dms(id);
                List<DmEntry> out = new ArrayList<>(dms.size());
                for (SocialState.Dm dm : dms) out.add(new DmEntry(dm.from(), dm.text(), dm.ts()));
                return out;
            }
            @Override public void invite(UUID id) { s.invite(id); }
            @Override public void requestJoin(UUID id) { s.requestJoin(id); }
            @Override public String joinQuestion(UUID id) { return s.joinQuestion(id); }
            @Override public String connectionStatus() { return s.connectionStatus(); }
            @Override public boolean showServer() { return s.showServer(); }
            @Override public void setShowServer(boolean show) { s.setShowServer(show); }
            @Override public boolean appearOffline() { return s.appearOffline(); }
            @Override public void setAppearOffline(boolean appearOffline) { s.setAppearOffline(appearOffline); }
            @Override public void onClose() { onClose.run(); }
        });
        FriendEntry dm = s.takeDmTarget();
        if (dm != null) logic.openDm(dm);
        SocialState.SwitchConfirm pending = s.takePendingSwitch();
        if (pending != null) logic.confirmSwitch(pending.question(), pending.go());
        return logic;
    }

    /** The Lads confirm over the screen: Confirm runs go (leave, then join), Cancel keeps everything. */
    public void confirmSwitch(String question, Runnable go) {
        confirm.open("Switch worlds?", question, go);
    }

    public Tab tab() { return currentTab; }
    public void setTab(Tab tab) {
        this.currentTab = tab != null ? tab : Tab.FRIENDS;
        this.popupFriendId = null;
        this.editingNickId = null;
        this.typingNickInput = false;
        this.scrollOffset = 0;
    }

    public FriendEntry activeDmFriend() { return activeDmFriend; }
    public void openDm(FriendEntry friend) {
        this.activeDmFriend = friend;
        this.dmInput = "";
        this.typingDmInput = true;
        this.dmScrollOffset = 0;
        this.stickDmToBottom = true;
        this.prevDmCount = safeDms(friend != null ? friend.id() : null).size();
    }
    public void closeDm() {
        this.activeDmFriend = null;
        this.dmInput = "";
        this.typingDmInput = false;
    }

    public String dmInput() { return dmInput; }
    public void setDmInput(String input) {
        if (input != null && input.length() > SocialProtocol.MAX_DM) {
            input = input.substring(0, SocialProtocol.MAX_DM);
        }
        this.dmInput = input != null ? input : "";
    }

    public String addFriendInput() { return addFriendInput; }
    public void setAddFriendInput(String input) {
        if (input != null && input.length() > 16) {
            input = input.substring(0, 16);
        }
        this.addFriendInput = input != null ? input : "";
    }

    public int scissorY() { return scissorY; }
    public int scissorH() { return scissorH; }

    public void sendDm() {
        if (activeDmFriend != null && !dmInput.trim().isEmpty()) {
            callbacks.sendDm(activeDmFriend.id(), dmInput.trim());
            dmInput = "";
            stickDmToBottom = true;
        }
    }

    // Nickname helpers
    public UUID editingNickId() { return editingNickId; }
    public void startEditingNick(UUID friendId) {
        this.editingNickId = friendId;
        FriendEntry f = findFriend(friendId);
        this.nickInput = (f != null && f.nick() != null) ? f.nick() : "";
        this.typingNickInput = true;
        this.popupFriendId = null;
    }
    public String nickInput() { return nickInput; }
    public void setNickInput(String input) {
        if (input != null && input.length() > 32) input = input.substring(0, 32);
        this.nickInput = input != null ? input : "";
    }
    public void commitNick() {
        if (editingNickId != null) {
            String trimmed = nickInput.trim();
            callbacks.setNick(editingNickId, trimmed.isEmpty() ? null : trimmed);
            editingNickId = null;
            typingNickInput = false;
            nickInput = "";
        }
    }
    public void cancelNick() {
        editingNickId = null;
        typingNickInput = false;
        nickInput = "";
    }

    private FriendEntry findFriend(UUID id) {
        if (id == null) return null;
        for (FriendEntry f : safeFriends()) {
            if (id.equals(f.id())) return f;
        }
        return null;
    }

    // Scrolling getters
    public int scrollOffset() { return scrollOffset; }
    public int maxScroll() { return maxScroll; }
    public int dmScrollOffset() { return dmScrollOffset; }
    public int maxDmScroll() { return maxDmScroll; }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (confirm.isOpen()) return true;
        int delta = (int) (amount * 20);
        if (activeDmFriend != null) {
            int prev = dmScrollOffset;
            dmScrollOffset = Math.max(0, Math.min(maxDmScroll, dmScrollOffset - delta));
            // If scrolled up from bottom, don't force stick to bottom
            if (dmScrollOffset < maxDmScroll) {
                stickDmToBottom = false;
            } else if (dmScrollOffset == maxDmScroll) {
                stickDmToBottom = true;
            }
            return prev != dmScrollOffset;
        } else {
            int prev = scrollOffset;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta));
            return prev != scrollOffset;
        }
    }

    public List<FriendEntry> sortedFriends() {
        List<FriendEntry> raw = safeFriends();
        if (lastRawFriends != null && isSameFriends(raw, lastRawFriends)) {
            return cachedSortedFriends;
        }
        List<FriendEntry> list = new ArrayList<>(raw);
        list.sort((a, b) -> {
            boolean aOnline = a.presence() != null && a.presence().online();
            boolean bOnline = b.presence() != null && b.presence().online();
            if (aOnline != bOnline) {
                return aOnline ? -1 : 1; // online first
            }
            String nameA = a.displayName() != null ? a.displayName() : "";
            String nameB = b.displayName() != null ? b.displayName() : "";
            return nameA.compareToIgnoreCase(nameB);
        });
        lastRawFriends = new ArrayList<>(raw);
        cachedSortedFriends = Collections.unmodifiableList(list);
        return cachedSortedFriends;
    }

    private static boolean isSameFriends(List<FriendEntry> a, List<FriendEntry> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        int size = a.size();
        if (size != b.size()) return false;
        for (int i = 0; i < size; i++) {
            if (!Objects.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    // Defensive null-safe getters
    private List<FriendEntry> safeFriends() {
        List<FriendEntry> r = callbacks.friends();
        return r != null ? r : List.of();
    }
    private List<FriendEntry> safeIncoming() {
        List<FriendEntry> r = callbacks.incomingRequests();
        return r != null ? r : List.of();
    }
    private List<FriendEntry> safeOutgoing() {
        List<FriendEntry> r = callbacks.outgoingRequests();
        return r != null ? r : List.of();
    }
    private List<FriendEntry> safeBlocked() {
        List<FriendEntry> r = callbacks.blocked();
        return r != null ? r : List.of();
    }
    private List<DmEntry> safeDms(UUID id) {
        if (id == null) return List.of();
        List<DmEntry> r = callbacks.dms(id);
        return r != null ? r : List.of();
    }

    public boolean isJoinEnabled(UUID friendId) {
        return Boolean.TRUE.equals(joinEnabledMap.get(friendId));
    }

    public String joinReason(UUID friendId) {
        return joinReasons.get(friendId);
    }

    public Box joinButtonBox(UUID friendId) {
        return joinBoxes.get(friendId);
    }

    public Box addInputBox() {
        return addInputBox;
    }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();

        // Clear hit-boxes
        tabBoxes.clear();
        joinBoxes.clear();
        inviteBoxes.clear();
        messageBoxes.clear();
        moreBoxes.clear();
        acceptBoxes.clear();
        declineBoxes.clear();
        unblockBoxes.clear();
        joinReasons.clear();
        joinEnabledMap.clear();
        deferredTooltip = null;

        // Background
        g.fill(0, 0, width, height, 0xD0070709);

        // Header panel
        int panelW = Math.min(width - 32, 540);
        int panelX = (width - panelW) / 2;
        int topY = 12;

        // Title & status
        g.drawText("Friends", panelX, topY + 4, LadsPalette.TEXT, true);
        String statusText = "Status: " + (callbacks.connectionStatus() != null ? callbacks.connectionStatus() : "Unknown");
        int statusColor = "Connected".equalsIgnoreCase(callbacks.connectionStatus()) ? LadsPalette.CARD_ON_GLOW : LadsPalette.MUTED;
        g.drawText(statusText, panelX + 70, topY + 4, statusColor, false);

        // Header action buttons: Show Server & Appear Offline
        int optW = 105;
        int optH = 18;
        showServerBtn = new Box(panelX + panelW - 2 * optW - 10, topY + 2, optW, optH);
        appearOfflineBtn = new Box(panelX + panelW - optW, topY + 2, optW, optH);

        boolean ssHover = showServerBtn.has(mouseX, mouseY);
        MenuGraphics.round(g, showServerBtn.x, showServerBtn.y, showServerBtn.w, showServerBtn.h,
                ssHover ? LadsPalette.HOVER : LadsPalette.PANEL);
        String ssLabel = "Server: " + (callbacks.showServer() ? "Shown" : "Hidden");
        g.drawCenteredText(ssLabel, showServerBtn.x + showServerBtn.w / 2, showServerBtn.y + 5,
                callbacks.showServer() ? LadsPalette.TEXT : LadsPalette.MUTED, false);

        boolean aoHover = appearOfflineBtn.has(mouseX, mouseY);
        MenuGraphics.round(g, appearOfflineBtn.x, appearOfflineBtn.y, appearOfflineBtn.w, appearOfflineBtn.h,
                aoHover ? LadsPalette.HOVER : LadsPalette.PANEL);
        String aoLabel = callbacks.appearOffline() ? "Appear Offline" : "Online";
        g.drawCenteredText(aoLabel, appearOfflineBtn.x + appearOfflineBtn.w / 2, appearOfflineBtn.y + 5,
                callbacks.appearOffline() ? LadsPalette.ACCENT : LadsPalette.TEXT, false);

        // Tab bar
        int tabY = topY + 26;
        int tabW = (panelW - (TABS.length - 1) * 4) / TABS.length;
        int tabH = 22;
        for (int i = 0; i < TABS.length; i++) {
            Tab t = TABS[i];
            int tx = panelX + i * (tabW + 4);
            Box b = new Box(tx, tabY, tabW, tabH);
            tabBoxes.put(t, b);
            boolean active = (t == currentTab);
            boolean hover = b.has(mouseX, mouseY);
            int tabBg = active ? LadsPalette.PRIMARY : (hover ? LadsPalette.HOVER : LadsPalette.CARD);
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, tabBg);

            String title = t.title;
            if (t == Tab.REQUESTS) {
                int reqCount = safeIncoming().size();
                if (reqCount > 0) title += " (" + reqCount + ")";
            }
            g.drawCenteredText(title, b.x + b.w / 2, b.y + (b.h - g.fontHeight()) / 2 + 1,
                    active ? LadsPalette.TEXT : LadsPalette.MUTED, active);
        }

        // Content Area
        int contentY = tabY + tabH + 8;
        int contentH = height - contentY - 36;
        MenuGraphics.round(g, panelX, contentY, panelW, contentH, LadsPalette.PANEL);

        if (activeDmFriend != null) {
            renderDmPane(g, panelX, contentY, panelW, contentH, mouseX, mouseY);
        } else {
            switch (currentTab) {
                case FRIENDS -> renderFriendsTab(g, panelX, contentY, panelW, contentH, mouseX, mouseY);
                case REQUESTS -> renderRequestsTab(g, panelX, contentY, panelW, contentH, mouseX, mouseY);
                case BLOCKED -> renderBlockedTab(g, panelX, contentY, panelW, contentH, mouseX, mouseY);
                case ADD -> renderAddTab(g, panelX, contentY, panelW, contentH, mouseX, mouseY);
            }
        }

        // Footer Done button (always drawn on top, outside scissor)
        int doneW = 100, doneH = 20;
        doneBtn = new Box(panelX + panelW - doneW, height - 28, doneW, doneH);
        boolean doneHover = doneBtn.has(mouseX, mouseY);
        MenuGraphics.round(g, doneBtn.x, doneBtn.y, doneBtn.w, doneBtn.h, doneHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Done", doneBtn.x + doneBtn.w / 2, doneBtn.y + 6, LadsPalette.TEXT, false);

        // Render popup menu if open
        if (popupFriendId != null) {
            renderMorePopup(g, mouseX, mouseY);
        }

        // Render deferred tooltip so it draws floating on top of everything
        if (deferredTooltip != null) {
            deferredTooltip.run();
        }
        confirm.render(g, mouseX, mouseY); // modal, over everything
    }

    private void renderFriendsTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<FriendEntry> friends = sortedFriends();
        if (friends.isEmpty()) {
            g.drawCenteredText("No friends yet. Click 'Add Friend' to connect!", x + w / 2, y + h / 2 - 5, LadsPalette.MUTED, false);
            maxScroll = 0;
            return;
        }

        int rowH = 34;
        int spacing = 4;
        int totalContentH = friends.size() * (rowH + spacing) + 8;
        int viewH = h - 12;
        maxScroll = Math.max(0, totalContentH - viewH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int scissorY = y + 6;
        int scissorH = viewH;
        this.scissorY = scissorY;
        this.scissorH = scissorH;
        g.enableScissor(x, scissorY, w, scissorH);

        int itemY = y + 6 - scrollOffset;

        for (int i = 0; i < friends.size(); i++) {
            FriendEntry friend = friends.get(i);
            int rowTop = itemY;
            int rowBottom = itemY + rowH;

            // Only register and draw if at least partly in scissor view
            boolean visible = rowBottom > scissorY && rowTop < scissorY + scissorH;

            if (visible) {
                Box rowBox = new Box(x + 6, rowTop, w - 12, rowH);
                boolean rowHover = rowBox.has(mx, my) && my >= scissorY && my <= scissorY + scissorH;
                MenuGraphics.round(g, rowBox.x, rowBox.y, rowBox.w, rowBox.h, rowHover ? LadsPalette.HOVER : LadsPalette.CARD);

                // Online indicator dot
                boolean online = friend.presence() != null && friend.presence().online();
                int dotColor = online ? LadsPalette.CARD_ON_GLOW : LadsPalette.DISABLED;
                g.fill(rowBox.x + 8, rowTop + 14, rowBox.x + 14, rowTop + 20, dotColor);

                // Name or inline nickname input
                if (friend.id().equals(editingNickId)) {
                    int nickW = 120, nickH = 16;
                    nickInputBox = new Box(rowBox.x + 20, rowTop + 4, nickW, nickH);
                    MenuGraphics.round(g, nickInputBox.x, nickInputBox.y, nickInputBox.w, nickInputBox.h, LadsPalette.PANEL);
                    g.fill(nickInputBox.x, nickInputBox.y + nickH - 1, nickInputBox.x + nickW, nickInputBox.y + nickH, LadsPalette.ACCENT);

                    String disp = nickInput.isEmpty() ? (typingNickInput ? "_" : "Nick...") : nickInput + (typingNickInput ? "_" : "");
                    g.drawText(MenuGraphics.fit(g, disp, nickW - 6), nickInputBox.x + 4, nickInputBox.y + 4, LadsPalette.TEXT, false);

                    nickSaveBtn = new Box(nickInputBox.x + nickW + 3, rowTop + 4, 30, nickH);
                    nickCancelBtn = new Box(nickSaveBtn.x + nickSaveBtn.w + 2, rowTop + 4, 30, nickH);

                    MenuGraphics.round(g, nickSaveBtn.x, nickSaveBtn.y, nickSaveBtn.w, nickSaveBtn.h,
                            nickSaveBtn.has(mx, my) ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
                    g.drawCenteredText("Save", nickSaveBtn.x + nickSaveBtn.w / 2, nickSaveBtn.y + 4, LadsPalette.TEXT, false);

                    MenuGraphics.round(g, nickCancelBtn.x, nickCancelBtn.y, nickCancelBtn.w, nickCancelBtn.h,
                            nickCancelBtn.has(mx, my) ? LadsPalette.HOVER : LadsPalette.PANEL);
                    g.drawCenteredText("Esc", nickCancelBtn.x + nickCancelBtn.w / 2, nickCancelBtn.y + 4, LadsPalette.MUTED, false);
                } else {
                    String nameStr = friend.displayName();
                    if (friend.nick() != null && !friend.nick().isEmpty() && !friend.nick().equals(friend.name())) {
                        nameStr = friend.nick() + " (" + friend.name() + ")";
                    }
                    g.drawText(MenuGraphics.fit(g, nameStr, 160), rowBox.x + 20, rowTop + 6, LadsPalette.TEXT, false);
                }

                // Status string including loader and pack if present (Finding 2)
                String status = formatPresence(friend.presence());
                String gameInfo = formatGameInfo(friend.presence() != null ? friend.presence().game() : null);
                int maxStatusW = Math.max(120, rowBox.w - 200);
                if (!gameInfo.isEmpty()) {
                    String fullStatus = status + " • " + gameInfo;
                    g.drawText(MenuGraphics.fit(g, fullStatus, maxStatusW), rowBox.x + 20, rowTop + 18, LadsPalette.MUTED, false);
                } else {
                    g.drawText(MenuGraphics.fit(g, status, maxStatusW), rowBox.x + 20, rowTop + 18, LadsPalette.MUTED, false);
                }

                // Action buttons on right
                int btnH = 20;
                int btnY = rowTop + 7;
                int currX = rowBox.x + rowBox.w - 8;

                // More [...] button
                currX -= 24;
                Box moreB = new Box(currX, btnY, 24, btnH);
                moreBoxes.put(friend.id(), moreB);
                boolean moreHov = moreB.has(mx, my) && my >= scissorY && my <= scissorY + scissorH;
                MenuGraphics.round(g, moreB.x, moreB.y, moreB.w, moreB.h, moreHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PANEL);
                g.drawCenteredText("...", moreB.x + moreB.w / 2, moreB.y + 4, LadsPalette.TEXT, false);

                // Message button
                currX -= 54;
                Box msgB = new Box(currX, btnY, 50, btnH);
                messageBoxes.put(friend.id(), msgB);
                boolean msgHov = msgB.has(mx, my) && my >= scissorY && my <= scissorY + scissorH;
                MenuGraphics.round(g, msgB.x, msgB.y, msgB.w, msgB.h, msgHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PANEL);
                g.drawCenteredText("Chat", msgB.x + msgB.w / 2, msgB.y + 5, LadsPalette.TEXT, false);

                // Invite button
                currX -= 54;
                Box invB = new Box(currX, btnY, 50, btnH);
                inviteBoxes.put(friend.id(), invB);
                boolean invHov = invB.has(mx, my) && my >= scissorY && my <= scissorY + scissorH;
                MenuGraphics.round(g, invB.x, invB.y, invB.w, invB.h, invHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PANEL);
                g.drawCenteredText("Invite", invB.x + invB.w / 2, invB.y + 5, LadsPalette.TEXT, false);

                // Join button
                JoinCheck check = callbacks.canJoin(friend);
                joinReasons.put(friend.id(), check.reason());
                joinEnabledMap.put(friend.id(), check.ok());

                currX -= 54;
                Box jnB = new Box(currX, btnY, 50, btnH);
                joinBoxes.put(friend.id(), jnB);
                boolean jnHov = jnB.has(mx, my) && my >= scissorY && my <= scissorY + scissorH;

                int joinColor = !check.ok() ? LadsPalette.DISABLED : (jnHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
                MenuGraphics.round(g, jnB.x, jnB.y, jnB.w, jnB.h, joinColor);
                g.drawCenteredText("Join", jnB.x + jnB.w / 2, jnB.y + 5, check.ok() ? LadsPalette.TEXT : LadsPalette.MUTED, false);

                // Finding 4: Incompatible reason only as a hover tooltip on the disabled Join button!
                if (!check.ok() && jnHov && check.reason() != null && !check.reason().isEmpty()) {
                    final String reasonText = check.reason();
                    final int jx = jnB.x, jy = jnB.y, jw = jnB.w;
                    deferredTooltip = () -> {
                        int tipW = g.textWidth(reasonText) + 12;
                        int tipH = 16;
                        int tipX = Math.max(10, jx + jw / 2 - tipW / 2);
                        int tipY = jy - tipH - 3;
                        if (tipY < 10) tipY = jy + btnH + 3;
                        MenuGraphics.round(g, tipX, tipY, tipW, tipH, LadsPalette.PANEL);
                        g.fill(tipX, tipY, tipX + tipW, tipY + 1, LadsPalette.ACCENT);
                        g.drawCenteredText(reasonText, tipX + tipW / 2, tipY + 4, LadsPalette.TEXT, false);
                    };
                }
            }

            itemY += rowH + spacing;
        }

        g.disableScissor();
    }

    private void renderRequestsTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<FriendEntry> incoming = safeIncoming();
        List<FriendEntry> outgoing = safeOutgoing();

        int viewH = h - 12;
        int totalH = 40 + incoming.size() * 30 + 40 + outgoing.size() * 28;
        maxScroll = Math.max(0, totalH - viewH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int scissorY = y + 6;
        int scissorH = viewH;
        this.scissorY = scissorY;
        this.scissorH = scissorH;
        g.enableScissor(x, scissorY, w, viewH);

        int itemY = y + 8 - scrollOffset;
        g.drawText("Incoming Friend Requests (" + incoming.size() + ")", x + 10, itemY, LadsPalette.ACCENT, true);
        itemY += 16;

        if (incoming.isEmpty()) {
            g.drawText("No incoming requests.", x + 20, itemY, LadsPalette.MUTED, false);
            itemY += 16;
        } else {
            for (FriendEntry req : incoming) {
                if (itemY + 26 > scissorY && itemY < scissorY + viewH) {
                    Box rBox = new Box(x + 10, itemY, w - 20, 26);
                    MenuGraphics.round(g, rBox.x, rBox.y, rBox.w, rBox.h, LadsPalette.CARD);
                    g.drawText(req.name(), rBox.x + 10, itemY + 8, LadsPalette.TEXT, false);

                    int btnW = 55, btnH = 18, btnY = itemY + 4;
                    Box accB = new Box(rBox.x + rBox.w - 125, btnY, btnW, btnH);
                    Box decB = new Box(rBox.x + rBox.w - 65, btnY, btnW, btnH);
                    acceptBoxes.put(req.id(), accB);
                    declineBoxes.put(req.id(), decB);

                    boolean accHov = accB.has(mx, my);
                    boolean decHov = decB.has(mx, my);
                    MenuGraphics.round(g, accB.x, accB.y, accB.w, accB.h, accHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
                    g.drawCenteredText("Accept", accB.x + accB.w / 2, accB.y + 5, LadsPalette.TEXT, false);

                    MenuGraphics.round(g, decB.x, decB.y, decB.w, decB.h, decHov ? LadsPalette.HOVER : LadsPalette.PANEL);
                    g.drawCenteredText("Decline", decB.x + decB.w / 2, decB.y + 5, LadsPalette.MUTED, false);
                }
                itemY += 30;
            }
        }

        itemY += 10;
        g.drawText("Outgoing Requests (" + outgoing.size() + ")", x + 10, itemY, LadsPalette.ACCENT, true);
        itemY += 16;

        if (outgoing.isEmpty()) {
            g.drawText("No outgoing requests.", x + 20, itemY, LadsPalette.MUTED, false);
        } else {
            for (FriendEntry req : outgoing) {
                if (itemY + 24 > scissorY && itemY < scissorY + viewH) {
                    Box rBox = new Box(x + 10, itemY, w - 20, 24);
                    MenuGraphics.round(g, rBox.x, rBox.y, rBox.w, rBox.h, LadsPalette.CARD);
                    g.drawText(req.name(), rBox.x + 10, itemY + 7, LadsPalette.TEXT, false);
                    g.drawText("Pending...", rBox.x + rBox.w - 70, itemY + 7, LadsPalette.MUTED, false);
                }
                itemY += 28;
            }
        }

        g.disableScissor();
    }

    private void renderBlockedTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<FriendEntry> blocked = safeBlocked();
        int viewH = h - 12;
        int totalH = 30 + blocked.size() * 30;
        maxScroll = Math.max(0, totalH - viewH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int scissorY = y + 6;
        int scissorH = viewH;
        this.scissorY = scissorY;
        this.scissorH = scissorH;
        g.enableScissor(x, scissorY, w, viewH);

        int itemY = y + 8 - scrollOffset;
        g.drawText("Blocked Players (" + blocked.size() + ")", x + 10, itemY, LadsPalette.ACCENT, true);
        itemY += 18;

        if (blocked.isEmpty()) {
            g.drawText("No blocked players.", x + 20, itemY, LadsPalette.MUTED, false);
        } else {
            for (FriendEntry b : blocked) {
                if (itemY + 26 > scissorY && itemY < scissorY + viewH) {
                    Box rBox = new Box(x + 10, itemY, w - 20, 26);
                    MenuGraphics.round(g, rBox.x, rBox.y, rBox.w, rBox.h, LadsPalette.CARD);
                    g.drawText(b.name(), rBox.x + 10, itemY + 8, LadsPalette.TEXT, false);

                    int btnW = 65, btnH = 18, btnY = itemY + 4;
                    Box unbB = new Box(rBox.x + rBox.w - 75, btnY, btnW, btnH);
                    unblockBoxes.put(b.id(), unbB);

                    boolean unbHov = unbB.has(mx, my);
                    MenuGraphics.round(g, unbB.x, unbB.y, unbB.w, unbB.h, unbHov ? LadsPalette.HOVER : LadsPalette.PANEL);
                    g.drawCenteredText("Unblock", unbB.x + unbB.w / 2, unbB.y + 5, LadsPalette.TEXT, false);
                }
                itemY += 30;
            }
        }

        g.disableScissor();
    }

    private void renderAddTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        maxScroll = 0;
        int centerX = x + w / 2;
        int itemY = y + 25;

        g.drawCenteredText("Add Friend by Minecraft Name", centerX, itemY, LadsPalette.TEXT, true);
        itemY += 18;
        g.drawCenteredText("Enter the exact username of the player you want to add.", centerX, itemY, LadsPalette.MUTED, false);
        itemY += 25;

        int inputW = 220, inputH = 24;
        addInputBox = new Box(centerX - inputW / 2, itemY, inputW, inputH);
        MenuGraphics.round(g, addInputBox.x, addInputBox.y, addInputBox.w, addInputBox.h,
                typingAddInput ? LadsPalette.HOVER : LadsPalette.CARD);

        String displayInput = addFriendInput.isEmpty() ? (typingAddInput ? "_" : "Username...") : addFriendInput + (typingAddInput ? "_" : "");
        int inputColor = addFriendInput.isEmpty() && !typingAddInput ? LadsPalette.MUTED : LadsPalette.TEXT;
        g.drawCenteredText(displayInput, centerX, addInputBox.y + 7, inputColor, false);

        itemY += 32;
        int btnW = 120, btnH = 22;
        addSendBtn = new Box(centerX - btnW / 2, itemY, btnW, btnH);
        boolean btnHov = addSendBtn.has(mx, my);
        MenuGraphics.round(g, addSendBtn.x, addSendBtn.y, addSendBtn.w, addSendBtn.h,
                btnHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Send Request", centerX, addSendBtn.y + 6, LadsPalette.TEXT, false);

        if (!addNotice.isEmpty()) {
            itemY += 26;
            g.drawCenteredText(addNotice, centerX, itemY, LadsPalette.CARD_ON_GLOW, false);
        }
    }

    private void renderDmPane(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        int headerH = 26;
        MenuGraphics.round(g, x, y, w, headerH, LadsPalette.CARD);
        g.drawText("Direct Messages: " + activeDmFriend.displayName(), x + 10, y + 8, LadsPalette.TEXT, true);

        int backW = 55, backH = 18;
        dmBackBtn = new Box(x + w - backW - 6, y + 4, backW, backH);
        boolean backHov = dmBackBtn.has(mx, my);
        MenuGraphics.round(g, dmBackBtn.x, dmBackBtn.y, dmBackBtn.w, dmBackBtn.h,
                backHov ? LadsPalette.HOVER : LadsPalette.PANEL);
        g.drawCenteredText("Back", dmBackBtn.x + dmBackBtn.w / 2, dmBackBtn.y + 5, LadsPalette.TEXT, false);

        // Messages scroll area
        int msgAreaY = y + headerH + 4;
        int msgAreaH = h - headerH - 36;
        List<DmEntry> dms = safeDms(activeDmFriend.id());

        // Auto-stick to newest messages when new messages arrive
        if (dms.size() != prevDmCount) {
            prevDmCount = dms.size();
            stickDmToBottom = true;
        }

        int totalDmH = dms.size() * 16 + 8;
        maxDmScroll = Math.max(0, totalDmH - msgAreaH);
        if (stickDmToBottom) {
            dmScrollOffset = maxDmScroll;
        } else {
            dmScrollOffset = Math.max(0, Math.min(maxDmScroll, dmScrollOffset));
        }

        g.enableScissor(x, msgAreaY, w, msgAreaH);

        int msgY = msgAreaY + 4 - dmScrollOffset;
        if (dms.isEmpty()) {
            g.drawCenteredText("No messages yet. Send a message to start the conversation!", x + w / 2, msgAreaY + msgAreaH / 2 - 5, LadsPalette.MUTED, false);
        } else {
            for (DmEntry dm : dms) {
                if (msgY + 16 > msgAreaY && msgY < msgAreaY + msgAreaH) {
                    boolean isFromMe = !activeDmFriend.id().equals(dm.from());
                    int color = isFromMe ? LadsPalette.ACCENT : LadsPalette.TEXT;
                    String prefix = isFromMe ? "You: " : activeDmFriend.name() + ": ";
                    g.drawText(prefix + dm.text(), x + 10, msgY, color, false);
                }
                msgY += 16;
            }
        }

        g.disableScissor();

        // Input field and send button (outside scissor)
        int inputAreaY = y + h - 26;
        int sendW = 50;
        int inW = w - sendW - 16;
        dmInputBox = new Box(x + 6, inputAreaY, inW, 20);
        dmSendBtn = new Box(x + 6 + inW + 4, inputAreaY, sendW, 20);

        MenuGraphics.round(g, dmInputBox.x, dmInputBox.y, dmInputBox.w, dmInputBox.h,
                typingDmInput ? LadsPalette.HOVER : LadsPalette.CARD);
        String inputDisp = dmInput.isEmpty() ? (typingDmInput ? "_" : "Write a message...") : dmInput + (typingDmInput ? "_" : "");
        int inCol = dmInput.isEmpty() && !typingDmInput ? LadsPalette.MUTED : LadsPalette.TEXT;
        g.drawText(MenuGraphics.fit(g, inputDisp, inW - 8), dmInputBox.x + 6, dmInputBox.y + 6, inCol, false);

        boolean sendHov = dmSendBtn.has(mx, my);
        MenuGraphics.round(g, dmSendBtn.x, dmSendBtn.y, dmSendBtn.w, dmSendBtn.h,
                sendHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Send", dmSendBtn.x + dmSendBtn.w / 2, dmSendBtn.y + 6, LadsPalette.TEXT, false);
    }

    private void renderMorePopup(LadsGraphics g, int mx, int my) {
        Box moreB = moreBoxes.get(popupFriendId);
        if (moreB == null) return;

        int popW = 95, popH = 65;
        int popX = Math.max(10, moreB.x - popW + moreB.w);
        int popY = moreB.y + moreB.h + 2;

        MenuGraphics.round(g, popX, popY, popW, popH, LadsPalette.PANEL);
        g.fill(popX, popY, popX + popW, popY + 1, LadsPalette.ACCENT);

        int itemH = 18;
        popupNickBtn = new Box(popX + 2, popY + 4, popW - 4, itemH);
        popupRemoveBtn = new Box(popX + 2, popY + 24, popW - 4, itemH);
        popupBlockBtn = new Box(popX + 2, popY + 44, popW - 4, itemH);

        boolean nHov = popupNickBtn.has(mx, my);
        boolean rHov = popupRemoveBtn.has(mx, my);
        boolean bHov = popupBlockBtn.has(mx, my);

        MenuGraphics.round(g, popupNickBtn.x, popupNickBtn.y, popupNickBtn.w, popupNickBtn.h, nHov ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("Set Nickname", popupNickBtn.x + popupNickBtn.w / 2, popupNickBtn.y + 5, LadsPalette.TEXT, false);

        MenuGraphics.round(g, popupRemoveBtn.x, popupRemoveBtn.y, popupRemoveBtn.w, popupRemoveBtn.h, rHov ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("Remove", popupRemoveBtn.x + popupRemoveBtn.w / 2, popupRemoveBtn.y + 5, LadsPalette.TEXT, false);

        MenuGraphics.round(g, popupBlockBtn.x, popupBlockBtn.y, popupBlockBtn.w, popupBlockBtn.h, bHov ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("Block", popupBlockBtn.x + popupBlockBtn.w / 2, popupBlockBtn.y + 5, LadsPalette.ACCENT, false);
    }

    private String formatPresence(Presence p) {
        if (p == null || !p.online()) return "Offline";
        String st = p.state();
        if ("menu".equals(st)) return "In menus";
        if ("singleplayer".equals(st)) return "Singleplayer";
        if ("hosting".equals(st)) {
            Presence.WorldAd ad = p.world();
            if (ad != null) {
                return "Hosting: " + ad.name() + " (" + ad.used() + "/" + ad.slots() + " guests)";
            }
            return "Hosting a world";
        }
        if ("server".equals(st)) {
            return p.server() != null ? "Playing on " + p.server() : "On a server";
        }
        return st != null ? st : "";
    }

    private String formatGameInfo(SocialBridge.GameInfo game) {
        if (game == null) return "";
        StringBuilder sb = new StringBuilder();
        if (game.mc() != null && !game.mc().isEmpty()) {
            sb.append(game.mc());
        }
        if (game.loader() != null && !game.loader().isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(capitalize(game.loader()));
        }
        if ("pack".equalsIgnoreCase(game.profileKind()) || (game.packId() != null && !game.packId().isEmpty())) {
            if (game.packId() != null && !game.packId().isEmpty()) {
                if (!sb.isEmpty()) sb.append(" - ");
                sb.append(game.packId());
                if (game.packVersion() != null && !game.packVersion().isEmpty()) {
                    sb.append(" ").append(game.packVersion());
                }
            }
        }
        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (confirm.isOpen()) return confirm.click(x, y, button);
        if (button != 0) return false;

        // Inline nickname editing controls
        if (editingNickId != null) {
            boolean inScissor = y >= scissorY && y <= scissorY + scissorH;
            if (inScissor) {
                if (nickSaveBtn != null && nickSaveBtn.has(x, y)) {
                    commitNick();
                    return true;
                }
                if (nickCancelBtn != null && nickCancelBtn.has(x, y)) {
                    cancelNick();
                    return true;
                }
                if (nickInputBox != null && nickInputBox.has(x, y)) {
                    typingNickInput = true;
                    return true;
                }
            }
        }

        // If popup is open, check popup clicks
        if (popupFriendId != null) {
            UUID target = popupFriendId;
            popupFriendId = null; // dismiss popup
            if (popupNickBtn != null && popupNickBtn.has(x, y)) {
                startEditingNick(target);
                return true;
            } else if (popupRemoveBtn != null && popupRemoveBtn.has(x, y)) {
                callbacks.removeFriend(target);
                return true;
            } else if (popupBlockBtn != null && popupBlockBtn.has(x, y)) {
                callbacks.blockFriend(target);
                return true;
            }
        }

        // Done button
        if (doneBtn != null && doneBtn.has(x, y)) {
            close();
            return true;
        }

        // Show Server toggle
        if (showServerBtn != null && showServerBtn.has(x, y)) {
            callbacks.setShowServer(!callbacks.showServer());
            return true;
        }

        // Appear Offline toggle
        if (appearOfflineBtn != null && appearOfflineBtn.has(x, y)) {
            callbacks.setAppearOffline(!callbacks.appearOffline());
            return true;
        }

        // Tab buttons
        for (Map.Entry<Tab, Box> entry : tabBoxes.entrySet()) {
            if (entry.getValue().has(x, y)) {
                setTab(entry.getKey());
                return true;
            }
        }

        // DM pane controls
        if (activeDmFriend != null) {
            if (dmBackBtn != null && dmBackBtn.has(x, y)) {
                closeDm();
                return true;
            }
            if (dmInputBox != null && dmInputBox.has(x, y)) {
                typingDmInput = true;
                return true;
            }
            if (dmSendBtn != null && dmSendBtn.has(x, y)) {
                sendDm();
                return true;
            }
            return false;
        }

        // Tab-specific clicks
        switch (currentTab) {
            case FRIENDS -> {
                boolean inScissor = y >= scissorY && y <= scissorY + scissorH;
                if (inScissor) {
                    // Join buttons
                    for (Map.Entry<UUID, Box> e : joinBoxes.entrySet()) {
                        if (e.getValue().has(x, y)) {
                            if (isJoinEnabled(e.getKey())) {
                                UUID id = e.getKey();
                                String question = callbacks.joinQuestion(id);
                                if (question == null) callbacks.requestJoin(id);
                                else confirmSwitch(question, () -> callbacks.requestJoin(id));
                            }
                            return true;
                        }
                    }
                    // Invite buttons
                    for (Map.Entry<UUID, Box> e : inviteBoxes.entrySet()) {
                        if (e.getValue().has(x, y)) {
                            callbacks.invite(e.getKey());
                            return true;
                        }
                    }
                    // Message buttons
                    for (Map.Entry<UUID, Box> e : messageBoxes.entrySet()) {
                        if (e.getValue().has(x, y)) {
                            for (FriendEntry f : safeFriends()) {
                                if (f.id().equals(e.getKey())) {
                                    openDm(f);
                                    break;
                                }
                            }
                            return true;
                        }
                    }
                    // More buttons
                    for (Map.Entry<UUID, Box> e : moreBoxes.entrySet()) {
                        if (e.getValue().has(x, y)) {
                            popupFriendId = (popupFriendId != null && popupFriendId.equals(e.getKey())) ? null : e.getKey();
                            return true;
                        }
                    }
                }
            }
            case REQUESTS -> {
                boolean inScissor = y >= scissorY && y <= scissorY + scissorH;
                if (inScissor) {
                    for (Map.Entry<UUID, Box> e : acceptBoxes.entrySet()) {
                        if (e.getValue().has(x, y)) {
                            callbacks.acceptRequest(e.getKey());
                            return true;
                        }
                    }
                    for (Map.Entry<UUID, Box> e : declineBoxes.entrySet()) {
                        if (e.getValue().has(x, y)) {
                            callbacks.declineRequest(e.getKey());
                            return true;
                        }
                    }
                }
            }
            case BLOCKED -> {
                boolean inScissor = y >= scissorY && y <= scissorY + scissorH;
                if (inScissor) {
                    for (Map.Entry<UUID, Box> e : unblockBoxes.entrySet()) {
                        if (e.getValue().has(x, y)) {
                            callbacks.unblockFriend(e.getKey());
                            return true;
                        }
                    }
                }
            }
            case ADD -> {
                if (addInputBox != null && addInputBox.has(x, y)) {
                    typingAddInput = true;
                    return true;
                } else {
                    typingAddInput = false;
                }
                if (addSendBtn != null && addSendBtn.has(x, y)) {
                    if (!addFriendInput.trim().isEmpty()) {
                        callbacks.addFriend(addFriendInput.trim());
                        addNotice = "Request sent to " + addFriendInput.trim() + "!";
                        addFriendInput = "";
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public boolean charTyped(char c) {
        if (confirm.isOpen()) return true;
        if (typingNickInput) {
            if (c >= 32 && c != 127 && nickInput.length() < 32) {
                nickInput += c;
                return true;
            }
        } else if (typingDmInput) {
            if (c >= 32 && c != 127 && dmInput.length() < SocialProtocol.MAX_DM) {
                dmInput += c;
                return true;
            }
        } else if (typingAddInput) {
            if (c >= 32 && c != 127 && addFriendInput.length() < 16) {
                addFriendInput += c;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirm.isOpen()) return confirm.key(keyCode == 1 ? 256 : keyCode == 28 ? 257 : keyCode); // 1.8.9's Esc/Enter too
        // Nickname editing keys
        if (typingNickInput) {
            if (keyCode == 256 || keyCode == 1) { // Esc
                cancelNick();
                return true;
            }
            if (keyCode == 257 || keyCode == 28) { // Enter
                commitNick();
                return true;
            }
            if ((keyCode == 259 || keyCode == 14) && !nickInput.isEmpty()) { // Backspace
                nickInput = nickInput.substring(0, nickInput.length() - 1);
                return true;
            }
            return false;
        }

        // Escape
        if (keyCode == 256 || keyCode == 1) { // GLFW_KEY_ESCAPE or 1.8.9 esc
            if (popupFriendId != null) {
                popupFriendId = null;
                return true;
            }
            if (activeDmFriend != null) {
                closeDm();
                return true;
            }
            close();
            return true;
        }

        // Enter
        if (keyCode == 257 || keyCode == 28) { // GLFW_KEY_ENTER or 1.8.9 enter
            if (typingDmInput) {
                sendDm();
                return true;
            }
            if (typingAddInput && !addFriendInput.trim().isEmpty()) {
                callbacks.addFriend(addFriendInput.trim());
                addNotice = "Request sent to " + addFriendInput.trim() + "!";
                addFriendInput = "";
                return true;
            }
        }

        // Backspace
        if (keyCode == 259 || keyCode == 14) { // GLFW_KEY_BACKSPACE or 1.8.9 backspace
            if (typingDmInput && !dmInput.isEmpty()) {
                dmInput = dmInput.substring(0, dmInput.length() - 1);
                return true;
            }
            if (typingAddInput && !addFriendInput.isEmpty()) {
                addFriendInput = addFriendInput.substring(0, addFriendInput.length() - 1);
                return true;
            }
        }

        return false;
    }

    public void close() {
        callbacks.onClose();
    }
}

package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.gui.MenuGraphics;
import com.thelads.core.client.social.FriendEntry;
import com.thelads.core.client.social.Presence;
import com.thelads.core.client.social.SocialBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Common GUI logic for the Friends screen (tabs Friends/Requests/Blocked/Add, status line,
 * version, Join/Invite/Message, DM pane), drawn through MenuGraphics / LadsGraphics.
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
        default String connectionStatus() { return "Connected"; }
        default boolean showServer() { return true; }
        default void setShowServer(boolean show) {}
        default boolean appearOffline() { return false; }
        default void setAppearOffline(boolean appearOffline) {}
        default void onClose() {}
    }

    private final Callbacks callbacks;
    private Tab currentTab = Tab.FRIENDS;

    // DM pane state
    private FriendEntry activeDmFriend = null;
    private String dmInput = "";

    // Add friend input
    private String addFriendInput = "";
    private String addNotice = "";

    // Nickname editing
    private UUID editingNickId = null;
    private String nickInput = "";

    // Scroll offsets
    private int scrollOffset = 0;
    private int dmScrollOffset = 0;

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

    private boolean typingAddInput = false;
    private boolean typingDmInput = false;
    private boolean typingNickInput = false;

    // More popup for a friend
    private UUID popupFriendId = null;
    private Box popupNickBtn;
    private Box popupRemoveBtn;
    private Box popupBlockBtn;

    public FriendsScreenLogic(Callbacks callbacks) {
        this.callbacks = callbacks != null ? callbacks : new Callbacks() {
            @Override public List<FriendEntry> friends() { return List.of(); }
        };
    }

    public Tab tab() { return currentTab; }
    public void setTab(Tab tab) {
        this.currentTab = tab != null ? tab : Tab.FRIENDS;
        this.popupFriendId = null;
        this.editingNickId = null;
        this.scrollOffset = 0;
    }

    public FriendEntry activeDmFriend() { return activeDmFriend; }
    public void openDm(FriendEntry friend) {
        this.activeDmFriend = friend;
        this.dmInput = "";
        this.typingDmInput = true;
        this.dmScrollOffset = 0;
    }
    public void closeDm() {
        this.activeDmFriend = null;
        this.dmInput = "";
        this.typingDmInput = false;
    }

    public String dmInput() { return dmInput; }
    public void setDmInput(String input) { this.dmInput = input != null ? input : ""; }

    public void sendDm() {
        if (activeDmFriend != null && !dmInput.trim().isEmpty()) {
            callbacks.sendDm(activeDmFriend.id(), dmInput.trim());
            dmInput = "";
        }
    }

    public List<FriendEntry> sortedFriends() {
        List<FriendEntry> list = new ArrayList<>(callbacks.friends());
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
        return list;
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

        // Background
        g.fill(0, 0, width, height, 0xD0070709);

        // Header panel
        int panelW = Math.min(width - 32, 540);
        int panelX = (width - panelW) / 2;
        int topY = 12;

        // Title & status
        g.drawText("Friends", panelX, topY + 4, LadsPalette.TEXT, true);
        String statusText = "Status: " + callbacks.connectionStatus();
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
        int tabW = (panelW - (Tab.values().length - 1) * 4) / Tab.values().length;
        int tabH = 22;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int tx = panelX + i * (tabW + 4);
            Box b = new Box(tx, tabY, tabW, tabH);
            tabBoxes.put(t, b);
            boolean active = (t == currentTab);
            boolean hover = b.has(mouseX, mouseY);
            int tabBg = active ? LadsPalette.PRIMARY : (hover ? LadsPalette.HOVER : LadsPalette.CARD);
            MenuGraphics.round(g, b.x, b.y, b.w, b.h, tabBg);

            String title = t.title;
            if (t == Tab.REQUESTS) {
                int reqCount = callbacks.incomingRequests().size();
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

        // Footer Done button
        int doneW = 100, doneH = 20;
        doneBtn = new Box(panelX + panelW - doneW, height - 28, doneW, doneH);
        boolean doneHover = doneBtn.has(mouseX, mouseY);
        MenuGraphics.round(g, doneBtn.x, doneBtn.y, doneBtn.w, doneBtn.h, doneHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Done", doneBtn.x + doneBtn.w / 2, doneBtn.y + 6, LadsPalette.TEXT, false);

        // Render popup menu if open
        if (popupFriendId != null) {
            renderMorePopup(g, mouseX, mouseY);
        }
    }

    private void renderFriendsTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<FriendEntry> friends = sortedFriends();
        if (friends.isEmpty()) {
            g.drawCenteredText("No friends yet. Click 'Add Friend' to connect!", x + w / 2, y + h / 2 - 5, LadsPalette.MUTED, false);
            return;
        }

        int rowH = 34;
        int itemY = y + 6;

        for (int i = 0; i < friends.size(); i++) {
            FriendEntry friend = friends.get(i);
            if (itemY + rowH > y + h - 6) break; // clipped

            Box rowBox = new Box(x + 6, itemY, w - 12, rowH);
            boolean rowHover = rowBox.has(mx, my);
            MenuGraphics.round(g, rowBox.x, rowBox.y, rowBox.w, rowBox.h, rowHover ? LadsPalette.HOVER : LadsPalette.CARD);

            // Online indicator dot
            boolean online = friend.presence() != null && friend.presence().online();
            int dotColor = online ? LadsPalette.CARD_ON_GLOW : LadsPalette.DISABLED;
            g.fill(rowBox.x + 8, itemY + 14, rowBox.x + 14, itemY + 20, dotColor);

            // Name
            String nameStr = friend.displayName();
            if (friend.nick() != null && !friend.nick().isEmpty() && !friend.nick().equals(friend.name())) {
                nameStr = friend.nick() + " (" + friend.name() + ")";
            }
            g.drawText(MenuGraphics.fit(g, nameStr, 140), rowBox.x + 20, itemY + 6, LadsPalette.TEXT, false);

            // Status string & Version
            String status = formatPresence(friend.presence());
            g.drawText(MenuGraphics.fit(g, status, 170), rowBox.x + 20, itemY + 18, LadsPalette.MUTED, false);

            // Game version
            if (friend.presence() != null && friend.presence().game() != null) {
                String ver = friend.presence().game().mc();
                if (ver != null && !ver.isEmpty()) {
                    g.drawText(ver, rowBox.x + 195, itemY + 12, LadsPalette.MUTED, false);
                }
            }

            // Action buttons on right
            int btnH = 20;
            int btnY = itemY + 7;
            int currX = rowBox.x + rowBox.w - 8;

            // More [...] button
            currX -= 24;
            Box moreB = new Box(currX, btnY, 24, btnH);
            moreBoxes.put(friend.id(), moreB);
            boolean moreHov = moreB.has(mx, my);
            MenuGraphics.round(g, moreB.x, moreB.y, moreB.w, moreB.h, moreHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PANEL);
            g.drawCenteredText("...", moreB.x + moreB.w / 2, moreB.y + 4, LadsPalette.TEXT, false);

            // Message button
            currX -= 54;
            Box msgB = new Box(currX, btnY, 50, btnH);
            messageBoxes.put(friend.id(), msgB);
            boolean msgHov = msgB.has(mx, my);
            MenuGraphics.round(g, msgB.x, msgB.y, msgB.w, msgB.h, msgHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PANEL);
            g.drawCenteredText("Chat", msgB.x + msgB.w / 2, msgB.y + 5, LadsPalette.TEXT, false);

            // Invite button
            currX -= 54;
            Box invB = new Box(currX, btnY, 50, btnH);
            inviteBoxes.put(friend.id(), invB);
            boolean invHov = invB.has(mx, my);
            MenuGraphics.round(g, invB.x, invB.y, invB.w, invB.h, invHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PANEL);
            g.drawCenteredText("Invite", invB.x + invB.w / 2, invB.y + 5, LadsPalette.TEXT, false);

            // Join button
            boolean isHosting = friend.presence() != null && "hosting".equals(friend.presence().state());
            JoinCheck check = callbacks.canJoin(friend);
            joinReasons.put(friend.id(), check.reason());
            joinEnabledMap.put(friend.id(), check.ok());

            currX -= 54;
            Box jnB = new Box(currX, btnY, 50, btnH);
            joinBoxes.put(friend.id(), jnB);
            boolean jnHov = jnB.has(mx, my);

            int joinColor = !check.ok() ? LadsPalette.DISABLED : (jnHov ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
            MenuGraphics.round(g, jnB.x, jnB.y, jnB.w, jnB.h, joinColor);
            g.drawCenteredText("Join", jnB.x + jnB.w / 2, jnB.y + 5, check.ok() ? LadsPalette.TEXT : LadsPalette.MUTED, false);

            // If disabled and hovered or hosting, display reason tooltip
            if (!check.ok() && (jnHov || isHosting) && check.reason() != null && !check.reason().isEmpty()) {
                g.drawText(MenuGraphics.fit(g, check.reason(), 120), jnB.x - 125, itemY + 12, LadsPalette.ACCENT, false);
            }

            itemY += rowH + 4;
        }
    }

    private void renderRequestsTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<FriendEntry> incoming = callbacks.incomingRequests();
        List<FriendEntry> outgoing = callbacks.outgoingRequests();

        int itemY = y + 8;
        g.drawText("Incoming Friend Requests (" + incoming.size() + ")", x + 10, itemY, LadsPalette.ACCENT, true);
        itemY += 16;

        if (incoming.isEmpty()) {
            g.drawText("No incoming requests.", x + 20, itemY, LadsPalette.MUTED, false);
            itemY += 16;
        } else {
            for (FriendEntry req : incoming) {
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
                Box rBox = new Box(x + 10, itemY, w - 20, 24);
                MenuGraphics.round(g, rBox.x, rBox.y, rBox.w, rBox.h, LadsPalette.CARD);
                g.drawText(req.name(), rBox.x + 10, itemY + 7, LadsPalette.TEXT, false);
                g.drawText("Pending...", rBox.x + rBox.w - 70, itemY + 7, LadsPalette.MUTED, false);
                itemY += 28;
            }
        }
    }

    private void renderBlockedTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<FriendEntry> blocked = callbacks.blocked();
        int itemY = y + 8;
        g.drawText("Blocked Players (" + blocked.size() + ")", x + 10, itemY, LadsPalette.ACCENT, true);
        itemY += 18;

        if (blocked.isEmpty()) {
            g.drawText("No blocked players.", x + 20, itemY, LadsPalette.MUTED, false);
            return;
        }

        for (FriendEntry b : blocked) {
            Box rBox = new Box(x + 10, itemY, w - 20, 26);
            MenuGraphics.round(g, rBox.x, rBox.y, rBox.w, rBox.h, LadsPalette.CARD);
            g.drawText(b.name(), rBox.x + 10, itemY + 8, LadsPalette.TEXT, false);

            int btnW = 65, btnH = 18, btnY = itemY + 4;
            Box unbB = new Box(rBox.x + rBox.w - 75, btnY, btnW, btnH);
            unblockBoxes.put(b.id(), unbB);

            boolean unbHov = unbB.has(mx, my);
            MenuGraphics.round(g, unbB.x, unbB.y, unbB.w, unbB.h, unbHov ? LadsPalette.HOVER : LadsPalette.PANEL);
            g.drawCenteredText("Unblock", unbB.x + unbB.w / 2, unbB.y + 5, LadsPalette.TEXT, false);

            itemY += 30;
        }
    }

    private void renderAddTab(LadsGraphics g, int x, int y, int w, int h, int mx, int my) {
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
        List<DmEntry> dms = callbacks.dms(activeDmFriend.id());

        int msgY = msgAreaY + 4;
        if (dms.isEmpty()) {
            g.drawCenteredText("No messages yet. Send a message to start the conversation!", x + w / 2, msgAreaY + msgAreaH / 2 - 5, LadsPalette.MUTED, false);
        } else {
            for (DmEntry dm : dms) {
                if (msgY + 16 > msgAreaY + msgAreaH) break;
                boolean isFromMe = !activeDmFriend.id().equals(dm.from());
                int color = isFromMe ? LadsPalette.ACCENT : LadsPalette.TEXT;
                String prefix = isFromMe ? "You: " : activeDmFriend.name() + ": ";
                g.drawText(prefix + dm.text(), x + 10, msgY, color, false);
                msgY += 14;
            }
        }

        // Input field and send button
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

        int popW = 90, popH = 65;
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
                return "Hosting: " + ad.name() + " (" + ad.used() + "/" + ad.slots() + ")";
            }
            return "Hosting a world";
        }
        if ("server".equals(st)) {
            return p.server() != null ? "Playing on " + p.server() : "On a server";
        }
        return st;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;

        // If popup is open, check popup clicks
        if (popupFriendId != null) {
            UUID target = popupFriendId;
            popupFriendId = null; // dismiss popup
            if (popupNickBtn != null && popupNickBtn.has(x, y)) {
                callbacks.setNick(target, "Friend");
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
                // Join buttons
                for (Map.Entry<UUID, Box> e : joinBoxes.entrySet()) {
                    if (e.getValue().has(x, y)) {
                        if (isJoinEnabled(e.getKey())) {
                            callbacks.requestJoin(e.getKey());
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
                        for (FriendEntry f : callbacks.friends()) {
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
            case REQUESTS -> {
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
            case BLOCKED -> {
                for (Map.Entry<UUID, Box> e : unblockBoxes.entrySet()) {
                    if (e.getValue().has(x, y)) {
                        callbacks.unblockFriend(e.getKey());
                        return true;
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
        if (typingDmInput) {
            if (c >= 32 && c != 127) {
                dmInput += c;
                return true;
            }
        } else if (typingAddInput) {
            if (c >= 32 && c != 127) {
                addFriendInput += c;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape
        if (keyCode == 256 || keyCode == 1) { // GLFW_KEY_ESCAPE or 1.8.9 esc
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

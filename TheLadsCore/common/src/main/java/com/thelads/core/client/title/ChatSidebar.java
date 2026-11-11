package com.thelads.core.client.title;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.MenuGraphics;
import com.thelads.core.client.social.FriendEntry;
import com.thelads.core.client.social.Presence;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.title.TitleOverlayLayout.Sidebar;
import com.thelads.core.client.title.TitleScreenTheme.Rect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The title screen's chat sidebar: a chat-bubble tab on the middle of the right edge that slides a mini social page out, with
 * Messages (conversations: initial, name, last message, unread dot) and Friends (online first, status dot, what they play), a
 * filter row and empty states. It only reads what the Friends screen already holds (SocialState) and a click hands the person to
 * the existing Friends screen's chat; nothing is sent, nothing is kept on a server.
 */
final class ChatSidebar {
    private static final int PANEL = 0xF4160D10, PANEL_ALT = 0xFF211217, LINE = 0xFF422029, TEXT = 0xFFFFF8F8, MUTED = 0xFFC6B2B8,
        RED_DEEP = 0xFF59050F, RED = 0xFFCC1427, ACCENT = 0xFFFF5C70, GREEN = 0xFF4ADE80, OFFLINE = 0xFF6F6168, HOVER = 0xFF2A171D;
    private static final int[] AVATARS = {0xFF5E1A21, 0xFF3F2A5A, 0xFF1F4A5A, 0xFF2D5A3A, 0xFF5A4A1F, 0xFF5A2D4A};
    private static final int MAX_QUERY = 24;
    static final int KEY_ESC = 256, KEY_ENTER = 257, KEY_KP_ENTER = 335, KEY_TAB = 258, KEY_BACKSPACE = 259, KEY_UP = 265, KEY_DOWN = 264;

    enum Tab { MESSAGES, FRIENDS }

    /** What the sidebar reads and does; the real one is SocialState, tests pass their own. */
    interface Source {
        List<FriendEntry> friends();
        List<SocialState.Dm> dms(UUID friend);
        String status();
        /** Opens the Friends screen on this friend's chat. */
        void openChat(UUID friend);
        void openFriends();
    }

    /** A list row: a section label (id null) or a person. */
    record Row(UUID id, String name, String sub, String time, boolean unread, boolean online, String initial, int color, int height) {
        boolean person() { return id != null; }
    }

    // ---- Pure model ----

    /** Conversations, newest first: a friend with any message; unread when their message came after both `since` and the chat was last opened. */
    static List<Row> conversations(List<FriendEntry> friends, Function<UUID, List<SocialState.Dm>> dms, String query, Map<UUID, Long> opened, long since, long now) {
        record Convo(FriendEntry friend, SocialState.Dm last) {}
        List<Convo> convos = new ArrayList<>();
        for (FriendEntry f : friends) {
            List<SocialState.Dm> list = dms.apply(f.id());
            if (list == null || list.isEmpty()) continue;
            SocialState.Dm last = list.get(list.size() - 1);
            if (matches(query, f, preview(f, last))) convos.add(new Convo(f, last));
        }
        convos.sort((a, b) -> Long.compare(b.last.ts(), a.last.ts()));
        List<Row> rows = new ArrayList<>();
        for (Convo c : convos) {
            boolean unread = c.last.from() != null && c.last.from().equals(c.friend.id())
                && c.last.ts() > Math.max(since, opened.getOrDefault(c.friend.id(), 0L));
            rows.add(person(c.friend, preview(c.friend, c.last), ago(now, c.last.ts()), unread));
        }
        return rows;
    }

    /** Friends: an Online section then an Offline one (empty sections left out), each person with what they are doing. */
    static List<Row> friends(List<FriendEntry> friends, String query) {
        List<Row> online = new ArrayList<>(), offline = new ArrayList<>();
        for (FriendEntry f : friends) {
            if (!matches(query, f, "")) continue;
            boolean on = f.presence() != null && f.presence().online();
            (on ? online : offline).add(person(f, status(f.presence()), "", false));
        }
        List<Row> rows = new ArrayList<>();
        if (!online.isEmpty()) {
            rows.add(section("ONLINE - " + online.size()));
            rows.addAll(online);
        }
        if (!offline.isEmpty()) {
            rows.add(section("OFFLINE - " + offline.size()));
            rows.addAll(offline);
        }
        return rows;
    }

    private static Row section(String label) {
        return new Row(null, label, "", "", false, false, "", 0, TitleOverlayLayout.SECTION_ROW);
    }

    private static Row person(FriendEntry f, String sub, String time, boolean unread) {
        String name = f.displayName() == null || f.displayName().isEmpty() ? "?" : f.displayName();
        int color = AVATARS[Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), AVATARS.length)];
        return new Row(f.id(), name, sub, time, unread, f.presence() != null && f.presence().online(),
            name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT), color, TitleOverlayLayout.PERSON_ROW);
    }

    private static boolean matches(String query, FriendEntry f, String text) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        return f.displayName().toLowerCase(Locale.ROOT).contains(q) || f.name().toLowerCase(Locale.ROOT).contains(q)
            || text.toLowerCase(Locale.ROOT).contains(q);
    }

    /** A conversation's last line: "You: ..." for our own, a picture or GIF named when there is no text. */
    static String preview(FriendEntry friend, SocialState.Dm last) {
        String text = last.text() == null ? "" : last.text().replace('\n', ' ').trim();
        if (last.gif() != null) text = "GIF";
        else if (last.img() != null) text = "Photo";
        boolean mine = last.from() != null && !last.from().equals(friend.id());
        return mine ? "You: " + text : text;
    }

    /** What a friend is doing, as the Friends screen words it, plus the game version they run. */
    static String status(Presence p) {
        if (p == null || !p.online()) return "Offline";
        String state = p.state() == null ? "" : p.state();
        String text = switch (state) {
            case "menu" -> "In menus";
            case "singleplayer" -> "Singleplayer";
            case "hosting" -> p.world() != null ? "Hosting " + p.world().name() : "Hosting a world";
            case "server" -> com.thelads.core.client.LadsNetwork.isAddress(p.server()) ? "On " + com.thelads.core.client.LadsNetwork.NAME
                : p.server() != null ? "Playing on " + p.server() : "On a server";
            default -> state.isEmpty() ? "Online" : state;
        };
        String mc = p.game() == null ? "" : p.game().mc();
        return mc == null || mc.isEmpty() ? text : text + " - " + mc;
    }

    /** Message age: now, 5m, 3h, 2d, 6w. */
    static String ago(long now, long ts) {
        long s = Math.max(0, (now - ts) / 1000);
        if (s < 60) return "now";
        if (s < 3600) return s / 60 + "m";
        if (s < 86400) return s / 3600 + "h";
        if (s < 604800) return s / 86400 + "d";
        return s / 604800 + "w";
    }

    static int[] heights(List<Row> rows) {
        int[] h = new int[rows.size()];
        for (int i = 0; i < h.length; i++) h[i] = rows.get(i).height();
        return h;
    }

    // ---- State ----

    /** When each chat was last opened from here, this game only. */
    private static final Map<UUID, Long> OPENED = new HashMap<>();
    private static final long SINCE = ProcessHandle.current().info().startInstant().map(java.time.Instant::toEpochMilli).orElse(System.currentTimeMillis());

    private final Source source;
    private boolean open;
    private float progress;
    private Tab tab = Tab.MESSAGES;
    private String query = "";
    private List<Row> rows = List.of();
    private int unread, online;
    private long refreshed;
    private double scroll, target;
    private int selected = -1;
    private int viewHeight = 80;
    private double lastMouseX = -1, lastMouseY = -1;

    ChatSidebar(Source source) { this.source = source; }

    boolean isOpen() { return open; }
    boolean capturing() { return open; }
    float progress() { return progress; }
    String query() { return query; }
    Tab tab() { return tab; }
    List<Row> rows() { return rows; }

    void toggle() { if (open) close(); else open(); }

    void open() {
        open = true;
        refreshed = 0;
        selected = -1;
        scroll = target = 0;
    }

    void close() { open = false; }

    private void setTab(Tab next) {
        if (tab == next) return;
        tab = next;
        query = "";
        selected = -1;
        scroll = target = 0;
        refreshed = 0;
    }

    private void refresh(long now) {
        if (now - refreshed < 400 && refreshed != 0) return;
        refreshed = now;
        List<FriendEntry> friends = source.friends();
        int unreadNow = 0, onlineNow = 0;
        for (FriendEntry f : friends) if (f.presence() != null && f.presence().online()) onlineNow++;
        for (Row r : conversations(friends, source::dms, "", OPENED, SINCE, now)) if (r.unread()) unreadNow++;
        unread = unreadNow;
        online = onlineNow;
        rows = tab == Tab.MESSAGES ? conversations(friends, source::dms, query, OPENED, SINCE, now) : friends(friends, query);
        if (selected >= rows.size()) selected = -1;
    }

    // ---- Drawing ----

    void render(LadsGraphics g, int sw, int sh, int mx, int my, float dt, boolean reducedMotion) {
        float goal = open ? 1f : 0f;
        progress = reducedMotion ? goal : progress + (goal - progress) * (float) (1 - Math.exp(-Math.min(0.1f, dt) * 14));
        if (Math.abs(goal - progress) < 0.004f) progress = goal;
        long now = System.currentTimeMillis();
        refresh(now);
        Rect tabBox = TitleOverlayLayout.tab(sw, sh, progress);
        if (progress > 0) {
            Sidebar s = TitleOverlayLayout.sidebar(sw, sh, progress);
            g.overlay(() -> {
                g.fill(0, 0, sw, sh, ((int) (0x66 * TitleOverlayLayout.ease(progress)) << 24));
                drawPanel(g, s, mx, my, dt, now);
            });
        }
        g.overlay(() -> drawTab(g, tabBox, mx, my));
    }

    private void drawTab(LadsGraphics g, Rect t, int mx, int my) {
        boolean hover = TitleOverlayLayout.inside(t, mx, my);
        int fill = hover ? 0xFF2A171D : 0xF2160D10;
        g.fill(t.x(), t.y(), t.x() + t.width(), t.y() + t.height(), LINE);
        g.fill(t.x() + 1, t.y() + 1, t.x() + t.width(), t.y() + t.height() - 1, fill);
        if (open) TitleScreenTheme.icon(g, "angle-right", t.x() + 2, t.y() + (t.height() - 10) / 2, 10, hover ? TEXT : MUTED);
        else {
            TitleScreenTheme.icon(g, "chat", t.x() + 2, t.y() + (t.height() - 10) / 2, 10, hover ? TEXT : MUTED);
            if (unread > 0) { // an unread message: a red dot on the bubble
                g.fill(t.x() + 8, t.y() + (t.height() - 10) / 2 - 3, t.x() + 13, t.y() + (t.height() - 10) / 2 + 2, 0xFF050304);
                g.fill(t.x() + 9, t.y() + (t.height() - 10) / 2 - 2, t.x() + 12, t.y() + (t.height() - 10) / 2 + 1, RED);
            }
        }
    }

    private void drawPanel(LadsGraphics g, Sidebar s, int mx, int my, float dt, long now) {
        Rect p = s.panel();
        g.fill(p.x() - 2, 0, p.x(), p.height(), 0x55000000);
        g.fill(p.x(), 0, p.x() + p.width(), p.height(), PANEL);
        g.fill(p.x(), 0, p.x() + 1, p.height(), LINE);
        // Header: title, connection, close
        g.drawText("Chat", p.x() + TitleOverlayLayout.SIDEBAR_PAD, 9, TEXT, false);
        String status = source.status() == null ? "" : source.status();
        int statusRight = s.close().x() - 6;
        int titleRight = p.x() + TitleOverlayLayout.SIDEBAR_PAD + g.textWidth("Chat") + 8;
        String fitted = MenuGraphics.fit(g, status, Math.max(0, statusRight - titleRight));
        g.drawText(fitted, statusRight - g.textWidth(fitted), 9, MUTED, false);
        boolean closeHover = TitleOverlayLayout.inside(s.close(), mx, my);
        TitleScreenTheme.icon(g, "cross", s.close().x() + 3, s.close().y() + 3, 8, closeHover ? ACCENT : MUTED);
        g.fill(p.x() + 1, TitleOverlayLayout.HEADER, p.x() + p.width(), TitleOverlayLayout.HEADER + 1, LINE);
        // Segments
        segment(g, s.messages(), "Messages", unread > 0 ? String.valueOf(unread) : "", tab == Tab.MESSAGES, mx, my);
        segment(g, s.friends(), "Friends", online > 0 ? String.valueOf(online) : "", tab == Tab.FRIENDS, mx, my);
        // Search
        Rect q = s.search();
        g.fill(q.x(), q.y(), q.x() + q.width(), q.y() + q.height(), LINE);
        g.fill(q.x() + 1, q.y() + 1, q.x() + q.width() - 1, q.y() + q.height() - 1, PANEL_ALT);
        TitleScreenTheme.icon(g, "search", q.x() + 6, q.y() + (q.height() - 9) / 2, 9, MUTED);
        int textY = q.y() + (q.height() - g.fontHeight()) / 2 + 1, textX = q.x() + 20;
        if (query.isEmpty()) g.drawText(tab == Tab.MESSAGES ? "Search messages..." : "Search friends...", textX, textY, 0xFF8F7B82, false);
        else g.drawText(MenuGraphics.fit(g, query, q.width() - 28), textX, textY, TEXT, false);
        if (open && (now / 500) % 2 == 0) { // caret: typing goes straight into the box
            int caretX = textX + (query.isEmpty() ? 0 : Math.min(q.width() - 28, g.textWidth(query))) + 1;
            g.fill(caretX, q.y() + 5, caretX + 1, q.y() + q.height() - 5, TEXT);
        }
        // List
        Rect l = s.list();
        viewHeight = l.height();
        int[] heights = heights(rows);
        int content = TitleOverlayLayout.sum(heights);
        target = TitleOverlayLayout.clampScroll((int) target, content, viewHeight);
        scroll += (target - scroll) * (1 - Math.exp(-Math.min(0.1f, dt) * 18));
        if (Math.abs(target - scroll) < 0.3) scroll = target;
        int shown = (int) Math.round(scroll);
        if (mx != lastMouseX || my != lastMouseY) {
            if (lastMouseX >= 0 && TitleOverlayLayout.inside(l, mx, my)) {
                int at = TitleOverlayLayout.rowAt(heights, shown, my - l.y());
                if (at >= 0 && rows.get(at).person()) selected = at;
            }
            lastMouseX = mx;
            lastMouseY = my;
        }
        g.enableScissor(l.x(), l.y(), l.x() + l.width(), l.y() + l.height());
        if (rows.isEmpty()) emptyState(g, l);
        int y = l.y() - shown;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (y + r.height() > l.y() && y < l.y() + l.height()) {
                if (r.person()) personRow(g, l, y, r, i == selected);
                else sectionRow(g, l, y, r);
            }
            y += r.height();
        }
        g.disableScissor();
        if (content > viewHeight) { // a slim scrollbar
            int thumb = Math.max(10, viewHeight * viewHeight / content);
            int thumbY = l.y() + (int) ((viewHeight - thumb) * (shown / (double) (content - viewHeight)));
            g.fill(l.x() + l.width() - 4, thumbY, l.x() + l.width() - 2, thumbY + thumb, ACCENT & 0xAAFFFFFF);
        }
        // Footer: the full Friends screen
        Rect f = s.footer();
        boolean footHover = TitleOverlayLayout.inside(f, mx, my);
        g.fill(f.x(), f.y(), f.x() + f.width(), f.y() + f.height(), LINE);
        g.fill(f.x() + 1, f.y() + 1, f.x() + f.width() - 1, f.y() + f.height() - 1, footHover ? RED : RED_DEEP);
        MenuGraphics.iconLabel(g, "Open Friends screen", "friends", f.x() + f.width() / 2, f.y() + (f.height() - g.fontHeight()) / 2 + 1, f.width(), TEXT, false);
    }

    private void segment(LadsGraphics g, Rect r, String label, String count, boolean active, int mx, int my) {
        boolean hover = TitleOverlayLayout.inside(r, mx, my);
        g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), active ? RED_DEEP : hover ? HOVER : PANEL_ALT);
        if (active) g.fill(r.x(), r.y() + r.height() - 2, r.x() + r.width(), r.y() + r.height(), RED);
        int total = g.textWidth(label) + (count.isEmpty() ? 0 : 6 + g.textWidth(count));
        int x = r.x() + Math.max(2, (r.width() - total) / 2), y = r.y() + (r.height() - g.fontHeight()) / 2;
        g.drawText(label, x, y, active ? TEXT : MUTED, false);
        if (!count.isEmpty()) g.drawText(count, x + g.textWidth(label) + 6, y, ACCENT, false);
    }

    private void sectionRow(LadsGraphics g, Rect l, int y, Row r) {
        int x = l.x() + TitleOverlayLayout.SIDEBAR_PAD, ty = y + (r.height() - g.fontHeight()) / 2 + 2;
        g.drawText(r.name(), x, ty, 0xFF8F7B82, false);
        int lineX = x + g.textWidth(r.name()) + 6;
        g.fill(lineX, y + r.height() / 2 + 1, l.x() + l.width() - TitleOverlayLayout.SIDEBAR_PAD, y + r.height() / 2 + 2, LINE);
    }

    private void personRow(LadsGraphics g, Rect l, int y, Row r, boolean selectedRow) {
        int pad = TitleOverlayLayout.SIDEBAR_PAD, left = l.x() + pad, right = l.x() + l.width() - pad - 4;
        if (selectedRow) g.fill(l.x() + 2, y, l.x() + l.width() - 2, y + r.height() - 1, HOVER);
        int av = 22, ay = y + (r.height() - 1 - av) / 2;
        g.fill(left, ay, left + av, ay + av, r.color());
        g.fill(left, ay, left + av, ay + 1, 0x22FFFFFF);
        g.drawCenteredText(r.initial(), left + av / 2, ay + (av - g.fontHeight()) / 2 + 1, TEXT, false);
        int dx = left + av - 5, dy = ay + av - 5; // presence dot with a ring in the panel colour
        g.fill(dx - 1, dy - 1, dx + 7, dy + 7, 0xFF160D10);
        MenuGraphics.round(g, dx, dy, 6, 6, r.online() ? GREEN : OFFLINE);
        int textX = left + av + 8;
        int timeW = r.time().isEmpty() ? 0 : g.textWidth(r.time()) + 6;
        g.drawText(MenuGraphics.fit(g, r.name(), right - textX - timeW), textX, y + 7, TEXT, false);
        if (!r.time().isEmpty()) g.drawText(r.time(), right - g.textWidth(r.time()), y + 7, r.unread() ? ACCENT : 0xFF8F7B82, false);
        int dot = r.unread() ? 10 : 0;
        g.drawText(MenuGraphics.fit(g, r.sub(), right - textX - dot), textX, y + 19, r.unread() ? TEXT : MUTED, false);
        if (r.unread()) MenuGraphics.round(g, right - 6, y + 20, 6, 6, RED);
    }

    private void emptyState(LadsGraphics g, Rect l) {
        boolean filtered = !query.isBlank();
        String icon = tab == Tab.MESSAGES ? "chat" : "users";
        String title = filtered ? "No matches" : tab == Tab.MESSAGES ? "No conversations yet" : "No friends yet";
        String hint = filtered ? "Nothing for \"" + query.trim() + "\"." : tab == Tab.MESSAGES ? "Pick a friend to say hi." : "Add some on the Friends screen.";
        int cx = l.x() + l.width() / 2, top = l.y() + Math.max(2, (l.height() - 46) / 2);
        TitleScreenTheme.icon(g, icon, cx - 9, top, 18, 0xFF6F6168);
        g.drawCenteredText(MenuGraphics.fit(g, title, l.width() - 16), cx, top + 24, TEXT, false);
        g.drawCenteredText(MenuGraphics.fit(g, hint, l.width() - 16), cx, top + 36, MUTED, false);
    }

    // ---- Input ----

    /** A press. Consumed when it hit the tab, or the sidebar while it is out (a click outside closes it). */
    boolean click(double x, double y, int button, int sw, int sh) {
        Rect tabBox = TitleOverlayLayout.tab(sw, sh, progress);
        if (TitleOverlayLayout.inside(tabBox, x, y)) {
            if (button == 0) toggle();
            return true;
        }
        if (!open) return false;
        Sidebar s = TitleOverlayLayout.sidebar(sw, sh, progress);
        if (!TitleOverlayLayout.inside(s.panel(), x, y)) {
            close();
            return true;
        }
        if (button != 0) return true;
        if (TitleOverlayLayout.inside(s.close(), x, y)) close();
        else if (TitleOverlayLayout.inside(s.messages(), x, y)) setTab(Tab.MESSAGES);
        else if (TitleOverlayLayout.inside(s.friends(), x, y)) setTab(Tab.FRIENDS);
        else if (TitleOverlayLayout.inside(s.footer(), x, y)) {
            source.openFriends();
            close();
        } else if (TitleOverlayLayout.inside(s.list(), x, y)) {
            int at = TitleOverlayLayout.rowAt(heights(rows), (int) Math.round(scroll), (int) y - s.list().y());
            if (at >= 0 && rows.get(at).person()) openRow(at);
        }
        return true;
    }

    private void openRow(int index) {
        Row r = rows.get(index);
        OPENED.put(r.id(), System.currentTimeMillis());
        source.openChat(r.id());
        close();
    }

    boolean wheel(double amount) {
        if (!open) return false;
        target = TitleOverlayLayout.clampScroll((int) Math.round(target - amount * 24), TitleOverlayLayout.sum(heights(rows)), viewHeight);
        return true;
    }

    /** GLFW key codes; false for keys the screen may still use (function keys...). */
    boolean key(int key) {
        if (!open) return false;
        switch (key) {
            case KEY_ESC -> close();
            case KEY_TAB -> setTab(tab == Tab.MESSAGES ? Tab.FRIENDS : Tab.MESSAGES);
            case KEY_DOWN -> step(1);
            case KEY_UP -> step(-1);
            case KEY_ENTER, KEY_KP_ENTER -> {
                if (selected >= 0 && selected < rows.size() && rows.get(selected).person()) openRow(selected);
            }
            case KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
                    refreshed = 0;
                }
            }
            default -> { return false; }
        }
        return true;
    }

    /** The next (dir 1) or previous person row, skipping section labels. */
    private void step(int dir) {
        int i = selected;
        do i += dir; while (i >= 0 && i < rows.size() && !rows.get(i).person());
        if (i < 0 || i >= rows.size()) return;
        selected = i;
        lastMouseX = lastMouseY = -1;
        target = TitleOverlayLayout.scrollToShow(heights(rows), i, (int) target, viewHeight);
    }

    /** Typed text goes into the filter. */
    boolean typed(char c) {
        if (!open) return false;
        if (c >= 32 && c != 127 && query.length() < MAX_QUERY) {
            query += c;
            selected = -1;
            scroll = target = 0;
            refreshed = 0;
        }
        return true;
    }
}

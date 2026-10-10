package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.HudSettings.Anchor;
import com.thelads.core.config.HudSettings.Axis;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where saved HUDs sit when the GUI scale, the window or a neighbour changes (1.9.0). A saved HUD is anchored to a box: the screen,
 * the vanilla hotbar, or the HUD it was docked against in the editor. Per axis one of its points (start, centre or end) keeps its
 * pixel offset from one of the box's points, the nearest pair when it was saved. So a HUD beside the hotbar stays beside it at
 * every GUI scale, one in a corner stays in that corner, and one docked under another HUD moves with it and is pushed along once
 * that HUD grows past the room it had in the editor, never overlapped. Of two docked HUDs the one nearer its screen edge holds
 * the other, so a stack grows away from that edge; the hotbar holds what is beside or on it.
 */
public final class HudAnchors {
    /** Edges at most this far apart are docked (the editor snaps within 4 px). */
    static final int DOCK = 4;
    private static final int[] NEAR = {0, 1, 2}, FAR = {2, 1, 0};

    private HudAnchors() {}

    /** The vanilla hotbar's box in this graphics' units: 182 x 22 at the bottom centre, above the hotbar lift. */
    public static Rect hotbar(LadsGraphics g) {
        double unit = g.hudUnit();
        int w = (int) Math.round(182 * unit), h = (int) Math.round(22 * unit);
        return new Rect(g.getScaledWidth() / 2 - w / 2, g.getScaledHeight() - g.hotbarLift() - h, w, h);
    }

    /** True for a HUD saved without an anchor (before 1.9.0, or by older code): the next layout anchors it where it shows. */
    public static boolean legacy(HudElement element) {
        String name = element.getModuleName();
        return name != null && element.usesSavedPosition() && HudSettings.getInstance().getPosition(name) != null
            && HudSettings.getInstance().getAnchor(name) == null;
    }

    private static Anchor anchorOf(HudElement element) {
        return element.getModuleName() == null || !element.usesSavedPosition() ? null : HudSettings.getInstance().getAnchor(element.getModuleName());
    }

    /** The position of a span whose pin point sits the axis offset from the ref point of [start, start + size). */
    static int at(int start, int size, Axis axis, int span) {
        return start + axis.ref() * size / 2 + axis.off() - axis.pin() * span / 2;
    }

    /**
     * This frame's places: every measured HUD with an anchor moves to where it puts it, what it is docked to first; the rest stay
     * where they measured. A HUD being dragged stays under the pointer, and what is docked to it keeps to its anchored place.
     */
    public static void place(Map<HudElement, Rect> measured, int width, int height, Rect hotbar) {
        new Layout(measured, width, height, hotbar).run();
    }

    private static final class Layout {
        final Map<HudElement, Rect> measured, anchored = new IdentityHashMap<>();
        final Map<String, HudElement> byName = new HashMap<>();
        final Set<HudElement> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        final int width, height;
        final Rect hotbar;

        Layout(Map<HudElement, Rect> measured, int width, int height, Rect hotbar) {
            this.measured = measured;
            this.width = width;
            this.height = height;
            this.hotbar = hotbar;
        }

        void run() {
            for (HudElement element : measured.keySet()) if (element.getModuleName() != null) byName.put(element.getModuleName(), element);
            for (HudElement element : measured.keySet()) anchored(element);
            for (var entry : measured.entrySet()) if (!entry.getKey().isEditingPosition()) entry.setValue(anchored.get(entry.getKey()));
        }

        /** Where the HUD's anchor puts it; its measured box without one, or when what it is docked to is not laid out. */
        Rect anchored(HudElement element) {
            Rect done = anchored.get(element);
            if (done != null) return done;
            Rect box = measured.get(element), result = box;
            Anchor anchor = anchorOf(element);
            if (anchor != null && visiting.add(element)) {
                Rect to = reference(anchor);
                if (to != null) {
                    int x = at(to.x(), to.width(), anchor.h(), box.width()), y = at(to.y(), to.height(), anchor.v(), box.height());
                    // The box is where the saved position (anchor.x, y) shows: module offsets are carried over.
                    result = new Rect(box.x() + x - anchor.x(), box.y() + y - anchor.y(), box.width(), box.height());
                }
                visiting.remove(element);
            }
            anchored.put(element, result);
            return result;
        }

        private Rect reference(Anchor anchor) {
            if (anchor.to() == null) return new Rect(0, 0, width, height);
            if (anchor.to().equals(HudSettings.HOTBAR)) return hotbar;
            HudElement holder = byName.get(anchor.to());
            if (holder == null || visiting.contains(holder)) return null;
            Rect box = anchored(holder);
            Anchor own = anchorOf(holder);
            // The holder keeps at least the room it had when the layout was saved, grown from the point it is pinned at.
            int pinX = own != null ? own.h().pin() : far(box.x(), box.width(), width) ? 2 : 0;
            int pinY = own != null ? own.v().pin() : far(box.y(), box.height(), height) ? 2 : 0;
            int w = Math.max(box.width(), anchor.h().room()), h = Math.max(box.height(), anchor.v().room());
            return new Rect(box.x() - pinX * (w - box.width()) / 2, box.y() - pinY * (h - box.height()) / 2, w, h);
        }
    }

    /**
     * Keeps each connected block on screen as one: a HUD with everything docked to it (the hotbar not included), and each saved
     * group. Docked HUDs never slide into each other at the screen's edges.
     */
    public static void clamp(Map<HudElement, Rect> placed, int width, int height) {
        Map<HudElement, HudElement> parent = new IdentityHashMap<>();
        Map<String, HudElement> byName = new HashMap<>();
        for (HudElement element : placed.keySet()) if (element.getModuleName() != null) byName.put(element.getModuleName(), element);
        HudSettings settings = HudSettings.getInstance();
        for (HudElement element : placed.keySet()) {
            Anchor anchor = anchorOf(element);
            HudElement holder = anchor == null || anchor.to() == null ? null : byName.get(anchor.to());
            if (holder != null) join(parent, element, holder);
            var group = settings.getGroups().isEmpty() ? null : settings.getGroupMembers(element.getModuleName());
            if (group != null) for (String name : group) if (byName.containsKey(name)) join(parent, element, byName.get(name));
        }
        Map<HudElement, Rect> blocks = new IdentityHashMap<>();
        for (var entry : placed.entrySet()) blocks.merge(root(parent, entry.getKey()), entry.getValue(), (a, b) -> HudGroupLayout.union(java.util.List.of(a, b)));
        for (var entry : placed.entrySet()) {
            var delta = HudGroupLayout.clampDelta(blocks.get(root(parent, entry.getKey())), 0, 0, width, height);
            if (delta.x() != 0 || delta.y() != 0) entry.setValue(HudGroupLayout.translate(entry.getValue(), delta));
        }
    }

    private static HudElement root(Map<HudElement, HudElement> parent, HudElement element) {
        HudElement next;
        while ((next = parent.get(element)) != null) element = next;
        return element;
    }

    private static void join(Map<HudElement, HudElement> parent, HudElement a, HudElement b) {
        HudElement ra = root(parent, a), rb = root(parent, b);
        if (ra != rb) parent.put(ra, rb);
    }

    /**
     * Anchors these saved HUDs where they show now. shown holds every HUD's box this frame; dockable picks the ones that can hold
     * another (those drawn); g is the graphics they are laid out on. A HUD docked against the hotbar, or beside it, holds on to
     * the hotbar; one docked against a HUD that holds it (the one nearer its screen edge) holds on to that HUD; a saved group's
     * other members hold on to its first one; everything else to the nearest screen edge or centre.
     */
    public static void anchor(Collection<HudElement> which, Map<HudElement, Rect> shown, Predicate<HudElement> dockable, LadsGraphics g) {
        HudSettings settings = HudSettings.getInstance();
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        Rect hotbar = hotbar(g);
        var anchoring = new java.util.ArrayList<HudElement>();
        for (HudElement element : which) {
            String name = element.getModuleName();
            if (name == null || !element.usesSavedPosition() || settings.getPosition(name) == null || shown.get(element) == null) continue;
            anchoring.add(element);
            settings.setAnchor(name, null); // re-anchored together: old links among them cannot make a loop
        }
        for (HudElement element : anchoring) {
            String name = element.getModuleName(), to = null;
            Rect box = shown.get(element), ref = new Rect(0, 0, width, height);
            boolean docked = false;
            if (byHotbar(box, hotbar, width)) { to = HudSettings.HOTBAR; ref = hotbar; }
            else {
                HudElement best = null;
                int bestEdge = 0;
                for (var entry : shown.entrySet()) {
                    HudElement other = entry.getKey();
                    if (other == element || other.getModuleName() == null || !dockable.test(other)) continue;
                    int edge = holds(entry.getValue(), box, width, height);
                    if (edge > bestEdge && !loops(other.getModuleName(), name)) { best = other; bestEdge = edge; }
                }
                Set<String> group = best != null || settings.getGroups().isEmpty() ? null : settings.getGroupMembers(name);
                HudElement first = null; // the group's first laid-out member (by name) holds the rest
                if (group != null) for (HudElement other : shown.keySet())
                    if (other.getModuleName() != null && group.contains(other.getModuleName()) && (first == null || other.getModuleName().compareTo(first.getModuleName()) < 0)) first = other;
                if (first != null && first != element && !loops(first.getModuleName(), name)) best = first;
                if (best != null) { to = best.getModuleName(); ref = shown.get(best); docked = true; }
            }
            int[] at = settings.getPosition(name);
            // Module offsets (the Scoreboard's X/Y Offset) stay on top of the anchored place, as they were before it.
            int shiftX = element.getDisplayX(g) - element.getX(), shiftY = element.getDisplayY(g) - element.getY();
            boolean screen = to == null;
            Axis h = fit(ref.x(), ref.width(), box.x(), box.width(), shiftX, far(box.x(), box.width(), width), docked ? ref.width() : 0, screen);
            Axis v = fit(ref.y(), ref.height(), box.y(), box.height(), shiftY, far(box.y(), box.height(), height), docked ? ref.height() : 0, screen);
            settings.setAnchor(name, new Anchor(at[0], at[1], to, h, v));
        }
    }

    /** Anchors every legacy HUD in shown (HudAnchors.legacy) where it shows, docked to the dockable ones. */
    public static void adopt(Map<HudElement, Rect> shown, Predicate<HudElement> dockable, LadsGraphics g) {
        var legacy = new java.util.ArrayList<HudElement>();
        for (HudElement element : shown.keySet()) if (legacy(element)) legacy.add(element);
        if (!legacy.isEmpty()) anchor(legacy, shown, dockable, g);
    }

    /** Whether name's chain of holders reaches target (anchoring target to name would close a loop). */
    private static boolean loops(String name, String target) {
        for (int depth = 0; name != null && depth < 256; depth++) {
            if (name.equals(target)) return true;
            Anchor anchor = HudSettings.getInstance().getAnchor(name);
            name = anchor == null ? null : anchor.to();
        }
        return name != null;
    }

    /**
     * The pair of points (box ref, HUD pin) with the smallest offset; ties pin the HUD at its nearer screen edge, so it grows
     * inwards. On the screen a HUD is pinned at its own like point (its left edge to the left edge, its centre to the centre).
     */
    static Axis fit(int start, int size, int pos, int span, int shift, boolean far, int room, boolean like) {
        int bestRef = 0, bestPin = 0;
        long best = Long.MAX_VALUE;
        for (int pin : far ? FAR : NEAR) for (int ref : far ? FAR : NEAR) {
            if (like && ref != pin) continue;
            long off = (long) pos + pin * span / 2 - (start + ref * size / 2);
            if (Math.abs(off) < Math.abs(best)) { best = off; bestRef = ref; bestPin = pin; }
        }
        return new Axis(bestRef, bestPin, (int) (best - shift), room);
    }

    /** Whether a span is nearer the end of the screen than its start. */
    private static boolean far(int pos, int span, int screen) { return 2L * pos + span > screen; }

    /** The edge length the HUD at r shares with the one at s when r holds it: docked, with r nearer its own screen edge. */
    static int holds(Rect r, Rect s, int width, int height) {
        int alongX = overlap(r.x(), r.right(), s.x(), s.right()), alongY = overlap(r.y(), r.bottom(), s.y(), s.bottom());
        if (alongX > 0 && docked(r.bottom(), s.y())) return r.y() <= height - s.bottom() ? alongX : 0; // r above s
        if (alongX > 0 && docked(s.bottom(), r.y())) return height - r.bottom() < s.y() ? alongX : 0; // r below s
        if (alongY > 0 && docked(r.right(), s.x())) return r.x() <= width - s.right() ? alongY : 0; // r left of s
        if (alongY > 0 && docked(s.right(), r.x())) return width - r.right() < s.x() ? alongY : 0; // r right of s
        return 0;
    }

    /** On the hotbar, docked on top of it, or in its row nearer to it than to the side of the screen. */
    static boolean byHotbar(Rect s, Rect hotbar, int width) {
        if (overlap(s.x(), s.right(), hotbar.x(), hotbar.right()) > 0 && docked(s.bottom(), hotbar.y())) return true;
        int sideGap = Math.max(hotbar.x() - s.right(), s.x() - hotbar.right());
        return overlap(s.y(), s.bottom(), hotbar.y(), hotbar.bottom()) > 0 && sideGap <= Math.min(s.x(), width - s.right());
    }

    /** Whether two boxes are docked edge to edge (at most DOCK apart, overlapping along that edge). */
    public static boolean touching(Rect a, Rect b) {
        boolean alongX = overlap(a.x(), a.right(), b.x(), b.right()) > 0, alongY = overlap(a.y(), a.bottom(), b.y(), b.bottom()) > 0;
        return alongX && (docked(a.bottom(), b.y()) || docked(b.bottom(), a.y())) || alongY && (docked(a.right(), b.x()) || docked(b.right(), a.x()));
    }

    private static boolean docked(int end, int start) { return start - end >= 0 && start - end <= DOCK; }

    private static int overlap(int a0, int a1, int b0, int b1) { return Math.min(a1, b1) - Math.max(a0, b0); }

    /** Name of what a HUD's anchor holds on to: null (screen), HudSettings.HOTBAR or a HUD (QA and tests). */
    public static String holder(String name) {
        Anchor anchor = HudSettings.getInstance().getAnchor(name);
        return anchor == null ? null : Objects.requireNonNullElse(anchor.to(), "screen");
    }
}

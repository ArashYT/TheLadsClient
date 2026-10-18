package com.thelads.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Global, client-side HUD preferences shared by every HUD element:
 *  - globalColor: the colour used by any HUD module set to "Global".
 *  - textShadow:  whether HUD text is drawn with a shadow.
 *  - positions:   saved x/y per HUD element (keyed by module name), and the anchor each one keeps across GUI scales.
 *
 * Lives in the config package and holds no client/render imports so it can be
 * serialized by ConfigManager without dragging in client-only classes.
 */
public class HudSettings {
    private static final HudSettings INSTANCE = new HudSettings();

    private int globalColor = 0xFFFFFFFF;      // ARGB, opaque white
    private int globalBackground = 0x80000000; // ARGB, 50% black
    private boolean textShadow = true;
    private boolean backgrounds = true;
    private final List<Integer> favoriteColors = new ArrayList<>();
    private boolean softShadow = false; // 1.9.1: the global shadow is On by default (it was Soft)
    private int hudScale = 0; // the Lads HUD's own GUI scale; 0 follows vanilla's (1.9.0)
    private boolean hudFpsCapEnabled = true; // 1.9.2: the HUD FPS cap is on by default to 60
    private int hudFpsLimit = 60; // 60 by default; 0 means Unlimited
    public static final int[] HUD_FPS_LEVELS = { 25, 30, 60, 75, 120, 144, 165, 180, 240, 0 };

    public boolean isHudFpsCapEnabled() { return hudFpsCapEnabled; }
    public void setHudFpsCapEnabled(boolean value) { hudFpsCapEnabled = value; }
    public int getHudFpsLimit() { return hudFpsLimit; }
    public void setHudFpsLimit(int limit) { hudFpsLimit = Math.max(0, limit); }
    public boolean isHudFpsUnlimited() { return !hudFpsCapEnabled || hudFpsLimit <= 0 || hudFpsLimit > 240; }
    public static String formatFpsLimit(int limit) {
        if (limit <= 0 || limit > 240) return "Unlimited";
        if (limit == 60) return "60 (Recommended)";
        return String.valueOf(limit);
    }
    public static int nextFpsLevel(int currentLimit, int direction) {
        int currentIdx = -1;
        for (int i = 0; i < HUD_FPS_LEVELS.length; i++) {
            if (HUD_FPS_LEVELS[i] == currentLimit || (HUD_FPS_LEVELS[i] == 0 && (currentLimit <= 0 || currentLimit > 240))) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx == -1) currentIdx = 2;
        int next = Math.max(0, Math.min(HUD_FPS_LEVELS.length - 1, currentIdx + direction));
        return HUD_FPS_LEVELS[next];
    }
    /** The Lads HUD's GUI scale: 0 (Auto) follows vanilla's GUI scale, 1 and up is fixed (HudScale caps it at the window size). */
    public int getHudScale() { return hudScale; }
    public void setHudScale(int scale) { hudScale = Math.max(0, Math.min(16, scale)); }
    public boolean isSoftShadow() { return softShadow; }
    public void setSoftShadow(boolean value) { softShadow = value; }
    public boolean isBackgrounds() { return backgrounds; }
    public void setBackgrounds(boolean value) { backgrounds = value; }
    public List<Integer> getFavoriteColors() { return favoriteColors; }
    private final Map<String, int[]> positions = new HashMap<>();
    private final Map<String, Anchor> anchors = new HashMap<>();
    private final List<Integer> fadePlaylist = new ArrayList<>();
    private final Set<String> locked = new HashSet<>();          // locked element names
    private final List<Set<String>> groups = new ArrayList<>();  // groups of element names
    private final Set<String> excludedModules = new LinkedHashSet<>();
    private final Set<String> excludedExternalMods = new LinkedHashSet<>();

    public boolean isModuleExempt(String name) { return name != null && excludedModules.contains(name); }
    public void setModuleExempt(String name, boolean exempt) {
        if (name == null) return;
        if (exempt) excludedModules.add(name); else excludedModules.remove(name);
    }
    public boolean isExternalModExempt(String modId) { return modId != null && excludedExternalMods.contains(modId); }
    public void setExternalModExempt(String modId, boolean exempt) {
        if (modId == null) return;
        if (exempt) excludedExternalMods.add(modId); else excludedExternalMods.remove(modId);
    }
    public Set<String> getExcludedModules() { return excludedModules; }
    public Set<String> getExcludedExternalMods() { return excludedExternalMods; }
    public void replaceExcludedModules(Collection<String> names) {
        excludedModules.clear();
        if (names != null) for (String n : names) if (validName(n)) excludedModules.add(n);
    }
    public void replaceExcludedExternalMods(Collection<String> names) {
        excludedExternalMods.clear();
        if (names != null) for (String n : names) if (validName(n)) excludedExternalMods.add(n);
    }

    public HudSettings() {
        resetToDefaults();
    }

    private void defaultHud(String name, int x, int y, String to, int hx, int hp, int ho, int hr, int vy, int vp, int vo, int vr) {
        positions.put(name, new int[] { x, y });
        anchors.put(name, new Anchor(x, y, to, new Axis(hx, hp, ho, hr), new Axis(vy, vp, vo, vr)));
    }

    /** Restores the shipped default HUD positions, anchors, and groups. */
    public void resetToDefaults() {
        globalColor = 0xFFFFFFFF;
        globalBackground = 0x80000000;
        textShadow = true;
        softShadow = false;
        backgrounds = true;
        hudScale = 0;
        hudFpsCapEnabled = true;
        hudFpsLimit = 60;
        favoriteColors.clear();
        fadePlaylist.clear();
        excludedModules.clear();
        excludedExternalMods.clear();
        positions.clear();
        anchors.clear();
        groups.clear();
        locked.clear();

        defaultHud("Memory", 4, 310, null, 0, 0, 4, 0, 1, 1, 66, 0);
        defaultHud("CPS", 100, 0, null, 0, 0, 100, 0, 0, 0, 0, 0);
        defaultHud("Stopwatch", 100, 16, "CPS", 0, 0, 0, 58, 2, 0, 0, 16);
        defaultHud("ServerAddress", 122, 484, null, 0, 0, 122, 0, 2, 2, -5, 0);
        defaultHud("FPS", 4, 84, "Keystrokes", 0, 0, 0, 64, 2, 0, 2, 76);
        defaultHud("BossBar", 389, 6, null, 1, 1, 0, 0, 0, 0, 6, 0);
        defaultHud("Paperdoll", 740, 3, null, 2, 2, -150, 0, 0, 0, 3, 0);
        defaultHud("Time", 907, 19, "Speed", 2, 2, 0, 60, 2, 0, 0, 16);
        defaultHud("Potion Effects", 4, 160, "Coordinates", 0, 0, 0, 68, 2, 0, 2, 38);
        defaultHud("Clock", 252, 0, "PortalCoordinates", 2, 0, 0, 94, 0, 0, 0, 16);
        defaultHud("Direction", 450, 30, null, 1, 1, 0, 0, 0, 0, 30, 0);
        defaultHud("Voice Chat Group", 4, 475, null, 0, 0, 4, 0, 2, 2, -2, 0);
        defaultHud("PortalCoordinates", 158, 0, "CPS", 2, 0, 0, 58, 0, 0, 0, 16);
        defaultHud("Hunger", 640, 484, HOTBAR, 2, 0, 69, 0, 1, 1, 0, 0);
        defaultHud("Biome", 823, 3, "Speed", 0, 2, 0, 60, 0, 0, 0, 16);
        defaultHud("Scoreboard", 803, 232, null, 2, 2, -2, 0, 1, 1, 0, 0);
        defaultHud("Keystrokes", 4, 6, null, 0, 0, 4, 0, 0, 0, 6, 0);
        defaultHud("PingHUD", 4, 102, "FPS", 0, 0, 0, 60, 2, 0, 2, 16);
        defaultHud("ArmorHUD", 270, 478, HOTBAR, 0, 2, -37, 0, 2, 2, -3, 0);
        defaultHud("Speed", 897, 3, null, 2, 2, -3, 0, 0, 0, 3, 0);
        defaultHud("Health", 640, 468, HOTBAR, 2, 0, 69, 0, 0, 2, 3, 0);
        defaultHud("Toggle Sprint & Sneak", 843, 487, null, 2, 2, -2, 0, 2, 2, -2, 0);
        defaultHud("ItemCounter", 252, 16, "Clock", 0, 0, 0, 70, 2, 0, 0, 16);
        defaultHud("Coordinates", 4, 120, "PingHUD", 0, 0, 0, 68, 2, 0, 2, 16);
        defaultHud("Minimap", 819, 60, null, 2, 2, -2, 0, 0, 0, 60, 0);
        defaultHud("ReachDisplay", 833, 19, "Biome", 2, 2, 10, 74, 2, 0, 0, 16);
        defaultHud("TexturePacks", 781, 459, null, 2, 2, -3, 0, 2, 2, -30, 0);
        defaultHud("XP", 690, 468, HOTBAR, 2, 0, 119, 0, 0, 2, 3, 0);
        defaultHud("Day", 192, 16, "PortalCoordinates", 2, 2, 0, 94, 2, 0, 0, 16);
        defaultHud("Voice Chat", 580, 478, HOTBAR, 2, 0, 9, 0, 2, 2, -3, 0);

        addGroup(Set.of("XP", "Hunger", "Health"));
        addGroup(Set.of("PingHUD", "Keystrokes", "Coordinates", "FPS"));
    }

    public static HudSettings getInstance() {
        return INSTANCE;
    }

    public int getGlobalColor() { return globalColor; }
    public void setGlobalColor(int globalColor) { this.globalColor = globalColor; }

    public int getGlobalBackground() { return globalBackground; }
    public void setGlobalBackground(int globalBackground) { this.globalBackground = globalBackground; }

    public boolean isTextShadow() { return textShadow; }
    public void setTextShadow(boolean textShadow) { this.textShadow = textShadow; }

    public void clearPositions() { positions.clear(); anchors.clear(); groups.clear(); locked.clear(); }

    /** Back to the HUD's organized default place (HudDefaults). */
    public void clearPosition(String name) { positions.remove(name); anchors.remove(name); }

    public Map<String, int[]> getPositions() { return positions; }

    /** A position saved without an anchor; the next layout anchors it where it shows (HudAnchors). */
    public void setPosition(String name, int x, int y) {
        positions.put(name, new int[] { x, y });
        anchors.remove(name);
    }

    public int[] getPosition(String name) {
        return positions.get(name);
    }

    /**
     * Where a saved HUD sits when the GUI scale, the window or a neighbour changes (HudAnchors, 1.9.0): {@code to} is null for the
     * screen, {@link #HOTBAR} for the vanilla hotbar, or the HUD it is docked against; per axis a point of the HUD keeps an offset
     * from a point of that box. It belongs to the saved position (x, y) it was made for, so a position changed without it is
     * anchored again where it shows.
     */
    public record Anchor(int x, int y, String to, Axis h, Axis v) {}
    /**
     * One axis of an anchor: the HUD's point at pin/2 of its size sits off pixels from the box's point at ref/2 of its size
     * (0 start, 1 centre, 2 end). room is the docked-to HUD's size when it was saved: it keeps at least that much room.
     */
    public record Axis(int ref, int pin, int off, int room) {}
    public static final String HOTBAR = "#hotbar";

    /** The anchor of a saved position, or null (none saved, or saved for another position). */
    public Anchor getAnchor(String name) {
        Anchor anchor = anchors.get(name);
        int[] at = positions.get(name);
        return anchor != null && at != null && at.length >= 2 && at[0] == anchor.x() && at[1] == anchor.y() ? anchor : null;
    }

    public void setAnchor(String name, Anchor anchor) {
        if (!validName(name)) return;
        if (anchor == null) anchors.remove(name); else anchors.put(name, anchor);
    }

    public Map<String, Anchor> getAnchors() { return anchors; }

    // --- Custom Fade colour playlist ---

    public List<Integer> getFadePlaylist() {
        return fadePlaylist;
    }

    // --- Locked elements ---

    public boolean isLocked(String name) { return locked.contains(name); }

    public void setLocked(String name, boolean lock) {
        if (!validName(name)) return;
        if (lock) locked.add(name); else locked.remove(name);
    }

    public Set<String> getLocked() { return locked; }

    // --- Groups ---

    /** Returns the group index for this element name, or -1 if not grouped. */
    public int getGroupIndex(String name) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(name)) return i;
        }
        return -1;
    }

    /** Merge complete existing groups. Grouping never silently disconnects an unselected member. */
    public int addGroup(Set<String> names) {
        Set<String> merged = new LinkedHashSet<>();
        if(names!=null)names.stream().filter(HudSettings::validName).forEach(merged::add);
        boolean changed;
        do { changed=false; for(Set<String> group:groups)if(group.stream().anyMatch(merged::contains))changed|=merged.addAll(group); } while(changed);
        if(merged.size()<2)return -1;
        groups.removeIf(group->group.stream().anyMatch(merged::contains));
        groups.add(merged);
        return groups.size() - 1;
    }

    /** Remove a group (by index), leaving the elements ungrouped. */
    public void removeGroup(int idx) {
        if (idx >= 0 && idx < groups.size()) groups.remove(idx);
    }

    /** Get all names in the same group as `name`, including `name` itself. */
    public Set<String> getGroupMembers(String name) {
        for (Set<String> g : groups) {
            if (g.contains(name)) return Set.copyOf(g);
        }
        return null;
    }

    public List<Set<String>> getGroups() { return groups; }

    public void replaceGroups(Collection<? extends Collection<String>> values) {
        groups.clear();
        if(values!=null)for(Collection<String> value:values)if(value!=null)addGroup(new LinkedHashSet<>(value));
    }
    public void replaceLocked(Collection<String> values) {
        locked.clear();if(values!=null)values.stream().filter(HudSettings::validName).forEach(locked::add);
    }
    public void ungroup(Collection<String> names) {
        if(names!=null)groups.removeIf(group->group.stream().anyMatch(names::contains));
    }
    private static boolean validName(String name){return name!=null&&!name.isBlank()&&name.length()<=128;}

    /** Returns the playlist as an opaque-ARGB array, or null if too short to use. */
    public int[] getFadePalette() {
        if (fadePlaylist.size() < 2) {
            return null;
        }
        int[] a = new int[fadePlaylist.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = fadePlaylist.get(i) | 0xFF000000;
        }
        return a;
    }
}

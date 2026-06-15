package com.thelads.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global, client-side HUD preferences shared by every HUD element:
 *  - globalColor: the colour used by any HUD module set to "Global".
 *  - textShadow:  whether HUD text is drawn with a shadow.
 *  - positions:   saved x/y per HUD element (keyed by module name).
 *
 * Lives in the config package and holds no client/render imports so it can be
 * serialized by ConfigManager without dragging in client-only classes.
 */
public class HudSettings {
    private static final HudSettings INSTANCE = new HudSettings();

    private int globalColor = 0xFFFFFFFF;      // ARGB, opaque white
    private int globalBackground = 0x80000000; // ARGB, 50% black
    private boolean textShadow = true;
    private final Map<String, int[]> positions = new HashMap<>();
    private final List<Integer> fadePlaylist = new ArrayList<>();
    private final Set<String> locked = new HashSet<>();          // locked element names
    private final List<Set<String>> groups = new ArrayList<>();  // groups of element names

    public static HudSettings getInstance() {
        return INSTANCE;
    }

    public int getGlobalColor() { return globalColor; }
    public void setGlobalColor(int globalColor) { this.globalColor = globalColor; }

    public int getGlobalBackground() { return globalBackground; }
    public void setGlobalBackground(int globalBackground) { this.globalBackground = globalBackground; }

    public boolean isTextShadow() { return textShadow; }
    public void setTextShadow(boolean textShadow) { this.textShadow = textShadow; }

    public Map<String, int[]> getPositions() { return positions; }

    public void setPosition(String name, int x, int y) {
        positions.put(name, new int[] { x, y });
    }

    public int[] getPosition(String name) {
        return positions.get(name);
    }

    // --- Custom Fade colour playlist ---

    public List<Integer> getFadePlaylist() {
        return fadePlaylist;
    }

    // --- Locked elements ---

    public boolean isLocked(String name) { return locked.contains(name); }

    public void setLocked(String name, boolean lock) {
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

    /** Create a new group from a set of names. Returns the new group index. */
    public int addGroup(Set<String> names) {
        // Remove these names from any existing group first
        for (Set<String> g : groups) g.removeAll(names);
        groups.removeIf(Set::isEmpty);
        groups.add(new HashSet<>(names));
        return groups.size() - 1;
    }

    /** Remove a group (by index), leaving the elements ungrouped. */
    public void removeGroup(int idx) {
        if (idx >= 0 && idx < groups.size()) groups.remove(idx);
    }

    /** Get all names in the same group as `name`, including `name` itself. */
    public Set<String> getGroupMembers(String name) {
        for (Set<String> g : groups) {
            if (g.contains(name)) return g;
        }
        return null;
    }

    public List<Set<String>> getGroups() { return groups; }

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

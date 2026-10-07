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
    private boolean backgrounds = true;
    private final List<Integer> favoriteColors = new ArrayList<>();
    private boolean softShadow = true;
    private boolean hudFpsCapEnabled = false; // the HUD redraws every frame unless the player opts in to a cap
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
    public boolean isSoftShadow() { return softShadow; }
    public void setSoftShadow(boolean value) { softShadow = value; }
    public boolean isBackgrounds() { return backgrounds; }
    public void setBackgrounds(boolean value) { backgrounds = value; }
    public List<Integer> getFavoriteColors() { return favoriteColors; }
    private final Map<String, int[]> positions = new HashMap<>();
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

    public static HudSettings getInstance() {
        return INSTANCE;
    }

    public int getGlobalColor() { return globalColor; }
    public void setGlobalColor(int globalColor) { this.globalColor = globalColor; }

    public int getGlobalBackground() { return globalBackground; }
    public void setGlobalBackground(int globalBackground) { this.globalBackground = globalBackground; }

    public boolean isTextShadow() { return textShadow; }
    public void setTextShadow(boolean textShadow) { this.textShadow = textShadow; }

    public void clearPositions() { positions.clear(); groups.clear(); locked.clear(); }

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

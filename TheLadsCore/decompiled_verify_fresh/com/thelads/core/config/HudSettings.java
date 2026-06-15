/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HudSettings {
    private static final HudSettings INSTANCE = new HudSettings();
    private int globalColor = -1;
    private int globalBackground = Integer.MIN_VALUE;
    private boolean textShadow = true;
    private final Map<String, int[]> positions = new HashMap<String, int[]>();
    private final List<Integer> fadePlaylist = new ArrayList<Integer>();
    private final Set<String> locked = new HashSet<String>();
    private final List<Set<String>> groups = new ArrayList<Set<String>>();

    public static HudSettings getInstance() {
        return INSTANCE;
    }

    public int getGlobalColor() {
        return this.globalColor;
    }

    public void setGlobalColor(int globalColor) {
        this.globalColor = globalColor;
    }

    public int getGlobalBackground() {
        return this.globalBackground;
    }

    public void setGlobalBackground(int globalBackground) {
        this.globalBackground = globalBackground;
    }

    public boolean isTextShadow() {
        return this.textShadow;
    }

    public void setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
    }

    public Map<String, int[]> getPositions() {
        return this.positions;
    }

    public void setPosition(String name, int x, int y) {
        this.positions.put(name, new int[]{x, y});
    }

    public int[] getPosition(String name) {
        return this.positions.get(name);
    }

    public List<Integer> getFadePlaylist() {
        return this.fadePlaylist;
    }

    public boolean isLocked(String name) {
        return this.locked.contains(name);
    }

    public void setLocked(String name, boolean lock) {
        if (lock) {
            this.locked.add(name);
        } else {
            this.locked.remove(name);
        }
    }

    public Set<String> getLocked() {
        return this.locked;
    }

    public int getGroupIndex(String name) {
        for (int i = 0; i < this.groups.size(); ++i) {
            if (!this.groups.get(i).contains(name)) continue;
            return i;
        }
        return -1;
    }

    public int addGroup(Set<String> names) {
        for (Set<String> g : this.groups) {
            g.removeAll(names);
        }
        this.groups.removeIf(Set::isEmpty);
        this.groups.add(new HashSet<String>(names));
        return this.groups.size() - 1;
    }

    public void removeGroup(int idx) {
        if (idx >= 0 && idx < this.groups.size()) {
            this.groups.remove(idx);
        }
    }

    public Set<String> getGroupMembers(String name) {
        for (Set<String> g : this.groups) {
            if (!g.contains(name)) continue;
            return g;
        }
        return null;
    }

    public List<Set<String>> getGroups() {
        return this.groups;
    }

    public int[] getFadePalette() {
        if (this.fadePlaylist.size() < 2) {
            return null;
        }
        int[] a = new int[this.fadePlaylist.size()];
        for (int i = 0; i < a.length; ++i) {
            a[i] = this.fadePlaylist.get(i) | 0xFF000000;
        }
        return a;
    }
}


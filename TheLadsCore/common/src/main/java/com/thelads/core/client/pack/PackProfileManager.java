package com.thelads.core.client.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages named sets of active resource packs (Pack Profiles, parity with OneClient/Badlion).
 */
public final class PackProfileManager {
    private static final Map<String, List<String>> PROFILES = new LinkedHashMap<>();
    private static String activeProfileName = "";

    private PackProfileManager() {}

    public static synchronized void saveProfile(String name, List<String> packs) {
        if (name == null || name.trim().isEmpty()) return;
        String trimmed = name.trim();
        List<String> copy = packs == null ? Collections.emptyList() : new ArrayList<>(packs);
        PROFILES.put(trimmed, copy);
    }

    public static synchronized List<String> getProfile(String name) {
        if (name == null) return Collections.emptyList();
        List<String> list = PROFILES.get(name.trim());
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public static synchronized boolean deleteProfile(String name) {
        if (name == null) return false;
        boolean removed = PROFILES.remove(name.trim()) != null;
        if (name.trim().equalsIgnoreCase(activeProfileName)) {
            activeProfileName = "";
        }
        return removed;
    }

    public static synchronized List<String> getProfileNames() {
        return new ArrayList<>(PROFILES.keySet());
    }

    public static synchronized void setActiveProfile(String name) {
        activeProfileName = name == null ? "" : name.trim();
    }

    public static synchronized String getActiveProfile() {
        return activeProfileName;
    }

    public static synchronized void clear() {
        PROFILES.clear();
        activeProfileName = "";
    }

    /** Serializes profiles to a compact string format: "Profile1:packA,packB;Profile2:packC" */
    public static synchronized String serialize() {
        if (PROFILES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean firstProf = true;
        for (Map.Entry<String, List<String>> entry : PROFILES.entrySet()) {
            if (!firstProf) sb.append(';');
            firstProf = false;
            sb.append(entry.getKey()).append(':');
            List<String> packs = entry.getValue();
            for (int i = 0; i < packs.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(packs.get(i));
            }
        }
        return sb.toString();
    }

    /** Deserializes profiles from a string format. */
    public static synchronized void deserialize(String data) {
        PROFILES.clear();
        if (data == null || data.trim().isEmpty()) return;
        for (String prof : data.split(";")) {
            int colon = prof.indexOf(':');
            if (colon <= 0) continue;
            String name = prof.substring(0, colon).trim();
            String listStr = prof.substring(colon + 1).trim();
            List<String> packs = new ArrayList<>();
            if (!listStr.isEmpty()) {
                for (String p : listStr.split(",")) {
                    String pt = p.trim();
                    if (!pt.isEmpty()) packs.add(pt);
                }
            }
            PROFILES.put(name, packs);
        }
    }
}

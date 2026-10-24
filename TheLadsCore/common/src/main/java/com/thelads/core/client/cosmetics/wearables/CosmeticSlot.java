package com.thelads.core.client.cosmetics.wearables;

/**
 * Cosmetic slot identifiers matching the cosmetics backend (ALLOWED_SLOTS in cosmetics.py:45).
 */
public enum CosmeticSlot {
    CAPE("cape", "Capes"),
    HAT("hat", "Hats"),
    GLASSES("glasses", "Glasses"),
    EMOTE("emote", "Emotes");

    private final String id;
    private final String displayName;

    CosmeticSlot(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }

    public static CosmeticSlot fromId(String id) {
        if (id == null) return null;
        for (CosmeticSlot slot : values()) {
            if (slot.id.equalsIgnoreCase(id)) return slot;
        }
        return null;
    }
}

package com.thelads.core.client.cosmetics.wearables;

/**
 * Definition of an equipable cosmetic item.
 */
public final class CosmeticItem {
    private final String id;
    private final CosmeticSlot slot;
    private final String name;
    private final String description;
    private final String texture;

    public CosmeticItem(String id, CosmeticSlot slot, String name, String description, String texture) {
        this.id = id;
        this.slot = slot;
        this.name = name;
        this.description = description;
        this.texture = texture;
    }

    public String id() { return id; }
    public CosmeticSlot slot() { return slot; }
    public String name() { return name; }
    public String description() { return description; }
    public String texture() { return texture; }
    public String texturePath() { return texture; }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}

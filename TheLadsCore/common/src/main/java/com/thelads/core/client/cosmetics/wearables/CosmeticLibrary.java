package com.thelads.core.client.cosmetics.wearables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in catalogue of wearable cosmetics supported by The Lads Client and backend service.
 */
public final class CosmeticLibrary {
    private static final Map<String, CosmeticItem> ITEMS = new LinkedHashMap<>();
    private static final Map<CosmeticSlot, List<CosmeticItem>> BY_SLOT = new LinkedHashMap<>();

    static {
        for (CosmeticSlot s : CosmeticSlot.values()) {
            BY_SLOT.put(s, new ArrayList<>());
        }

        // Capes (64x32)
        register(new CosmeticItem("lads:cape_default", CosmeticSlot.CAPE, "The Lads Cape", "Classic dark navy blue cape with cyan emblem.", "textures/cosmetics/cape_default.png"));
        register(new CosmeticItem("lads:cape_aurora", CosmeticSlot.CAPE, "Aurora Cape", "Vibrant aurora borealis waves over a night sky.", "textures/cosmetics/cape_aurora.png"));
        register(new CosmeticItem("lads:cape_discord", CosmeticSlot.CAPE, "Discord Cloak", "Exclusive reward cloak for linking your Discord account.", "textures/cosmetics/cape_discord.png"));
        register(new CosmeticItem("lads:cape_fire", CosmeticSlot.CAPE, "Inferno Cape", "Fiery cape woven from molten embers.", "textures/cosmetics/cape_fire.png"));

        // Hats (64x64)
        register(new CosmeticItem("lads:hat_top", CosmeticSlot.HAT, "Classic Top Hat", "Dapper black top hat with ruby red ribbon.", "textures/cosmetics/hat_top.png"));
        register(new CosmeticItem("lads:hat_halo", CosmeticSlot.HAT, "Golden Halo", "Angelic glowing ring floating above your head.", "textures/cosmetics/hat_halo.png"));
        register(new CosmeticItem("lads:hat_crown", CosmeticSlot.HAT, "Royal Crown", "Gleaming golden crown set with noble jewels.", "textures/cosmetics/hat_crown.png"));
        register(new CosmeticItem("lads:hat_beanie", CosmeticSlot.HAT, "Cozy Beanie", "Warm knitted winter beanie with pom-pom.", "textures/cosmetics/hat_beanie.png"));

        // Glasses (64x64)
        register(new CosmeticItem("lads:glasses_sunglasses", CosmeticSlot.GLASSES, "Dark Sunglasses", "Sleek polarized dark shades.", "textures/cosmetics/glasses_sunglasses.png"));
        register(new CosmeticItem("lads:glasses_goggles", CosmeticSlot.GLASSES, "Steampunk Goggles", "Brass framed engineer goggles with cyan lenses.", "textures/cosmetics/glasses_goggles.png"));
        register(new CosmeticItem("lads:glasses_bandana", CosmeticSlot.GLASSES, "Ninja Mask", "Shadow bandana covering lower face.", "textures/cosmetics/glasses_bandana.png"));
    }

    private CosmeticLibrary() {}

    private static void register(CosmeticItem item) {
        ITEMS.put(item.id(), item);
        BY_SLOT.get(item.slot()).add(item);
    }

    public static CosmeticItem get(String id) {
        return id == null ? null : ITEMS.get(id);
    }

    public static List<CosmeticItem> getItems(CosmeticSlot slot) {
        List<CosmeticItem> list = BY_SLOT.get(slot);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public static List<CosmeticItem> allItems() {
        return new ArrayList<>(ITEMS.values());
    }
}

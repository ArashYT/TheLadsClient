package com.thelads.core.client.cosmetics.wearables;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.cosmetics.net.CosmeticsLink;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.modules.CosmeticsModule;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State and manager for all wearable cosmetics (capes, hats, glasses).
 * Handles local equips, remote equip syncing, visibility filtering, and link code states.
 */
public final class CosmeticsManager {
    private static final CosmeticsManager INSTANCE = new CosmeticsManager();

    private final Map<UUID, Map<CosmeticSlot, String>> remoteEquipped = new ConcurrentHashMap<>();
    private volatile String activeLinkCode;
    private volatile long linkCodeExpiresAt;

    private CosmeticsManager() {}

    public static CosmeticsManager get() {
        return INSTANCE;
    }

    /** Returns currently equipped item id for the given slot on a player (or null). */
    public String getEquipped(UUID uuid, CosmeticSlot slot) {
        if (uuid == null || slot == null) return null;
        LadsGameBridge.Session session = LadsGameBridge.get().session();
        if (session != null && uuid.equals(session.uuid())) {
            return getLocalEquipped(slot);
        }
        Map<CosmeticSlot, String> playerMap = remoteEquipped.get(uuid);
        String val = playerMap == null ? null : playerMap.get(slot);
        if (val == null && session == null) {
            // In unit tests or offline without session bridge
            return getLocalEquipped(slot);
        }
        return val;
    }

    public String getEquippedCape(UUID uuid) {
        return getEquipped(uuid, CosmeticSlot.CAPE);
    }

    public String getEquippedHat(UUID uuid) {
        return getEquipped(uuid, CosmeticSlot.HAT);
    }

    public String getEquippedGlasses(UUID uuid) {
        return getEquipped(uuid, CosmeticSlot.GLASSES);
    }

    /** Returns currently equipped item id for the local player. */
    public String getLocalEquipped(CosmeticSlot slot) {
        CosmeticsModule module = CosmeticsModule.get();
        if (slot == CosmeticSlot.CAPE) {
            String val = module.equippedCape.get();
            return val == null || val.isBlank() ? null : val.trim();
        } else if (slot == CosmeticSlot.HAT) {
            String val = module.equippedHat.get();
            return val == null || val.isBlank() ? null : val.trim();
        } else if (slot == CosmeticSlot.GLASSES) {
            String val = module.equippedGlasses.get();
            return val == null || val.isBlank() ? null : val.trim();
        }
        return null;
    }

    /** Equips or unequips (itemId == null) a slot for the local player. */
    public void setLocalEquipped(CosmeticSlot slot, String itemId) {
        if (slot == null) return;
        CosmeticsModule module = CosmeticsModule.get();
        String val = itemId == null ? "" : itemId.trim();
        if (slot == CosmeticSlot.CAPE) {
            module.equippedCape.set(val);
        } else if (slot == CosmeticSlot.HAT) {
            module.equippedHat.set(val);
        } else if (slot == CosmeticSlot.GLASSES) {
            module.equippedGlasses.set(val);
        }
        module.touch();
        ConfigManager.save();

        // Broadcast to server if connected
        CosmeticsLink.get().sendEquip(slot.id(), itemId);
    }

    /** Called when an equip message arrives from the network. */
    public void onRemoteEquip(UUID uuid, String slotName, String itemId) {
        if (uuid == null || slotName == null) return;
        CosmeticSlot slot = CosmeticSlot.fromId(slotName);
        if (slot == null) return;

        remoteEquipped.compute(uuid, (key, map) -> {
            if (map == null) map = new ConcurrentHashMap<>();
            if (itemId == null || itemId.isBlank()) {
                map.remove(slot);
            } else {
                map.put(slot, itemId.trim());
            }
            return map.isEmpty() ? null : map;
        });
    }

    /** Remove all remote cosmetics for a player who left. */
    public void removeRemote(UUID uuid) {
        if (uuid != null) remoteEquipped.remove(uuid);
    }

    /** Whether cosmetics of the given slot should render on this player according to settings. */
    public boolean shouldRender(UUID uuid, CosmeticSlot slot) {
        if (uuid == null || slot == null) return false;
        CosmeticsModule module = CosmeticsModule.get();

        // Per-slot checks
        if (slot == CosmeticSlot.CAPE && !module.showCapes.get()) return false;
        if (slot == CosmeticSlot.HAT && !module.showHats.get()) return false;
        if (slot == CosmeticSlot.GLASSES && !module.showGlasses.get()) return false;

        // Visibility filter: 0=Everyone, 1=Friends Only, 2=Self Only, 3=None
        int vis = module.visibility.getIndex();
        if (vis == 3) return false; // None

        LadsGameBridge.Session session = LadsGameBridge.get().session();
        boolean isSelf = (session != null && uuid.equals(session.uuid())) || (session == null && !remoteEquipped.containsKey(uuid));
        if (isSelf) return true;

        if (vis == 2) return false; // Self only

        // vis == 1: "Friends Only (needs Lads friends - coming soon)" - falls back to showOthers until social lane friends list is wired; vis == 0: Everyone
        return vis == 0 || module.showOthers.get();
    }

    public String activeLinkCode() {
        if (activeLinkCode != null && System.currentTimeMillis() <= linkCodeExpiresAt) {
            return activeLinkCode;
        }
        return null;
    }

    public void setLinkCode(String code, int ttlSeconds) {
        this.activeLinkCode = code;
        this.linkCodeExpiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
    }

    public void clear() {
        remoteEquipped.clear();
        activeLinkCode = null;
        linkCodeExpiresAt = 0;
    }
}

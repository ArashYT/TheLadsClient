package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SlotLockingModule;
import java.util.HashSet;
import java.util.Set;

/**
 * Slot locking and accidental drop prevention.
 * Locked slots cannot be dropped via Q or accidentally shifted away.
 */
public final class SlotLocking {
    private static final Set<Integer> LOCKED_SLOTS = new HashSet<>();

    private SlotLocking() {}

    public static boolean enabled() {
        Module mod = ModuleManager.getInstance().getModule(SlotLockingModule.NAME);
        return mod != null && mod.isEnabled();
    }

    public static synchronized boolean isSlotLocked(int slotIndex) {
        return enabled() && LOCKED_SLOTS.contains(slotIndex);
    }

    public static synchronized void toggleLock(int slotIndex) {
        if (!LOCKED_SLOTS.add(slotIndex)) {
            LOCKED_SLOTS.remove(slotIndex);
        }
    }

    public static synchronized void setSlotLocked(int slotIndex, boolean locked) {
        if (locked) LOCKED_SLOTS.add(slotIndex);
        else LOCKED_SLOTS.remove(slotIndex);
    }

    public static synchronized void clearLocks() {
        LOCKED_SLOTS.clear();
    }

    public static boolean canDropSlot(int slotIndex) {
        return !isSlotLocked(slotIndex);
    }
}

package com.thelads.core.client;

public final class GappleTracker {
    private static final GappleTracker INSTANCE = new GappleTracker();
    public static GappleTracker get() { return INSTANCE; }

    private boolean eating;
    private int preEatCount;
    private int eatSlot = -1;

    private GappleTracker() {}

    public synchronized void reset() {
        eating = false;
        preEatCount = 0;
        eatSlot = -1;
    }

    public synchronized void onTick(boolean isUsingItem, boolean isHoldingGapple, int slot, int heldGappleCount) {
        if (isUsingItem && isHoldingGapple) {
            if (!eating) {
                eating = true;
                preEatCount = heldGappleCount;
                eatSlot = slot;
            } else if (slot == eatSlot) {
                if (heldGappleCount < preEatCount) {
                    CombatTracker.get().onGapple();
                    preEatCount = heldGappleCount;
                }
            }
        } else if (eating) {
            if (slot == eatSlot && heldGappleCount < preEatCount) {
                CombatTracker.get().onGapple();
            }
            eating = false;
            eatSlot = -1;
            preEatCount = 0;
        }
    }
}

package com.thelads.core.client;

public final class GappleTracker {
    private static final GappleTracker INSTANCE = new GappleTracker();
    public static GappleTracker get() { return INSTANCE; }

    private boolean eating;
    private int preEatCount;
    private int eatSlot = -1;
    private int maxDuration;
    private int lastTicksUsed;

    private GappleTracker() {}

    public synchronized void reset() {
        eating = false;
        preEatCount = 0;
        eatSlot = -1;
        maxDuration = 0;
        lastTicksUsed = 0;
    }

    public synchronized void onTick(boolean isUsingItem, boolean isHoldingGapple, int slot, int slotGappleCount, int ticksUsed, int totalDuration) {
        if (isUsingItem && isHoldingGapple) {
            if (!eating) {
                eating = true;
                preEatCount = slotGappleCount;
                eatSlot = slot;
                maxDuration = totalDuration > 0 ? totalDuration : 32;
                lastTicksUsed = ticksUsed;
            } else if (slot == eatSlot) {
                lastTicksUsed = Math.max(lastTicksUsed, ticksUsed);
                if (slotGappleCount < preEatCount && lastTicksUsed >= (maxDuration - 2)) {
                    CombatTracker.get().onGapple();
                    preEatCount = slotGappleCount;
                }
            }
        } else if (eating) {
            // Eating just ended: only count if duration was completed (not cancelled/tapped)
            // and the gapple count in the eating slot actually decreased.
            if (slot == eatSlot && lastTicksUsed >= (maxDuration - 2) && slotGappleCount < preEatCount) {
                CombatTracker.get().onGapple();
            }
            eating = false;
            eatSlot = -1;
            preEatCount = 0;
            maxDuration = 0;
            lastTicksUsed = 0;
        }
    }
}

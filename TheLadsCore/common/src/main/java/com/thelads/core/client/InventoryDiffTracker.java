package com.thelads.core.client;

import com.thelads.core.client.hud.ItemTrackerHudElement;

public final class InventoryDiffTracker {
    private static final InventoryDiffTracker INSTANCE = new InventoryDiffTracker();
    public static InventoryDiffTracker get() { return INSTANCE; }

    private static final int MAX_SLOTS = 64;
    private int prevCount = -1;
    private final int[] prevIds = new int[MAX_SLOTS];
    private final int[] prevQtys = new int[MAX_SLOTS];
    private final String[] prevNames = new String[MAX_SLOTS];

    private int currCount = 0;
    private final int[] currIds = new int[MAX_SLOTS];
    private final int[] currQtys = new int[MAX_SLOTS];
    private final String[] currNames = new String[MAX_SLOTS];

    private InventoryDiffTracker() {}

    public synchronized void reset() {
        prevCount = -1;
        currCount = 0;
    }

    public synchronized void beginScan(boolean containerOpen) {
        if (containerOpen) {
            // While a container screen is open (e.g. chest, furnace), moving items between inventory
            // and container or rearranging slots is skipped to prevent false pickup/drop spam.
            // Baseline is reset so closing the container seamlessly takes a fresh snapshot.
            prevCount = -1;
            currCount = 0;
            return;
        }
        currCount = 0;
    }

    public synchronized void addScannedItem(int itemId, int count, String displayName) {
        if (count <= 0 || displayName == null || displayName.isEmpty()) return;
        for (int i = 0; i < currCount; i++) {
            if (currIds[i] == itemId && currNames[i].equals(displayName)) {
                currQtys[i] += count;
                return;
            }
        }
        if (currCount < MAX_SLOTS) {
            currIds[currCount] = itemId;
            currQtys[currCount] = count;
            currNames[currCount] = displayName;
            currCount++;
        }
    }

    public synchronized void endScan() {
        if (prevCount >= 0) {
            // Diff current items against previous snapshot
            for (int i = 0; i < currCount; i++) {
                int id = currIds[i];
                String name = currNames[i];
                int qty = currQtys[i];
                int prevIdx = findPrev(id, name);
                if (prevIdx >= 0) {
                    int delta = qty - prevQtys[prevIdx];
                    if (delta != 0) {
                        ItemTrackerHudElement.recordChange(name, delta);
                    }
                } else {
                    ItemTrackerHudElement.recordChange(name, qty);
                }
            }
            // Check for items that were in previous snapshot but completely removed
            for (int i = 0; i < prevCount; i++) {
                int id = prevIds[i];
                String name = prevNames[i];
                int currIdx = findCurr(id, name);
                if (currIdx < 0) {
                    ItemTrackerHudElement.recordChange(name, -prevQtys[i]);
                }
            }
        }

        // Copy current state to previous snapshot (reuse preallocated arrays)
        prevCount = currCount;
        for (int i = 0; i < currCount; i++) {
            prevIds[i] = currIds[i];
            prevQtys[i] = currQtys[i];
            prevNames[i] = currNames[i];
        }
    }

    private int findPrev(int id, String name) {
        for (int i = 0; i < prevCount; i++) {
            if (prevIds[i] == id && prevNames[i].equals(name)) return i;
        }
        return -1;
    }

    private int findCurr(int id, String name) {
        for (int i = 0; i < currCount; i++) {
            if (currIds[i] == id && currNames[i].equals(name)) return i;
        }
        return -1;
    }
}

package com.thelads.core.client;

public final class BlockTracker {
    private static final BlockTracker INSTANCE = new BlockTracker();
    public static BlockTracker get() { return INSTANCE; }

    private int blocksPlaced;
    private int lastUseBlockTick = -100;
    private int prevHeldSlot = -1;
    private int prevHeldCount = 0;
    private boolean prevHeldIsBlock = false;

    private BlockTracker() {}

    public synchronized void onBlockPlaced() {
        blocksPlaced++;
    }

    public synchronized void onBlockPlaced(int count) {
        if (count > 0) blocksPlaced += count;
    }

    public synchronized int getBlocksPlaced() {
        return blocksPlaced;
    }

    public synchronized void onUseKeyOnBlock(int currentTick) {
        this.lastUseBlockTick = currentTick;
    }

    public synchronized void checkHeldBlockTick(int currentTick, int slot, boolean isBlock, int count) {
        if (slot == prevHeldSlot && prevHeldIsBlock) {
            if (count < prevHeldCount && (currentTick - lastUseBlockTick) >= 0 && (currentTick - lastUseBlockTick) <= 2) {
                blocksPlaced += (prevHeldCount - count);
            }
        }
        prevHeldSlot = slot;
        prevHeldIsBlock = isBlock;
        prevHeldCount = count;
    }

    public synchronized void reset() {
        blocksPlaced = 0;
        lastUseBlockTick = -100;
        prevHeldSlot = -1;
        prevHeldCount = 0;
        prevHeldIsBlock = false;
    }
}

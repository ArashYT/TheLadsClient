package com.thelads.core.client;

public final class BlockTracker {
    private static final BlockTracker INSTANCE = new BlockTracker();
    public static BlockTracker get() { return INSTANCE; }

    private int blocksPlaced;

    private BlockTracker() {}

    public synchronized void onBlockPlaced() {
        blocksPlaced++;
    }

    public synchronized int getBlocksPlaced() {
        return blocksPlaced;
    }

    public synchronized void reset() {
        blocksPlaced = 0;
    }
}

package com.thelads.core.client.hud;

import com.thelads.core.client.BlockTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class BlockCounterHudElement extends TextHudElement {
    private int lastPlaced = -1;
    private int lastMode = -1;
    private int lastPrefix = -1;
    private String cachedText = "";

    public BlockCounterHudElement() {
        super(60);
        this.x = 5;
        this.y = 250;
        this.width = 70;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int placed = BlockTracker.get().getBlocksPlaced();
        int mode = optCycle("Mode", 0); // 0 = Placed blocks, 1 = Remaining held, 2 = Both
        int prefixIdx = optCycle("Prefix", 0); // 0 = Placed: , 1 = Blocks: , 2 = None

        if (editor && placed == 0) {
            placed = 48;
        }

        if (placed == lastPlaced && mode == lastMode && prefixIdx == lastPrefix) {
            return cachedText;
        }

        lastPlaced = placed;
        lastMode = mode;
        lastPrefix = prefixIdx;

        String prefix = switch (prefixIdx) {
            case 1 -> "Blocks: ";
            case 2 -> "";
            default -> "Placed: ";
        };

        if (mode == 1) {
            String heldText = g.getGame().getItemCountText(0);
            return cachedText = (heldText != null && !heldText.isEmpty()) ? heldText : "Blocks: 0";
        } else if (mode == 2) {
            String heldText = g.getGame().getItemCountText(0);
            return cachedText = prefix + placed + " (" + heldText + ")";
        } else {
            return cachedText = prefix + placed;
        }
    }
}

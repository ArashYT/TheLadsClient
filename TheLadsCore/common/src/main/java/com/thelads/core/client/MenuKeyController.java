package com.thelads.core.client;

/** Press-edge routing, independent of Minecraft so repeat/release behavior is testable. */
public final class MenuKeyController {
    public enum Action { PRESS, REPEAT, RELEASE }
    public enum Decision { PASS, CONSUME, OPEN, TRY_CLOSE }
    private boolean captured;
    private int capturedKey, capturedScan;

    public Decision key(int key, int scan, Action action, boolean matches,
                        boolean canOpen, boolean inMenu) {
        // Match the original physical input even if Controls changed its binding meanwhile.
        if (captured && key == capturedKey && scan == capturedScan) {
            if (action == Action.RELEASE) reset();
            return Decision.CONSUME;
        }
        if (action != Action.PRESS || !matches) return Decision.PASS;
        if (canOpen) {
            capture(key, scan);
            return Decision.OPEN;
        }
        return inMenu ? Decision.TRY_CLOSE : Decision.PASS;
    }

    public void capture(int key, int scan) {
        captured = true;
        capturedKey = key;
        capturedScan = scan;
    }

    public void reset() { captured = false; }
}

package com.thelads.core.v26_2.feature;

public final class BackgroundFrameLimitChecks {
    private static int passed;
    public static void main(String[] args) {
        BackgroundFrameLimit policy = new BackgroundFrameLimit();
        require(144, policy.apply(30, 144, true, 0, true, false, 15, 5, 0), "focused cap is never reduced to 30");
        require(240, policy.apply(60, 240, true, 1, true, false, 15, 5, 1), "focused menu uses configured cap");
        require(30, policy.apply(30, 144, false, 0, false, true, 15, 5, 2), "disabled keeps vanilla cap");
        require(10, policy.apply(10, 144, true, 2, false, true, 15, 5, 3), "Off keeps vanilla cap");
        require(17, policy.apply(144, 144, true, 0, false, false, 17, 3, 4), "Aggressive unfocused limit");
        require(3, policy.apply(10, 144, true, 0, false, true, 17, 3, 5), "hidden limit wins");
        require(3, policy.apply(10, 144, true, 1, true, true, 17, 3, 6), "iconified beats stale focus");
        require(144, policy.apply(144, 144, true, 0, true, false, 17, 3, 7), "focus restores immediately");
        require(144, policy.apply(144, 144, true, 1, false, false, 17, 3, 10), "Balanced starts grace period");
        require(144, policy.apply(144, 144, true, 1, false, false, 17, 3, 3_000_000_009L), "Balanced grace lasts three seconds");
        require(17, policy.apply(144, 144, true, 1, false, false, 17, 3, 3_000_000_010L), "Balanced throttles at three seconds");
        require(144, policy.apply(144, 144, true, 1, true, false, 17, 3, 3_000_000_011L), "Balanced resets on focus");
        require(144, policy.apply(144, 144, true, 1, false, false, 17, 3, 3_000_000_012L), "next Alt-Tab gets new grace period");
        require(12, policy.apply(12, 12, true, 0, false, false, 60, 30, 3_000_000_013L), "never raises low configured cap");
        require(12, policy.apply(12, 12, true, 0, false, true, 60, 30, 3_000_000_014L), "hidden never raises low configured cap");
        require(1, policy.apply(144, 144, true, 0, false, true, 15, 0, 3_000_000_015L), "corrupt hidden limit stays positive");
        System.out.println("PASS " + passed + " background FPS checks: focus, hidden, modes, grace period and cap preservation");
    }
    private static void require(int expected, int actual, String message) {
        if (actual != expected) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        passed++;
    }
}

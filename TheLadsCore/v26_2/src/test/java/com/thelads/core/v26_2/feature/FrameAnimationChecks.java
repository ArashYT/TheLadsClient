package com.thelads.core.v26_2.feature;

/** Native animation invariants; no Minecraft bootstrap or broken legacy suite required. */
public final class FrameAnimationChecks {
    private static int passed;
    public static void main(String[] args) {
        double expected = 160 * (1 - Math.exp(-18 * .25));
        for (int frames : new int[] { 8, 15, 30, 36, 60, 120 }) {
            FrameAnimation animation = new FrameAnimation();
            require(animation.update(0, 18, 1_000_000_000L, true) == 0, "initial position");
            double position = 0;
            for (int frame = 1; frame <= frames; frame++) {
                double next = animation.update(160, 18, 1_000_000_000L + 250_000_000L * frame / frames, true);
                require(next >= position && next <= 160, "no overshoot");
                position = next;
            }
            require(Math.abs(position - expected) < .00001, "same elapsed time gives same travel at all frame rates");
        }
        FrameAnimation animation = new FrameAnimation();
        animation.update(100, 18, 1_000_000_000L, true);
        require(animation.update(0, 18, 1_016_000_000L, false) == 0, "disable takes immediate effect");
        double moving = animation.update(100, 18, 1_032_000_000L, true);
        require(moving > 0 && moving < 100, "re-enabled resumes smoothly");
        require(animation.update(50, 18, 2_000_000_000L, true) == 50, "long pause snaps to current selection");
        require(animation.update(70, 18, 1_000_000_000L, true) == 70, "clock discontinuity resets");
        require(animation.update(0, 18, 1_000_000_000L, true) == 70, "duplicate frame timestamp does not move");
        System.out.println("PASS " + passed + " animation checks: frame-rate independence, bounded travel, toggles and pause reset");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        passed++;
    }
}

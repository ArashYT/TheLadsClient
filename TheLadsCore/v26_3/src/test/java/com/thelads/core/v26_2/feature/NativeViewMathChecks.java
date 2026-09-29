package com.thelads.core.v26_2.feature;

/** Dependency-free regression checks for frame-rate independence and safe rendering bounds. */
public final class NativeViewMathChecks {
    private static int passed;
    public static void main(String[] args) {
        double sixty = simulate(60, .42, 1, 1);
        for (int fps : new int[] {30, 60, 75, 120, 144, 240}) {
            require(Math.abs(simulate(fps, .42, 1, 1) - sixty) < .000001, "same displacement after one second at " + fps + " FPS");
            require(Math.abs(simulate(fps, -100, 1.5, 1)) <= .112501, "terminal fall is bounded at " + fps + " FPS");
        }
        require(sixty < 0, "jumping offsets camera down");
        require(simulate(60, -.42, 1, 1) > 0, "falling offsets camera up");
        require(Math.abs(simulate(60, .42, .5, 1) / sixty - .5) < .00001, "low intensity is half");
        require(Math.abs(simulate(60, .42, 1.5, 1) / sixty - 1.5) < .00001, "high intensity scales");
        require(simulate(60, .42, 1, 0) == 0, "zero accessibility strength disables motion");
        require(Math.abs(simulate(60, .42, 1, .25) / sixty - .25) < .00001, "reduced effects proportionally scale motion");
        var motion = new VerticalBob();
        long now = 1_000_000_000L;
        require(motion.update(.42, false, 1, 1, now, true) == 0, "first frame starts without jump");
        float previous = motion.update(.42, false, 1, 1, now += 16_666_667, true);
        require(previous < 0 && previous > -.05, "first motion step is eased");
        require(motion.update(.42, false, 1, 1, now, true) == previous, "repeated same-time sample cannot speed animation");
        for (int i = 0; i < 90; i++) previous = motion.update(0, true, 1, 1, now += 16_666_667, true);
        require(Math.abs(previous) < .000001, "grounded camera settles completely");
        require(motion.update(.42, false, 1, 1, now, false) == 0, "disabled resets instantly");
        motion.update(.42, false, 1, 1, now += 1, true);
        motion.update(.42, false, 1, 1, now += 16_666_667, true);
        require(motion.update(.42, false, 1, 1, now + 1_000_000_000L, true) == 0, "long suspension cannot cause motion jump");
        require(motion.update(Double.NaN, false, 1, 1, now, true) == 0, "invalid velocity cannot poison camera matrix");
        require(FarBlockDistance.resolve(64, 128, false) == 64, "disabled distance preserves vanilla");
        require(FarBlockDistance.resolve(64, 128, true) == 128, "default extension");
        require(FarBlockDistance.resolve(512, 128, true) == 512, "never shortens special renderer distance");
        require(FarBlockDistance.resolve(64, 8192, true) == 256, "configured distance has hard upper bound");
        require(FarBlockDistance.resolve(64, -2, true) == 64, "configured distance has hard lower bound");
        require(FarBlockDistance.resolve(64, Double.NaN, true) == 64, "invalid config keeps vanilla");
        System.out.println("PASS " + passed + " native view and distance math checks");
    }

    private static float simulate(int fps, double velocity, double intensity, double accessibility) {
        var motion = new VerticalBob();
        long start = 1_000_000_000L;
        motion.update(velocity, false, intensity, accessibility, start, true);
        float value = 0;
        for (int frame = 1; frame <= fps; frame++)
            value = motion.update(velocity, false, intensity, accessibility, start + Math.round(frame * 1_000_000_000d / fps), true);
        return value;
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
}

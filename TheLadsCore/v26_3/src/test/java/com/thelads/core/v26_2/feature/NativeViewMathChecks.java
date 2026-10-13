package com.thelads.core.v26_2.feature;

/** Dependency-free regression checks for frame-rate independence and safe rendering bounds. */
public final class NativeViewMathChecks {
    private static int passed;
    public static void main(String[] args) {
        require(FarBlockDistance.resolve(64, 128, false) == 64, "disabled distance preserves vanilla");
        require(FarBlockDistance.resolve(64, 128, true) == 128, "default extension");
        require(FarBlockDistance.resolve(512, 128, true) == 512, "never shortens special renderer distance");
        require(FarBlockDistance.resolve(64, 8192, true) == 256, "configured distance has hard upper bound");
        require(FarBlockDistance.resolve(64, -2, true) == 64, "configured distance has hard lower bound");
        require(FarBlockDistance.resolve(64, Double.NaN, true) == 64, "invalid config keeps vanilla");
        System.out.println("PASS " + passed + " native view and distance math checks");
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
}

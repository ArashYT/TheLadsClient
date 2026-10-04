package com.thelads.core.perf;

import java.util.Arrays;

/** Call sites for EnumValuesRewriterTest. Methods named read* only read the array; the rest let it escape. */
public final class EnumValuesSamples {
    public static Object stored;

    public static int readLoop() {
        int sum = 0;
        for (SampleColor color : SampleColor.values()) sum += color.ordinal() + 1;
        return sum;
    }

    public static int readIndexed(int i) {
        return SampleColor.values()[i].ordinal();
    }

    public static int readLength() {
        return SampleColor.values().length;
    }

    public static int readAlias() {
        SampleColor[] values = SampleColor.values();
        Object[] alias = values;
        int found = 0;
        for (int i = 0; i < alias.length; i++) if (alias[i] != null && values[i] instanceof SampleColor) found++;
        return found;
    }

    public static SampleColor[] returned() {
        return SampleColor.values();
    }

    public static int storedInField() {
        stored = SampleColor.values();
        return 1;
    }

    public static int written() {
        SampleColor[] values = SampleColor.values();
        values[0] = SampleColor.BLUE;
        return values[0].ordinal();
    }

    public static int writtenThroughAlias() {
        SampleColor[] values = SampleColor.values();
        Object[] alias = values;
        alias[1] = SampleColor.RED;
        return values[1].ordinal();
    }

    public static int passed() {
        return Arrays.asList(SampleColor.values()).size();
    }

    public static boolean sameArray() {
        return SampleColor.values() == SampleColor.values();
    }

    public static int writtenAfterBranch(boolean fresh) {
        SampleColor[] values = fresh ? SampleColor.values() : new SampleColor[] {SampleColor.RED};
        values[0] = SampleColor.GREEN;
        return values.length;
    }

    public static int comparedAcrossIterations() {
        Object previous = null;
        int changed = 0;
        for (int i = 0; i < 3; i++) {
            SampleColor[] current = SampleColor.values();
            if (previous != null && previous != current) changed++;
            previous = current;
        }
        return changed;
    }

    public static int captured() {
        SampleColor[] values = SampleColor.values();
        Runnable task = () -> values[0] = SampleColor.BLUE;
        task.run();
        return values[0].ordinal();
    }

    public static int cloned() {
        return SampleColor.values().clone().length;
    }

    public static int locked() {
        SampleColor[] values = SampleColor.values();
        synchronized (values) {
            return values.length;
        }
    }

    public static int notAnEnum() {
        return NotAnEnum.values().length;
    }

    /** A class with a values() that is not an enum's: never touched. */
    public static final class NotAnEnum {
        public static NotAnEnum[] values() {
            return new NotAnEnum[2];
        }
    }
}

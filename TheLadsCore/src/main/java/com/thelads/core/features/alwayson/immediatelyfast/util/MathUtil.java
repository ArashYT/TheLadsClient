/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.immediatelyfast.util;

public class MathUtil {
    private static final int DATA_BASE_UNIT = 1024;
    private static final String[] DATA_UNITS = new String[]{"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};

    public static String formatBytes(long bytes) {
        boolean negative = bytes < 0L;
        if ((bytes = Math.abs(bytes)) < 1024L) {
            return bytes + " B";
        }
        int exponent = (int)(Math.log(bytes) / Math.log(1024.0));
        return (negative ? "-" : "") + String.format("%.1f ", (double)bytes / Math.pow(1024.0, exponent)) + DATA_UNITS[exponent - 1];
    }
}


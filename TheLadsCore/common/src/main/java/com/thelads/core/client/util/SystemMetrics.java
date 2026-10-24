package com.thelads.core.client.util;

import java.lang.management.ManagementFactory;

public final class SystemMetrics {
    private SystemMetrics() {}

    /**
     * The whole system's CPU use in percent, or -1 when this JVM cannot tell. Through the com.sun.management interface, not
     * reflection on the bean's class: that class is not public (and its package not exported since Java 9), so invoking its methods
     * always failed. getSystemCpuLoad is the name both Java 8 (1.8.9) and 25 have; a JVM without the interface gets -1.
     */
    @SuppressWarnings("deprecation")
    public static double getCpuLoad() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                double d = os.getSystemCpuLoad();
                if (d >= 0) return d * 100.0;
            }
        } catch (Throwable ignored) {} // LinkageError where com.sun.management is missing
        return -1.0;
    }
}

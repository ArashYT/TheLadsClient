package com.thelads.core.client.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

public final class SystemMetrics {
    private static Method cpuMethod;
    private static boolean methodLookedUp;

    private SystemMetrics() {}

    public static double getCpuLoad() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (!methodLookedUp) {
                methodLookedUp = true;
                try {
                    cpuMethod = osBean.getClass().getMethod("getCpuLoad");
                } catch (Exception ignored) {
                    try {
                        cpuMethod = osBean.getClass().getMethod("getProcessCpuLoad");
                    } catch (Exception ignored2) {
                        try {
                            cpuMethod = osBean.getClass().getMethod("getSystemCpuLoad");
                        } catch (Exception ignored3) {}
                    }
                }
            }
            if (cpuMethod != null) {
                Object val = cpuMethod.invoke(osBean);
                if (val instanceof Number n) {
                    double d = n.doubleValue();
                    if (d >= 0) return d * 100.0;
                }
            }
        } catch (Exception ignored) {}
        return -1.0;
    }
}

package com.thelads.core.v26_2.feature.dynamicfps.feature.battery;
import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.util.Logging;
import com.thelads.core.v26_2.feature.dynamicfps.util.Threads;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import oshi.SystemInfo;
import oshi.hardware.PowerSource;
/** Local OS battery data through Minecraft's existing OSHI/JNA; no network or downloaded native code. */
public final class BatteryTracker {
    private static volatile int charge;
    private static volatile BatteryState status = BatteryState.UNKNOWN;
    private static volatile boolean present;
    private static ScheduledExecutorService worker;
    private static long generation;
    private static boolean reportedFailure;
    private BatteryTracker() {}
    public static int charge() { return charge; }
    public static BatteryState status() { return status; }
    public static boolean hasBatteries() { return present; }
    public static boolean isFeatureEnabled() { return !com.thelads.core.v26_2.feature.dynamicfps.LadsBackgroundBridge.disabled() && DynamicFPSConfig.INSTANCE.batteryTracker().enabled(); }
    public static synchronized void init() {
        close();
        if (!isFeatureEnabled()) return;
        long current = generation;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Lads battery status"); thread.setDaemon(true); return thread;
        });
        worker.scheduleWithFixedDelay(() -> read(current), 0, 15, TimeUnit.SECONDS);
    }
    public static synchronized void close() {
        generation++;
        if (worker != null) { worker.shutdownNow(); worker = null; }
        present = false; status = BatteryState.UNKNOWN; charge = 0;
    }
    private static void read(long current) {
        try {
            List<PowerSource> batteries = new SystemInfo().getHardware().getPowerSources();
            BatteryReading reading = BatteryReading.from(batteries);
            Threads.runOnMainThread(() -> apply(current, reading));
        } catch (RuntimeException | LinkageError failure) {
            if (!reportedFailure) {
                reportedFailure = true;
                Logging.getLogger().warn("Local battery information is unavailable", failure);
            }
            Threads.runOnMainThread(() -> apply(current, new BatteryReading(false, 0, BatteryState.UNKNOWN)));
        }
    }
    private static synchronized void apply(long current, BatteryReading reading) {
        if (current != generation || worker == null) return;
        boolean hadBattery = present;
        int beforeCharge = charge;
        BatteryState beforeStatus = status;
        present = reading.present(); charge = reading.charge(); status = reading.state();
        if (!hadBattery && present) com.thelads.core.v26_2.feature.dynamicfps.feature.state.IdleHandler.init();
        if (hadBattery && present) {
            if (beforeCharge != charge) DynamicFPSMod.onBatteryChargeChanged(beforeCharge, charge);
            if (beforeStatus != status) DynamicFPSMod.onBatteryStatusChanged(beforeStatus, status);
        }
        if (hadBattery != present || beforeStatus != status) DynamicFPSMod.onStatusChanged(false);
    }
}

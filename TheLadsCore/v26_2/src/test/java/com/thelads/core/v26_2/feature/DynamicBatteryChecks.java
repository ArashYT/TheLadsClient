package com.thelads.core.v26_2.feature;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryReading;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryState;
import java.lang.reflect.Proxy;
import java.util.List;
import oshi.hardware.PowerSource;
/** Standalone OSHI input checks; fixtures never enter the production tracker. */
public final class DynamicBatteryChecks {
    private static int passed;
    public static void main(String[] args) {
        check(BatteryReading.from(List.of()).equals(new BatteryReading(false, 0, BatteryState.UNKNOWN)), "desktop has no invented battery");
        check(read(.36, false, true, false).equals(new BatteryReading(true, 36, BatteryState.DISCHARGING)), "discharging");
        check(read(.72, true, false, true).equals(new BatteryReading(true, 72, BatteryState.CHARGING)), "charging");
        check(read(1, false, false, true).equals(new BatteryReading(true, 100, BatteryState.FULL)), "full on AC");
        check(read(1, false, true, false).state() == BatteryState.DISCHARGING, "full unplugged still discharging");
        check(read(.7, false, false, true).state() == BatteryState.UNKNOWN, "conservation mode is not fabricated charging");
        check(read(0, false, true, false).charge() == 0, "empty battery is real zero");
        check(read(.105, false, true, false).charge() == 11, "percentage rounds to nearest whole unit");
        check(!read(Double.NaN, false, true, false).present(), "NaN discarded");
        check(!read(Double.POSITIVE_INFINITY, false, true, false).present(), "infinity discarded");
        check(!read(-1, false, true, false).present(), "unavailable negative discarded");
        check(!read(1.01, false, true, false).present(), "out of range discarded");
        var mixed = BatteryReading.from(List.of(source(.4, false, true, false), source(Double.NaN, false, false, false)));
        check(mixed.charge() == 40 && mixed.present(), "invalid device does not corrupt valid battery");
        var multi = BatteryReading.from(List.of(source(.2, false, true, false), source(.8, false, true, false)));
        check(multi.charge() == 50 && multi.state() == BatteryState.DISCHARGING, "multiple device percentages combined");
        System.out.println("Dynamic battery checks: " + passed + " passed");
        if (args.length > 0 && args[0].equals("--read-local")) {
            var live = BatteryReading.from(new oshi.SystemInfo().getHardware().getPowerSources());
            System.out.println("Local OSHI reading: " + live);
        }
    }
    private static BatteryReading read(double fraction, boolean charging, boolean discharging, boolean online) { return BatteryReading.from(List.of(source(fraction, charging, discharging, online))); }
    private static PowerSource source(double fraction, boolean charging, boolean discharging, boolean online) {
        return (PowerSource) Proxy.newProxyInstance(PowerSource.class.getClassLoader(), new Class<?>[]{PowerSource.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getRemainingCapacityPercent" -> fraction;
            case "isCharging" -> charging;
            case "isDischarging" -> discharging;
            case "isPowerOnLine" -> online;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); passed++; }
}

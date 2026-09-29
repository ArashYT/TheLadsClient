package com.thelads.core.v26_2.feature.dynamicfps.feature.battery;
import java.util.List;
import oshi.hardware.PowerSource;
public record BatteryReading(boolean present, int charge, BatteryState state) {
    public static BatteryReading from(List<PowerSource> batteries) {
        double total = 0;
        int count = 0;
        boolean charging = false, discharging = false, online = false;
        for (PowerSource battery : batteries) {
            double fraction = battery.getRemainingCapacityPercent();
            if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) continue;
            total += fraction; count++;
            charging |= battery.isCharging(); discharging |= battery.isDischarging(); online |= battery.isPowerOnLine();
        }
        if (count == 0) return new BatteryReading(false, 0, BatteryState.UNKNOWN);
        int percentage = (int) Math.round(total * 100 / count);
        BatteryState state = charging ? BatteryState.CHARGING : discharging ? BatteryState.DISCHARGING
            : online && percentage >= 99 ? BatteryState.FULL : BatteryState.UNKNOWN;
        return new BatteryReading(true, percentage, state);
    }
}

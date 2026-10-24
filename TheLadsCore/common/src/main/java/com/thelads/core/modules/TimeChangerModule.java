package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.util.Locale;

/**
 * Time Changer: this client draws the sky, sun, moon, stars and daylight at a fixed time of day; the server's time keeps running
 * (nothing is sent, the world's real time is never written). Each version feeds the real time through {@link #time} where its client
 * world reads the time of day (1.8.9 World.getCelestialAngle, 1.20.1 LevelTimeAccess.getTimeOfDay, 26.x the client clocks).
 */
public final class TimeChangerModule extends Module {
    public static final String NAME = "Time Changer";
    public static final long DAY = 24000;
    private static volatile TimeChangerModule cached;

    /** The time of day as a clock hour (12 = noon, 0 = midnight). */
    public final SliderOption hour;

    public TimeChangerModule() {
        super(NAME, "See the world at the time of day you choose (only on your screen; the server's time is unchanged).");
        hour = addOption(new SliderOption("Time", 12, 0, 24, 0.25) {
            @Override
            public String display() {
                int minutes = (int) Math.round(getValue() * 60);
                return String.format(Locale.ROOT, "%02d:%02d", minutes / 60 % 24, minutes % 60);
            }
        });
    }

    public static TimeChangerModule get() {
        TimeChangerModule module = cached;
        if (module == null && ModuleManager.getInstance().getModule(NAME) instanceof TimeChangerModule m) cached = module = m;
        return module;
    }

    /** The time to draw for the world's real time: the chosen time of day on the real day (so the moon phase stays the server's). */
    public long time(long real) {
        return fixed(real, hour.getValue());
    }

    /** Minecraft's day starts at 06:00 (tick 0); noon is 6000, midnight 18000. */
    static long fixed(long real, double hour) {
        long tickOfDay = Math.floorMod(Math.round(hour * 1000) - 6000, DAY);
        return Math.floorDiv(real, DAY) * DAY + tickOfDay;
    }
}

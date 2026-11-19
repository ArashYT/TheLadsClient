package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The server's camera shot (PROTOCOL.md section 11). Visual only: the version hook, every frame, asks {@link #poseAt} and, when
 * it is not null, draws the world from that pose (position, rotation, fov), draws letterbox bars by {@link #barsAt} and hides
 * the HUD while {@link #hidesHud}. Esc, the pause menu and disconnecting are never touched. Game thread only.
 */
public final class CameraState {
    /** The player's own view, for the fields a shot leaves out. */
    public record Own(double x, double y, double z, double yaw, double pitch, double fov) {}

    /** Where the camera is and looks; fov is already resolved. */
    public record Pose(double x, double y, double z, double yaw, double pitch, double fov) {}

    public static final int MAX_POINTS = 32;
    /** A camera point further than this from the player's real position is pulled in to it. */
    public static final double MAX_DISTANCE = 512;
    /** Both bars together cover this part of the screen height at full strength. */
    public static final double BARS_HEIGHT = 0.12;
    public static final long BARS_FADE_MS = 300;
    /** Orbiting an entity looks at its middle, this far above the feet the lookup returns. */
    static final double ENTITY_CENTRE = 1.0;

    private enum Mode { SPLINE, ORBIT, FIXED }

    private record Pt(double x, double y, double z, double yaw, double pitch) {}

    private static final class Shot {
        Mode mode;
        long start;
        int ms;
        boolean smooth, bars, hideHud, clockwise;
        double fov = Double.NaN;
        List<Pt> points = List.of();
        double cx, cy, cz, radius = 6, height = 2;
        int periodMs = 8000;
        UUID entity, lookEntity;
        double[] lookAt;
        double yaw = Double.NaN, pitch = Double.NaN;
        double lastYaw = Double.NaN, lastPitch = Double.NaN;
    }

    private Shot shot;

    public void clear() {
        shot = null;
    }

    /** A shot is set and has not run out. */
    public boolean active(long now) {
        return shot != null && now - shot.start < shot.ms;
    }

    public boolean hidesHud(long now) {
        return active(now) && shot.hideHud;
    }

    /** Letterbox strength 0-1: fades in over 300 ms and out over the last 300 ms. */
    public double barsAt(long now) {
        if (!active(now) || !shot.bars) return 0;
        long el = Math.max(0, now - shot.start);
        double in = Math.min(1, el / (double) BARS_FADE_MS), out = Math.min(1, (shot.ms - el) / (double) BARS_FADE_MS);
        return Math.max(0, Math.min(in, out));
    }

    /** A camera message. A bad one is ignored and leaves a running shot alone; off and an unknown mode end it. */
    public void apply(JsonObject json, long now) {
        String mode = Wire.str(json, "mode");
        if (mode == null) return;
        Mode m = switch (mode) {
            case "spline" -> Mode.SPLINE;
            case "orbit" -> Mode.ORBIT;
            case "fixed" -> Mode.FIXED;
            default -> null;
        };
        if (m == null) {
            shot = null;
            return;
        }
        Double ms = Wire.num(json, "ms");
        if (ms == null) return;
        Shot s = new Shot();
        s.mode = m;
        s.start = now;
        s.ms = (int) Math.round(Math.max(100, Math.min(30000, ms)));
        s.smooth = "smooth".equals(Wire.str(json, "ease"));
        s.bars = Wire.flag(json, "bars");
        s.hideHud = Wire.flag(json, "hide_hud");
        Double fov = Wire.num(json, "fov");
        if (fov != null) s.fov = Math.max(30, Math.min(110, fov));
        boolean ok = switch (m) {
            case SPLINE -> spline(s, json);
            case ORBIT -> orbit(s, json);
            case FIXED -> fixed(s, json);
        };
        if (ok) shot = s;
    }

    private static boolean spline(Shot s, JsonObject json) {
        JsonArray arr = Wire.arr(json, "points");
        if (arr == null) return false;
        List<Pt> pts = new ArrayList<>();
        for (JsonElement e : arr) {
            if (pts.size() >= MAX_POINTS) break;
            if (!e.isJsonObject()) continue;
            JsonObject p = e.getAsJsonObject();
            Double x = Wire.num(p, "x"), y = Wire.num(p, "y"), z = Wire.num(p, "z");
            if (x == null || y == null || z == null) continue;
            Double yaw = Wire.num(p, "yaw"), pitch = Wire.num(p, "pitch");
            pts.add(new Pt(x, y, z, yaw == null ? Double.NaN : yaw,
                pitch == null ? Double.NaN : Math.max(-90, Math.min(90, pitch))));
        }
        if (pts.isEmpty()) return false;
        s.points = pts;
        return true;
    }

    private static boolean orbit(Shot s, JsonObject json) {
        s.entity = Wire.uuid(json, "entity");
        if (s.entity == null) {
            Double x = Wire.num(json, "x"), y = Wire.num(json, "y"), z = Wire.num(json, "z");
            if (x == null || y == null || z == null) return false;
            s.cx = x;
            s.cy = y;
            s.cz = z;
        }
        s.radius = Wire.num(json, "radius", 1, 64, 6);
        s.height = Wire.num(json, "height", -32, 64, 2);
        s.periodMs = (int) Wire.num(json, "period_ms", 1000, 60000, 8000);
        s.clockwise = Wire.flag(json, "clockwise");
        return true;
    }

    private static boolean fixed(Shot s, JsonObject json) {
        Double x = Wire.num(json, "x"), y = Wire.num(json, "y"), z = Wire.num(json, "z");
        if (x == null || y == null || z == null) return false;
        s.points = List.of(new Pt(x, y, z, Double.NaN, Double.NaN));
        JsonObject look = Wire.obj(json, "look_at");
        Double lx = look == null ? null : Wire.num(look, "x");
        Double ly = look == null ? null : Wire.num(look, "y");
        Double lz = look == null ? null : Wire.num(look, "z");
        if (lx != null && ly != null && lz != null) {
            s.lookAt = new double[] {lx, ly, lz};
        } else {
            s.lookEntity = Wire.uuid(json, "look_entity");
            if (s.lookEntity == null) {
                Double yaw = Wire.num(json, "yaw"), pitch = Wire.num(json, "pitch");
                if (yaw != null) s.yaw = yaw;
                if (pitch != null) s.pitch = Math.max(-90, Math.min(90, pitch));
            }
        }
        return true;
    }

    /**
     * The view to draw at now, or null when there is no shot, it has run out (dropped, the normal view returns at once), or its
     * orbit entity is not loaded (the shot ends). Call it every frame.
     */
    public Pose poseAt(long now, Own own, Hooks.EntityLookup lookup) {
        Shot s = shot;
        if (s == null) return null;
        if (now - s.start >= s.ms) {
            shot = null;
            return null;
        }
        Pose p = evaluate(s, Math.max(0, now - s.start), own, lookup);
        if (p == null) shot = null;
        return p;
    }

    private static Pose evaluate(Shot s, long el, Own own, Hooks.EntityLookup lookup) {
        double x, y, z, yaw, pitch;
        switch (s.mode) {
            case ORBIT -> {
                double cx = s.cx, cy = s.cy, cz = s.cz;
                if (s.entity != null) {
                    double[] at = lookup == null ? null : lookup.position(s.entity);
                    if (at == null) return null;
                    cx = at[0];
                    cy = at[1] + ENTITY_CENTRE;
                    cz = at[2];
                }
                // Starts on the +Z side. Counter-clockwise seen from above runs toward +X, clockwise toward -X.
                double a = 2 * Math.PI * (el % s.periodMs) / s.periodMs;
                double side = s.clockwise ? -1 : 1;
                x = cx + side * s.radius * Math.sin(a);
                z = cz + s.radius * Math.cos(a);
                y = cy + s.height;
                yaw = Geo.yawTo(cx - x, cz - z);
                pitch = Geo.pitchTo(cx - x, cy - y, cz - z);
            }
            case SPLINE -> {
                List<Pt> pts = s.points;
                int n = pts.size();
                double[] yaws = new double[n], pitches = new double[n];
                double py = own.yaw(), pp = own.pitch();
                for (int i = 0; i < n; i++) {
                    Pt q = pts.get(i);
                    py = Double.isNaN(q.yaw()) ? py : q.yaw();
                    pp = Double.isNaN(q.pitch()) ? pp : q.pitch();
                    yaws[i] = py;
                    pitches[i] = pp;
                }
                double u = Math.max(0, Math.min(1, (double) el / s.ms));
                if (s.smooth) u = u * u * (3 - 2 * u);
                if (n == 1) {
                    Pt q = pts.get(0);
                    x = q.x();
                    y = q.y();
                    z = q.z();
                    yaw = yaws[0];
                    pitch = pitches[0];
                } else {
                    double pos = u * (n - 1);
                    int i = Math.min((int) Math.floor(pos), n - 2);
                    double f = pos - i;
                    Pt p0 = pts.get(Math.max(i - 1, 0)), p1 = pts.get(i), p2 = pts.get(i + 1),
                        p3 = pts.get(Math.min(i + 2, n - 1));
                    x = catmull(p0.x(), p1.x(), p2.x(), p3.x(), f);
                    y = catmull(p0.y(), p1.y(), p2.y(), p3.y(), f);
                    z = catmull(p0.z(), p1.z(), p2.z(), p3.z(), f);
                    yaw = Geo.lerpAngle(yaws[i], yaws[i + 1], f);
                    pitch = pitches[i] + (pitches[i + 1] - pitches[i]) * f;
                }
            }
            default -> {
                Pt q = s.points.get(0);
                x = q.x();
                y = q.y();
                z = q.z();
                if (s.lookAt != null) {
                    yaw = Geo.yawTo(s.lookAt[0] - x, s.lookAt[2] - z);
                    pitch = Geo.pitchTo(s.lookAt[0] - x, s.lookAt[1] - y, s.lookAt[2] - z);
                } else if (s.lookEntity != null) {
                    double[] at = lookup == null ? null : lookup.position(s.lookEntity);
                    if (at != null) {
                        yaw = Geo.yawTo(at[0] - x, at[2] - z);
                        pitch = Geo.pitchTo(at[0] - x, at[1] + ENTITY_CENTRE - y, at[2] - z);
                    } else {
                        yaw = Double.isNaN(s.lastYaw) ? own.yaw() : s.lastYaw;
                        pitch = Double.isNaN(s.lastPitch) ? own.pitch() : s.lastPitch;
                    }
                } else {
                    yaw = Double.isNaN(s.yaw) ? own.yaw() : s.yaw;
                    pitch = Double.isNaN(s.pitch) ? own.pitch() : s.pitch;
                }
                s.lastYaw = yaw;
                s.lastPitch = pitch;
            }
        }
        double dx = x - own.x(), dy = y - own.y(), dz = z - own.z();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > MAX_DISTANCE) {
            double k = MAX_DISTANCE / d;
            x = own.x() + dx * k;
            y = own.y() + dy * k;
            z = own.z() + dz * k;
        }
        return new Pose(x, y, z, yaw, pitch, Double.isNaN(s.fov) ? own.fov() : s.fov);
    }

    /** Uniform Catmull-Rom between p1 (f = 0) and p2 (f = 1). */
    static double catmull(double p0, double p1, double p2, double p3, double f) {
        double f2 = f * f, f3 = f2 * f;
        return 0.5 * (2 * p1 + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2 + (-p0 + 3 * p1 - 3 * p2 + p3) * f3);
    }
}

package com.thelads.ladsapi.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.ladsapi.session.Features;
import com.thelads.ladsapi.session.Wire;
import java.util.List;
import java.util.UUID;

/**
 * A cinematic camera shot (PROTOCOL.md section 11). The player's real position is untouched; only the client's view moves.
 * {@code lp.camera().spline(points).ms(5000).smooth().bars().hideHud().play()}.
 */
public final class CameraBuilder {
    private final Features features;
    private String mode;
    private List<CameraPoint> points = List.of();
    private double x, y, z, yaw, pitch, lx, ly, lz;
    private boolean hasLook, hasAngles;
    private UUID entity, lookEntity;
    private int ms = 5000;
    private boolean smooth, bars, hideHud, clockwise;
    private double fov = Double.NaN;
    private double radius = 6, height = 2;
    private int periodMs = 8000;

    public CameraBuilder(Features features) { this.features = features; }

    /** Flies through the points (1..32; one point is a fixed shot). */
    public CameraBuilder spline(List<CameraPoint> points) { mode = "spline"; this.points = points == null ? List.of() : points; return this; }

    /** Circles a position and looks at it. */
    public CameraBuilder orbit(double x, double y, double z) { mode = "orbit"; this.x = x; this.y = y; this.z = z; return this; }

    /** Circles an entity the client has loaded (a player, a mob). */
    public CameraBuilder orbitEntity(UUID entity) { mode = "orbit"; this.entity = entity; return this; }

    /** Stands still at x,y,z looking along yaw and pitch. */
    public CameraBuilder fixed(double x, double y, double z, double yaw, double pitch) {
        mode = "fixed"; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; hasAngles = true;
        return this;
    }

    /** Stands still at x,y,z looking at a point. */
    public CameraBuilder fixedLookAt(double x, double y, double z, double lx, double ly, double lz) {
        mode = "fixed"; this.x = x; this.y = y; this.z = z; this.lx = lx; this.ly = ly; this.lz = lz; hasLook = true;
        return this;
    }

    /** Stands still at x,y,z looking at an entity. */
    public CameraBuilder fixedLookEntity(double x, double y, double z, UUID entity) {
        mode = "fixed"; this.x = x; this.y = y; this.z = z; this.lookEntity = entity;
        return this;
    }

    /** Shot length, 100..30000 ms (default 5000). */
    public CameraBuilder ms(int ms) { this.ms = ms; return this; }
    public CameraBuilder smooth() { smooth = true; return this; }
    public CameraBuilder fov(double fov) { this.fov = fov; return this; }
    public CameraBuilder bars() { bars = true; return this; }
    public CameraBuilder hideHud() { hideHud = true; return this; }
    public CameraBuilder radius(double radius) { this.radius = radius; return this; }
    public CameraBuilder height(double height) { this.height = height; return this; }
    public CameraBuilder periodMs(int periodMs) { this.periodMs = periodMs; return this; }
    public CameraBuilder clockwise() { clockwise = true; return this; }

    /** The camera message with every value clamped, or null when the shot is unusable (no mode, no finite point). */
    public JsonObject toJson() {
        if (mode == null) return null;
        JsonObject o = Wire.obj("camera");
        o.addProperty("mode", mode);
        o.addProperty("ms", Wire.whole(ms, 100, 30000, 5000));
        if ("spline".equals(mode)) {
            JsonArray arr = new JsonArray();
            for (CameraPoint p : points) {
                if (p == null || !Wire.finite(p.x()) || !Wire.finite(p.y()) || !Wire.finite(p.z())) continue;
                if (arr.size() >= 32) break;
                JsonObject j = new JsonObject();
                j.addProperty("x", Wire.round(p.x(), 3));
                j.addProperty("y", Wire.round(p.y(), 3));
                j.addProperty("z", Wire.round(p.z(), 3));
                j.addProperty("yaw", Wire.round(Wire.num(p.yaw(), -3600, 3600, 0), 2));
                j.addProperty("pitch", Wire.round(Wire.num(p.pitch(), -90, 90, 0), 2));
                arr.add(j);
            }
            if (arr.size() == 0) return null;
            o.add("points", arr);
            if (smooth) o.addProperty("ease", "smooth");
        } else if ("orbit".equals(mode)) {
            if (entity != null) o.addProperty("entity", entity.toString());
            else if (Wire.finite(x) && Wire.finite(y) && Wire.finite(z)) pos(o);
            else return null;
            o.addProperty("radius", Wire.num(radius, 1, 64, 6));
            o.addProperty("height", Wire.num(height, -32, 64, 2));
            o.addProperty("period_ms", Wire.whole(periodMs, 1000, 60000, 8000));
            if (clockwise) o.addProperty("clockwise", true);
        } else {
            if (!Wire.finite(x) || !Wire.finite(y) || !Wire.finite(z)) return null;
            pos(o);
            if (hasAngles) {
                o.addProperty("yaw", Wire.round(Wire.num(yaw, -3600, 3600, 0), 2));
                o.addProperty("pitch", Wire.round(Wire.num(pitch, -90, 90, 0), 2));
            } else if (hasLook && Wire.finite(lx) && Wire.finite(ly) && Wire.finite(lz)) {
                JsonObject l = new JsonObject();
                l.addProperty("x", Wire.round(lx, 3));
                l.addProperty("y", Wire.round(ly, 3));
                l.addProperty("z", Wire.round(lz, 3));
                o.add("look_at", l);
            } else if (lookEntity != null) {
                o.addProperty("look_entity", lookEntity.toString());
            }
        }
        if (Wire.finite(fov)) o.addProperty("fov", Wire.num(fov, 30, 110, 70));
        if (bars) o.addProperty("bars", true);
        if (hideHud) o.addProperty("hide_hud", true);
        return o;
    }

    private void pos(JsonObject o) {
        o.addProperty("x", Wire.round(x, 3));
        o.addProperty("y", Wire.round(y, 3));
        o.addProperty("z", Wire.round(z, 3));
    }

    /** Starts the shot. False without the client's {@code camera} cap or with an unusable shot. */
    public boolean play() {
        JsonObject o = toJson();
        return o != null && features.send("camera", o);
    }

    /** Ends the shot at once. False without the cap. */
    public boolean off() {
        JsonObject o = Wire.obj("camera");
        o.addProperty("mode", "off");
        return features.send("camera", o);
    }
}

package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.api.CameraPoint;
import com.thelads.ladsapi.api.LadsPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;

/** Bukkit-typed shortcuts for the Minigame API (PROTOCOL.md section 23): Location, Material and Entity instead of numbers and ids. */
public final class LadsPaper {
    private LadsPaper() {}

    /** An item or block as the id the client knows ("minecraft:diamond_sword"). */
    public static String icon(Material material) {
        return material == null ? null : "minecraft:" + material.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** A camera point at a location, looking the way the location looks. */
    public static CameraPoint point(Location l) {
        return new CameraPoint(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static List<CameraPoint> points(List<Location> locations) {
        List<CameraPoint> out = new ArrayList<>();
        for (Location l : locations) out.add(point(l));
        return out;
    }

    /** A waypoint at a location. */
    public static boolean waypoint(LadsPlayer lp, String id, Location l, String label, String color) {
        return lp.waypoints().set(id, l.getX(), l.getY(), l.getZ(), label, color);
    }

    /** A waypoint that follows an entity. */
    public static boolean waypoint(LadsPlayer lp, String id, Entity entity, String label, String color, Material icon) {
        UUID u = entity.getUniqueId();
        return lp.waypoints().setEntity(id, u, label, color, icon(icon));
    }

    /** A circle telegraph on the ground at a location. */
    public static boolean circle(LadsPlayer lp, String id, Location l, double radius, String color, int ms, int fillMs) {
        return lp.telegraphs().circle(id, l.getX(), l.getY(), l.getZ(), radius, color, ms, fillMs);
    }

    /** A cone telegraph: apex at the location, pointing the way it looks. */
    public static boolean cone(LadsPlayer lp, String id, Location l, double radius, double angle, String color, int ms, int fillMs) {
        return lp.telegraphs().cone(id, l.getX(), l.getY(), l.getZ(), radius, angle, l.getYaw(), color, ms, fillMs);
    }

    /** A cooldown widget with a Material as its icon. */
    public static boolean cooldown(LadsPlayer lp, String id, String title, Material icon, long remainingMs, long totalMs, int charges) {
        return lp.hud().cooldown(id, title, icon(icon), remainingMs, totalMs, charges);
    }
}

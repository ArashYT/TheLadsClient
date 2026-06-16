package com.thelads.core.features.alwayson.advancementsreloaded;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class ClickableRegion {
    private String name;
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;
    private boolean clicked = false;

    private ClickableRegion(String name, int minX, int maxX, int minY, int maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.name = name;
    }

    public boolean isInside(double x, double y) {
        return x >= (double)this.minX && x <= (double)this.maxX && y >= (double)this.minY && y <= (double)this.maxY;
    }

    public void setName(String name) { this.name = name; }
    public void setWidth(int width) { this.maxX = this.minX + width; }
    public void setHeight(int height) { this.maxY = this.minY + height; }
    public void setOriginX(int x) { this.minX = x; }
    public void setOriginY(int y) { this.minY = y; }
    public void setClicked(boolean clicked) { this.clicked = clicked; }
    public boolean isClicked() { return this.clicked; }
    public String getName() { return this.name; }

    @Override
    public String toString() {
        return "ClickableRegion{name='" + this.name + "', minX=" + this.minX + ", minY=" + this.minY + ", maxX=" + this.maxX + ", maxY=" + this.maxY + ", clicked=" + this.clicked + "}";
    }

    public static ClickableRegion create(String name, int x, int y, int width, int height) {
        return new ClickableRegion(name, x, x + width, y, y + height);
    }

    public static List<ClickableRegion> foundRegions(List<ClickableRegion> regions, double x, double y) {
        List<ClickableRegion> found = new ArrayList<>();
        for (ClickableRegion region : regions) {
            if (region.isInside(x, y)) {
                found.add(region);
            }
        }
        return found;
    }

    public static List<ClickableRegion> foundClickedRegions(List<ClickableRegion> regions) {
        List<ClickableRegion> found = new ArrayList<>();
        if (regions == null) return found;
        for (ClickableRegion region : regions) {
            if (region.isClicked()) {
                found.add(region);
            }
        }
        return found;
    }

    public static Optional<ClickableRegion> findRegion(List<ClickableRegion> regions, Predicate<ClickableRegion> predicate) {
        for (ClickableRegion region : regions) {
            if (predicate.test(region)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }
}

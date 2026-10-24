package com.thelads.core.client.wecui;

/**
 * WorldEdit CUI protocol parser and selection tracker (parity with Lunar / Badlion WECUI).
 */
public final class WorldEditCuiHandler {
    public record SelectionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int getVolume() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private static Integer p0X, p0Y, p0Z;
    private static Integer p1X, p1Y, p1Z;
    private static String shape = "cuboid";

    private WorldEditCuiHandler() {}

    public static synchronized void handleMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        String[] parts = message.trim().split("\\|");
        if (parts.length == 0) return;

        switch (parts[0]) {
            case "s": // shape
                if (parts.length >= 2) shape = parts[1];
                clear();
                break;
            case "p": // point
                if (parts.length >= 5) {
                    try {
                        int pointIdx = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        int z = Integer.parseInt(parts[4]);
                        if (pointIdx == 0) {
                            p0X = x; p0Y = y; p0Z = z;
                        } else if (pointIdx == 1) {
                            p1X = x; p1Y = y; p1Z = z;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                break;
            case "e": // clear
                clear();
                break;
        }
    }

    public static synchronized SelectionBox getSelection() {
        if (p0X == null || p1X == null) return null;
        int minX = Math.min(p0X, p1X);
        int maxX = Math.max(p0X, p1X);
        int minY = Math.min(p0Y, p1Y);
        int maxY = Math.max(p0Y, p1Y);
        int minZ = Math.min(p0Z, p1Z);
        int maxZ = Math.max(p0Z, p1Z);
        return new SelectionBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static synchronized boolean hasSelection() {
        return p0X != null && p1X != null;
    }

    public static synchronized void clear() {
        p0X = p0Y = p0Z = null;
        p1X = p1Y = p1Z = null;
    }
}

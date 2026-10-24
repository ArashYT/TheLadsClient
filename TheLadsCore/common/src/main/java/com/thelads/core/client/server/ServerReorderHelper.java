package com.thelads.core.client.server;

import java.util.List;

/**
 * Pure state machine and reordering logic for dragging server entries in the multiplayer screen (parity with Feather).
 */
public final class ServerReorderHelper {
    private static boolean dragging;
    private static int draggedIndex = -1;
    private static int targetIndex = -1;

    private ServerReorderHelper() {}

    public static void startDrag(int index, int mouseY) {
        if (index < 0) return;
        dragging = true;
        draggedIndex = index;
        targetIndex = index;
    }

    public static int updateDrag(int mouseY, int top, int slotHeight, int totalSlots) {
        if (!dragging || totalSlots <= 0 || slotHeight <= 0) return -1;
        int relativeY = mouseY - top;
        int slot = relativeY / slotHeight;
        targetIndex = Math.max(0, Math.min(totalSlots - 1, slot));
        return targetIndex;
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static int getDraggedIndex() {
        return draggedIndex;
    }

    public static int getTargetIndex() {
        return targetIndex;
    }

    public static void cancelDrag() {
        dragging = false;
        draggedIndex = -1;
        targetIndex = -1;
    }

    public static <T> boolean applyReorder(List<T> list) {
        if (!dragging || draggedIndex < 0 || targetIndex < 0 || list == null) {
            cancelDrag();
            return false;
        }
        if (draggedIndex >= list.size() || targetIndex >= list.size() || draggedIndex == targetIndex) {
            cancelDrag();
            return false;
        }
        T item = list.remove(draggedIndex);
        list.add(targetIndex, item);
        cancelDrag();
        return true;
    }
}

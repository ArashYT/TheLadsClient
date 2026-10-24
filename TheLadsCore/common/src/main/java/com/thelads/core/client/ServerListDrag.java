package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.function.IntFunction;

/**
 * Draggable Server List (Feather): hold a saved server in the multiplayer list and drag it up or down; it drops where the line shows and
 * servers.dat keeps the new order. A press only becomes a drag after the mouse moved {@link #THRESHOLD} pixels, so clicks, double-click
 * to join and vanilla's move arrows work as before. The adapters' server list mixins track the drag; this class holds the rules.
 */
public final class ServerListDrag {
    public static final String NAME = "Draggable Server List";
    public static final int THRESHOLD = 4;
    private static Module module;

    private ServerListDrag() {}

    /** Asked when a press starts, not per frame. */
    public static boolean enabled() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        return module != null && module.isEnabled();
    }

    public static boolean dragging(double pressY, double y) { return Math.abs(y - pressY) > THRESHOLD; }

    /** The saved-server row a drag drops on: the row under the mouse (the LAN rows below count as the last saved one), else where it was. */
    public static int target(int hovered, int previous, int count) {
        return hovered < 0 ? previous : Math.min(hovered, count - 1);
    }

    /** Sets index {@code i} to {@code value} (ServerList.replace, which does not save). */
    public interface Put<T> {
        void put(int i, T value);
    }

    /** Moves the entry at {@code from} to {@code to}, shifting the ones between by one, as dragging a row does. */
    public static <T> void move(int from, int to, IntFunction<T> get, Put<T> put) {
        if (from == to) return;
        T moving = get.apply(from);
        int step = from < to ? 1 : -1;
        for (int i = from; i != to; i += step) put.put(i, get.apply(i + step));
        put.put(to, moving);
    }
}

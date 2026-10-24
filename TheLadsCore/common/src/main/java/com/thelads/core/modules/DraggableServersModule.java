package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Draggable Server List: sort and organize multiplayer servers by dragging with the mouse (parity with Feather).
 */
public class DraggableServersModule extends Module {
    public static final String NAME = "Draggable Server List";

    public DraggableServersModule() {
        super(NAME, "Reorganize and sort multiplayer server list entries by dragging with the mouse.");
        setEnabled(false);
    }
}

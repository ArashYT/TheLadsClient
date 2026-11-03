package com.thelads.core.client.gui;

import com.thelads.core.client.gui.LadsSettingsScreen.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the script verb {@code import113} drives the import dialog over the open Lads settings with real clicks:
 * {@code open} (the catalog's button), {@code click <label>} (a client, category or link by its label), {@code log} (its state and every click target).
 */
public final class ClientImportQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    private ClientImportQa() {}

    public static void step(LadsSettingsScreen menu, String[] s) {
        ClientImportDialog dialog = menu.clientImportDialog();
        switch (s[1]) {
            case "open" -> click(menu, menu.controlBounds("client-import"), "the catalog's import button");
            case "click" -> {
                String label = String.join(" ", java.util.Arrays.copyOfRange(s, 2, s.length));
                click(menu, dialog.hitBounds(label), label);
            }
            case "log" -> LOGGER.info("Lads qa import113: open={} status='{}' selected={} chosen={} canUndo={} targets={}", dialog.isOpen(), dialog.status(),
                dialog.selected() == null ? "none" : dialog.selected().get("id").getAsString(), dialog.chosen(), dialog.canUndo(), dialog.hitLog());
            default -> throw new IllegalArgumentException("import113 " + s[1]);
        }
    }

    private static void click(LadsSettingsScreen menu, Rect rect, String what) {
        if (rect == null) throw new IllegalStateException("not drawn: " + what);
        LOGGER.info("Lads qa import113: click '{}' at {},{} {}x{}", what, rect.x(), rect.y(), rect.width(), rect.height());
        menu.mouseClicked(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0, 0);
    }
}

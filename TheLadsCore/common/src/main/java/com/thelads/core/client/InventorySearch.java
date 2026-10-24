package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.InventorySearchModule;
import java.util.List;
import java.util.Locale;

/**
 * Inventory search bar: highlights matching item stacks and dims non-matching items in container GUIs.
 */
public final class InventorySearch {
    private static String query = "";
    private static boolean active = false;

    private InventorySearch() {}

    public static boolean enabled() {
        Module mod = ModuleManager.getInstance().getModule(InventorySearchModule.NAME);
        return mod != null && mod.isEnabled();
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized void setActive(boolean a) {
        active = a;
        if (!active) query = "";
    }

    public static synchronized String getQuery() {
        return query;
    }

    public static synchronized void setQuery(String q) {
        query = (q == null) ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    public static synchronized boolean matches(String itemName, List<String> tooltipLines) {
        if (!enabled() || !active || query.isEmpty()) return true;
        if (itemName != null && itemName.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (tooltipLines != null) {
            for (String line : tooltipLines) {
                if (line != null && line.toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized boolean shouldDim(String itemName, List<String> tooltipLines) {
        return enabled() && active && !query.isEmpty() && !matches(itemName, tooltipLines);
    }
}

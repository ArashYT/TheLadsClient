// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core, 1.20.1 list API.
package com.thelads.core.v1_20_1.embedded.controlling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The base of Controlling's key lists. It remembers every entry it was given, so that searching and sorting can show a subset
 * ({@link #addEntryInternal}) and show all again.
 */
public class CustomList extends ContainerObjectSelectionList<KeyListEntry> {

    public List<KeyListEntry> allEntries = new ArrayList<>();
    protected final NewKeyBindsScreen screen;

    public CustomList(NewKeyBindsScreen screen, Minecraft minecraft) {

        super(minecraft, screen.width, screen.height, 48, screen.height - 56, 20);
        this.screen = screen;
    }

    public List<KeyListEntry> getAllEntries() {

        return allEntries;
    }

    @Override
    public void clearEntries() {

        super.clearEntries();
    }

    public void sort(Comparator<KeyListEntry> comparator) {

        this.children().sort(comparator);
    }

    @Override
    protected int addEntry(KeyListEntry entry) {

        allEntries.add(entry);
        return addEntryInternal(entry);
    }

    public int addEntryInternal(KeyListEntry entry) {

        return super.addEntry(entry);
    }

    /** Rows are as wide as the search box above them. */
    @Override
    public int getRowWidth() {

        return 340;
    }

    @Override
    protected int getScrollbarPosition() {

        return this.width / 2 + 170 + 10;
    }

    /** Called after a key changed: all key bindings are looked at again for conflicts and the rows are refreshed. */
    public void resetMappingAndUpdateButtons() {

        KeyMapping.resetMapping();
        this.refreshEntries();
    }

    public void refreshEntries() {

        this.allEntries.forEach(KeyListEntry::refreshEntry);
    }

}

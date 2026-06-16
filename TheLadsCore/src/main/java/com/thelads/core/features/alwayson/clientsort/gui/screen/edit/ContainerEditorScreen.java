/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 */
package com.thelads.core.features.alwayson.clientsort.gui.screen.edit;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.gui.TriggerButtonManager;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.EditorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.widget.TriggerButton;
import java.util.LinkedList;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class ContainerEditorScreen
extends EditorScreen {
    public ContainerEditorScreen(AbstractContainerScreen<?> underlay, TriggerButton button) {
        super(underlay, false, button);
    }

    @Override
    protected LinkedList<TriggerButton> getButtons() {
        return TriggerButtonManager.getContainerButtons();
    }
}

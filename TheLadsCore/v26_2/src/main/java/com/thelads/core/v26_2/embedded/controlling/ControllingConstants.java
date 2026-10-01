// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling;

import com.thelads.core.v26_2.embedded.controlling.api.entries.ICategoryEntry;
import com.thelads.core.v26_2.embedded.controlling.api.entries.IInputEntry;
import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v26_2.embedded.controlling.searchables.api.SearchableComponent;
import com.thelads.core.v26_2.embedded.controlling.searchables.api.SearchableType;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Optional;

public class ControllingConstants {
    
    public static final MutableComponent COMPONENT_CONTROLS_RESET = Component.translatable("controls.reset");
    public static final MutableComponent COMPONENT_CONTROLS_RESET_ALL = Component.translatable("controls.resetAll");
    public static final MutableComponent COMPONENT_OPTIONS_CONFIRM_RESET = Component.translatable("options.confirmReset");
    public static final MutableComponent COMPONENT_OPTIONS_SHOW_NONE = Component.translatable("options.showNone");
    public static final MutableComponent COMPONENT_OPTIONS_SHOW_ALL = Component.translatable("options.showAll");
    public static final MutableComponent COMPONENT_OPTIONS_SHOW_CONFLICTS = Component.translatable("options.showConflicts");
    public static final MutableComponent COMPONENT_OPTIONS_SORT = Component.translatable("options.sort");
    public static final MutableComponent COMPONENT_OPTIONS_TOGGLE_FREE = Component.translatable("options.toggleFree");
    public static final MutableComponent COMPONENT_OPTIONS_AVAILABLE_KEYS = Component.translatable("options.availableKeys");
    
    
    public static final SearchableType<KeyBindsList.Entry> SEARCHABLE_KEYBINDINGS = new SearchableType.Builder<KeyBindsList.Entry>()
            .component(SearchableComponent.create("category", entry -> {
                if(entry instanceof ICategoryEntry cat) {
                    return Optional.of(cat.category().label().getString());
                } else if(entry instanceof IKeyEntry key) {
                    return Optional.of(key.categoryName().getString());
                }
                return Optional.empty();
            }))
            .component(SearchableComponent.create("key", entry -> {
                if(entry instanceof IKeyEntry key && !key.getKey().isUnbound()) {
                    return Optional.of(key.getKey().getTranslatedKeyMessage().getString());
                }
                return Optional.empty();
            }))
            .defaultComponent(SearchableComponent.create("name", entry -> {
                if(entry instanceof IKeyEntry key) {
                    return Optional.of(key.getName().getString());
                } else if(entry instanceof IInputEntry input) {
                    return Optional.of(input.getInput().getName());
                }
                return Optional.empty();
            }))
            .build();
    
    
}

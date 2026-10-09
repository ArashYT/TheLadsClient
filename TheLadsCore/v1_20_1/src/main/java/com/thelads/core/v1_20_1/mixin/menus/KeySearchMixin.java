package com.thelads.core.v1_20_1.mixin.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Controls: a search field (names, keys, categories or mods) and a conflicts/unbound filter above the key list, as on 26.3.
 * Essential's own bindings stay out of the list.
 */
@Mixin(KeyBindsScreen.class)
public abstract class KeySearchMixin extends OptionsSubScreen {
    @Shadow private KeyBindsList keyBindsList;
    @Unique private EditBox ladsSearch;
    @Unique private Button ladsModeButton, ladsStateButton;
    @Unique private List<KeyBindsList.Entry> ladsAll = List.of();
    @Unique private int ladsMode, ladsState;
    @Unique private static final String[] LADS_MODES = {"All", "Name", "Keybind", "Category", "Mod"};
    @Unique private static final String[] LADS_STATES = {"All bindings", "Conflicts", "Unbound"};

    protected KeySearchMixin(Screen parent, Options options, Component title) {
        super(parent, options, title);
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsSearchControls(CallbackInfo ci) {
        ladsAll = List.copyOf(keyBindsList.children());
        keyBindsList.updateSize(width + 45, height, 86, height - 32);
        String previous = ladsSearch == null ? "" : ladsSearch.getValue();
        ladsSearch = new EditBox(font, width / 2 - 150, 32, 300, 20, Component.literal("Search controls"));
        ladsSearch.setHint(Component.literal("Search names, keys, categories or mods"));
        ladsSearch.setMaxLength(128);
        ladsSearch.setResponder(value -> ladsFilter(true));
        ladsModeButton = Button.builder(Component.literal("Search: " + LADS_MODES[ladsMode]), button -> {
            ladsMode = (ladsMode + 1) % LADS_MODES.length;
            button.setMessage(Component.literal("Search: " + LADS_MODES[ladsMode]));
            ladsFilter(true);
        }).bounds(width / 2 - 150, 57, 148, 20).build();
        ladsStateButton = Button.builder(Component.literal(LADS_STATES[ladsState]), button -> {
            ladsState = (ladsState + 1) % LADS_STATES.length;
            button.setMessage(Component.literal(LADS_STATES[ladsState]));
            ladsFilter(true);
        }).bounds(width / 2 + 2, 57, 148, 20).build();
        addRenderableWidget(ladsSearch);
        addRenderableWidget(ladsModeButton);
        addRenderableWidget(ladsStateButton);
        ladsSearch.setValue(previous);
        ladsFilter(true);
    }

    @Unique
    private void ladsFilter(boolean resetScroll) {
        String query = ladsSearch.getValue().strip().toLowerCase(Locale.ROOT);
        List<KeyBindsList.Entry> filtered = new ArrayList<>();
        KeyBindsList.Entry heading = null;
        boolean headingAdded = false;
        for (var entry : ladsAll) {
            if (!(entry instanceof KeyEntryAccessor accessor)) {
                if (ladsHeadingText(entry).contains("essential")) {
                    heading = null;
                    continue;
                }
                heading = entry;
                headingAdded = false;
                continue;
            }
            KeyMapping key = accessor.ladsKey();
            if (key == null) continue;
            String kname = key.getName().toLowerCase(Locale.ROOT);
            String kcat = key.getCategory().toLowerCase(Locale.ROOT);
            if (kname.contains("essential") || kcat.contains("essential")) continue;
            String name = Component.translatable(key.getName()).getString(), binding = key.getTranslatedKeyMessage().getString();
            String category = Component.translatable(key.getCategory()).getString(), mod = ladsModName(key);
            String text = switch (ladsMode) {
                case 1 -> name;
                case 2 -> binding;
                case 3 -> category;
                case 4 -> mod;
                default -> name + " " + binding + " " + category + " " + mod;
            };
            boolean state = ladsState == 0 || (ladsState == 2 ? key.isUnbound()
                : !key.isUnbound() && Arrays.stream(options.keyMappings).anyMatch(other -> other != key && key.same(other)));
            if (state && text.toLowerCase(Locale.ROOT).contains(query)) {
                if (heading != null && !headingAdded) {
                    filtered.add(heading);
                    headingAdded = true;
                }
                filtered.add(entry);
            }
        }
        // Key assignment and ordinary clicks update labels, not the viewport. Replacing identical entries also clears
        // the focused row, so only rebuild on a real filter change.
        double scroll = keyBindsList.getScrollAmount();
        if (!keyBindsList.children().equals(filtered)) ((SelectionListInvoker) keyBindsList).ladsReplaceEntries(filtered);
        if (resetScroll) keyBindsList.setScrollAmount(0);
        else if (keyBindsList.getScrollAmount() != scroll) keyBindsList.setScrollAmount(scroll);
    }

    /** A category heading's text (1.20.1 keeps it in the entry's private name). */
    @Unique
    private static String ladsHeadingText(KeyBindsList.Entry entry) {
        return entry instanceof CategoryEntryAccessor heading && heading.ladsName() != null
            ? heading.ladsName().getString().toLowerCase(Locale.ROOT) : "";
    }

    @Unique
    private static String ladsModName(KeyMapping key) {
        String raw = key.getName().toLowerCase(Locale.ROOT);
        String category = key.getCategory().toLowerCase(Locale.ROOT);
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (raw.startsWith("key." + id + ".") || raw.startsWith("key." + id + ":") || raw.startsWith(id + ".")
                || raw.startsWith("keybind." + id + ".") || category.startsWith("key.category." + id + ".")
                || category.startsWith("key.categories." + id)) {
                return mod.getMetadata().getName() + " " + id;
            }
        }
        String[] parts = raw.split("\\.");
        String namespace = parts.length > 1 && parts[0].equals("key") ? parts[1] : parts[0];
        return namespace + " " + Component.translatable(key.getCategory()).getString();
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("TAIL"), require = 1)
    private void ladsRefreshClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch != null) ladsFilter(false);
    }

    @Inject(method = "keyPressed(III)Z", at = @At("TAIL"), require = 1)
    private void ladsRefreshKey(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        if (ladsSearch != null) ladsFilter(false);
    }
}

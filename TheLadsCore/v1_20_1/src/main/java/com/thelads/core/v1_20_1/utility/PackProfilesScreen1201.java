package com.thelads.core.v1_20_1.utility;

import com.thelads.core.client.pack.PackProfiles;
import com.thelads.core.v1_20_1.utility.mixin.PackModelAccessor1201;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

/**
 * Pack Profiles on 1.20.1: pick a saved profile (the button cycles through them) and Apply it (its packs load now, as the pack screen's Done
 * would), or name the packs selected on the pack screen now and Save them. Done goes back to the pack screen.
 */
public final class PackProfilesScreen1201 extends Screen {
    private static final String VERSION = "1.20.1";
    private final PackSelectionScreen parent;
    private final PackModelAccessor1201 model;
    private int shown;
    private EditBox name;
    private String status = "";

    public PackProfilesScreen1201(PackSelectionScreen parent, PackModelAccessor1201 model) {
        super(Component.literal("Pack Profiles"));
        this.parent = parent;
        this.model = model;
    }

    @Override
    protected void init() {
        List<String> names = PackProfiles.names(VERSION);
        if (shown >= names.size()) shown = 0;
        int x = width / 2 - 100, y = height / 2 - 56;
        Button pick = addRenderableWidget(Button.builder(Component.literal(names.isEmpty() ? "No profiles yet" : "Profile: " + names.get(shown)), b -> {
            shown = (shown + 1) % names.size();
            rebuildWidgets();
        }).bounds(x, y, 200, 20).build());
        pick.active = names.size() > 1;
        Button apply = addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply(names.get(shown))).bounds(x, y + 24, 98, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
            PackProfiles.delete(VERSION, names.get(shown));
            status = "Deleted " + names.get(shown);
            shown = 0;
            rebuildWidgets();
        }).bounds(x + 102, y + 24, 98, 20).build());
        apply.active = delete.active = !names.isEmpty();
        String typed = name == null ? "" : name.getValue();
        name = addRenderableWidget(new EditBox(font, x, y + 60, 200, 20, Component.literal("Profile name")));
        name.setMaxLength(PackProfiles.MAX_NAME);
        name.setHint(Component.literal("Profile name"));
        name.setValue(typed);
        addRenderableWidget(Button.builder(Component.literal("Save Selected Packs"), b -> save()).bounds(x, y + 84, 200, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, height - 28, 200, 20).build());
    }

    private void save() {
        List<String> ids = new ArrayList<>();
        for (Pack pack : model.lads$selected()) ids.add(pack.getId());
        String typed = name.getValue().trim();
        if (!PackProfiles.save(VERSION, typed, ids)) {
            status = "Type a name first";
            return;
        }
        status = "Saved " + typed + " (" + ids.size() + " packs)";
        shown = Math.max(0, PackProfiles.names(VERSION).indexOf(typed));
        name.setValue("");
        rebuildWidgets();
    }

    /** The profile's packs become the Selected list (in its order, required packs kept), the rest Available; then they load. */
    private void apply(String profile) {
        List<Pack> selected = model.lads$selected(), available = model.lads$unselected();
        Map<String, Pack> all = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Pack pack : selected) {
            all.put(pack.getId(), pack);
            if (pack.isRequired()) required.add(pack.getId());
        }
        for (Pack pack : available) all.put(pack.getId(), pack);
        List<String> order = PackProfiles.arrange(PackProfiles.get(VERSION, profile), all.keySet(), required);
        selected.clear();
        for (String id : order) selected.add(all.get(id));
        available.clear();
        for (Pack pack : all.values()) if (!selected.contains(pack)) available.add(pack);
        parent.onClose(); // commits and loads them, as the pack screen's Done does; its opener then shows its own screen
        if (minecraft.screen == this) minecraft.setScreen(parent); // an opener that does not: back to the packs
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 80, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, height / 2 + 54, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

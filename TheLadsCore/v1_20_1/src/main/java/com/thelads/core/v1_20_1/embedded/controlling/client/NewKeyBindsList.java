// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core, rewritten for the 1.20.1 list and widget API.
package com.thelads.core.v1_20_1.embedded.controlling.client;

import com.google.common.collect.ImmutableList;
import com.thelads.core.v1_20_1.embedded.controlling.ControllingConstants;
import com.thelads.core.v1_20_1.embedded.controlling.api.MouseClick;
import com.thelads.core.v1_20_1.embedded.controlling.api.entries.ICategoryEntry;
import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v1_20_1.embedded.controlling.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

public class NewKeyBindsList extends CustomList {

    private final Minecraft mc;

    public NewKeyBindsList(NewKeyBindsScreen screen, Minecraft minecraft) {

        super(screen, minecraft);
        this.mc = minecraft;
        KeyMapping[] bindings = ArrayUtils.clone(minecraft.options.keyMappings);
        Arrays.sort(bindings);
        String lastCategory = null;

        for(KeyMapping keybinding : bindings) {
            String kname = keybinding.getName().toLowerCase(Locale.ROOT);
            String kcat = keybinding.getCategory().toLowerCase(Locale.ROOT);
            if(kname.contains("essential") || kcat.contains("essential")) {
                continue;
            }
            String category = keybinding.getCategory();
            if(!category.equals(lastCategory)) {
                lastCategory = category;
                if(shouldShow(category)) {
                    addEntry(new NewKeyBindsList.CategoryEntry(category));
                }
            }

            Component component = Services.PLATFORM.getKeyName(keybinding);
            if(shouldShow(category)) {
                addEntry(new NewKeyBindsList.KeyEntry(keybinding, component));
            }
        }

    }

    private static boolean shouldShow(String category) {

        String key = category.toLowerCase(Locale.ROOT);
        return !key.contains("essential") && !key.endsWith(".hidden");
    }

    public class CategoryEntry extends KeyListEntry implements ICategoryEntry {

        private final String category;
        private final Component label;

        public CategoryEntry(String category) {

            this.category = category;
            this.label = Component.translatable(category);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTicks) {

            graphics.drawString(mc.font, this.label, NewKeyBindsList.this.width / 2 - mc.font.width(this.label) / 2, top + height - 9 - 1, 0xFFFFFF, false);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {

            return List.of();
        }

        @Override
        public List<? extends GuiEventListener> children() {

            return List.of();
        }

        @Override
        public void refreshEntry() {

        }

        @Override
        public String category() {

            return this.category;
        }

        @Override
        public Component label() {

            return this.label;
        }

    }

    public class KeyEntry extends KeyListEntry implements IKeyEntry {

        /**
         * The keybinding specified for this KeyEntry
         */
        private final KeyMapping key;
        /**
         * The localized key description for this KeyEntry
         */
        private final Component name;
        private final Button btnChangeKeyBinding;
        private final Button btnResetKeyBinding;

        private boolean hasCollision;

        private final Component categoryName;

        public KeyEntry(final KeyMapping key, final Component name) {

            this.key = key;
            this.name = name;
            this.btnChangeKeyBinding = Button.builder(this.name, button -> {
                        NewKeyBindsList.this.screen.selectedKey = key;
                        NewKeyBindsList.this.resetMappingAndUpdateButtons();
                    })
                    .bounds(0, 0, 75, 20)
                    .createNarration(supp -> key.isUnbound() ? Component.translatable("narrator.controls.unbound", name) : Component.translatable("narrator.controls.bound", name, supp.get()))
                    .build();

            this.btnResetKeyBinding = Button.builder(ControllingConstants.COMPONENT_CONTROLS_RESET, button -> {
                        Services.PLATFORM.setToDefault(mc.options, key);
                        NewKeyBindsList.this.resetMappingAndUpdateButtons();
                    }).bounds(0, 0, 50, 20)
                    .createNarration(supp -> Component.translatable("narrator.controls.reset", name))
                    .build();

            this.categoryName = Component.translatable(this.key.getCategory());
            refreshEntry();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTicks) {

            Services.EVENT.fireKeyEntryRenderEvent(this, graphics, left, top, left, width, hovered, partialTicks);

            int resetKeyX = left + width - this.btnResetKeyBinding.getWidth();
            this.btnResetKeyBinding.setX(resetKeyX);
            this.btnResetKeyBinding.setY(top);
            this.btnResetKeyBinding.render(graphics, mouseX, mouseY, partialTicks);

            this.btnChangeKeyBinding.setX(resetKeyX - 5 - this.btnChangeKeyBinding.getWidth());
            this.btnChangeKeyBinding.setY(top);
            this.btnChangeKeyBinding.render(graphics, mouseX, mouseY, partialTicks);
            graphics.drawString(mc.font, this.name, left, top + height / 2 - 9 / 2, 0xFFFFFF, false);

            if(this.hasCollision) {
                int markerWidth = 3;
                int minX = this.btnChangeKeyBinding.getX() - 6;
                graphics.fill(minX, top - 1, minX + markerWidth, top + height, 0xFFFFFF55);
            }
        }

        @Override
        public List<GuiEventListener> children() {

            return Services.EVENT.fireKeyEntryListenersEvent(this)
                    .map(IKeyEntryListenersEvent::listeners, UnaryOperator.identity());
        }

        @Override
        public List<? extends NarratableEntry> narratables() {

            return ImmutableList.of(this.btnChangeKeyBinding, this.btnResetKeyBinding);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {

            if(Services.EVENT.fireKeyEntryMouseClickedEvent(this, new MouseClick(x, y, button), false)
                    .map(IKeyEntryMouseClickedEvent::handled, UnaryOperator.identity())) {
                return true;
            }
            return super.mouseClicked(x, y, button);
        }

        @Override
        public boolean mouseClicked(MouseClick event, boolean doubleClick) {

            return mouseClicked(event.x(), event.y(), event.button());
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {

            if(Services.EVENT.fireKeyEntryMouseReleasedEvent(this, new MouseClick(x, y, button))
                    .map(IKeyEntryMouseReleasedEvent::handled, UnaryOperator.identity())) {
                return true;
            }

            return super.mouseReleased(x, y, button);
        }

        @Override
        public boolean mouseReleased(MouseClick event) {

            return mouseReleased(event.x(), event.y(), event.button());
        }

        public KeyMapping getKey() {

            return key;
        }

        public Component getName() {

            return name;
        }

        public Component categoryName() {

            return categoryName;
        }

        public Button getBtnResetKeyBinding() {

            return btnResetKeyBinding;
        }

        public Button getBtnChangeKeyBinding() {

            return btnChangeKeyBinding;
        }

        @Override
        public void refreshEntry() {

            this.btnChangeKeyBinding.setMessage(this.key.getTranslatedKeyMessage());
            this.btnResetKeyBinding.active = !this.key.isDefault();
            this.hasCollision = false;
            MutableComponent duplicates = Component.empty();
            if(!this.key.isUnbound()) {
                KeyMapping[] mappings = mc.options.keyMappings;

                for(KeyMapping mapping : mappings) {
                    if(mapping != this.key && this.key.same(mapping) || Services.PLATFORM.hasConflictingModifier(key, mapping)) {
                        if(this.hasCollision) {
                            duplicates.append(", ");
                        }

                        this.hasCollision = true;
                        duplicates.append(Services.PLATFORM.getKeyName(mapping));
                    }
                }
            }
            MutableComponent tooltip = categoryName.copy();
            if(this.hasCollision) {
                this.btnChangeKeyBinding.setMessage(Component.literal("[ ")
                        .append(this.btnChangeKeyBinding.getMessage().copy().withStyle(ChatFormatting.WHITE))
                        .append(" ]")
                        .withStyle(ChatFormatting.YELLOW));
                tooltip.append(CommonComponents.NEW_LINE);
                tooltip.append(Component.translatable("controls.keybinds.duplicateKeybinds", duplicates));
            }
            this.btnChangeKeyBinding.setTooltip(Tooltip.create(tooltip));

            if(NewKeyBindsList.this.screen.selectedKey == this.key) {
                this.btnChangeKeyBinding.setMessage(Component.literal("> ")
                        .append(this.btnChangeKeyBinding.getMessage()
                                .copy()
                                .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                        .append(" <")
                        .withStyle(ChatFormatting.YELLOW));
            }

        }

    }

}

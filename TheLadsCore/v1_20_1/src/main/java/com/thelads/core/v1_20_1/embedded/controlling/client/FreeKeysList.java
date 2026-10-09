// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core, rewritten for the 1.20.1 list API.
package com.thelads.core.v1_20_1.embedded.controlling.client;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v1_20_1.embedded.controlling.ControllingConstants;
import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IInputEntry;
import com.thelads.core.v1_20_1.embedded.controlling.mixin.AccessInputConstantsKey;
import com.thelads.core.v1_20_1.embedded.controlling.mixin.AccessKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The "free keys" view: every key no key binding uses. */
public class FreeKeysList extends CustomList {

    private final Minecraft mc;
    private final List<KeyMapping> keyBindings;

    public FreeKeysList(NewKeyBindsScreen screen, Minecraft mcIn) {

        super(screen, mcIn);
        this.mc = mcIn;
        this.keyBindings = Arrays.stream(mc.options.keyMappings).collect(Collectors.toList());

        recalculate();
    }

    public void recalculate() {

        this.clearEntries();
        allEntries.clear();

        addEntry(new HeaderEntry());
        AccessInputConstantsKey.controlling$getNAME_MAP()
                .values()
                .stream()
                .filter(input -> !input.getName().startsWith("key.keyboard.world"))
                .filter(Predicate.not(InputConstants.UNKNOWN::equals))
                .sorted(Comparator.comparing(o -> o.getDisplayName().getString()))
                .forEach(input -> {
                    if(keyBindings.stream()
                            .noneMatch(keyBinding -> ((AccessKeyMapping) keyBinding).controlling$getKey()
                                    .equals(input))) {
                        addEntry(new InputEntry(input));
                    }
                });
    }

    public class InputEntry extends KeyListEntry implements IInputEntry {

        private final InputConstants.Key input;

        public InputEntry(InputConstants.Key input) {

            this.input = input;
        }

        public InputConstants.Key getInput() {

            return input;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTicks) {

            String str = this.input + " - " + input.getValue();
            Component display = input.getDisplayName();

            graphics.drawString(mc.font, str, left, top + height / 2 - 9 / 2, 0xFFFFFF, false);
            graphics.drawString(mc.font, display, left + width - mc.font.width(display), top + height / 2 - 9 / 2, 0xAAAAAA, false);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {

            return ImmutableList.of();
        }

        @Override
        public List<? extends GuiEventListener> children() {

            return ImmutableList.of();
        }

        @Override
        public void refreshEntry() {

        }

    }

    public class HeaderEntry extends KeyListEntry {

        @Override
        public List<? extends NarratableEntry> narratables() {

            return ImmutableList.of();
        }

        @Override
        public List<? extends GuiEventListener> children() {

            return ImmutableList.of();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTicks) {

            graphics.drawCenteredString(mc.font, ControllingConstants.COMPONENT_OPTIONS_AVAILABLE_KEYS, FreeKeysList.this.width / 2, top + height - 9 - 1, 0xFFFFFF);
        }

        @Override
        public void refreshEntry() {

        }

    }

}

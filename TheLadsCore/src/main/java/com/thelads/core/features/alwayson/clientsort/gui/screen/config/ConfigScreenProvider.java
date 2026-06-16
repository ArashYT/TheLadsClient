/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.MultiLineTextWidget
 *  net.minecraft.client.gui.screens.ConfirmLinkScreen
 *  net.minecraft.network.chat.CommonComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Util
 */
package com.thelads.core.features.alwayson.clientsort.gui.screen.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.gui.screen.config.ClothScreenProvider;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class ConfigScreenProvider {
    public static Screen getConfigScreen(Screen parent) {
        try {
            return ClothScreenProvider.getConfigScreen(parent);
        }
        catch (NoClassDefFoundError ignored) {
            return new BackupScreen(parent, "noConfig", "https://modrinth.com/project/9s6osm5g");
        }
    }

    private static class BackupScreen
    extends Screen {
        private final Screen parent;
        private final String modKey;
        private final String modUrl;

        public BackupScreen(Screen parent, String modKey, String modUrl) {
            super((Component)Localization.localized("name", new Object[0]));
            this.parent = parent;
            this.modKey = modKey;
            this.modUrl = modUrl;
        }

        @Override
        public void init() {
            MultiLineTextWidget messageWidget = new MultiLineTextWidget(this.width / 2 - 120, this.height / 2 - 40, (Component)Localization.localized("message", this.modKey, new Object[0]), Minecraft.getInstance().font);
            messageWidget.setMaxWidth(240);
            messageWidget.setCentered(true);
            this.addRenderableWidget(messageWidget);
            Button openLinkButton = Button.builder((Component)Localization.localized("message", "viewModrinth", new Object[0]), button -> Minecraft.getInstance().setScreen((Screen)new ConfirmLinkScreen(open -> {
                if (open) {
                    Util.getPlatform().openUri(this.modUrl);
                }
                this.onClose();
            }, this.modUrl, true))).pos(this.width / 2 - 120, this.height / 2).size(115, 20).build();
            this.addRenderableWidget(openLinkButton);
            Button exitButton = Button.builder((Component)CommonComponents.GUI_OK, button -> this.onClose()).pos(this.width / 2 + 5, this.height / 2).size(115, 20).build();
            this.addRenderableWidget(exitButton);
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(this.parent);
        }
    }

    private static class DisabledScreen
    extends Screen {
        private final Screen parent;

        public DisabledScreen(Screen parent) {
            super((Component)Localization.localized("name", new Object[0]));
            this.parent = parent;
        }

        @Override
        public void init() {
            MultiLineTextWidget messageWidget = new MultiLineTextWidget(this.width / 2 - 120, this.height / 2 - 40, (Component)Localization.localized("message", "configScreenDisabled", new Object[0]), Minecraft.getInstance().font);
            messageWidget.setMaxWidth(240);
            messageWidget.setCentered(true);
            this.addRenderableWidget(messageWidget);
            Button exitButton = Button.builder((Component)CommonComponents.GUI_OK, button -> this.onClose()).pos(this.width / 2 - 115, this.height / 2).size(230, 20).build();
            this.addRenderableWidget(exitButton);
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(this.parent);
        }
    }
}

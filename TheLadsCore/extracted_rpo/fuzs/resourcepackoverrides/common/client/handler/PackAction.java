/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.toasts.Toast
 *  net.minecraft.client.gui.components.toasts.TutorialToast
 *  net.minecraft.client.gui.components.toasts.TutorialToast$Icons
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.packs.PackSelectionScreen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  net.minecraft.util.Mth
 *  org.jspecify.annotations.Nullable
 */
package fuzs.resourcepackoverrides.common.client.handler;

import com.mojang.blaze3d.platform.InputConstants;
import fuzs.resourcepackoverrides.common.ResourcePackOverrides;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

public abstract class PackAction {
    private final int keyCode;
    public final Component title;
    public final Component description;
    public final Component success;
    private @Nullable TutorialToast toast;
    private @Nullable TutorialToast successToast;
    private int successTicks;
    private int pressTime;
    private int lastPressTime;
    private int decreaseTimeDelay;
    private boolean wasExecuted;

    public PackAction(int keyCode, String name) {
        this(keyCode, ResourcePackOverrides.id(name));
    }

    public PackAction(int keyCode, Identifier resourceLocation) {
        this(keyCode, (Component)Component.translatable((String)resourceLocation.toLanguageKey("pack_action", "title")), (Component)Component.translatable((String)resourceLocation.toLanguageKey("pack_action", "description"), (Object[])new Object[]{InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().copy().withStyle(ChatFormatting.BOLD)}), (Component)Component.translatable((String)resourceLocation.toLanguageKey("pack_action", "success")));
    }

    public PackAction(int keyCode, Component title, Component description, Component success) {
        this.keyCode = keyCode;
        this.title = title;
        this.description = description;
        this.success = success;
    }

    public int getKeyCode() {
        return this.keyCode;
    }

    public void tick(Minecraft minecraft) {
        if (this.pressTime == this.lastPressTime && this.pressTime > 0 && --this.decreaseTimeDelay < 0) {
            if (this.wasExecuted) {
                this.reset();
            } else {
                --this.pressTime;
            }
        }
        this.lastPressTime = this.pressTime;
        if (this.pressTime > 0) {
            Screen screen;
            if (this.toast == null) {
                this.toast = new TutorialToast(minecraft.font, TutorialToast.Icons.MOVEMENT_KEYS, this.title, this.description, true);
                minecraft.gui.toastManager().addToast((Toast)this.toast);
            }
            if (this.pressTime < 20) {
                this.toast.updateProgress(Mth.clamp((float)((float)this.pressTime / 20.0f), (float)0.0f, (float)1.0f));
            } else if (!this.wasExecuted && (screen = minecraft.gui.screen()) instanceof PackSelectionScreen) {
                PackSelectionScreen screen2 = (PackSelectionScreen)screen;
                if (screen2.model.repository == minecraft.getResourcePackRepository()) {
                    if (this.execute(minecraft, screen2)) {
                        this.finish(minecraft);
                    }
                    this.wasExecuted = true;
                    this.toast.updateProgress(1.0f);
                }
            }
        } else {
            this.reset();
        }
        if (this.successTicks > 0) {
            --this.successTicks;
            this.successToast.updateProgress((float)this.successTicks / 80.0f);
        } else if (this.successToast != null) {
            this.successToast.hide();
            this.successToast = null;
        }
    }

    private void reset() {
        if (this.toast != null) {
            this.toast.hide();
            this.toast = null;
        }
        this.lastPressTime = 0;
        this.pressTime = 0;
        this.wasExecuted = false;
    }

    abstract boolean execute(Minecraft var1, PackSelectionScreen var2);

    private void finish(Minecraft minecraft) {
        if (this.successToast != null) {
            this.successToast.hide();
        }
        this.successToast = new TutorialToast(minecraft.font, TutorialToast.Icons.MOVEMENT_KEYS, this.title, this.success, true);
        minecraft.gui.toastManager().addToast((Toast)this.successToast);
        this.successTicks = 80;
        this.successToast.updateProgress(1.0f);
    }

    public void update() {
        ++this.pressTime;
        this.resetDelay();
    }

    public void resetDelay() {
        this.decreaseTimeDelay = 10;
    }
}


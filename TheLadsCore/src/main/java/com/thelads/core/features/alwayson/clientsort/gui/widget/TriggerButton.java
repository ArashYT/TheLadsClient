/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.ComponentPath
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.Button$OnPress
 *  net.minecraft.client.gui.components.Tooltip
 *  net.minecraft.client.gui.components.WidgetSprites
 *  net.minecraft.client.gui.navigation.FocusNavigationEvent
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.CommonComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.Container
 *  net.minecraft.world.inventory.Slot
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.gui.widget;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.ContainerEditorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.EditorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.PlayerEditorScreen;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractContainerScreenAccessor;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractWidgetAccessor;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TriggerButton
extends Button {
    public static final int WIDTH = 13;
    public static final int HEIGHT = 13;
    public static final int HALF_WIDTH = 6;
    public static final int HALF_HEIGHT = 6;
    private final AbstractContainerScreen<?> screen;
    public final Container container;
    public final Slot referenceSlot;
    public final boolean referenceLeft;
    public final boolean isPlayerInv;
    private final WidgetSprites sprites;
    private final Component name;
    @Nullable
    public final String activePolicyKey;
    public final String lowestPolicyKey;
    public Vec2i offset;
    public boolean offsetFromSlot;
    public boolean operationAllowed;

    protected TriggerButton(AbstractContainerScreen<?> screen, Container container, Slot referenceSlot, boolean referenceLeft, boolean isPlayerInv, WidgetSprites sprites, Component name, @Nullable String activePolicyKey, String lowestPolicyKey, Vec2i offset, boolean offsetFromSlot, boolean operationAllowed, boolean active, Button.OnPress onPress) {
        super(((AbstractContainerScreenAccessor)screen).clientsort$getLeftPos() + ((AbstractContainerScreenAccessor)screen).clientsort$getImageWidth() + offset.x(), ((AbstractContainerScreenAccessor)screen).clientsort$getTopPos() + referenceSlot.y + offset.y(), 13, 13, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
        this.screen = screen;
        this.container = container;
        this.referenceSlot = referenceSlot;
        this.referenceLeft = referenceLeft;
        this.isPlayerInv = isPlayerInv;
        this.sprites = sprites;
        this.offset = offset;
        this.offsetFromSlot = offsetFromSlot;
        this.activePolicyKey = activePolicyKey;
        this.lowestPolicyKey = lowestPolicyKey;
        this.operationAllowed = operationAllowed;
        this.active = active;
        this.name = name;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.isMouseOver(event.x(), event.y())) {
            boolean rightClick;
            boolean bl = rightClick = event.button() == 1;
            if (Minecraft.getInstance().gui.screen() instanceof EditorScreen) {
                if (rightClick) {
                    if (event.hasShiftDown()) {
                        this.operationAllowed = !this.operationAllowed;
                    } else {
                        this.active = !this.active;
                    }
                }
                return true;
            }
            if (rightClick) {
                this.openEditScreen();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= (double)this.getX() && mouseY >= (double)this.getY() && mouseX < (double)(this.getX() + this.width) && mouseY < (double)(this.getY() + this.height);
    }

    public void openEditScreen() {
        Minecraft.getInstance().setScreenAndShow((Screen)(this.isPlayerInv ? new PlayerEditorScreen(this.screen, this) : new ContainerEditorScreen(this.screen, this)));
    }

    public void extractContents(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AbstractContainerScreenAccessor acs = (AbstractContainerScreenAccessor)this.screen;
        int newX = Math.clamp((long)(this.getAnchorSideX(acs) + this.offset.x()), 0, this.screen.width - 13);
        int newY = Math.clamp((long)(acs.clientsort$getTopPos() + Math.max(0, this.referenceSlot.y) + this.offset.y()), 0, this.screen.height - 13);
        this.setX(newX);
        this.setY(newY);
        Identifier texture = this.sprites.get(this.isActive(), this.isHoveredOrFocused());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, this.getX(), this.getY(), this.width, this.height);
        if (!this.operationAllowed) {
            graphics.horizontalLine(this.getX(), this.getX() + this.width - 1, this.getY() + this.height / 2, -65536);
        }
        if (this.isMouseOver(mouseX, mouseY)) {
            if (((AbstractWidgetAccessor)((Object)this)).clientsort$getTooltip().get() == null) {
                if (Minecraft.getInstance().gui.screen() instanceof EditorScreen) {
                    MutableComponent visibilityStatus = Localization.localized("editor", this.active ? "enabled" : "disabled", new Object[0]).withStyle(this.active ? ChatFormatting.GREEN : ChatFormatting.RED);
                    MutableComponent operationStatus = Localization.localized("editor", this.operationAllowed ? "enabled" : "disabled", new Object[0]).withStyle(this.operationAllowed ? ChatFormatting.GREEN : ChatFormatting.RED);
                    this.setTooltip(Tooltip.create((Component)Component.empty().append(this.name).append("\n").append((Component)Localization.localized("editor", "visibility", visibilityStatus)).append("\n").append((Component)Localization.localized("editor", "operation", operationStatus))));
                } else if (Config.options().showButtonTooltips) {
                    this.setTooltip(Tooltip.create((Component)this.name));
                }
            }
        } else {
            this.setTooltip(null);
        }
    }

    protected void onDrag(@NotNull MouseButtonEvent event, double dragX, double dragY) {
        if (Minecraft.getInstance().gui.screen() instanceof EditorScreen) {
            AbstractContainerScreenAccessor acs = (AbstractContainerScreenAccessor)this.screen;
            int newX = Math.clamp((long)((int)event.x() - 6), 0, this.screen.width - 13);
            int newY = Math.clamp((long)((int)event.y() - 6), 0, this.screen.height - 13);
            this.offset = new Vec2i(newX - this.getAnchorSideX(acs), newY - (acs.clientsort$getTopPos() + Math.clamp((long)this.referenceSlot.y, 0, this.screen.height)));
        } else {
            super.onDrag(event, dragX, dragY);
        }
    }

    public ComponentPath nextFocusPath(@NotNull FocusNavigationEvent event) {
        return null;
    }

    public void setFocused(boolean focused) {
        if (!focused) {
            super.setFocused(false);
        }
    }

    public abstract boolean getPolicyStatus(ClassPolicy var1);

    public abstract void savePolicy(@Nullable Vec2i var1, boolean var2, @Nullable Operation var3, boolean var4, Collection<Integer> var5);

    private int getAnchorSideX(AbstractContainerScreenAccessor acs) {
        int anchorSideX;
        if (this.offsetFromSlot) {
            anchorSideX = acs.clientsort$getLeftPos() + this.referenceSlot.x;
            anchorSideX = this.referenceLeft ? (anchorSideX -= 13) : (anchorSideX += 16);
        } else {
            anchorSideX = acs.clientsort$getLeftPos();
            if (!this.referenceLeft) {
                anchorSideX += acs.clientsort$getImageWidth();
            }
        }
        return anchorSideX;
    }
}

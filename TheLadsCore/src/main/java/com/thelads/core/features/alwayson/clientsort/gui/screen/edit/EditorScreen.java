/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.CycleButton
 *  net.minecraft.client.gui.components.Tooltip
 *  net.minecraft.client.gui.screens.ConfirmScreen
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.CommonComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.SimpleContainer
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.Slot
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.gui.screen.edit;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.config.Policy;
import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.SelectorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.widget.TriggerButton;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractContainerScreenAccessor;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.GuiGraphicsExtractorAccessor;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.GuiRenderStateAccessor;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class EditorScreen
extends Screen {
    private final Screen lastScreen;
    private final AbstractContainerScreen<?> underlay;
    private final boolean isPlayerInv;
    private final LinkedList<TriggerButton> buttons = new LinkedList();
    private boolean offsetFromSlot = false;
    @Nullable
    private Operation autoOp = null;
    private boolean autoOpOther = false;
    public final Set<Integer> ignoredSlots = new TreeSet<Integer>();
    private TriggerButton rep;
    private String lowestPolicyClassName;
    private boolean dragging;

    public EditorScreen(AbstractContainerScreen<?> underlay, boolean isPlayerInv, TriggerButton button) {
        this(underlay, isPlayerInv, button, (Screen)underlay);
    }

    public EditorScreen(AbstractContainerScreen<?> underlay, boolean isPlayerInv, TriggerButton button, Screen lastScreen) {
        super((Component)Localization.localized("title", "positionEditor", new Object[0]));
        this.lastScreen = lastScreen;
        this.underlay = underlay;
        this.isPlayerInv = isPlayerInv;
        this.rep = button;
        this.buttons.add(button);
    }

    @Override
    public void init() {
        super.init();
        this.underlay.init(this.width, this.height);
        if (!this.reloadButtonsAndIgnoredSlots()) {
            this.clearWidgets();
            return;
        }
        this.rebuildGui();
    }

    private boolean reloadButtonsAndIgnoredSlots() {
        this.buttons.clear();
        this.ignoredSlots.clear();
        this.buttons.addAll(Config.options().justifyButtonsTopLeft ? this.getButtons() : this.getButtons().reversed());
        if (this.buttons.size() != 4) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.error("Failed to reload buttons on PositionEditScreen: Button list is too small (expected: {}, actual: {})", 4, this.buttons.size());
            }
            return false;
        }
        this.rep = this.buttons.getFirst();
        ClassPolicy policy = Config.options().classPolicies.get(this.rep.activePolicyKey);
        if (policy != null) {
            this.buttons.forEach(button -> {
                button.active = button.getPolicyStatus(policy);
            });
            this.offsetFromSlot = policy.offsetFromSlot();
            this.autoOp = policy.autoOp();
            this.autoOpOther = policy.autoOpOther();
            this.ignoredSlots.addAll(policy.ignoredSlots());
        }
        Object keyObject = this.rep.container instanceof SimpleContainer ? this.underlay.getMenu() : this.rep.container;
        this.lowestPolicyClassName = keyObject.getClass().getName();
        return true;
    }

    protected abstract LinkedList<TriggerButton> getButtons();

    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    private void rebuildGui() {
        this.clearWidgets();
        Minecraft mc = Minecraft.getInstance();
        int numButtons = 14;
        int x = 2;
        int movingY = this.height - 21 * numButtons;
        int width = 100;
        int height = 20;
        Button instructionsButton = Button.builder((Component)Localization.localized("editor", "instructions", new Object[0]), button -> {}).tooltip(Tooltip.create((Component)Localization.localized("editor", "instructions.tooltip.1", new Object[0]).append("\n\n").append((Component)Localization.localized("editor", "instructions.tooltip.2", new Object[0])).append("\n\n").append((Component)Localization.localized("editor", "instructions.tooltip.3", new Object[0])).append("\n\n").append((Component)Localization.localized("editor", "instructions.tooltip.4", new Object[0])).append("\n\n").append((Component)Localization.localized("editor", "instructions.tooltip.5", new Object[0])))).pos(x, movingY).size(width, height).build();
        instructionsButton.active = false;
        this.addRenderableWidget(instructionsButton);
        Button copyPolicyKeyButton = Button.builder((Component)Localization.localized("editor", "copyPolicyKey", new Object[0]), button -> mc.keyboardHandler.setClipboard(this.rep.activePolicyKey == null ? "null" : this.rep.activePolicyKey)).pos(x, movingY += 21).size(width, height).build();
        copyPolicyKeyButton.active = this.rep.activePolicyKey != null;
        this.addRenderableWidget(copyPolicyKeyButton);
        Button splitPolicyClassButton = Button.builder((Component)Localization.localized("editor", "splitPolicyClass", new Object[0]), button -> Minecraft.getInstance().setScreenAndShow((Screen)new ConfirmScreen(confirm -> {
            if (confirm) {
                Config.options().classPolicies.put(ClassPolicy.getKey(this.lowestPolicyClassName, null), new ClassPolicy(this.lowestPolicyClassName, null, this.buttons.getFirst().offset, this.offsetFromSlot, this.buttons.getFirst().operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.buttons.get((int)1).operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.buttons.get((int)2).operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.buttons.get((int)3).operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.autoOp, this.autoOpOther, new TreeSet<Integer>(this.ignoredSlots)));
                Config.save();
                this.init();
            }
            Minecraft.getInstance().setScreenAndShow((Screen)this);
        }, (Component)Localization.localized("title", "confirm.splitPolicyClass", new Object[0]), (Component)Localization.localized("message", "confirm.splitPolicyClass", Component.literal((String)(this.rep.activePolicyKey == null ? this.lowestPolicyClassName : this.rep.activePolicyKey)).withStyle(ChatFormatting.GOLD), Component.literal((String)this.lowestPolicyClassName).withStyle(ChatFormatting.GOLD))))).tooltip(Tooltip.create((Component)Localization.localized("editor", "splitPolicyClass.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        splitPolicyClassButton.active = this.rep.activePolicyKey != null && !((String)ClassPolicy.parseKey(this.rep.activePolicyKey).getFirst()).equals(this.lowestPolicyClassName);
        this.addRenderableWidget(splitPolicyClassButton);
        Button splitPolicyTitleButton = Button.builder((Component)Localization.localized("editor", "splitPolicyTitle", new Object[0]), button -> {
            Component invTitle = this.isPlayerInv ? ((AbstractContainerScreenAccessor)this.underlay).clientsort$getPlayerInventoryTitle() : this.underlay.getTitle();
            Minecraft.getInstance().setScreenAndShow((Screen)new ConfirmScreen(confirm -> {
                if (confirm) {
                    if (ClassPolicy.hasInvTitle(this.rep.activePolicyKey)) {
                        com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.error("Cannot split policy with title: activePolicyKey '{}' already has title.", this.rep.activePolicyKey);
                        return;
                    }
                    Config.options().classPolicies.put(ClassPolicy.getKey(this.rep.activePolicyKey, invTitle.getString()), new ClassPolicy(this.rep.activePolicyKey, invTitle.getString(), this.buttons.getFirst().offset, this.offsetFromSlot, this.buttons.getFirst().operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.buttons.get((int)1).operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.buttons.get((int)2).operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.buttons.get((int)3).operationAllowed ? (this.buttons.getFirst().active ? Policy.KEYBIND_BUTTON : Policy.KEYBIND) : Policy.NONE, this.autoOp, this.autoOpOther, new TreeSet<Integer>(this.ignoredSlots)));
                    Config.save();
                    this.init();
                }
                Minecraft.getInstance().setScreenAndShow((Screen)this);
            }, (Component)Localization.localized("title", "confirm.splitPolicyTitle", new Object[0]), (Component)Localization.localized("message", "confirm.splitPolicyTitle", Component.literal((String)(this.rep.activePolicyKey == null ? this.lowestPolicyClassName : this.rep.activePolicyKey)).withStyle(ChatFormatting.GOLD), Component.literal((String)invTitle.getString()).withStyle(ChatFormatting.GOLD))));
        }).tooltip(Tooltip.create((Component)Localization.localized("editor", "splitPolicyTitle.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        splitPolicyTitleButton.active = this.rep.activePolicyKey != null && !ClassPolicy.hasInvTitle(this.rep.activePolicyKey);
        this.addRenderableWidget(splitPolicyTitleButton);
        @NotNull CycleButton switchOffsetTypeButton = CycleButton.booleanBuilder((Component)Localization.localized("editor", "switchOffsetType.slot", new Object[0]), (Component)Localization.localized("editor", "switchOffsetType.edge", new Object[0]), (boolean)this.offsetFromSlot).withTooltip(v -> Tooltip.create((Component)Localization.localized("editor", "switchOffsetType.tooltip." + (v != false ? "slot" : "edge"), new Object[0]))).create(x, movingY += 21, width, height, (Component)Localization.localized("editor", "switchOffsetType", new Object[0]), (button, v) -> {
            this.offsetFromSlot = v;
            this.buttons.forEach(b -> {
                b.offsetFromSlot = v;
            });
        });
        this.addRenderableWidget(switchOffsetTypeButton);
        Button moveToDefaultButton = Button.builder((Component)Localization.localized("editor", "moveToDefault", new Object[0]), button -> {
            Vec2i before = this.buttons.getFirst().offset;
            this.buttons.getFirst().offset = Config.options().layoutOffset;
            this.repositionButtons(this.buttons.getFirst(), before);
        }).tooltip(Tooltip.create((Component)Localization.localized("editor", "moveToDefault.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(moveToDefaultButton);
        Button saveAsDefaultButton = Button.builder((Component)Localization.localized("editor", "saveAsDefault", new Object[0]), button -> Minecraft.getInstance().setScreenAndShow((Screen)new ConfirmScreen(confirm -> {
            if (confirm) {
                Config.options().layoutOffset = this.buttons.getFirst().offset;
                Config.save();
                this.init();
            }
            Minecraft.getInstance().setScreenAndShow((Screen)this);
        }, (Component)Localization.localized("title", "confirm.saveAsDefault", new Object[0]), (Component)Localization.localized("message", "confirm.saveAsDefault", new Object[0])))).tooltip(Tooltip.create((Component)Localization.localized("editor", "saveAsDefault.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(saveAsDefaultButton);
        @NotNull CycleButton autoOpOtherButton = CycleButton.booleanBuilder((Component)Component.literal((String)"1").withStyle(ChatFormatting.RED), (Component)Component.literal((String)"0").withStyle(ChatFormatting.GREEN), (boolean)this.autoOpOther).withTooltip(v -> Tooltip.create((Component)Localization.localized("editor", "autoOp.other.tooltip", new Object[0]))).displayOnlyValue().create(x + width - 10, movingY += 21, 10, height, (Component)Component.empty(), (b, v) -> {
            this.autoOpOther = v;
        });
        this.addRenderableWidget(autoOpOtherButton);
        @NotNull CycleButton autoOpButton = CycleButton.<Integer>builder(v -> v == 0 ? Localization.localized("editor", "autoOp.none", new Object[0]) : Localization.localized("key", "op." + Operation.values()[v.intValue() - 1].translationKey, new Object[0]), (Integer)(this.autoOp == null ? 0 : List.of(Operation.values()).indexOf(this.autoOp) + 1)).withTooltip(v -> Tooltip.create((Component)Localization.localized("editor", "autoOp.tooltip", new Object[0]))).withValues(0, 1, 2, 3, 4).create(x, movingY, width - 10, height, (Component)Localization.localized("editor", "autoOp", new Object[0]), (b, v) -> {
            this.autoOp = v == 0 ? null : Operation.values()[v - 1];
        });
        this.addRenderableWidget(autoOpButton);
        Button toggleButtonsVisibleButton = Button.builder((Component)Localization.localized("editor", "toggleVisibility", new Object[0]), button -> {
            boolean status = this.buttons.stream().noneMatch(b -> b.active);
            this.buttons.forEach(b -> {
                b.active = status;
            });
        }).tooltip(Tooltip.create((Component)Localization.localized("editor", "toggleVisibility.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(toggleButtonsVisibleButton);
        Button toggleSlotsIgnoredButton = Button.builder((Component)Localization.localized("editor", "toggleIgnoreSlots", new Object[0]), button -> {
            if (this.ignoredSlots.isEmpty()) {
                for (Slot slot : this.underlay.getMenu().slots) {
                    Object object = ClientSort.getObj(slot, this.underlay.getMenu());
                    if (object == null || !object.getClass().getName().equals(this.lowestPolicyClassName)) continue;
                    this.ignoredSlots.add(((ISlot)slot).clientsort$getIndexInContainer());
                }
            } else {
                this.ignoredSlots.clear();
            }
        }).tooltip(Tooltip.create((Component)Localization.localized("editor", "toggleIgnoreSlots.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(toggleSlotsIgnoredButton);
        Button undoChangesButton = Button.builder((Component)Localization.localized("editor", "undoChanges", new Object[0]), button -> this.init()).tooltip(Tooltip.create((Component)Localization.localized("editor", "undoChanges.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(undoChangesButton);
        Button reselectButton = Button.builder((Component)Localization.localized("editor", "reselect", new Object[0]), button -> {
            this.onClose();
            Minecraft.getInstance().setScreenAndShow((Screen)new SelectorScreen(this.underlay, this));
        }).tooltip(Tooltip.create((Component)Localization.localized("editor", "reselect.tooltip", new Object[0]))).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(reselectButton);
        Button cancelButton = Button.builder((Component)CommonComponents.GUI_CANCEL, button -> this.onClose()).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(cancelButton);
        Button doneButton = Button.builder((Component)CommonComponents.GUI_DONE, button -> this.saveAndClose()).pos(x, movingY += 21).size(width, height).build();
        this.addRenderableWidget(doneButton);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.underlay.extractBackground(graphics, mouseX, mouseY, partialTick);
        this.underlay.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ((GuiRenderStateAccessor)((GuiGraphicsExtractorAccessor)((Object)graphics)).clientsort$getGuiRenderState()).clientsort$setFirstStratumAfterBlur(Integer.MAX_VALUE);
        graphics.nextStratum();
        this.extractBlurredBackground(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 2, -1);
        for (Slot slot : this.underlay.getMenu().slots) {
            Object object = ClientSort.getObj(slot, this.underlay.getMenu());
            if (object == null || !object.getClass().getName().equals(this.lowestPolicyClassName) || !this.ignoredSlots.contains(((ISlot)slot).clientsort$getIndexInContainer())) continue;
            graphics.text(Minecraft.getInstance().font, "\u274c", ((AbstractContainerScreenAccessor)this.underlay).clientsort$getLeftPos() + slot.x, ((AbstractContainerScreenAccessor)this.underlay).clientsort$getTopPos() + slot.y, -65536);
        }
        if (this.buttons.isEmpty()) {
            return;
        }
        this.drawLineFor(graphics, this.buttons.getFirst());
        Vec2i offset = this.buttons.getFirst().offset;
        String string = Localization.localized("editor", "offset", offset.x(), offset.y()).getString();
        Objects.requireNonNull(this.font);
        graphics.text(this.font, string, 105, this.height - (9 + 1) * 3, -1);
        MutableComponent mutableComponent = Localization.localized("editor", "policyKey.current", this.rep.activePolicyKey == null ? Localization.localized("editor", "policyKey.unset", new Object[0]) : this.rep.activePolicyKey);
        Objects.requireNonNull(this.font);
        graphics.text(this.font, (Component)mutableComponent, 105, this.height - (9 + 1) * 2, -1);
        MutableComponent mutableComponent2 = Localization.localized("editor", "policyKey.menu", this.lowestPolicyClassName);
        Objects.requireNonNull(this.font);
        graphics.text(this.font, (Component)mutableComponent2, 105, this.height - (9 + 1), -1);
        for (TriggerButton cb : this.buttons) {
            cb.extractContents(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (Minecraft.getInstance().level == null) {
            this.extractPanorama(graphics, partialTick);
        }
        this.extractMenuBackground(graphics);
    }

    @Override
    protected void extractBlurredBackground(@NotNull GuiGraphicsExtractor graphics) {
        int original = (Integer)Minecraft.getInstance().options.menuBackgroundBlurriness().get();
        Minecraft.getInstance().options.menuBackgroundBlurriness().set(1);
        super.extractBlurredBackground(graphics);
        Minecraft.getInstance().options.menuBackgroundBlurriness().set(original);
    }

    private void drawLineFor(GuiGraphicsExtractor graphics, TriggerButton button) {
        graphics.horizontalLine(button.getX() - button.offset.x(), button.getX(), button.getY(), -4473925);
        graphics.verticalLine(button.getX() - button.offset.x(), button.getY() - button.offset.y(), button.getY(), -4473925);
    }

    @Override
    public void onClose() {
        super.onClose();
        this.lastScreen.init(this.width, this.height);
        Minecraft.getInstance().setScreenAndShow(this.lastScreen);
    }

    public void saveAndClose() {
        @Nullable Vec2i offset = this.buttons.getFirst().offset.equals(Config.options().layoutOffset) ? null : this.buttons.getFirst().offset;
        this.buttons.forEach(b -> b.savePolicy(offset, this.offsetFromSlot, this.autoOp, this.autoOpOther, this.ignoredSlots));
        Config.save();
        this.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        Vec2i movement;
        int distance = event.hasShiftDown() ? 6 : 1;
        switch (event.key()) {
            case 263: { movement = new Vec2i(-distance, 0); break; }
            case 262: { movement = new Vec2i(distance, 0); break; }
            case 265: { movement = new Vec2i(0, -distance); break; }
            case 264: { movement = new Vec2i(0, distance); break; }
            default: { movement = null; }
        }
        if (movement != null) {
            Vec2i before = this.rep.offset;
            this.rep.offset = this.rep.offset.add(movement);
            this.repositionButtons(this.rep, before);
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            this.dragging = false;
            return true;
        }
        for (TriggerButton cb : this.buttons) {
            if (!cb.isMouseOver(event.x(), event.y())) continue;
            cb.mouseClicked(event, doubleClick);
            this.rep = cb;
            this.dragging = true;
            return true;
        }
        for (Slot slot : this.underlay.getMenu().slots) {
            Object object;
            if (!((AbstractContainerScreenAccessor)this.underlay).clientsort$isHovering(slot, event.x(), event.y()) || (object = ClientSort.getObj(slot, this.underlay.getMenu())) == null || !object.getClass().getName().equals(this.lowestPolicyClassName)) continue;
            int slotId = ((ISlot)slot).clientsort$getIndexInContainer();
            if (this.ignoredSlots.contains(slotId)) {
                this.ignoredSlots.remove(slotId);
                continue;
            }
            this.ignoredSlots.add(slotId);
        }
        return false;
    }

    public boolean mouseDragged(@NotNull MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging) {
            Vec2i before = this.rep.offset;
            if (this.rep.mouseDragged(event, dragX, dragY)) {
                this.repositionButtons(this.rep, before);
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    private void repositionButtons(TriggerButton button, Vec2i before) {
        if (!button.offset.equals(before)) {
            Vec2i diff = button.offset.subtract(before);
            for (TriggerButton cb : this.buttons) {
                if (cb == button) continue;
                cb.offset = cb.offset.add(diff);
            }
        }
    }
}

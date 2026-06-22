/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.gui.screens.inventory.InventoryScreen
 *  net.minecraft.client.input.InputWithModifiers
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.input.MouseButtonInfo
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.Container
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.Slot
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.gui;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.ContainerEditorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.PlayerEditorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.screen.edit.SelectorScreen;
import com.thelads.core.features.alwayson.clientsort.gui.widget.MatchTransferButton;
import com.thelads.core.features.alwayson.clientsort.gui.widget.SortButton;
import com.thelads.core.features.alwayson.clientsort.gui.widget.StackFillButton;
import com.thelads.core.features.alwayson.clientsort.gui.widget.TransferButton;
import com.thelads.core.features.alwayson.clientsort.gui.widget.TriggerButton;
import com.thelads.core.features.alwayson.clientsort.inventory.Scope;
import com.thelads.core.features.alwayson.clientsort.inventory.helper.ContainerScreenHelper;
import com.thelads.core.features.alwayson.clientsort.util.KeybindManager;
import com.thelads.core.features.alwayson.clientsort.util.PolicyManager;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractContainerScreenAccessor;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.ScreenAccessor;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.lang.invoke.LambdaMetafactory;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

public class TriggerButtonManager {
    private static final int BUTTON_SPACING = 1;
    @Nullable
    private static AbstractContainerScreen<?> screen;
    private static final LinkedHashSet<TriggerButton> containerButtons;
    private static final LinkedHashSet<TriggerButton> visibleContainerButtons;
    private static final LinkedHashSet<TriggerButton> playerButtons;
    private static final LinkedHashSet<TriggerButton> visiblePlayerButtons;

    private TriggerButtonManager() {
    }

    @Nullable
    public static AbstractContainerScreen<?> getScreen() {
        return screen;
    }

    public static LinkedList<TriggerButton> getContainerButtons() {
        return new LinkedList<TriggerButton>(containerButtons);
    }

    public static LinkedList<TriggerButton> getPlayerButtons() {
        return new LinkedList<TriggerButton>(playerButtons);
    }

    @Nullable
    public static Slot getContainerRefSlot(Operation op) {
        return TriggerButtonManager.getRefSlot(containerButtons, op);
    }

    @Nullable
    public static Slot getPlayerRefSlot(Operation op) {
        return TriggerButtonManager.getRefSlot(playerButtons, op);
    }

    @Nullable
    private static Slot getRefSlot(LinkedHashSet<TriggerButton> buttons, Operation op) {
        Class clazz = switch (op) {
            default -> throw new MatchException(null, null);
            case Operation.SORT -> SortButton.class;
            case Operation.STACK_FILL -> StackFillButton.class;
            case Operation.MATCH_TRANSFER -> MatchTransferButton.class;
            case Operation.TRANSFER -> TransferButton.class;
        };
        for (TriggerButton button : buttons) {
            if (!((Object)((Object)button)).getClass().equals(clazz)) continue;
            return button.referenceSlot;
        }
        return null;
    }

    public static void afterScreenInit(Screen initScreen) {
        Slot refSlotP;
        Slot refSlotC;
        if (!(initScreen instanceof AbstractContainerScreen)) {
            return;
        }
        AbstractContainerScreen acs = (AbstractContainerScreen)initScreen;
        if (Minecraft.getInstance().player.isSpectator()) {
            return;
        }
        containerButtons.clear();
        visibleContainerButtons.clear();
        playerButtons.clear();
        visiblePlayerButtons.clear();
        screen = acs;
        boolean enabled = Config.options().showButtons;
        boolean isEditorC = false;
        boolean isEditorP = false;
        Screen currentScreen = Minecraft.getInstance().gui.screen();
        if (currentScreen instanceof SelectorScreen) {
            isEditorC = true;
            isEditorP = true;
        } else if (currentScreen instanceof ContainerEditorScreen) {
            isEditorC = true;
        } else if (currentScreen instanceof PlayerEditorScreen) {
            isEditorP = true;
        }
        boolean left = Config.options().anchorButtonsLeft;
        boolean justifyLeft = Config.options().justifyButtonsTopLeft;
        SequencedCollection<Operation> ops = List.of(Config.options().firstButtonOp, Config.options().secondButtonOp, Config.options().thirdButtonOp, Config.options().fourthButtonOp);
        if (!justifyLeft) {
            ops = ops.reversed();
        }
        if ((refSlotC = TriggerButtonManager.getReferenceSlot(acs, false, left)) != null) {
            boolean isEditor = isEditorC;
            ops.forEach(op -> TriggerButtonManager.generate(acs, refSlotC, left, justifyLeft, false, isEditor, enabled, op));
        }
        if ((refSlotP = TriggerButtonManager.getReferenceSlot(acs, true, left)) != null) {
            boolean isEditor = isEditorP;
            ops.forEach(op -> TriggerButtonManager.generate(acs, refSlotP, left, justifyLeft, true, isEditor, enabled, op));
        }
    }

    private static void generate(AbstractContainerScreen<?> screen, Slot referenceSlot, boolean referenceLeft, boolean justifyTopLeft, boolean isPlayerInv, boolean isEditor, boolean enabled, Operation op) {
        switch (op) {
            case SORT: {
                TriggerButtonManager.generateSimpleButton(screen, referenceSlot, referenceLeft, justifyTopLeft, isPlayerInv, isEditor, enabled, ClassPolicy::canSort, ClassPolicy::showSortButton, ClassPolicy::autoSort, SortButton::new, (Component)Localization.localized("key", "op.sort", new Object[0]));
                break;
            }
            case STACK_FILL: {
                TriggerButtonManager.generateDirectionalButton(screen, referenceSlot, referenceLeft, justifyTopLeft, isPlayerInv, isEditor, enabled, ClassPolicy::canStackFill, ClassPolicy::showStackFillButton, ClassPolicy::autoStackFill, StackFillButton::new, (Component)Localization.localized("key", "op.stackFill", new Object[0]));
                break;
            }
            case MATCH_TRANSFER: {
                TriggerButtonManager.generateDirectionalButton(screen, referenceSlot, referenceLeft, justifyTopLeft, isPlayerInv, isEditor, enabled, ClassPolicy::canMatchTransfer, ClassPolicy::showMatchTransferButton, ClassPolicy::autoMatchTransfer, MatchTransferButton::new, (Component)Localization.localized("key", "op.matchTransfer", new Object[0]));
                break;
            }
            case TRANSFER: {
                TriggerButtonManager.generateDirectionalButton(screen, referenceSlot, referenceLeft, justifyTopLeft, isPlayerInv, isEditor, enabled, ClassPolicy::canTransfer, ClassPolicy::showTransferButton, ClassPolicy::autoTransfer, TransferButton::new, (Component)Localization.localized("key", "op.transfer", new Object[0]));
            }
        }
    }

    private static void generateSimpleButton(AbstractContainerScreen<?> screen, Slot referenceSlot, boolean referenceLeft, boolean justifyTopLeft, boolean isPlayerInv, boolean isEditor, boolean enabled, Function<ClassPolicy, Boolean> opCheck, Function<ClassPolicy, Boolean> buttonCheck, Function<ClassPolicy, Boolean> autoCheck, TriggerButtonCreator creator, Component name) {
        Vec2i offset;
        boolean add;
        boolean autoPress = false;
        @Nullable LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (screen instanceof InventoryScreen && !isPlayerInv) {
            return;
        }
        @Nullable Object container = isPlayerInv ? player.getInventory() : TriggerButtonManager.getContainer((Player)player);
        Object object = com.thelads.core.features.alwayson.clientsort.ClientSort.getObj((Container)container, screen.getMenu());
        if (object == null) {
            return;
        }
        Component invTitle = isPlayerInv ? ((AbstractContainerScreenAccessor)screen).clientsort$getPlayerInventoryTitle() : screen.getTitle();
        @Nullable ClassPolicy policy = PolicyManager.getPolicy(object.getClass(), invTitle.getString());
        boolean create = isEditor || policy == null || opCheck.apply(policy) != false;
        boolean bl = add = enabled && (isEditor || policy != null && buttonCheck.apply(policy) != false);
        if (!create) {
            return;
        }
        Vec2i vec2i = offset = policy != null ? policy.getButtonOffset() : Config.options().layoutOffset;
        if (policy != null && autoCheck.apply(policy).booleanValue()) {
            autoPress = true;
        }
        TriggerButton button = creator.create(screen, (Container)container, referenceSlot, referenceLeft, isPlayerInv, policy, ClassPolicy.getKey(object.getClass().getName(), null), TriggerButtonManager.getShiftedOffset(offset, isPlayerInv, justifyTopLeft), name);
        BiConsumer<SequencedCollection, TriggerButton> adder = justifyTopLeft ? Collection::add : SequencedCollection::addFirst;
        adder.accept(isPlayerInv ? playerButtons : containerButtons, button);
        if (add) {
            adder.accept(isPlayerInv ? visiblePlayerButtons : visibleContainerButtons, button);
            ((ScreenAccessor)screen).clientsort$addRenderableWidget(button);
        }
        if (autoPress) {
            ClientSortClient.taskManager.schedule(isPlayerInv ? Config.options().autoOpDelayPlayer : Config.options().autoOpDelayContainer, () -> {
                if (Minecraft.getInstance().gui.screen() == screen && !KeybindManager.isDown(KeybindManager.CANCEL_AUTO_KEY) && (isPlayerInv ? playerButtons : containerButtons).contains((Object)button)) {
                    button.onPress((InputWithModifiers)new MouseButtonEvent((double)button.getX(), (double)button.getY(), new MouseButtonInfo(0, 0)));
                }
            });
        }
    }

    private static void generateDirectionalButton(AbstractContainerScreen<?> screen, Slot referenceSlot, boolean referenceLeft, boolean justifyTopLeft, boolean isPlayerInv, boolean isEditor, boolean enabled, Function<ClassPolicy, Boolean> opCheck, Function<ClassPolicy, Boolean> buttonCheck, Function<ClassPolicy, Boolean> autoCheck, TriggerButtonCreator creator, Component name) {
        Container dstContainer;
        Vec2i offset;
        boolean add;
        boolean autoPress = false;
        @Nullable LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (screen instanceof InventoryScreen && !isPlayerInv) {
            return;
        }
        @Nullable Object container = isPlayerInv ? player.getInventory() : TriggerButtonManager.getContainer((Player)player);
        Object object = com.thelads.core.features.alwayson.clientsort.ClientSort.getObj((Container)container, screen.getMenu());
        if (object == null) {
            return;
        }
        Component invTitle = isPlayerInv ? ((AbstractContainerScreenAccessor)screen).clientsort$getPlayerInventoryTitle() : screen.getTitle();
        @Nullable ClassPolicy policy = PolicyManager.getPolicy(object.getClass(), invTitle.getString());
        boolean create = isEditor || policy == null || opCheck.apply(policy) != false;
        boolean bl = add = enabled && (isEditor || policy != null && buttonCheck.apply(policy) != false);
        if (!create) {
            return;
        }
        Vec2i vec2i = offset = policy != null ? policy.getButtonOffset() : Config.options().layoutOffset;
        if (policy != null && autoCheck.apply(policy).booleanValue() && !policy.autoOpOther()) {
            autoPress = true;
        }
        Object object2 = dstContainer = isPlayerInv ? TriggerButtonManager.getContainer((Player)player) : player.getInventory();
        if (dstContainer != null) {
            Object dstObject = com.thelads.core.features.alwayson.clientsort.ClientSort.getObj(dstContainer, screen.getMenu());
            if (dstObject == null) {
                return;
            }
            Component dstInvTitle = isPlayerInv ? screen.getTitle() : ((AbstractContainerScreenAccessor)screen).clientsort$getPlayerInventoryTitle();
            @Nullable ClassPolicy dstPolicy = PolicyManager.getPolicy(dstObject.getClass(), dstInvTitle.getString());
            create = isEditor || dstPolicy == null || opCheck.apply(dstPolicy) != false;
            boolean bl2 = add = add && (isEditor || dstPolicy != null && buttonCheck.apply(dstPolicy) != false);
            if (!create) {
                return;
            }
            if (dstPolicy != null && autoCheck.apply(dstPolicy).booleanValue() && dstPolicy.autoOpOther()) {
                autoPress = true;
            }
        }
        TriggerButton button = creator.create(screen, (Container)container, referenceSlot, referenceLeft, isPlayerInv, policy, ClassPolicy.getKey(object.getClass().getName(), null), TriggerButtonManager.getShiftedOffset(offset, isPlayerInv, justifyTopLeft), name);
        BiConsumer<SequencedCollection, TriggerButton> adder = justifyTopLeft ? Collection::add : SequencedCollection::addFirst;
        adder.accept(isPlayerInv ? playerButtons : containerButtons, button);
        if (add) {
            adder.accept(isPlayerInv ? visiblePlayerButtons : visibleContainerButtons, button);
            ((ScreenAccessor)screen).clientsort$addRenderableWidget(button);
        }
        if (autoPress) {
            ClientSortClient.taskManager.schedule(isPlayerInv ? Config.options().autoOpDelayPlayer : Config.options().autoOpDelayContainer, () -> {
                if (Minecraft.getInstance().gui.screen() == screen && !KeybindManager.isDown(KeybindManager.CANCEL_AUTO_KEY) && (isPlayerInv ? playerButtons : containerButtons).contains((Object)button)) {
                    button.onPress((InputWithModifiers)new MouseButtonEvent((double)button.getX(), (double)button.getY(), new MouseButtonInfo(0, 0)));
                }
            });
        }
    }

    @Nullable
    public static Container getContainer(Player player) {
        class ScoredContainer {
            public final Container container;
            public int score;

            public ScoredContainer(Container container, int score) {
                this.container = container;
                this.score = score;
            }
        }
        HashMap<Container, ScoredContainer> map = new HashMap<Container, ScoredContainer>();
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == null) continue;
            if (slot.container == player.getInventory() || slot.container instanceof Inventory) break;
            @Nullable ScoredContainer scoredContainer = (ScoredContainer)map.get(slot.container);
            if (scoredContainer == null) {
                map.put(slot.container, new ScoredContainer(slot.container, 1));
                continue;
            }
            ++scoredContainer.score;
        }
        if (map.isEmpty()) {
            return null;
        }
        return map.values().stream().max(Comparator.comparingInt((ToIntFunction<ScoredContainer>)s -> s.score)).get().container;
    }

    @Nullable
    private static Slot getReferenceSlot(AbstractContainerScreen<?> screen, boolean isPlayerInv, boolean anchorButtonsLeft) {
        ContainerScreenHelper<AbstractContainerScreen<?>> helper = ContainerScreenHelper.of(screen);
        Slot bestSlot = null;
        double bestScore = 0.0;
        Scope scope = isPlayerInv ? Scope.PLAYER_INV : Scope.CONTAINER_INV;
        for (Slot slot : helper.getLargestSlotGroup(scope)) {
            double yFactor;
            double y;
            double xFactor = (double)Math.clamp((long)slot.x, 0, screen.width) / (double)screen.width;
            double x = anchorButtonsLeft ? 1.0 - xFactor : xFactor;
            double score = x * 0.8 + (y = 1.0 - (yFactor = (double)Math.clamp((long)slot.y, 0, screen.height) / (double)screen.height)) * 0.2;
            if (!(score > bestScore)) continue;
            bestSlot = slot;
            bestScore = score;
        }
        return bestSlot;
    }

    public static Vec2i getShiftedOffset(Vec2i offset, boolean isPlayerInv, boolean justifyTopLeft) {
        int y;
        int x;
        int shiftY;
        int index = (isPlayerInv ? visiblePlayerButtons : visibleContainerButtons).size();
        boolean horizontal = Config.options().buttonsHorizontal;
        int shiftX = horizontal ? 1 : 0;
        int n = shiftY = horizontal ? 0 : 1;
        if (justifyTopLeft) {
            x = offset.x() + shiftX * 14 * index;
            y = offset.y() + shiftY * 14 * index;
        } else {
            x = offset.x() - shiftX * 14 * index;
            y = offset.y() - shiftY * 14 * index;
        }
        return new Vec2i(x, y);
    }

    

    static {
        containerButtons = new LinkedHashSet();
        visibleContainerButtons = new LinkedHashSet();
        playerButtons = new LinkedHashSet();
        visiblePlayerButtons = new LinkedHashSet();
    }

    @FunctionalInterface
    public static interface TriggerButtonCreator {
        public TriggerButton create(AbstractContainerScreen<?> var1, Container var2, Slot var3, boolean var4, boolean var5, @Nullable ClassPolicy var6, String var7, Vec2i var8, Component var9);
    }
}

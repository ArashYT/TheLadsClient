/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.Window
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
 *  net.minecraft.client.KeyboardHandler
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.MouseHandler
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
 *  net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
 *  net.minecraft.client.gui.screens.packs.PackSelectionScreen
 *  net.minecraft.client.gui.screens.packs.TransferableSelectionList
 *  net.minecraft.client.gui.screens.packs.TransferableSelectionList$Entry
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.FormattedCharSequence
 */
package fuzs.resourcepackoverrides.common.client.handler;

import com.mojang.blaze3d.platform.Window;
import fuzs.resourcepackoverrides.common.client.handler.PackAction;
import fuzs.resourcepackoverrides.common.config.ResourceOverridesManager;
import fuzs.resourcepackoverrides.common.services.ClientAbstractions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class PackActionsHandler {
    public static final PackAction COPY_ID_ACTION = new PackAction(67, "copy_id"){

        @Override
        boolean execute(Minecraft minecraft, PackSelectionScreen screen) {
            MouseHandler mouseHandler = minecraft.mouseHandler;
            Window window = minecraft.getWindow();
            int mouseX = (int)(mouseHandler.xpos() * (double)window.getGuiScaledWidth() / (double)window.getScreenWidth());
            int mouseY = (int)(mouseHandler.ypos() * (double)window.getGuiScaledHeight() / (double)window.getScreenHeight());
            Optional<String> hoveredPackId = PackActionsHandler.getHoveredPackId(screen, mouseX, mouseY);
            hoveredPackId.ifPresent(arg_0 -> ((KeyboardHandler)minecraft.keyboardHandler).setClipboard(arg_0));
            return hoveredPackId.isPresent();
        }
    };
    public static final PackAction TOGGLE_DEBUG_ACTION = new PackAction(68, "toggle_debug"){

        @Override
        boolean execute(Minecraft minecraft, PackSelectionScreen screen) {
            debugTooltips = !debugTooltips;
            return true;
        }
    };
    public static final PackAction RELOAD_SETTINGS_ACTION = new PackAction(82, "reload_settings"){

        @Override
        boolean execute(Minecraft minecraft, PackSelectionScreen screen) {
            ResourceOverridesManager.load();
            screen.reload();
            return true;
        }
    };
    public static final PackAction RESTORE_DEFAULTS_ACTION = new PackAction(84, "restore_defaults"){

        @Override
        boolean execute(Minecraft minecraft, PackSelectionScreen screen) {
            minecraft.getResourcePackRepository().setSelected(ResourceOverridesManager.getDefaultResourcePacks(true));
            screen.model.selected.clear();
            screen.model.selected.addAll(minecraft.getResourcePackRepository().getSelectedPacks().stream().filter(Predicate.not(ClientAbstractions.INSTANCE::isPackHidden)).toList());
            Collections.reverse(screen.model.selected);
            screen.reload();
            return true;
        }
    };
    private static final Int2ObjectMap<PackAction> PACK_ACTIONS = (Int2ObjectMap)Stream.of(COPY_ID_ACTION, TOGGLE_DEBUG_ACTION, RELOAD_SETTINGS_ACTION, RESTORE_DEFAULTS_ACTION).collect(Collectors.toMap(PackAction::getKeyCode, Function.identity(), (o1, o2) -> o2, Int2ObjectOpenHashMap::new));
    private static boolean debugTooltips;

    public static void onBeforeRenderScreen(Minecraft minecraft, PackSelectionScreen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (debugTooltips && screen.model.repository == minecraft.getResourcePackRepository()) {
            PackActionsHandler.getHoveredPackId(screen, mouseX, mouseY).map(Component::literal).ifPresent(component -> {
                ClientTooltipComponent clientTooltipComponent = ClientTooltipComponent.create((FormattedCharSequence)component.getVisualOrderText());
                guiGraphics.tooltip(screen.getFont(), Collections.singletonList(clientTooltipComponent), mouseX, mouseY - (ClientAbstractions.INSTANCE.getModLoader() == ClientAbstractions.ModLoader.FABRIC ? 15 : 0), DefaultTooltipPositioner.INSTANCE, null);
            });
        }
    }

    private static Optional<String> getHoveredPackId(PackSelectionScreen screen, int mouseX, int mouseY) {
        if (screen == null) {
            return Optional.empty();
        }
        for (GuiEventListener guiEventListener : screen.children()) {
            if (!(guiEventListener instanceof TransferableSelectionList)) continue;
            TransferableSelectionList selectionList = (TransferableSelectionList)guiEventListener;
            TransferableSelectionList.Entry hoveredEntry = null;
            for (TransferableSelectionList.Entry packEntry : selectionList.children()) {
                if (!packEntry.isMouseOver((double)mouseX, (double)mouseY)) continue;
                hoveredEntry = packEntry;
                break;
            }
            if (hoveredEntry == null) continue;
            return Optional.of(hoveredEntry.getPackId().replace("\u00a7", "\\u00A7"));
        }
        return Optional.empty();
    }

    public static void onEndClientTick(Minecraft minecraft) {
        PACK_ACTIONS.values().forEach(packAction -> packAction.tick(minecraft));
    }

    public static void onAfterKeyPressed(Minecraft minecraft, PackSelectionScreen screen, KeyEvent keyEvent) {
        PackAction packAction;
        if (screen.model.repository == minecraft.getResourcePackRepository() && keyEvent.hasControlDownWithQuirk() && (packAction = (PackAction)PACK_ACTIONS.get(keyEvent.key())) != null) {
            packAction.update();
        }
    }
}


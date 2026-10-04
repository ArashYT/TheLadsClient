package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * QA only (LADS_VERIFY_189_FOCUS=inventory): the survival and creative inventories with and without potion effects. Each is
 * checked to sit at the centred x (lads-qa/screenshots/189-inv-*.png), with every slot found where the centred picture draws it:
 * the screen is drawn with the mouse over each slot (the real cursor is not moved), which is the hover path. Then the creative
 * Search Items tab (its search box), and a narrow window (800x600) where the effect list is the icon column, hovered for its tooltip.
 */
final class ProbeInventory {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static WorldSettings.GameType modeWas;
    private static ItemStack slotWas;
    private static int widthWas, heightWas, tabWas = -1, leftSurvival = -1, leftCreative = -1;
    private static boolean asked;

    private ProbeInventory() {}

    static List<CoreProbe.Step> steps() {
        return Arrays.<CoreProbe.Step>asList(
            ProbeInventory::begin,
            mc -> open(mc, WorldSettings.GameType.SURVIVAL),
            mc -> {
                leftSurvival = scene(mc, "survival, no effects", "189-inv-survival-none")[2];
                return give(mc);
            },
            ProbeInventory::arrived,
            mc -> {
                check(scene(mc, "survival, with effects", "189-inv-survival-effects")[2] == leftSurvival, "the survival inventory did not move when the effects arrived");
                return after(1);
            },
            mc -> open(mc, WorldSettings.GameType.CREATIVE),
            mc -> {
                leftCreative = scene(mc, "creative, with effects", "189-inv-creative-effects")[2];
                setTab(5); // Search Items: its search box is placed from guiLeft too
                mc.displayGuiScreen(new GuiContainerCreative(mc.thePlayer));
                return after(25);
            },
            mc -> {
                scene(mc, "creative search tab, with effects", "189-inv-creative-search-effects");
                return clear(mc);
            },
            ProbeInventory::gone,
            mc -> {
                check(scene(mc, "creative search tab, effects ended", "189-inv-creative-search-none")[2] == leftCreative, "the creative inventory did not move when the effects ended");
                return give(mc);
            },
            ProbeInventory::arrived,
            mc -> {
                resize(mc, 800, 600);
                return after(40);
            },
            mc -> {
                int[] box = scene(mc, "creative search tab, narrow window, with effects", "189-inv-creative-search-effects-narrow");
                hover(mc, box[2] - 34 + 16, box[3] + 16, "189-inv-creative-search-effects-narrow-tooltip");
                return after(1);
            },
            mc -> open(mc, WorldSettings.GameType.SURVIVAL),
            mc -> {
                int[] box = scene(mc, "survival, narrow window, with effects", "189-inv-survival-effects-narrow");
                hover(mc, box[2] - 34 + 16, box[3] + 16, "189-inv-survival-effects-narrow-tooltip");
                return end(mc);
            });
    }

    private static boolean begin(Minecraft mc) {
        modeWas = mc.playerController.getCurrentGameType();
        widthWas = Display.getWidth();
        heightWas = Display.getHeight();
        slotWas = mc.thePlayer.inventory.mainInventory[0] == null ? null : mc.thePlayer.inventory.mainInventory[0].copy();
        LOG.info("Lads 1.8.9 inventory probe: begin in {} mode, window {}x{}, hotbar slot 0 holds {}", modeWas, widthWas, heightWas, slotWas);
        onServer(mc, player -> {
            player.clearActivePotions();
            player.inventory.mainInventory[0] = new ItemStack(Items.diamond, 5); // a tooltip to hover
        });
        return after(20);
    }

    /** In this game mode (asked of the server first), the inventory screen opens (a creative player's turns into the creative one). */
    private static boolean open(Minecraft mc, WorldSettings.GameType mode) {
        if (mc.playerController.getCurrentGameType() != mode) {
            if (!asked) onServer(mc, player -> player.setGameType(mode));
            asked = true;
            return retry(5);
        }
        asked = false;
        mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
        return after(25);
    }

    private static boolean give(Minecraft mc) {
        onServer(mc, player -> {
            player.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 12000));
            player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 12000));
        });
        return after(1);
    }

    private static boolean clear(Minecraft mc) {
        onServer(mc, EntityPlayerMP::clearActivePotions);
        return after(1);
    }

    /** The effects reached the client and the screen has had ticks to move itself for them: vanilla does. */
    private static boolean arrived(Minecraft mc) {
        return mc.thePlayer.getActivePotionEffects().size() < 2 ? retry(5) : after(25);
    }

    private static boolean gone(Minecraft mc) {
        return !mc.thePlayer.getActivePotionEffects().isEmpty() ? retry(5) : after(25);
    }

    private static boolean end(Minecraft mc) throws Exception {
        setTab(-1);
        resize(mc, widthWas, heightWas);
        mc.displayGuiScreen(null);
        ItemStack held = slotWas;
        onServer(mc, player -> {
            player.clearActivePotions();
            player.inventory.mainInventory[0] = held;
            player.setGameType(modeWas);
        });
        return after(20);
    }

    /** The screen the player has now, checked and photographed. Its xSize, ySize, guiLeft and guiTop. */
    private static int[] scene(Minecraft mc, String what, String shot) throws Exception {
        if (!(mc.currentScreen instanceof GuiContainer)) throw new IllegalStateException("1.8.9 inventory QA: no inventory screen for " + what);
        GuiContainer screen = (GuiContainer) mc.currentScreen;
        ScaledResolution scaled = new ScaledResolution(mc);
        int[] box = box(screen);
        int centredLeft = (scaled.getScaledWidth() - box[0]) / 2, centredTop = (scaled.getScaledHeight() - box[1]) / 2;
        CoreProbe.screenshot(mc, shot);
        int found = 0, slots = 0;
        mc.getFramebuffer().bindFramebuffer(true);
        mc.entityRenderer.setupOverlayRendering();
        for (Slot slot : screen.inventorySlots.inventorySlots) {
            slots++;
            screen.drawScreen(centredLeft + slot.xDisplayPosition + 8, centredTop + slot.yDisplayPosition + 8, 0.0F);
            if (screen.getSlotUnderMouse() == slot) found++;
        }
        mc.getFramebuffer().unbindFramebuffer();
        LOG.info("Lads 1.8.9 inventory probe: {}: {} at x {} y {} of a {}x{} GUI (centred {}, {}), {} of {} slots hovered where the centred picture draws them, effects {}",
            what, screen.getClass().getSimpleName(), box[2], box[3], scaled.getScaledWidth(), scaled.getScaledHeight(), centredLeft, centredTop, found, slots,
            mc.thePlayer.getActivePotionEffects().size());
        check(box[2] == centredLeft && box[3] == centredTop, what + ": the inventory is centred (x " + box[2] + ", centred " + centredLeft + ")");
        check(slots > 0 && found == slots, what + ": slot hit-testing lines up with the picture (" + found + "/" + slots + ")");
        Slot last = screen.inventorySlots.inventorySlots.get(screen.inventorySlots.inventorySlots.size() - 1);
        hover(mc, centredLeft + last.xDisplayPosition + 8, centredTop + last.yDisplayPosition + 8, shot + "-hover");
        return box;
    }

    /** The open screen drawn once more over the last frame with the mouse at a GUI position, and photographed. */
    private static void hover(Minecraft mc, int guiX, int guiY, String shot) {
        mc.getFramebuffer().bindFramebuffer(true);
        mc.entityRenderer.setupOverlayRendering();
        mc.currentScreen.drawScreen(guiX, guiY, 0.0F);
        CoreProbe.screenshot(mc, shot);
        mc.getFramebuffer().unbindFramebuffer();
    }

    private static void resize(Minecraft mc, int width, int height) throws Exception {
        Display.setDisplayMode(new DisplayMode(width, height));
        mc.resize(Display.getWidth(), Display.getHeight());
    }

    /** GuiContainer's protected ints, by shape (the names differ between dev and game): xSize, ySize, guiLeft, guiTop. */
    private static int[] box(GuiContainer screen) throws Exception {
        List<Field> ints = new ArrayList<>();
        for (Field field : GuiContainer.class.getDeclaredFields())
            if (field.getType() == int.class && Modifier.isProtected(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) ints.add(field);
        if (ints.size() != 4) throw new IllegalStateException("GuiContainer has " + ints.size() + " protected ints, not 4");
        int[] box = new int[4];
        for (int i = 0; i < 4; i++) {
            ints.get(i).setAccessible(true);
            box[i] = ints.get(i).getInt(screen);
        }
        return box;
    }

    /** The creative inventory's selected tab (its one non-final static int), which a new creative screen opens on; -1: put it back. */
    private static void setTab(int index) throws Exception {
        for (Field field : GuiContainerCreative.class.getDeclaredFields())
            if (field.getType() == int.class && Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                if (tabWas < 0) tabWas = field.getInt(null);
                field.setInt(null, index < 0 ? tabWas : index);
                return;
            }
        throw new IllegalStateException("GuiContainerCreative has no selected-tab field");
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<EntityPlayerMP> task) {
        java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> task.accept(mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id)));
    }
}

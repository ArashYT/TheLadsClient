package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-inventory" from the harness's LADS_VERIFY_CAPTURE_INVENTORY): the survival and creative
 * inventories with and without potion effects (inv-*.png), each checked to sit at the centred x, with every slot found where the
 * centred picture draws it; the survival recipe book open; and a narrow GUI (scale 4 on the 1280x720 window, 320x180).
 */
final class InventoryCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> FAILURES = new ArrayList<>();
    private static int step = -1, passed, saved, scaleWas, scaleNow, leftSurvival = -1, leftCreative = -1;
    private static long due;
    private static String shot, modeWas = "survival";
    private static boolean capturing;

    private InventoryCapture() {}

    static boolean busy() { return step >= 0 && step < 99; }

    static void tick(Path game, boolean ready) {
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-inventory");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads inventory capture FAILED: request", failure); return; }
        Minecraft mc = Minecraft.getInstance();
        modeWas = mc.gameMode.getPlayerMode().getName();
        scaleWas = mc.getWindow().getGuiScale();
        command("gamemode survival @a");
        command("effect clear @a");
        LOGGER.info("Lads inventory capture BEGIN: GUI scale {}, window {}x{}, game mode was {}", scaleWas, mc.getWindow().getGuiScaledWidth(),
            mc.getWindow().getGuiScaledHeight(), modeWas);
        step = 0;
        at(1500, null);
    }

    static void frame(RenderTarget target, Path game) {
        if (!busy() || capturing || System.nanoTime() < due) return;
        if (shot == null) { next(); return; }
        capturing = true;
        String name = shot;
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads inventory frame {}", output); }
                catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(InventoryCapture::next); }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            next();
        }
    }

    private static void next() {
        capturing = false;
        Minecraft mc = Minecraft.getInstance();
        try {
            switch (step++) {
                case 0 -> { mc.setScreenAndShow(new InventoryScreen(mc.player)); at(700, "inv-survival-none"); }
                case 1 -> {
                    leftSurvival = centred("survival, no effects");
                    command("effect give @a minecraft:speed 600 0 true");
                    command("effect give @a minecraft:night_vision 600 0 true");
                    at(1500, null);
                }
                case 2 -> {
                    check(mc.player.getActiveEffects().size() == 2, "the player has 2 effects (" + mc.player.getActiveEffects().size() + ")");
                    at(300, "inv-survival-effects");
                }
                case 3 -> {
                    check(centred("survival, with effects") == leftSurvival, "the survival inventory did not move when the effects arrived (x " + leftSurvival + ")");
                    check(effectsShown(mc.gui.screen()), "the effect list is drawn beside the survival inventory");
                    toggleRecipeBook(mc);
                    at(700, "inv-survival-effects-book");
                }
                case 4 -> {
                    LOGGER.info("Lads inventory capture: recipe book open, the survival inventory is at x {} (vanilla puts it right of the book)", left(mc.gui.screen()));
                    toggleRecipeBook(mc);
                    at(500, null);
                }
                case 5 -> {
                    check(centred("survival, recipe book closed again") == leftSurvival, "closing the recipe book brings the inventory back to the same x");
                    command("gamemode creative @a");
                    at(500, null);
                }
                case 6 -> {
                    if (!mc.player.hasInfiniteMaterials()) { step--; at(200, null); return; }
                    mc.setScreenAndShow(new InventoryScreen(mc.player)); // opens the creative inventory
                    at(700, "inv-creative-effects");
                }
                case 7 -> {
                    check(mc.gui.screen() instanceof CreativeModeInventoryScreen, "the creative inventory is open (" + mc.gui.screen() + ")");
                    leftCreative = centred("creative, with effects");
                    check(effectsShown(mc.gui.screen()), "the effect list is drawn beside the creative inventory");
                    command("effect clear @a");
                    at(1500, null);
                }
                case 8 -> {
                    check(mc.player.getActiveEffects().isEmpty(), "the effects are gone");
                    at(300, "inv-creative-none");
                }
                case 9 -> {
                    check(centred("creative, no effects") == leftCreative, "the creative inventory did not move when the effects ended (x " + leftCreative + ")");
                    command("effect give @a minecraft:speed 600 0 true");
                    command("effect give @a minecraft:night_vision 600 0 true");
                    at(1500, null);
                }
                case 10 -> {
                    scaleNow = 4;
                    narrow(mc, 4);
                    at(500, "inv-creative-effects-narrow");
                }
                case 11 -> {
                    check(centred("creative, narrow GUI") >= 0, "the creative inventory is centred in the narrow GUI");
                    command("gamemode survival @a");
                    at(500, null);
                }
                case 12 -> {
                    if (mc.player.hasInfiniteMaterials()) { step--; at(200, null); return; }
                    mc.setScreenAndShow(new InventoryScreen(mc.player));
                    narrow(mc, 4);
                    at(700, "inv-survival-effects-narrow");
                }
                case 13 -> {
                    check(centred("survival, narrow GUI") >= 0, "the survival inventory is centred in the narrow GUI");
                    finish();
                }
                default -> finish();
            }
        } catch (Throwable failure) {
            fail("step " + step + ": " + failure);
            finish();
        }
    }

    private static void finish() {
        step = 99;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getWindow().setGuiScale(scaleWas);
            mc.resizeGui();
        } catch (Throwable failure) { fail("restoring the GUI scale: " + failure); }
        if (mc.gui.screen() != null) mc.setScreenAndShow(null);
        command("effect clear @a");
        command("gamemode " + modeWas + " @a");
        LOGGER.info("Lads inventory capture END: {} passed, {} failed, {} frames saved{}", passed, FAILURES.size(), saved,
            FAILURES.isEmpty() ? "" : "; " + FAILURES);
    }

    /** A narrow GUI: the 1280x720 window at a GUI scale beyond the game's own limit (3), as a window 320 px wide at scale 1 would give. */
    private static void narrow(Minecraft mc, int scale) {
        mc.getWindow().setGuiScale(scale);
        mc.gui.screen().resize(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private static boolean effectsShown(Screen screen) {
        return screen instanceof InventoryScreen inventory ? inventory.showsActiveEffects()
            : screen instanceof CreativeModeInventoryScreen creative && creative.showsActiveEffects();
    }

    /** Logs and checks where the open inventory is; its x. Every slot is hit where the centred picture draws it. */
    private static int centred(String what) throws ReflectiveOperationException {
        Screen screen = Minecraft.getInstance().gui.screen();
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        int left = left(screen), top = field(container, "topPos"), image = field(container, "imageWidth"), high = field(container, "imageHeight");
        int expectedLeft = (screen.width - image) / 2, expectedTop = (screen.height - high) / 2;
        Method hovered = AbstractContainerScreen.class.getDeclaredMethod("getHoveredSlot", double.class, double.class);
        hovered.setAccessible(true);
        int slots = 0, found = 0;
        for (Slot slot : container.getMenu().slots) {
            if (!slot.isActive()) continue;
            slots++;
            if (hovered.invoke(container, (double) (expectedLeft + slot.x + 8), (double) (expectedTop + slot.y + 8)) == slot) found++;
        }
        LOGGER.info("Lads inventory capture: {}: {} at x {} y {} of a {}x{} GUI (centred x {}), {} of {} slots found where the centred picture draws them, effects shown {}",
            what, screen.getClass().getSimpleName(), left, top, screen.width, screen.height, expectedLeft, found, slots, effectsShown(screen));
        check(left == expectedLeft && top == expectedTop, what + ": the inventory is centred (x " + left + ", centred " + expectedLeft + ")");
        check(slots > 0 && found == slots, what + ": slot hit-testing lines up with the picture (" + found + "/" + slots + ")");
        return left;
    }

    private static int left(Screen screen) throws ReflectiveOperationException { return field(screen, "leftPos"); }

    private static int field(Object screen, String name) throws ReflectiveOperationException {
        var field = AbstractContainerScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(screen);
    }

    /** A real click on the survival inventory's recipe book button (InventoryScreen puts it 104 pixels from the inventory's left). */
    private static void toggleRecipeBook(Minecraft mc) throws ReflectiveOperationException {
        Screen screen = mc.gui.screen();
        int x = left(screen) + 104;
        for (var child : screen.children())
            if (child instanceof ImageButton button && button.getX() == x) {
                screen.mouseClicked(new MouseButtonEvent(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false); // 0 on 26.2 (GLFW), 1 on 26.3 (SDL)
                return;
            }
        fail("the survival inventory has no recipe book button at x " + x);
    }

    private static void at(long ms, String name) {
        due = System.nanoTime() + ms * 1_000_000L;
        shot = name;
    }

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads inventory capture PASS: {}", what); }
        else fail(what);
    }

    private static void fail(String what) {
        FAILURES.add(what);
        LOGGER.error("Lads inventory capture FAILED: {}", what);
    }
}

package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.v26_2.feature.food.NativeFood;
import com.thelads.core.v26_2.feature.food.mixin.FoodDataAccess;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-hudinfo" from the harness's LADS_VERIFY_CAPTURE_HUDINFO): photographs the AppleSkin
 * module's hunger, saturation, exhaustion and health previews in survival, food and durability tooltips through the real tooltip
 * path, and a few Crosshair Tweaks styles, as hudinfo-*.png. Game mode, difficulty, food, health, held items and every module
 * option touched are restored afterwards.
 */
public final class HudInfoCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private record Shot(String name, Runnable setup, ItemStack tooltip) {}
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static int step = -1, wait, saved, food;
    private static float saturation, exhaustion, health;
    private static ItemStack held = ItemStack.EMPTY, main, off;
    private static GameType mode;
    private static Difficulty difficulty;
    private static final List<String> FAILURES = new ArrayList<>();
    private HudInfoCapture() {}

    public static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld")) ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (step < 0) {
            if (!NativeWorldVerification.worldReady()) return;
            Path request = FabricLoader.getInstance().getGameDir().resolve(".lads-qa-capture-hudinfo");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); begin(mc); }
            catch (Exception failure) { step = Integer.MAX_VALUE; LOGGER.error("Lads HUD info capture FAILED: start", failure); }
            return;
        }
        if (step >= SHOTS.size()) return;
        if (mc.player.getMainHandItem() != held) mc.player.setItemInHand(InteractionHand.MAIN_HAND, held); // the server may resend the slot
        if (--wait > 0) return;
        Shot shot = SHOTS.get(step);
        wait = Integer.MAX_VALUE; // until the frame is written
        try {
            Path folder = FabricLoader.getInstance().getGameDir().resolve("screenshots");
            Files.createDirectories(folder);
            Path output = folder.resolve("hudinfo-" + shot.name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads HUD info frame {}", output); }
                catch (Exception failure) { fail(shot.name + ": " + failure); }
                finally { image.close(); mc.execute(HudInfoCapture::next); }
            });
        } catch (Exception failure) { fail(shot.name + ": " + failure); next(); }
    }

    private static void begin(Minecraft mc) {
        ServerPlayer own = own();
        mode = own.gameMode();
        difficulty = mc.getSingleplayerServer().getWorldData().getDifficulty();
        food = own.getFoodData().getFoodLevel();
        saturation = own.getFoodData().getSaturationLevel();
        exhaustion = ((FoodDataAccess) own.getFoodData()).lads$exhaustion();
        health = own.getHealth();
        main = mc.player.getMainHandItem().copy();
        off = mc.player.getOffhandItem().copy();
        for (String name : List.of("AppleSkin", "EnhancedToolbars", "Crosshair Tweaks")) {
            Module module = NativeQualityOfLife.module(name);
            ENABLED.put(module, module.isEnabled());
            module.getOptions().forEach(option -> OPTIONS.put(option, option.save().deepCopy()));
            module.getOptions().stream().filter(option -> !(option instanceof com.thelads.core.config.ActionOption)).forEach(Option::reset);
            module.setEnabled(true);
        }
        NativeFood.qaPulse = 1f;
        server(player -> { player.setGameMode(GameType.SURVIVAL); player.level().getServer().setDifficulty(Difficulty.EASY, true); });
        // Survival previews: hunger and saturation from cooked beef over a half-empty bar, with exhaustion behind it.
        food("food-preview-beef", Items.COOKED_BEEF, 11, 4.5f, 2.5f, 11, () -> {});
        food("food-saturation", Items.DIAMOND, 20, 13f, 3f, 20, () -> {});
        food("food-rotten-flesh", Items.ROTTEN_FLESH, 6, 0, 1f, 20, () -> {});
        food("food-golden-apple-health", Items.GOLDEN_APPLE, 20, 2f, 0, 7, () -> {});
        food("food-saturation-off", Items.BREAD, 9, 6f, 0, 20, () -> option("AppleSkin", "Show Saturation", false));
        tooltip("apple", new ItemStack(Items.APPLE), () -> option("AppleSkin", "Show Saturation", true));
        tooltip("golden-carrot", new ItemStack(Items.GOLDEN_CARROT), () -> {});
        tooltip("rotten-flesh", new ItemStack(Items.ROTTEN_FLESH), () -> {});
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(1200);
        String[] styles = {"numbers", "bar", "text"};
        for (int i = 0; i < styles.length; i++) {
            int style = i;
            tooltip("durability-" + styles[i], sword, () -> ((DropdownOption) NativeQualityOfLife.module("EnhancedToolbars").getOption("Durability Style")).setIndex(style));
        }
        crosshair("cross", 0, () -> { option("Crosshair Tweaks", "Center Dot", true); });
        crosshair("circle-green", 4, () -> color("Color", 0xff55ff55));
        crosshair("triangle-rotated", 5, () -> slider("Rotation", 180));
        crosshair("arrow-thick", 6, () -> { slider("Rotation", 0); ((com.thelads.core.config.DoubleOption) NativeQualityOfLife.module("Crosshair Tweaks").getOption("Thickness")).set(2.0); });
        crosshair("vanilla-adaptive", 3, () -> {});
        LOGGER.info("Lads HUD info capture BEGIN: {} frames", SHOTS.size());
        step = 0;
        start();
    }

    /** The shot is written: the next one's state, or put everything back. */
    private static void next() {
        step++;
        if (step < SHOTS.size()) { start(); return; }
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.gui.screen() instanceof TooltipScreen) mc.setScreenAndShow(null);
            NativeFood.qaPulse = null;
            OPTIONS.forEach(Option::load);
            ENABLED.forEach(Module::setEnabled);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, main);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, off);
            server(player -> {
                player.getFoodData().setFoodLevel(food);
                player.getFoodData().setSaturation(saturation);
                ((FoodDataAccess) player.getFoodData()).lads$setExhaustion(exhaustion);
                player.setHealth(health);
                player.setGameMode(mode);
                player.level().getServer().setDifficulty(difficulty, true);
            });
        } catch (Exception failure) { fail("restore: " + failure); }
        held = ItemStack.EMPTY;
        if (FAILURES.isEmpty()) LOGGER.info("Lads HUD info capture END: {} frames saved, 0 failed", saved);
        else LOGGER.error("Lads HUD info capture FAILED: {}", String.join(" | ", FAILURES));
    }

    private static void start() {
        Minecraft mc = Minecraft.getInstance();
        Shot shot = SHOTS.get(step);
        try {
            shot.setup.run();
            if (shot.tooltip != null) mc.setScreenAndShow(new TooltipScreen(shot.tooltip));
            else if (mc.gui.screen() instanceof TooltipScreen) mc.setScreenAndShow(null);
        } catch (Exception failure) { fail(shot.name + " setup: " + failure); }
        wait = 25;
    }

    private static void food(String name, net.minecraft.world.item.Item item, int level, float saturation, float exhaustion, float health, Runnable setup) {
        SHOTS.add(new Shot(name, () -> {
            setup.run();
            held = new ItemStack(item);
            server(player -> {
                player.getFoodData().setFoodLevel(level);
                player.getFoodData().setSaturation(saturation);
                ((FoodDataAccess) player.getFoodData()).lads$setExhaustion(exhaustion);
                player.setHealth(health);
            });
        }, null));
    }
    private static void tooltip(String name, ItemStack stack, Runnable setup) {
        SHOTS.add(new Shot("tooltip-" + name, () -> { held = new ItemStack(Items.DIAMOND); setup.run(); }, stack));
    }
    private static void crosshair(String name, int shape, Runnable setup) {
        SHOTS.add(new Shot("crosshair-" + name, () -> {
            held = new ItemStack(Items.DIAMOND);
            ((DropdownOption) NativeQualityOfLife.module("Crosshair Tweaks").getOption("Shape")).setIndex(shape);
            setup.run();
        }, null));
    }
    private static void option(String module, String name, boolean value) { ((BoolOption) NativeQualityOfLife.module(module).getOption(name)).set(value); }
    private static void slider(String name, double value) { ((com.thelads.core.config.SliderOption) NativeQualityOfLife.module("Crosshair Tweaks").getOption(name)).setValue(value); }
    private static void color(String name, int argb) {
        var color = (ColorOption) NativeQualityOfLife.module("Crosshair Tweaks").getOption(name);
        color.setUseGlobal(false);
        color.setColor(argb);
    }

    private static ServerPlayer own() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }
    private static void server(Consumer<ServerPlayer> task) {
        Minecraft mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> task.accept(server.getPlayerList().getPlayer(id)));
    }
    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads HUD info capture check failed: {}", failure);
    }

    /** The world behind one item's real tooltip (ItemStack.getTooltipLines and the game's tooltip renderer). */
    private static final class TooltipScreen extends Screen {
        private final ItemStack stack;
        TooltipScreen(ItemStack stack) { super(Component.literal("QA tooltip")); this.stack = stack; }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
            graphics.setTooltipForNextFrame(font, stack, width / 2 - 60, height / 2 - 20);
        }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {}
        @Override public boolean isPauseScreen() { return false; }
    }
}

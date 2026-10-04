package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.CustomFovModule;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/**
 * QA only, run by CoreProbe in its QA world (-Dthelads.verify189F3Fov=true runs it alone). Better F3: the debug screen with the
 * module off (vanilla + OptiFine), mid slide-in, with its defaults, with Hide Inessential off, and with System, World and the
 * background off (f3-*.png). Custom FOV: the player's getFovModifier standing, sprinting, flying, with Speed II (given by the
 * integrated server) and with a fully drawn bow, at 0% and 100% of that change, plus the world FOV the camera settled at
 * (Zoom189's last world FOV) standing (Underwater at 100% and 0%, should the camera be in water), with Speed II and with the bow;
 * lads-qa/screenshots/custom-fov.csv. Everything restored.
 */
final class Probe170F3Fov {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe170F3Fov::vanilla, Probe170F3Fov::betterOn,
        Probe170F3Fov::open, Probe170F3Fov::slide, Probe170F3Fov::defaults, Probe170F3Fov::allLines, Probe170F3Fov::sectionsOff,
        mc -> world(mc, "standing", "Underwater 100%", 1, CustomFovModule.UNDERWATER, 0),
        mc -> world(mc, "standing", "Underwater 0%", 1, CustomFovModule.UNDERWATER, 100) && give(mc), Probe170F3Fov::speedSynced,
        mc -> world(mc, "speed II", "100%", 1.2, CustomFovModule.EFFECTS, 0), mc -> world(mc, "speed II", "0%", 1, CustomFovModule.EFFECTS, 100),
        Probe170F3Fov::bowDrawn, mc -> world(mc, "bow drawn", "100%", 0.85, CustomFovModule.BOW, 0), Probe170F3Fov::bowDone);
    private static final Map<Option, JsonElement> SAVED = new LinkedHashMap<>();
    private static final StringBuilder CSV = new StringBuilder("state,share,fov_modifier,expected_modifier,world_fov\n");
    private static boolean f3Enabled, fovEnabled, flying, screenshotsEnabled;
    private static ItemStack heldBefore;
    private Probe170F3Fov() {}

    private static Module f3() { return ModuleManager.getInstance().getModule("BetterF3"); }
    private static Module fov() { return ModuleManager.getInstance().getModule(CustomFovModule.NAME); }

    private static boolean vanilla(Minecraft mc) {
        for (Module module : new Module[] {f3(), fov()}) for (Option option : module.getOptions()) SAVED.put(option, option.save());
        f3Enabled = f3().isEnabled();
        // A clear right column: no "Press E" hint, and no Lads preview of each QA screenshot over it.
        mc.guiAchievement.clearAchievements();
        screenshotsEnabled = ModuleManager.getInstance().getModule("BetterScreenshots").isEnabled();
        ModuleManager.getInstance().getModule("BetterScreenshots").setEnabled(false);
        fovEnabled = fov().isEnabled();
        f3().setEnabled(false);
        mc.gameSettings.showDebugInfo = true;
        return after(30);
    }

    private static boolean betterOn(Minecraft mc) {
        screenshot(mc, "f3-1-vanilla");
        mc.gameSettings.showDebugInfo = false;
        for (Option option : f3().getOptions()) option.reset();
        f3().setEnabled(true);
        return after(10);
    }

    private static boolean open(Minecraft mc) {
        mc.gameSettings.showDebugInfo = true;
        return after(2);
    }

    private static boolean slide(Minecraft mc) {
        screenshot(mc, "f3-2-slide-in");
        return after(24);
    }

    private static boolean defaults(Minecraft mc) {
        screenshot(mc, "f3-3-default");
        bool(f3(), "Hide Inessential", false);
        return after(12);
    }

    private static boolean allLines(Minecraft mc) {
        screenshot(mc, "f3-4-hide-inessential-off");
        bool(f3(), "Hide Inessential", true);
        bool(f3(), "Show System", false);
        bool(f3(), "Show World", false);
        bool(f3(), "Background", false);
        return after(12);
    }

    /** The last F3 frame; then the FOV modifier standing, sprinting and flying. */
    private static boolean sectionsOff(Minecraft mc) throws Exception {
        screenshot(mc, "f3-5-system-world-background-off");
        mc.gameSettings.showDebugInfo = false;
        for (Option option : f3().getOptions()) option.load(SAVED.get(option));
        f3().setEnabled(f3Enabled);
        ModuleManager.getInstance().getModule("BetterScreenshots").setEnabled(screenshotsEnabled);
        for (Option option : fov().getOptions()) option.reset();
        fov().setEnabled(true);
        flying = mc.thePlayer.capabilities.isFlying;
        mc.thePlayer.capabilities.isFlying = false;
        measure(mc, "standing", CustomFovModule.SPRINTING, 1);
        mc.thePlayer.setSprinting(true);
        measure(mc, "sprinting", CustomFovModule.SPRINTING, 1.15);
        mc.thePlayer.setSprinting(false);
        mc.thePlayer.capabilities.isFlying = true;
        measure(mc, "flying", CustomFovModule.FLYING, 1.1);
        mc.thePlayer.capabilities.isFlying = flying;
        return after(20);
    }

    /** The server gives Speed II and a bow. */
    private static boolean give(Minecraft mc) {
        heldBefore = ItemStack.copyItemStack(mc.thePlayer.getCurrentEquippedItem());
        server(mc, player -> {
            player.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 20 * 60, 1));
            player.inventory.setInventorySlotContents(player.inventory.currentItem, new ItemStack(Items.bow));
        });
        return after(30);
    }

    private static boolean speedSynced(Minecraft mc) {
        check(mc.thePlayer.isPotionActive(Potion.moveSpeed), "Custom FOV: the server's Speed II reached the client");
        measure(mc, "speed II", CustomFovModule.EFFECTS, 1.2);
        return after(20);
    }

    /**
     * After the camera settled: the world FOV is the FOV setting times the modifier and, with the camera in water, 1.8.9's 60/70
     * kept at the Underwater share; then {@code next} is set to {@code nextShare}% for the next step.
     */
    private static boolean world(Minecraft mc, String state, String share, double modifier, String next, int nextShare) {
        boolean wet = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, mc.getRenderViewEntity(), 1.0F).getMaterial() == Material.water;
        double water = wet ? CustomFovModule.scaled(60.0 / 70.0, ((CustomFovModule) fov()).share(CustomFovModule.UNDERWATER)) : 1;
        double expected = mc.gameSettings.fovSetting * modifier * water, world = Zoom189.lastWorldFov;
        String where = wet ? "water" : "dry";
        CSV.append(String.format(Locale.ROOT, "%s (world; %s),%s,,%.5f,%.3f\n", state, where, share, modifier, world));
        check(Math.abs(world / expected - 1) < 2e-3, "Custom FOV: " + state + " (" + where + ") at " + share + ", world FOV " + world + " (expected " + expected + ")");
        share(next, nextShare);
        return after(20);
    }

    private static boolean bowDrawn(Minecraft mc) {
        server(mc, player -> player.removePotionEffect(Potion.moveSpeed.id));
        ItemStack held = mc.thePlayer.getCurrentEquippedItem();
        check(held != null && held.getItem() == Items.bow, "Custom FOV: the bow reached the client's hand");
        if (mc.thePlayer.getItemInUse() == null) {
            // Held use key: runTick stops using an item whose key is up.
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
            return CoreProbe.retry(30);
        }
        check(mc.thePlayer.getItemInUseDuration() >= 20, "Custom FOV: the bow is fully drawn (" + mc.thePlayer.getItemInUseDuration() + " ticks)");
        measure(mc, "bow drawn", CustomFovModule.BOW, 0.85);
        return after(20);
    }

    private static boolean bowDone(Minecraft mc) throws Exception {
        world(mc, "bow drawn", "0%", 1, CustomFovModule.BOW, 100);
        mc.thePlayer.clearItemInUse();
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        final ItemStack held = heldBefore;
        server(mc, player -> player.inventory.setInventorySlotContents(player.inventory.currentItem, held));
        for (Option option : fov().getOptions()) option.load(SAVED.get(option));
        fov().setEnabled(fovEnabled);
        File csv = new File(mc.mcDataDir, "lads-qa/screenshots/custom-fov.csv");
        Files.write(csv.toPath(), CSV.toString().getBytes(StandardCharsets.UTF_8));
        return after(10);
    }

    /** getFovModifier now with {@code change} at 100% (expect {@code full}) and at 0% (expect 1); then 100% again. */
    private static void measure(Minecraft mc, String state, String change, double full) {
        for (int share : new int[] {100, 0}) {
            share(change, share);
            double expected = share == 100 ? full : 1;
            float modifier = mc.thePlayer.getFovModifier();
            CSV.append(String.format(Locale.ROOT, "%s,%d%%,%.5f,%.5f,\n", state, share, modifier, expected));
            check(Math.abs(modifier - expected) < 1e-3, "Custom FOV: " + state + " at " + share + "%, FOV modifier " + modifier + " (expected " + expected + ")");
        }
        share(change, 100);
    }

    private interface ServerAction { void run(EntityPlayerMP player); }

    private static void server(Minecraft mc, ServerAction action) {
        final UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> {
            EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
            if (player != null) action.run(player);
        });
    }

    private static void bool(Module module, String name, boolean value) { ((BoolOption) module.getOption(name)).set(value); }
    private static void share(String change, int percent) { ((SliderOption) fov().getOption(change)).setValue(percent); }
}

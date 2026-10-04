package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;
import static com.thelads.core.v1_8_9.feature.CoreProbe.tap;

import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.client.SignalLossPolicy;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_8_9.gui.ScreenshotsScreen189;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only: the 1.5.0 ports, run by CoreProbe in its QA world after Probe145, each through the real 1.8.9 paths: FarBlockEntities
 * (TileEntity's distance), EnhancedTooltips and AppleSkin's tooltip (ItemTooltipEvent), AppleSkin's HUD in survival with food
 * data from the integrated server, SignalLoss (the connection's packet time, the warning drawn), Clumps (orbs spawned on the
 * integrated server merge, clients see the clump), RenderScale at 50 % and BetterScreenshots (the screenshot key's preview,
 * the gallery key and its list). Every module and the player are put back as found.
 */
final class Probe150e {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe150e::farBlockEntities, Probe150e::tooltips,
        Probe150e::appleStart, Probe150e::appleShown, Probe150e::rottenShown, Probe150e::saturationShown, Probe150e::goldenShown,
        Probe150e::appleDone, Probe150e::tooltipShown, Probe150e::tooltipShown, Probe150e::tooltipShown, Probe150e::tooltipShown,
        Probe150e::tooltipShown, Probe150e::tooltipShown, Probe150e::crosshairShown, Probe150e::crosshairShown, Probe150e::crosshairShown,
        Probe150e::crosshairShown, Probe150e::crosshairShown, Probe150e::crosshairsDone, Probe150e::signalLoss, Probe150e::signalShown,
        Probe150e::clumpsStart, Probe150e::clumpsSpawn, Probe150e::clumpsMerged, Probe150e::scaleStart, Probe150e::scaleShown,
        Probe150e::scaleOff, Probe150e::shotTaken, Probe150e::shotPreview, Probe150e::galleryShown, Probe150e::galleryClosed);
    private static boolean[] states;
    private static ItemStack slot;
    private static long count;
    private static double scale;
    private static int preset;
    private static File shot;

    private Probe150e() {}

    private static final String[] MODULES = {"FarBlockEntities", "EnhancedTooltips", "AppleSkin", "SignalLoss", "Clumps", "RenderScale", "BetterScreenshots",
        "EnhancedToolbars", "Crosshair Tweaks"};
    /** Options the 1.7.0 food, tooltip and crosshair shots change, put back afterwards. */
    private static final java.util.Map<com.thelads.core.config.Option, com.google.gson.JsonElement> OPTIONS = new java.util.LinkedHashMap<>();
    private static int pose;

    private static boolean farBlockEntities(Minecraft mc) {
        states = new boolean[MODULES.length];
        for (int i = 0; i < MODULES.length; i++) states[i] = Options189.enabled(MODULES[i]);
        Module far = Options189.module("FarBlockEntities");
        double distance = Math.max(64, Math.min(256, Options189.number("FarBlockEntities", "Distance", 128)));
        far.setEnabled(true);
        double on = new TileEntityChest().getMaxRenderDistanceSquared(), beacon = new TileEntityBeacon().getMaxRenderDistanceSquared();
        far.setEnabled(false);
        double off = new TileEntityChest().getMaxRenderDistanceSquared();
        check(on == distance * distance && off == 4096 && beacon == 65536, "FarBlockEntities: a chest renders out to " + Math.sqrt(on)
            + " blocks (Distance " + distance + "), 64 with the module off; a beacon keeps 256");
        restore("FarBlockEntities");
        return after(1);
    }

    private static boolean tooltips(Minecraft mc) {
        Options189.module("EnhancedTooltips").setEnabled(true);
        Options189.module("AppleSkin").setEnabled(true);
        List<String> lines = new ItemStack(Items.apple).getTooltip(mc.thePlayer, false);
        long food = lines.stream().filter(line -> line.contains("Food: +4 hunger, +2.4 saturation")).count();
        check(lines.contains(EnumChatFormatting.DARK_GRAY + "minecraft:apple") && food == 1,
            "EnhancedTooltips: the apple's tooltip names minecraft:apple and its food values, once with AppleSkin on too " + lines);
        Options189.module("EnhancedTooltips").setEnabled(false);
        lines = new ItemStack(Items.apple).getTooltip(mc.thePlayer, false);
        check(lines.size() == 2 && lines.get(1).contains("Food: +4 hunger, +2.4 saturation"), "AppleSkin's food tooltip alone " + lines);
        check(com.thelads.core.client.FoodPreview.regenerated(20, 5, 0, 100, true) == 11, "AppleSkin: 1.8.9's regeneration estimate (20 hunger, 5 saturation: 11 health)");
        check(Food189.regenerationEffect(new ItemStack(Items.golden_apple)) == 4 && Food189.regenerationEffect(new ItemStack(Items.golden_apple, 1, 1)) == 200
            && Food189.harmful(new ItemStack(Items.rotten_flesh)) && Food189.harmful(new ItemStack(Items.spider_eye)) && !Food189.harmful(new ItemStack(Items.apple)),
            "AppleSkin: golden apples' Regeneration (4 and 200 health) and harmful foods' green icons, through ItemFoodFields");
        return after(1);
    }

    /** Survival, hungry and hurt on the integrated server, an apple in hand on the client: every AppleSkin overlay has work. */
    private static boolean appleStart(Minecraft mc) {
        count = Food189.frames;
        Food189.qaPulse = 1f; // previews at full strength in the photos
        for (String module : new String[] {"AppleSkin", "EnhancedToolbars", "Crosshair Tweaks"})
            for (com.thelads.core.config.Option option : Options189.module(module).getOptions()) OPTIONS.put(option, option.save());
        slot = mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem];
        mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = new ItemStack(Items.apple);
        onServer(mc, player -> {
            player.setGameType(WorldSettings.GameType.SURVIVAL);
            player.getFoodStats().readNBT(food(14, 3.5f, 2.5f));
            player.setHealth(9);
        });
        return after(40);
    }

    private static boolean appleShown(Minecraft mc) throws Exception {
        // Exhaustion grows with every move, so compare with the server's value now, not the 2.5 it started at.
        MinecraftServer server = mc.getIntegratedServer();
        float serverExhaustion = server.callFromMainThread(() -> {
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            server.getConfigurationManager().getPlayerByUUID(mc.thePlayer.getUniqueID()).getFoodStats().writeNBT(tag);
            return tag.getFloat("foodExhaustionLevel");
        }).get();
        EntityPlayerMP own = Food189.serverPlayer(mc.thePlayer);
        float read = Food189.exhaustion(own);
        LOG.info("Lads 1.8.9 core probe: AppleSkin: saturation {}, exhaustion {} (server {}), frames {}", own == null ? -1 : own.getFoodStats().getSaturationLevel(),
            read, serverExhaustion, Food189.frames - count);
        check(own != null && Math.abs(own.getFoodStats().getSaturationLevel() - 3.5f) < 0.01f && serverExhaustion >= 2.5f && Math.abs(read - serverExhaustion) < 0.3f,
            "AppleSkin: saturation 3.5 and exhaustion " + read + " (server " + serverExhaustion + ") are the integrated server's");
        check(Food189.frames - count > 20 && Food189.heldFood(mc.thePlayer) != null, "AppleSkin: the saturation outlines are drawn over 1.8.9's hunger bar, the apple previewed");
        screenshot(mc, "150-appleskin");
        hold(mc, new ItemStack(Items.rotten_flesh));
        onServer(mc, player -> { player.getFoodStats().readNBT(food(6, 0, 1)); player.setHealth(player.getMaxHealth()); });
        return after(30);
    }

    /** 1.7.0 shots: rotten flesh's green hunger preview; saturation outlines and the exhaustion band; a golden apple's health. */
    private static boolean rottenShown(Minecraft mc) {
        screenshot(mc, "170-food-rotten-flesh");
        hold(mc, null);
        onServer(mc, player -> player.getFoodStats().readNBT(food(20, 13, 3)));
        return after(30);
    }

    private static boolean saturationShown(Minecraft mc) {
        screenshot(mc, "170-food-saturation-exhaustion");
        hold(mc, new ItemStack(Items.golden_apple));
        onServer(mc, player -> { player.getFoodStats().readNBT(food(20, 2, 0)); player.setHealth(7); });
        return after(30);
    }

    private static boolean goldenShown(Minecraft mc) {
        check(Food189.heldFood(mc.thePlayer) != null, "AppleSkin: a golden apple is edible at full hunger");
        screenshot(mc, "170-food-golden-apple-health");
        return after(1);
    }

    /** The real tooltip renderer (GuiScreen.renderToolTip) over the world: food lines, then EnhancedToolbars' durability styles. */
    private static boolean tooltipShown(Minecraft mc) {
        String[] names = {"apple", "golden-carrot", "durability-numbers", "durability-bar", "durability-text"};
        if (pose > 0) screenshot(mc, "170-tooltip-" + names[pose - 1]);
        if (pose == names.length) { pose = 0; mc.displayGuiScreen(null); return after(5); }
        ItemStack stack = pose == 0 ? new ItemStack(Items.apple) : pose == 1 ? new ItemStack(Items.golden_carrot) : new ItemStack(Items.diamond_sword, 1, 1200);
        Options189.module("AppleSkin").setEnabled(true); // put back as found by crosshairsDone
        if (pose >= 2) {
            Options189.module("EnhancedToolbars").setEnabled(true);
            ((DropdownOption) Options189.module("EnhancedToolbars").getOption("Durability Style")).setIndex(pose - 2);
            List<String> lines = stack.getTooltip(mc.thePlayer, true);
            String expected = new String[] {"361", "\u2588", "Severely damaged"}[pose - 2];
            check(!lines.contains("Durability: 361 / 1561") && String.join("\n", lines).contains(expected),
                "EnhancedToolbars on 1.8.9: style " + (pose - 2) + " replaces the advanced durability line " + lines);
        }
        pose++;
        mc.displayGuiScreen(new TooltipScreen(stack));
        return after(10);
    }

    private static final class TooltipScreen extends net.minecraft.client.gui.GuiScreen {
        private final ItemStack stack;
        TooltipScreen(ItemStack stack) { this.stack = stack; }
        @Override public void drawScreen(int mouseX, int mouseY, float partial) { renderToolTip(stack, width / 2 - 60, height / 2 - 20); }
        @Override public boolean doesGuiPauseGame() { return false; }
    }

    /** Crosshair Tweaks styles: a cross with a centre dot, a green circle, a turned triangle, a thick arrow, the vanilla shape. */
    private static boolean crosshairShown(Minecraft mc) {
        String[] names = {"cross-dot", "circle-green", "triangle-rotated", "arrow-thick", "vanilla-adaptive"};
        if (pose > 0) screenshot(mc, "170-crosshair-" + names[pose - 1]);
        Module crosshair = Options189.module("Crosshair Tweaks");
        crosshair.setEnabled(true);
        ((DropdownOption) crosshair.getOption("Shape")).setIndex(new int[] {0, 4, 5, 6, 3}[pose]);
        ((com.thelads.core.config.BoolOption) crosshair.getOption("Center Dot")).set(pose == 0);
        if (pose == 1) {
            com.thelads.core.config.ColorOption color = (com.thelads.core.config.ColorOption) crosshair.getOption("Color");
            color.setUseGlobal(false);
            color.setColor(0xff55ff55);
        }
        ((SliderOption) crosshair.getOption("Rotation")).setValue(pose == 2 ? 180 : 0);
        ((com.thelads.core.modules.CrosshairModule) crosshair).thickness.set(pose == 3 ? 2 : 1);
        pose++;
        return after(10);
    }

    private static boolean crosshairsDone(Minecraft mc) {
        screenshot(mc, "170-crosshair-vanilla-adaptive");
        pose = 0;
        OPTIONS.forEach(com.thelads.core.config.Option::load);
        restore("AppleSkin");
        restore("EnhancedToolbars");
        restore("Crosshair Tweaks");
        return after(5);
    }

    private static void hold(Minecraft mc, ItemStack stack) {
        mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = stack;
    }

    private static boolean appleDone(Minecraft mc) {
        mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = slot;
        onServer(mc, player -> {
            player.getFoodStats().readNBT(food(20, 5, 0));
            player.setHealth(player.getMaxHealth());
            player.setGameType(WorldSettings.GameType.CREATIVE);
        });
        Food189.qaPulse = null;
        restore("AppleSkin");
        restore("EnhancedTooltips");
        return after(20);
    }

    private static boolean signalLoss(Minecraft mc) {
        long received = ((SignalLoss189.PacketActivity) mc.getNetHandler().getNetworkManager()).ladsLastPacketNanos();
        check(received > 0 && System.nanoTime() - received < 3_000_000_000L, "SignalLoss: the connection received a server packet "
            + (System.nanoTime() - received) / 1_000_000 + " ms ago (NetworkManagerMixin)");
        Options189.module("SignalLoss").setEnabled(true);
        check(SignalLoss189.frame().progress() == 0, "SignalLoss: a singleplayer world shows no warning by default");
        SignalLoss189.qaFrame = new SignalLossPolicy.Frame(1, 3.2, true, false);
        return after(5);
    }

    private static boolean signalShown(Minecraft mc) {
        screenshot(mc, "150-signalloss-warning");
        SignalLoss189.qaFrame = null;
        restore("SignalLoss");
        return after(1);
    }

    private static boolean clumpsStart(Minecraft mc) {
        Options189.module("Clumps").setEnabled(true);
        return after(2); // Clumps189 reads the module on the client tick
    }

    /** Ten 3-XP orbs 12 blocks away (out of the player's 8-block pull), spawned on the integrated server. */
    private static boolean clumpsSpawn(Minecraft mc) {
        onServer(mc, player -> {
            for (int i = 0; i < 10; i++)
                player.worldObj.spawnEntityInWorld(new EntityXPOrb(player.worldObj, player.posX + 12, player.posY + 1, player.posZ, 3));
        });
        return after(30);
    }

    private static boolean clumpsMerged(Minecraft mc) throws Exception {
        MinecraftServer server = mc.getIntegratedServer();
        List<EntityXPOrb> merged = server.callFromMainThread(() -> {
            EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(mc.thePlayer.getUniqueID());
            return player.worldObj.getEntitiesWithinAABB(EntityXPOrb.class, player.getEntityBoundingBox().expand(24, 12, 24));
        }).get();
        List<EntityXPOrb> seen = mc.theWorld.getEntitiesWithinAABB(EntityXPOrb.class, mc.thePlayer.getEntityBoundingBox().expand(24, 12, 24));
        StringBuilder found = new StringBuilder();
        for (EntityXPOrb orb : merged) found.append(' ').append(orb.xpValue).append(String.format("@%.1f,%.1f,%.1f", orb.posX, orb.posY, orb.posZ));
        check(merged.size() == 1 && merged.get(0).xpValue == 30, "Clumps: ten 3-XP orbs became one 30-XP orb on the integrated server ("
            + merged.size() + " orbs:" + found + "; player at " + String.format("%.1f,%.1f,%.1f", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)
            + ", XP " + mc.thePlayer.experienceTotal + ")");
        check(seen.size() == 1 && seen.get(0).xpValue == 30, "Clumps: the client shows the one 30-XP clump (" + seen.size() + " orbs)");
        onServer(mc, player -> { for (EntityXPOrb orb : merged) orb.setDead(); });
        restore("Clumps");
        return after(2);
    }

    private static boolean scaleStart(Minecraft mc) {
        SliderOption option = (SliderOption) Options189.module("RenderScale").getOption("Scale");
        DropdownOption presets = (DropdownOption) Options189.module("RenderScale").getOption("Preset");
        scale = option.getValue();
        preset = presets.getIndex();
        option.setValue(50);
        presets.setIndex(0); // Custom: the Scale slider
        Options189.module("RenderScale").setEnabled(true);
        count = RenderScale189.scaledFrames;
        return after(30);
    }

    private static boolean scaleShown(Minecraft mc) {
        RenderScalePolicy.Size half = RenderScalePolicy.size(mc.displayWidth, mc.displayHeight, 0.5, Integer.MAX_VALUE);
        check(RenderScale189.scaledFrames - count > 10 && RenderScale189.scaledWidth == half.width() && RenderScale189.scaledHeight == half.height(),
            "RenderScale 50 %: the world rendered at " + RenderScale189.scaledWidth + "x" + RenderScale189.scaledHeight + " in "
            + (RenderScale189.scaledFrames - count) + " frames for a " + mc.displayWidth + "x" + mc.displayHeight + " window (needs framebuffers: OptiFine Fast Render off)");
        screenshot(mc, "150-renderscale-50");
        ((SliderOption) Options189.module("RenderScale").getOption("Scale")).setValue(scale);
        ((DropdownOption) Options189.module("RenderScale").getOption("Preset")).setIndex(preset);
        restore("RenderScale");
        return after(10);
    }

    private static boolean scaleOff(Minecraft mc) {
        count = RenderScale189.scaledFrames;
        return after(10);
    }

    private static boolean shotTaken(Minecraft mc) throws Exception {
        if (!Options189.enabled("RenderScale") || preset == 0 && scale == 100)
            check(RenderScale189.scaledFrames == count, "RenderScale back as found (off or 100 %): the world renders into Minecraft's framebuffer again");
        Options189.module("BetterScreenshots").setEnabled(true);
        Screenshots189.lastSaved = null;
        tap(mc.gameSettings.keyBindScreenshot.getKeyCode(), '\0');
        return after(5);
    }

    private static boolean shotPreview(Minecraft mc) throws Exception {
        shot = Screenshots189.lastSaved;
        if (shot != null && !Screenshots189.previewShown()) return retry(5); // decoded off the render thread
        check(shot != null && shot.isFile() && shot.getParentFile().equals(Screenshots189.folder()), "BetterScreenshots: the screenshot key saved " + shot);
        check(Screenshots189.previewShown(), "BetterScreenshots: its preview is shown");
        screenshot(mc, "150-screenshot-preview");
        tap(Screenshots189.OPEN.getKeyCode(), '\0');
        return after(10);
    }

    private static boolean galleryShown(Minecraft mc) {
        if (mc.currentScreen instanceof ScreenshotsScreen189 && !((ScreenshotsScreen189) mc.currentScreen).previewShown()) return retry(5);
        check(mc.currentScreen instanceof ScreenshotsScreen189, "BetterScreenshots: its key (F10) opens the gallery in gameplay");
        ScreenshotsScreen189 gallery = (ScreenshotsScreen189) mc.currentScreen;
        check(!gallery.files().isEmpty() && shot.equals(gallery.files().get(0)) && shot.equals(gallery.selectedFile()),
            "BetterScreenshots: the gallery lists the new screenshot first, selected and previewed (" + gallery.files().size() + " files)");
        screenshot(mc, "150-gallery");
        mc.displayGuiScreen(null);
        return after(10);
    }

    private static boolean galleryClosed(Minecraft mc) throws Exception {
        check(mc.currentScreen == null, "the gallery closed back to the game");
        Files.deleteIfExists(shot.toPath()); // the QA world's own screenshot, in the sandbox game folder
        restore("BetterScreenshots");
        return true;
    }

    private static NBTTagCompound food(int level, float saturation, float exhaustion) {
        NBTTagCompound food = new NBTTagCompound();
        food.setInteger("foodLevel", level);
        food.setInteger("foodTickTimer", 0);
        food.setFloat("foodSaturationLevel", saturation);
        food.setFloat("foodExhaustionLevel", exhaustion);
        return food;
    }

    private static AxisAlignedBB around(double x, double y, double z) {
        return new AxisAlignedBB(x - 2, y - 3, z - 2, x + 2, y + 4, z + 2);
    }

    private interface ServerTask { void run(EntityPlayerMP player); }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        java.util.UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> task.run(server.getConfigurationManager().getPlayerByUUID(id)));
    }

    private static void restore(String module) {
        Options189.module(module).setEnabled(states[Arrays.asList(MODULES).indexOf(module)]);
    }
}

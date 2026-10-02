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
        Probe150e::appleStart, Probe150e::appleShown, Probe150e::appleDone, Probe150e::signalLoss, Probe150e::signalShown,
        Probe150e::clumpsStart, Probe150e::clumpsSpawn, Probe150e::clumpsMerged, Probe150e::scaleStart, Probe150e::scaleShown,
        Probe150e::scaleOff, Probe150e::shotTaken, Probe150e::shotPreview, Probe150e::galleryShown, Probe150e::galleryClosed);
    private static boolean[] states;
    private static ItemStack slot;
    private static long count;
    private static double scale;
    private static int preset;
    private static File shot;

    private Probe150e() {}

    private static final String[] MODULES = {"FarBlockEntities", "EnhancedTooltips", "AppleSkin", "SignalLoss", "Clumps", "RenderScale", "BetterScreenshots"};

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
        check(FoodOverlay189.regeneration(20, 5, 0) == 11 && FoodOverlay189.regeneration(17, 20, 0) == 0,
            "AppleSkin: 1.8.9's regeneration estimate (20 hunger, 5 saturation: 11 health)");
        return after(1);
    }

    /** Survival, hungry and hurt on the integrated server, an apple in hand on the client: every AppleSkin overlay has work. */
    private static boolean appleStart(Minecraft mc) {
        count = FoodOverlay189.frames;
        slot = mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem];
        mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = new ItemStack(Items.apple);
        onServer(mc, player -> {
            player.setGameType(WorldSettings.GameType.SURVIVAL);
            player.getFoodStats().readNBT(food(14, 3.5f, 2.5f));
            player.setHealth(9);
        });
        return after(40);
    }

    private static boolean appleShown(Minecraft mc) {
        LOG.info("Lads 1.8.9 core probe: AppleSkin: synced {}, saturation {}, exhaustion {}, frames {}", FoodOverlay189.synced,
            mc.thePlayer.getFoodStats().getSaturationLevel(), FoodOverlay189.exhaustion, FoodOverlay189.frames - count);
        check(FoodOverlay189.synced && Math.abs(mc.thePlayer.getFoodStats().getSaturationLevel() - 3.5f) < 0.01f
            && Math.abs(FoodOverlay189.exhaustion - 2.5f) < 0.6f, "AppleSkin: saturation 3.5 and exhaustion 2.5 come from the integrated server");
        check(FoodOverlay189.frames - count > 20, "AppleSkin: the saturation overlay is drawn over 1.8.9's hunger bar");
        screenshot(mc, "150-appleskin");
        return after(1);
    }

    private static boolean appleDone(Minecraft mc) {
        mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = slot;
        onServer(mc, player -> {
            player.getFoodStats().readNBT(food(20, 5, 0));
            player.setHealth(player.getMaxHealth());
            player.setGameType(WorldSettings.GameType.CREATIVE);
        });
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
            return player.worldObj.getEntitiesWithinAABB(EntityXPOrb.class, around(player.posX + 12, player.posY, player.posZ));
        }).get();
        List<EntityXPOrb> seen = mc.theWorld.getEntitiesWithinAABB(EntityXPOrb.class, around(mc.thePlayer.posX + 12, mc.thePlayer.posY, mc.thePlayer.posZ));
        check(merged.size() == 1 && merged.get(0).xpValue == 30, "Clumps: ten 3-XP orbs became one 30-XP orb on the integrated server ("
            + merged.size() + " orbs)");
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

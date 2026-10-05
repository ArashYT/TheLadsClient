package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ItemPhysicsModule;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * QA only (LADS_VERIFY_189_ONLY=hudflicker): does any HUD drawing flicker under the HUD FPS cap? In survival with the Lads HUD
 * elements, title, subtitle, action bar, boss bar, scoreboard, chat with heads, a kill banner, Item Physics' throw bar and an
 * Essential notification on screen, then again with F3, it saves runs of consecutive frames to lads-qa/screenshots/hudflicker: the
 * HUD hidden (reference), the cap off (control), the cap on at 10 FPS and the HUD hidden again (the view stayed still), with the
 * elements' rectangles and the Lads HUD's cost per frame (hudflicker.json). On 1.8.9 the cap covers the Lads HUD only. artifacts/1.7.2/hudfps/flicker.py checks that every
 * element is in every frame. Everything is put back.
 */
final class Probe172HudFlicker {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String[] MODULES = {"FPS", "Coordinates", "Keystrokes", "CPS", "Paperdoll", "ArmorHUD", "Potion Effects", "Scoreboard",
        "BossBar", "KillBanner", "Item Physics", "Chat Heads", "Autohide"};
    private static final int FRAMES = 36, HIDDEN = 4, CAP = 10;
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe172HudFlicker::setup,
        mc -> run(mc, "hud-hidden"), mc -> run(mc, "hud-off"), mc -> run(mc, "hud-on"), mc -> run(mc, "hud-after"),
        mc -> run(mc, "f3-hidden"), mc -> run(mc, "f3-off"), mc -> run(mc, "f3-on"), mc -> run(mc, "f3-after"), Probe172HudFlicker::restore);
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<Option, JsonElement>();
    private static final Map<Module, Boolean> enabledWere = new LinkedHashMap<Module, Boolean>();
    private static final JsonObject report = new JsonObject();
    private static final List<int[]> pending = new ArrayList<int[]>();
    private static final List<String> names = new ArrayList<String>();
    private static boolean capWas, f3Was;
    private static int limitWas, width, height;
    private static String run;
    private static Frames frames;
    private static ItemStack handWas;
    private static double pinX, pinY, pinZ, homeX, homeY, homeZ;
    private static net.minecraft.util.BlockPos below;
    private static net.minecraft.block.state.IBlockState belowWas;
    private static float pinYaw;

    private Probe172HudFlicker() {}

    private static boolean setup(Minecraft mc) {
        for (String name : MODULES) {
            Module module = Options189.module(name);
            if (module == null) continue;
            enabledWere.put(module, module.isEnabled());
            for (Option option : module.getOptions()) optionsWere.put(option, option.save());
            module.setEnabled(!name.equals("Autohide"));
        }
        if (Options189.module("Item Physics") instanceof ItemPhysicsModule) ((ItemPhysicsModule) Options189.module("Item Physics")).charged.set(true);
        if (Options189.module("KillBanner") instanceof KillBannerModule) ((KillBannerModule) Options189.module("KillBanner")).duration.setValue(5);
        capWas = HudSettings.getInstance().isHudFpsCapEnabled();
        limitWas = HudSettings.getInstance().getHudFpsLimit();
        f3Was = mc.gameSettings.showDebugInfo;
        // Standing still on a crafting table 30 blocks above the ground, looking straight down: a background that stays the same in
        // every frame. The table goes and the player goes back afterwards.
        homeX = mc.thePlayer.posX;
        homeY = mc.thePlayer.posY;
        homeZ = mc.thePlayer.posZ;
        int x = net.minecraft.util.MathHelper.floor_double(homeX), z = net.minecraft.util.MathHelper.floor_double(homeZ);
        int y = mc.theWorld.getHeight(new net.minecraft.util.BlockPos(x, 0, z)).getY() + 30;
        pinX = x + 0.5;
        pinY = y;
        pinZ = z + 0.5;
        pinYaw = mc.thePlayer.rotationYaw;
        below = new net.minecraft.util.BlockPos(x, y - 1, z);
        pin(mc);
        onServer(mc, player -> {
            handWas = player.inventory.getCurrentItem();
            player.inventory.mainInventory[player.inventory.currentItem] = null; // the throw bar charges, nothing is thrown
            belowWas = player.worldObj.getBlockState(below);
            player.worldObj.setBlockState(below, net.minecraft.init.Blocks.crafting_table.getDefaultState());
            player.playerNetServerHandler.setPlayerLocation(pinX, pinY, pinZ, pinYaw, 90);
        });
        for (String command : new String[] {"gamemode 0 @a", "scoreboard objectives add ladsflicker dummy Flicker QA",
            "scoreboard objectives setdisplay sidebar ladsflicker", "scoreboard players set Alpha ladsflicker 2", "scoreboard players set Beta ladsflicker 1",
            "effect @a 3 600 0 true"}) command(mc, command);
        BossStatus.bossName = "QA Boss";
        BossStatus.healthScale = 0.6f;
        BossStatus.statusBarTime = 100_000;
        frames = new Frames();
        MinecraftForge.EVENT_BUS.register(frames);
        LOG.info("Lads HUD flicker capture BEGIN: {} frames per run, cap {} FPS (the Lads HUD)", FRAMES, CAP);
        return after(40);
    }

    /** One run of frames: set up on the first call, then waits until the frames are in. */
    private static boolean run(Minecraft mc, String name) {
        pin(mc);
        if (!name.equals(run)) {
            run = name;
            boolean hidden = name.endsWith("-hidden") || name.endsWith("-after");
            mc.gameSettings.hideGUI = hidden;
            mc.gameSettings.showDebugInfo = !hidden && name.startsWith("f3");
            HudSettings.getInstance().setHudFpsCapEnabled(name.endsWith("-on"));
            HudSettings.getInstance().setHudFpsLimit(CAP);
            if (!hidden) refresh(mc);
            frames.start(name, hidden ? HIDDEN : FRAMES, hidden ? 800 : 1600, !hidden);
            return retry(1);
        }
        if (!frames.done()) return retry(1);
        LOG.info("Lads HUD flicker capture {}: {} frames", name, frames.count);
        return after(1);
    }

    /** Every tick: the player stays put, looking straight down (a still background), and Item Physics' throw bar keeps charging. */
    private static void pin(Minecraft mc) {
        mc.thePlayer.setPosition(pinX, pinY, pinZ);
        mc.thePlayer.prevPosX = mc.thePlayer.lastTickPosX = pinX;
        mc.thePlayer.prevPosY = mc.thePlayer.lastTickPosY = pinY;
        mc.thePlayer.prevPosZ = mc.thePlayer.lastTickPosZ = pinZ;
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = pinYaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 90;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), true);
    }

    /** Elements that time out are shown again before each run: chat, titles, action bar, kill banner, Essential. */
    private static void refresh(Minecraft mc) {
        for (int i = 1; i <= 3; i++) mc.thePlayer.sendChatMessage("Flicker QA chat " + i);
        mc.ingameGUI.displayTitle(null, null, 0, 1200, 0);
        mc.ingameGUI.displayTitle(null, "QA Subtitle", -1, -1, -1);
        mc.ingameGUI.displayTitle("QA Title", null, -1, -1, -1);
        mc.ingameGUI.setRecordPlaying("QA Action Bar", false);
        KillBanner189.trigger(1, false);
        try {
            Object notifications = Class.forName("gg.essential.api.EssentialAPI").getMethod("getNotifications").invoke(null);
            notifications.getClass().getMethod("push", String.class, String.class).invoke(notifications, "QA notification", "HUD FPS cap flicker check");
        } catch (Throwable absent) { LOG.info("Lads HUD flicker capture: no Essential notification ({})", absent.toString()); }
    }

    private static boolean restore(Minecraft mc) throws Exception {
        MinecraftForge.EVENT_BUS.unregister(frames);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), false);
        mc.gameSettings.hideGUI = false;
        mc.gameSettings.showDebugInfo = f3Was;
        mc.ingameGUI.displayTitle(null, null, -1, -1, -1);
        BossStatus.statusBarTime = 0;
        optionsWere.forEach(Option::load);
        enabledWere.forEach(Module::setEnabled);
        HudSettings.getInstance().setHudFpsCapEnabled(capWas);
        HudSettings.getInstance().setHudFpsLimit(limitWas);
        final ItemStack hand = handWas;
        onServer(mc, player -> {
            player.inventory.mainInventory[player.inventory.currentItem] = hand;
            if (belowWas != null) player.worldObj.setBlockState(below, belowWas);
            player.playerNetServerHandler.setPlayerLocation(homeX, homeY, homeZ, pinYaw, 0);
        });
        for (String command : new String[] {"scoreboard objectives remove ladsflicker", "effect @a clear", "gamemode 1 @a"}) command(mc, command);
        File folder = new File(mc.mcDataDir, "lads-qa/screenshots/hudflicker");
        folder.mkdirs();
        int saved = 0;
        for (int i = 0; i < pending.size(); i++) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] pixels = pending.get(i);
            for (int y = 0; y < height; y++) image.setRGB(0, height - 1 - y, width, 1, pixels, y * width, width); // GL rows are bottom-up
            if (ImageIO.write(image, "png", new File(folder, names.get(i) + ".png"))) saved++;
        }
        Files.write(new File(folder, "hudflicker.json").toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
        check(saved == 2 * (2 * HIDDEN + 2 * FRAMES), "HUD flicker: every frame of every run saved (" + saved + ")");
        LOG.info("Lads HUD flicker capture END: {} passed, 0 failed; frames in lads-qa/screenshots/hudflicker", saved);
        pending.clear();
        return after(5);
    }

    /** Where each element is, in pixels, for flicker.py (named rows; anything else is found by itself). */
    private static void rects(Minecraft mc, String phase) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int s = resolution.getScaleFactor(), w = resolution.getScaledWidth(), h = resolution.getScaledHeight();
        GuiLadsAdapter adapter = new GuiLadsAdapter(mc.fontRendererObj, w, h);
        int lift = adapter.hotbarLift();
        JsonObject rects = new JsonObject();
        for (HudElement element : HudManager.getInstance().getElements()) {
            if (!element.isEnabled() || !element.isAvailable()) continue;
            HudGroupLayout.Rect bounds = element.measureBounds(adapter, false);
            HudGroupLayout.Rect placed = HudGroupLayout.translate(bounds, HudGroupLayout.clampDelta(bounds, 0, 0, w, h));
            rect(rects, "Lads " + element.getModuleName(), placed.x(), placed.y(), placed.width(), placed.height(), s);
        }
        int title = mc.fontRendererObj.getStringWidth("QA Title"), subtitle = mc.fontRendererObj.getStringWidth("QA Subtitle");
        int action = mc.fontRendererObj.getStringWidth("QA Action Bar");
        rect(rects, "Crosshair", w / 2 - 8, h / 2 - 8, 16, 16, s);
        rect(rects, "Item Physics throw bar", w / 2 - 8, h / 2 + 9, 16, 2, s);
        rect(rects, "Title", w / 2 - title * 2 - 4, h / 2 - 42, title * 4 + 8, 40, s);
        rect(rects, "Subtitle", w / 2 - subtitle - 4, h / 2 + 8, subtitle * 2 + 8, 22, s);
        rect(rects, "Action bar", w / 2 - action / 2 - 4, h - 76 - lift, action + 8, 16, s);
        rect(rects, "Hotbar", w / 2 - 91, h - 22 - lift, 182, 22, s);
        rect(rects, "Health, armour, food, XP", w / 2 - 91, h - 52 - lift, 182, 30, s);
        rect(rects, "Chat", 0, h - 48 - lift - 3 * 9, Math.min(w / 2, mc.ingameGUI.getChatGUI().getChatWidth() + 24), 3 * 9 + 10, s);
        if (phase.equals("f3")) { rect(rects, "F3 left", 0, 0, w / 2, h / 2, s); rect(rects, "F3 right", w / 2, 0, w / 2, h / 2, s); }
        JsonObject entry = new JsonObject();
        entry.addProperty("guiScale", s);
        entry.addProperty("width", mc.displayWidth);
        entry.addProperty("height", mc.displayHeight);
        entry.add("rects", rects);
        report.add(phase, entry);
    }

    private static void rect(JsonObject rects, String name, int x, int y, int w, int h, int scale) {
        if (w <= 0 || h <= 0) return;
        JsonArray a = new JsonArray();
        for (int v : new int[] {x * scale, y * scale, w * scale, h * scale}) a.add(new com.google.gson.JsonPrimitive(v));
        String key = name;
        for (int i = 2; rects.has(key); i++) key = name + " " + i;
        rects.add(key, a);
    }

    /** Each finished frame (RenderTickEvent END): after the settle time, a second for the HUD's cost, then the run's frames. */
    public static final class Frames {
        private String name;
        private int wanted, count, measured;
        private long due, measureEnd, nanos0, frames0, start;
        private boolean measure;
        private IntBuffer buffer;

        void start(String name, int wanted, long settleMs, boolean measure) {
            this.name = name;
            this.wanted = wanted;
            this.measure = measure;
            count = 0;
            due = System.nanoTime() + settleMs * 1_000_000L;
            measureEnd = 0;
        }

        boolean done() { return count >= wanted; }

        @SubscribeEvent
        public void frame(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END || name == null || done()) return;
            long now = System.nanoTime();
            if (measure && now >= due - 1_100_000_000L && measureEnd == 0) {
                measureEnd = now + 1_000_000_000L;
                start = now;
                nanos0 = NativeHud.hudNanos;
                frames0 = NativeHud.frames;
            }
            if (measure && measureEnd > 0 && now >= measureEnd) {
                long frames = NativeHud.frames - frames0;
                double seconds = (now - start) / 1e9;
                String line = String.format(java.util.Locale.ROOT, "%.1f FPS, frame %.2f ms, Lads HUD %.1f us per frame", frames / seconds,
                    seconds * 1000 / Math.max(1, frames), (NativeHud.hudNanos - nanos0) / 1e3 / Math.max(1, frames));
                LOG.info("Lads HUD flicker capture {} cost: {}", name, line);
                if (!report.has("cost")) report.add("cost", new JsonObject());
                report.getAsJsonObject("cost").addProperty(name, line);
                measure = false;
            }
            if (now < due) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (count == 0 && name.endsWith("-off")) rects(mc, name.substring(0, name.indexOf('-')));
            width = mc.displayWidth;
            height = mc.displayHeight;
            int textureWidth = mc.getFramebuffer().framebufferTextureWidth, textureHeight = mc.getFramebuffer().framebufferTextureHeight;
            if (buffer == null || buffer.capacity() < textureWidth * textureHeight) buffer = BufferUtils.createIntBuffer(textureWidth * textureHeight);
            buffer.clear();
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GlStateManager.bindTexture(mc.getFramebuffer().framebufferTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) { buffer.position(y * textureWidth); buffer.get(pixels, y * width, width); }
            pending.add(pixels);
            names.add(name + "-" + (count < 10 ? "0" : "") + count);
            count++;
        }
    }

    private interface ServerTask { void run(EntityPlayerMP player); }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        java.util.UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> task.run(server.getConfigurationManager().getPlayerByUUID(id)));
    }

    private static void command(Minecraft mc, String command) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }
}

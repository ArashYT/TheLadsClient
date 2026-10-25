package com.thelads.core.v1_8_9.qol;

import com.thelads.core.client.ChunkBorders;
import com.thelads.core.client.CrashCatcher;
import com.thelads.core.client.GuiScaleOverride;
import com.thelads.core.client.KeepCursor;
import com.thelads.core.client.QuickReconnect;
import com.thelads.core.client.SoundVolumes;
import com.thelads.core.client.TooltipScroll;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_8_9.qol.mixin.GuiScreenServerListAccessor189;
import java.io.File;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenServerList;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ReportedException;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Parity 1.9.19 chat lane rows on 1.8.9 (common TooltipScroll, CrashCatcher, QuickReconnect, KeepCursor, SoundVolumes,
 * GuiScaleOverride, ChunkBorders), hooked by theladscore.v1_8_9.qol.mixins.json and Forge's events: Scrollable Tooltips (Forge's
 * GuiUtils.drawHoveringText through GuiUtilsTooltipMixin, the wheel through MouseInputEvent.Pre), Crash Catcher, Keep Cursor
 * Position and the resource reload flag (MinecraftQolMixin189: runGameLoop's runTick() call, displayGuiScreen, refreshResources),
 * Sound Volumes (SoundManagerQolMixin189), GUI Scale (ScaledResolutionMixin, plus the places that turn GUI pixels into window pixels:
 * the chat under the pointer, the enchanting book, Lads menus' clipping), Quick Reconnect (the disconnected and Direct Connect screens
 * through Forge's screen events) and Chunk Borders (RenderWorldLastEvent; 1.8.9 has no F3+G borders of its own).
 */
public final class Qol189 {
    /** The modules registered as built in here (CoreProbe's catalog check). */
    public static final String[] BUILT_IN = {TooltipScroll.NAME, CrashCatcher.NAME, QuickReconnect.NAME, KeepCursor.NAME, SoundVolumes.NAME,
        GuiScaleOverride.NAME, ChunkBorders.NAME, com.thelads.core.client.SoundFilters.NAME, com.thelads.core.client.media.MumbleLink.NAME, com.thelads.core.client.worldedit.WorldEditCUI.NAME,
        // 1.11.0 lanes: InventoryTweaksMixin189 (sorting, terminal solvers), PlayerCapabilitiesMixin189, GuiConfirmOpenLinkMixin189,
        // MlgClutch189, the schematic overlay here and GuiScreenMixin's toast buttons (LaneModuleSupportTest).
        com.thelads.core.modules.ContainerSortingModule.NAME, com.thelads.core.modules.CreativeFlyBoostModule.NAME,
        com.thelads.core.modules.LinkPreviewModule.NAME, com.thelads.core.modules.MlgCobwebModule.NAME,
        com.thelads.core.client.schematic.SchematicManager.NAME, "Server Notification Actions", com.thelads.core.client.hypixel.SkyBlockSuite.MODULE_NAME};
    private static final int RECONNECT = 0x4C510;
    private static final OldServerPinger PINGER = new OldServerPinger();
    private static ExecutorService pings;
    private static ServerData lastServer;
    private static GuiButton reconnect;
    /** The Direct Connect ping shown now, or null. */
    private static volatile ServerData pinged;
    private static int guiRevision;
    /** Crash Catcher's tick hook ran (it wraps the tick these events come from); false after the first tick: it did not apply. */
    static boolean tickHooked;
    private static boolean hookChecked;
    // Scrollable Tooltips: the screen size of the tooltip being drawn and whether it pushed a matrix (render thread).
    private static int tooltipWidth, tooltipHeight;
    private static boolean tooltipMoved;
    // Chunk Borders: the camera and the buffer being written (render thread).
    private static double camX, camY, camZ;
    private static WorldRenderer buffer;
    private static final ChunkBorders.Lines SINK = Qol189::line;
    private static final com.thelads.core.client.schematic.SchematicManager.Lines SCHEMATIC_SINK = Qol189::line;

    private Qol189() {}

    public static void register() {
        SoundVolumes.legacy = true;
        ModuleSupport.registerBuiltIn(BUILT_IN);
        ModuleSupport.registerUnavailable(com.thelads.core.modules.ElytraSwapModule.NAME, "Minecraft 1.8.9 has no elytra.");
        // BedColors189 tints the red bed texture, so other team colors come out nearly black (1.11.0 QA R2-F5): off until it draws right.
        ModuleSupport.registerUnavailable(com.thelads.core.modules.BedColorsModule.NAME, "Not ready on Minecraft 1.8.9 yet: team colors "
            + "on the red bed texture still look too dark.");
        MinecraftForge.EVENT_BUS.register(new Events());
    }

    public static final class Events {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            TooltipScroll.tick();
            QuickReconnect.tick();
            KeepCursor.tick();
            SoundVolumes.tick();
            GuiScaleOverride.tick();
            ChunkBorders.tick();
            boolean water = mc.thePlayer != null && (mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.water) || mc.thePlayer.isInWater());
            com.thelads.core.client.SoundFilters.tick(water);
            if (mc.thePlayer != null) {
                double px = mc.thePlayer.posX;
                double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
                double pz = mc.thePlayer.posZ;
                float yaw = mc.thePlayer.rotationYaw;
                float pitch = mc.thePlayer.rotationPitch;
                String name = mc.thePlayer.getName();
                String server = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "singleplayer";
                com.thelads.core.client.media.MumbleLink.tick(px, py, pz, yaw, pitch, name, server);
            } else {
                com.thelads.core.client.media.MumbleLink.tick();
            }
            com.thelads.core.client.worldedit.WorldEditCUI.tick();
            if (!hookChecked) {
                hookChecked = true;
                if (!tickHooked) ModuleSupport.registerUnavailable(CrashCatcher.NAME, "This game's Minecraft.runGameLoop is changed by another mod, "
                    + "so the game tick can't be watched for crashes.");
            }

            if (guiRevision != GuiScaleOverride.revision) { // lay the open screen out at the new scale
                guiRevision = GuiScaleOverride.revision;
                if (mc.currentScreen != null) {
                    ScaledResolution resolution = new ScaledResolution(mc);
                    mc.currentScreen.onResize(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
                }
            }
            if (mc.currentScreen instanceof GuiScreenServerList && QuickReconnect.ping) {
                GuiTextField field = ((GuiScreenServerListAccessor189) mc.currentScreen).lads$address();
                String due = QuickReconnect.due(field.getText(), System.nanoTime());
                if (due != null) ping(due);
                PINGER.pingPendingNetworks();
            }
        }

        /** Scrollable Tooltips: a wheel turn over a tooltip too big for the screen scrolls it; the screen (and Mouse Tweaks) never sees it. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void wheel(GuiScreenEvent.MouseInputEvent.Pre event) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0 && TooltipScroll.scroll(Integer.signum(wheel), GuiScreen.isShiftKeyDown(), System.nanoTime())) event.setCanceled(true);
        }

        @SubscribeEvent
        public void open(GuiOpenEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            // GuiConnecting's constructor already made its server the current one: the one Reconnect joins again.
            if (event.gui instanceof GuiConnecting && mc.getCurrentServerData() != null) lastServer = mc.getCurrentServerData();
            if (event.gui instanceof GuiScreenServerList || mc.currentScreen instanceof GuiScreenServerList) { // Direct Connect opens or closes
                PINGER.clearPendingNetworks();
                QuickReconnect.reset();
                pinged = null;
            }
        }

        /** After AutoReconnect's (normal priority): no second Reconnect when it shows its own. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void init(GuiScreenEvent.InitGuiEvent.Post event) {
            reconnect = null;
            if (!(event.gui instanceof GuiDisconnected) || !QuickReconnect.button || lastServer == null || event.buttonList.isEmpty()) return;
            for (GuiButton button : event.buttonList) if (button.displayString != null && button.displayString.startsWith("Reconnect")) return;
            GuiButton back = event.buttonList.get(event.buttonList.size() - 1);
            reconnect = new GuiButton(RECONNECT, back.xPosition, back.yPosition + 24, back.getButtonWidth(), 20, "Reconnect");
            event.buttonList.add(reconnect);
        }

        @SubscribeEvent
        public void press(GuiScreenEvent.ActionPerformedEvent.Pre event) {
            if (event.button != reconnect || reconnect == null || lastServer == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, lastServer));
            event.setCanceled(true);
        }

        @SubscribeEvent
        public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
            ServerData data = pinged;
            if (!(event.gui instanceof GuiScreenServerList) || data == null || !QuickReconnect.ping) return;
            Minecraft mc = Minecraft.getMinecraft();
            FontRenderer font = mc.fontRendererObj;
            GuiTextField field = ((GuiScreenServerListAccessor189) event.gui).lads$address();
            int y = field.yPosition + field.height + 4, center = event.gui.width / 2;
            String motd = data.serverMOTD == null || data.serverMOTD.isEmpty() ? EnumChatFormatting.GRAY + "Pinging..." : data.serverMOTD;
            List<String> lines = font.listFormattedStringToWidth(motd, 270);
            for (int i = 0; i < Math.min(2, lines.size()); i++, y += 10) font.drawStringWithShadow(lines.get(i), center - font.getStringWidth(lines.get(i)) / 2F, y, 0xFFFFFF);
            if (data.pingToServer < 0) return;
            long ms = data.pingToServer;
            String status = (data.populationInfo == null ? "" : data.populationInfo + "  ") + (ms < 150 ? EnumChatFormatting.GREEN : ms < 300
                ? EnumChatFormatting.YELLOW : EnumChatFormatting.RED) + ms + " ms" + (data.gameVersion == null ? "" : "  " + EnumChatFormatting.GRAY + data.gameVersion);
            font.drawStringWithShadow(status, center - font.getStringWidth(status) / 2F, y, 0xAAAAAA);
        }

        @SubscribeEvent
        public void world(RenderWorldLastEvent event) {
            if (ChunkBorders.on || com.thelads.core.client.worldedit.WorldEditCUI.on) renderWorldLines();
            if (com.thelads.core.client.schematic.SchematicManager.isEnabled() && com.thelads.core.client.schematic.SchematicManager.isLoaded()) schematicOverlay();
        }
    }

    private static void ping(String address) {
        PINGER.clearPendingNetworks();
        ServerData data = new ServerData("", address, false);
        data.pingToServer = -2;
        pinged = data;
        if (pings == null) pings = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Lads Direct Connect ping");
            thread.setDaemon(true);
            return thread;
        });
        pings.execute(() -> {
            try {
                PINGER.ping(data);
            } catch (UnknownHostException unknown) {
                data.serverMOTD = EnumChatFormatting.DARK_RED + "Can't resolve hostname";
            } catch (Exception failed) {
                data.serverMOTD = EnumChatFormatting.DARK_RED + "Can't connect to server";
            }
        });
    }

    /** Crash Catcher: runGameLoop's tick hook is in place (MinecraftQolMixin189). */
    public static void tickHooked() {
        tickHooked = true;
    }

    /**
     * Crash Catcher (MinecraftError, the game's stop signal, is an Error and never reaches here): true when {@code error}, thrown by
     * Minecraft.runTick, was caught: its report is saved and logged as vanilla's
     * would be, the world is left and the Lads crash screen shows. False (the game crashes as vanilla does) when it may not be caught
     * or leaving the world fails too.
     */
    public static boolean crashed(Minecraft mc, RuntimeException error) {
        long now = System.nanoTime();
        CrashReport reported = error instanceof ReportedException ? ((ReportedException) error).getCrashReport() : null;
        if (!CrashCatcher.catches(error, reported == null ? null : reported.getDescription(), mc.theWorld != null && mc.thePlayer != null, now))
            return false;
        try {
            CrashReport report = reported != null ? reported : new CrashReport("Ticking the game (caught by Lads Crash Catcher)", error);
            mc.addGraphicsAndWorldToCrashReport(report);
            report.makeCategory(CrashCatcher.MARKER).addCrashSection(CrashCatcher.MARKER_DETAIL, CrashCatcher.MARKER_TEXT); // the launcher sends these
            File file = new File(new File(mc.mcDataDir, "crash-reports"), "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-client.txt");
            report.saveToFile(file);
            LogManager.getLogger("TheLadsCore").error("Lads Crash Catcher caught a crash and left the world (report: " + file + ")", error);
            CrashCatcher.caught(error, report.getDescription(), report.getCompleteReport(), now);
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new CrashScreen189());
            return true;
        } catch (Throwable failed) {
            error.addSuppressed(failed);
            return false;
        }
    }

    /** Keep Cursor Position: a container screen opened right after one closed puts the pointer back. */
    public static void restoreCursor() {
        if (KeepCursor.reopened(System.nanoTime())) Mouse.setCursorPosition((int) KeepCursor.x, (int) KeepCursor.y);
    }

    /** Scrollable Tooltips: Forge's drawHoveringText starts with a screen this size (TooltipFit's larger one while it draws smaller). */
    public static void tooltipStart(int screenWidth, int screenHeight) {
        tooltipWidth = screenWidth;
        tooltipHeight = screenHeight;
        tooltipMoved = false;
    }

    /** Scrollable Tooltips: Forge placed the tooltip's text at (x, y); it is drawn moved by the scroll from here on. */
    public static void tooltipPlaced(int x, int y, int width, int height) {
        if (!TooltipScroll.on || tooltipMoved) return;
        TooltipScroll.place(x, y, width, height, tooltipWidth, tooltipHeight, System.nanoTime());
        if (TooltipScroll.x == x && TooltipScroll.y == y) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(TooltipScroll.x - x, TooltipScroll.y - y, 0);
        tooltipMoved = true;
    }

    /** Scrollable Tooltips: drawHoveringText returned. */
    public static void tooltipEnd() {
        if (!tooltipMoved) return;
        tooltipMoved = false;
        GlStateManager.popMatrix();
    }

    /** GUI Scale: the exact GUI scale (ScaledResolution keeps a whole one for code that multiplies by it). */
    public static double scale(ScaledResolution resolution) {
        Minecraft mc = Minecraft.getMinecraft();
        return GuiScaleOverride.on ? GuiScaleOverride.scale(mc.displayWidth, mc.displayHeight) : resolution.getScaleFactor();
    }

    /** GUI Scale: window pixels as code that divides them by ScaledResolution's whole scale expects them, so the result is exact. */
    public static int pixels(int pixels, ScaledResolution resolution) {
        return GuiScaleOverride.on ? (int) Math.round(pixels * resolution.getScaleFactor() / scale(resolution)) : pixels;
    }

    private static void renderWorldLines() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) return;
        RenderManager manager = mc.getRenderManager();
        camX = manager.viewerPosX;
        camY = manager.viewerPosY;
        camZ = manager.viewerPosZ;
        Tessellator tessellator = Tessellator.getInstance();
        buffer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GL11.glLineWidth(2);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        int x = (int) Math.floor(mc.getRenderViewEntity().posX), z = (int) Math.floor(mc.getRenderViewEntity().posZ);
        if (ChunkBorders.on) ChunkBorders.draw(x >> 4, z >> 4, 0, 256, SINK);
        if (com.thelads.core.client.worldedit.WorldEditCUI.on) com.thelads.core.client.worldedit.WorldEditCUI.draw(SINK);
        tessellator.draw();
        buffer = null;
        GL11.glLineWidth(1);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static void schematicOverlay() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) return;
        RenderManager manager = mc.getRenderManager();
        camX = manager.viewerPosX;
        camY = manager.viewerPosY;
        camZ = manager.viewerPosZ;
        Tessellator tessellator = Tessellator.getInstance();
        buffer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GL11.glLineWidth(2);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        com.thelads.core.client.schematic.SchematicManager.draw(SCHEMATIC_SINK, camX, camY, camZ);
        tessellator.draw();
        buffer = null;
        GL11.glLineWidth(1);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255, a = argb >>> 24;
        buffer.pos(x0 - camX, y0 - camY, z0 - camZ).color(r, g, b, a).endVertex();
        buffer.pos(x1 - camX, y1 - camY, z1 - camZ).color(r, g, b, a).endVertex();
    }
}

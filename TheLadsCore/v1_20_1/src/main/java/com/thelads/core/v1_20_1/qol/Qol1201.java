package com.thelads.core.v1_20_1.qol;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ChunkBorders;
import com.thelads.core.client.CrashCatcher;
import com.thelads.core.client.GuiScaleOverride;
import com.thelads.core.client.KeepCursor;
import com.thelads.core.client.QuickReconnect;
import com.thelads.core.client.SoundVolumes;
import com.thelads.core.client.TooltipFit;
import com.thelads.core.client.TooltipScroll;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.qol.mixin.MouseHandlerAccessor1201;
import java.io.File;
import java.net.UnknownHostException;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/**
 * Parity 1.9.19 chat lane rows on 1.20.1 (common TooltipScroll, CrashCatcher, QuickReconnect, KeepCursor, SoundVolumes,
 * GuiScaleOverride, ChunkBorders), hooked by theladscore.v1_20_1.qol.mixins.json: Scrollable Tooltips (GuiGraphicsQolMixin1201 at
 * the tooltip positioner, MouseHandlerQolMixin1201 takes the wheel), Crash Catcher and Keep Cursor Position and GUI Scale
 * (MinecraftQolMixin1201: runTick's tick() call, setScreen, resizeDisplay's setGuiScale), Sound Volumes (SoundEngineQolMixin1201),
 * Quick Reconnect (ConnectScreenQolMixin1201 remembers the server; the disconnected and Direct Connect screens through Fabric's
 * screen events) and Chunk Borders (drawn before the debug renderers).
 */
public final class Qol1201 {
    private static final Vector2i PLACED = new Vector2i();
    private static final Vector3f VEC = new Vector3f();
    private static final ServerStatusPinger PINGER = new ServerStatusPinger();
    private static int guiRevision;
    /** Crash Catcher's tick hook ran (it wraps the tick that fires the tick event); checked once: unavailable when it did not apply. */
    private static boolean tickHooked, hookChecked;
    private static ServerAddress lastAddress;
    private static ServerData lastServer;
    /** The Direct Connect ping shown now, or null. */
    private static volatile ServerData pinged;
    // Chunk Borders: the frame being drawn (render thread).
    private static VertexConsumer lines;
    private static PoseStack.Pose pose;
    private static double camX, camY, camZ;
    private static final ChunkBorders.Lines SINK = Qol1201::line;

    private Qol1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(TooltipScroll.NAME, CrashCatcher.NAME, QuickReconnect.NAME, KeepCursor.NAME, SoundVolumes.NAME,
            GuiScaleOverride.NAME, ChunkBorders.NAME, com.thelads.core.client.SoundFilters.NAME, com.thelads.core.client.media.MumbleLink.NAME, com.thelads.core.client.worldedit.WorldEditCUI.NAME, "TeamSpeak");

        ClientTickEvents.END_CLIENT_TICK.register(Qol1201::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(Qol1201::chunkBorders);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DisconnectedScreen) reconnectButton(client, screen);
            else if (screen instanceof DirectJoinServerScreen) directPing(screen);
        });
    }

    private static void tick(Minecraft mc) {
        TooltipScroll.tick();
        QuickReconnect.tick();
        KeepCursor.tick();
        SoundVolumes.tick();
        GuiScaleOverride.tick();
        ChunkBorders.tick();
        boolean water = mc.player != null && (mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || mc.player.isInWater());
        com.thelads.core.client.SoundFilters.tick(water);
        if (mc.player != null) {
            double px = mc.player.getX();
            double py = mc.player.getEyeY();
            double pz = mc.player.getZ();
            float yaw = mc.player.getYRot();
            float pitch = mc.player.getXRot();
            String name = mc.player.getName().getString();
            String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
            com.thelads.core.client.media.MumbleLink.tick(px, py, pz, yaw, pitch, name, server);
        } else {
            com.thelads.core.client.media.MumbleLink.tick();
        }
        com.thelads.core.client.worldedit.WorldEditCUI.tick();
        if (!hookChecked) {
            hookChecked = true;
            if (!tickHooked) ModuleSupport.registerUnavailable(CrashCatcher.NAME, "Another mod changes Minecraft.runTick here, so the game tick "
                + "can't be watched for crashes.");
        }
        if (guiRevision != GuiScaleOverride.revision) {
            guiRevision = GuiScaleOverride.revision;
            mc.resizeDisplay();
        }
    }

    /** Scrollable Tooltips: where a tooltip goes, from where its positioner put it (in TooltipFit's larger space while it fits one). */
    public static Vector2ic tooltip(Vector2ic placed, int width, int height, int screenWidth, int screenHeight) {
        if (!TooltipScroll.on) return placed;
        float fit = TooltipFit.last;
        TooltipScroll.place(placed.x(), placed.y(), width, height, (int) (screenWidth / fit), (int) (screenHeight / fit), System.nanoTime());
        return TooltipScroll.x == placed.x() && TooltipScroll.y == placed.y() ? placed : PLACED.set(TooltipScroll.x, TooltipScroll.y);
    }

    /** Crash Catcher: runTick's tick hook is in place (MinecraftQolMixin1201). */
    public static void tickHooked() {
        tickHooked = true;
    }

    /** GUI Scale: the scale resizeDisplay gives the window, vanilla's whole one while the module is off. */
    public static double guiScale(double vanilla) {
        if (!GuiScaleOverride.on) return vanilla;
        var window = Minecraft.getInstance().getWindow();
        return GuiScaleOverride.scale(window.getWidth(), window.getHeight());
    }

    /** Keep Cursor Position: a container screen opened right after one closed puts the pointer back. */
    public static void restoreCursor(Minecraft mc) {
        if (!KeepCursor.reopened(System.nanoTime())) return;
        MouseHandlerAccessor1201 mouse = (MouseHandlerAccessor1201) mc.mouseHandler;
        mouse.lads$setXpos(KeepCursor.x);
        mouse.lads$setYpos(KeepCursor.y);
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), KeepCursor.x, KeepCursor.y);
    }

    /**
     * Crash Catcher: true when {@code error}, thrown by Minecraft.tick, was caught: its report is saved and logged as vanilla's would
     * be, the world is left and the Lads crash screen shows. False (the game crashes as vanilla does) when it may not be caught or
     * leaving the world fails too.
     */
    public static boolean crashed(Minecraft mc, RuntimeException error) {
        long now = System.nanoTime();
        CrashReport reported = error instanceof ReportedException exception ? exception.getReport() : null;
        if (!CrashCatcher.catches(error, reported == null ? null : reported.getTitle(), mc.level != null && mc.player != null && mc.getOverlay() == null, now))
            return false;
        try {
            CrashReport report = reported != null ? reported : new CrashReport("Ticking the game (caught by Lads Crash Catcher)", error);
            mc.fillReport(report);
            report.addCategory(CrashCatcher.MARKER).setDetail(CrashCatcher.MARKER_DETAIL, CrashCatcher.MARKER_TEXT); // the launcher sends these
            File file = new File(new File(mc.gameDirectory, "crash-reports"), "crash-" + Util.getFilenameFormattedDateTime() + "-client.txt");
            report.saveToFile(file);
            LoggerFactory.getLogger("TheLadsCore").error("Lads Crash Catcher caught a crash and left the world (report: {})", file, error);
            CrashCatcher.caught(error, report.getTitle(), report.getFriendlyReport(), now);
            boolean local = mc.isLocalServer();
            mc.level.disconnect();
            if (local) mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
            else mc.clearLevel();
            mc.setScreen(new CrashScreen1201());
            return true;
        } catch (Throwable failed) {
            error.addSuppressed(failed);
            return false;
        }
    }

    /** Quick Reconnect: ConnectScreen.startConnecting, every server join; the disconnected screen's Reconnect joins it again. */
    public static void connecting(ServerAddress address, ServerData server) {
        if (server == null) return;
        lastAddress = address;
        lastServer = server;
    }

    private static void reconnectButton(Minecraft mc, Screen screen) {
        if (!QuickReconnect.button || lastServer == null) return;
        List<AbstractWidget> widgets = Screens.getButtons(screen);
        AbstractWidget last = null;
        for (AbstractWidget widget : widgets) {
            if (!(widget instanceof Button)) continue;
            if (widget.getMessage().getString().startsWith("Reconnect")) return; // AutoReconnect's own
            last = widget;
        }
        int width = last == null ? 200 : last.getWidth(), x = last == null ? (screen.width - width) / 2 : last.getX();
        int y = last == null ? screen.height / 2 : last.getY() + 24;
        ServerAddress address = lastAddress;
        ServerData server = lastServer;
        widgets.add(Button.builder(Component.literal("Reconnect"), button ->
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, address, server, false)).bounds(x, y, width, 20).build());
    }

    /** Quick Reconnect's Direct Connect ping: the typed address is pinged once typing stops; the result shows under the field. */
    private static void directPing(Screen screen) {
        if (!QuickReconnect.ping) return;
        EditBox field = null;
        for (GuiEventListener child : screen.children()) if (child instanceof EditBox box) field = box;
        if (field == null) return;
        EditBox address = field;
        QuickReconnect.reset();
        pinged = null;
        ScreenEvents.afterTick(screen).register(shown -> {
            String due = QuickReconnect.due(address.getValue(), System.nanoTime());
            if (due != null) ping(due);
            PINGER.tick();
        });
        ScreenEvents.afterRender(screen).register((shown, graphics, mouseX, mouseY, delta) -> drawPing(shown, graphics, address));
        ScreenEvents.remove(screen).register(shown -> {
            PINGER.removeAll();
            QuickReconnect.reset();
            pinged = null;
        });
    }

    private static void ping(String address) {
        PINGER.removeAll();
        ServerData data = new ServerData("", address, false);
        data.motd = Component.translatable("multiplayer.status.pinging");
        data.ping = -1;
        pinged = data;
        Util.backgroundExecutor().execute(() -> {
            try {
                PINGER.pingServer(data, () -> {});
            } catch (UnknownHostException unknown) {
                data.motd = Component.translatable("multiplayer.status.cannot_resolve");
            } catch (Exception failed) {
                data.motd = Component.translatable("multiplayer.status.cannot_connect");
            }
        });
    }

    private static void drawPing(Screen screen, GuiGraphics graphics, EditBox field) {
        ServerData data = pinged;
        if (data == null || !QuickReconnect.ping) return;
        var font = Minecraft.getInstance().font;
        int y = field.getY() + field.getHeight() + 4, center = screen.width / 2;
        List<FormattedCharSequence> motd = font.split(data.motd == null ? Component.empty() : data.motd, 270);
        for (int i = 0; i < Math.min(2, motd.size()); i++, y += 10) graphics.drawCenteredString(font, motd.get(i), center, y, 0xFFFFFF);
        if (data.ping < 0) return;
        Component status = Component.literal(data.ping + " ms").withStyle(s -> s.withColor(data.ping < 150 ? 0x55FF55 : data.ping < 300 ? 0xFFFF55 : 0xFF5555));
        if (data.status != null && !data.status.getString().isEmpty()) status = Component.empty().append(data.status).append("  ").append(status);
        if (data.version != null && !data.version.getString().isEmpty()) status = Component.empty().append(status).append("  ").append(data.version.copy().withStyle(s -> s.withColor(0xAAAAAA)));
        graphics.drawCenteredString(font, status, center, y, 0xAAAAAA);
    }

    private static void chunkBorders(WorldRenderContext context) {
        MultiBufferSource buffers = context.consumers();
        Minecraft mc = Minecraft.getInstance();
        if ((!ChunkBorders.on && !com.thelads.core.client.worldedit.WorldEditCUI.on) || buffers == null || mc.level == null || mc.getCameraEntity() == null) return;
        Vec3 camera = context.camera().getPosition();
        camX = camera.x;
        camY = camera.y;
        camZ = camera.z;
        pose = context.matrixStack().last();
        lines = buffers.getBuffer(RenderType.lines());
        var at = mc.getCameraEntity().blockPosition();
        if (ChunkBorders.on) ChunkBorders.draw(at.getX() >> 4, at.getZ() >> 4, mc.level.getMinBuildHeight(), mc.level.getMaxBuildHeight(), SINK);
        if (com.thelads.core.client.worldedit.WorldEditCUI.on) com.thelads.core.client.worldedit.WorldEditCUI.draw(SINK);
        lines = null;
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(RenderType.lines());
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        float ax = (float) (x0 - camX), ay = (float) (y0 - camY), az = (float) (z0 - camZ);
        float bx = (float) (x1 - camX), by = (float) (y1 - camY), bz = (float) (z1 - camZ);
        float dx = bx - ax, dy = by - ay, dz = bz - az, length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        pose.normal().transform(dx / length, dy / length, dz / length, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255, a = argb >>> 24;
        pose.pose().transformPosition(ax, ay, az, VEC);
        lines.vertex(VEC.x, VEC.y, VEC.z).color(r, g, b, a).normal(nx, ny, nz).endVertex();
        pose.pose().transformPosition(bx, by, bz, VEC);
        lines.vertex(VEC.x, VEC.y, VEC.z).color(r, g, b, a).normal(nx, ny, nz).endVertex();
    }
}

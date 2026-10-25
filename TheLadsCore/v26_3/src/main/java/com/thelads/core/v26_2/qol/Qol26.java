package com.thelads.core.v26_2.qol;

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
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

/**
 * Parity 1.9.19 chat lane rows on 26.3 (common TooltipScroll, CrashCatcher, QuickReconnect, KeepCursor, SoundVolumes, ChunkBorders),
 * hooked by theladscore.v26_3.qol.mixins.json: Scrollable Tooltips (GuiGraphicsQolMixin26 at the tooltip positioner,
 * MouseHandlerQolMixin26 takes the wheel), Crash Catcher (MinecraftQolMixin26 at runTick's tick() call), Keep Cursor Position
 * (GuiQolMixin26 on Gui.setScreen, MouseHandlerQolMixin26 where releaseMouse centres the pointer), Sound Volumes
 * (SoundEngineQolMixin26), Quick Reconnect (ConnectScreenQolMixin26 remembers the server; the disconnected and Direct Connect screens
 * through Fabric's screen events) and Chunk Borders (submitted with the level's geometry). GUI Scale is unavailable: 26.x keeps the
 * GUI scale a whole number through its GUI renderer.
 */
public final class Qol26 {
    private static final Vector2i PLACED = new Vector2i();
    private static final Vector3f VEC = new Vector3f();
    private static final ServerStatusPinger PINGER = new ServerStatusPinger();
    private static ServerAddress lastAddress;
    private static ServerData lastServer;
    /** The Direct Connect ping shown now, or null. */
    private static volatile ServerData pinged;
    private static double camX, camY, camZ;
    /** Crash Catcher's tick hook ran (it wraps the tick that fires the tick event); checked once: unavailable when it did not apply. */
    private static boolean tickHooked, hookChecked;
    // Chunk Borders: the geometry being written (render thread).
    private static VertexConsumer lines;
    private static PoseStack.Pose pose;
    private static final ChunkBorders.Lines SINK = Qol26::line;

    private Qol26() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(TooltipScroll.NAME, CrashCatcher.NAME, QuickReconnect.NAME, KeepCursor.NAME, SoundVolumes.NAME,
            ChunkBorders.NAME, com.thelads.core.client.SoundFilters.NAME, com.thelads.core.client.media.MumbleLink.NAME, com.thelads.core.client.worldedit.WorldEditCUI.NAME, "TeamSpeak");

        ModuleSupport.registerUnavailable(GuiScaleOverride.NAME, "26.x keeps the GUI scale a whole number through its whole GUI renderer "
            + "(projection, scissor, text and item atlases): use the game's GUI Scale option.");
        ClientTickEvents.END_CLIENT_TICK.register(Qol26::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(Qol26::chunkBorders);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DisconnectedScreen) reconnectButton(client, screen);
            else if (screen instanceof DirectJoinServerScreen) directPing(client, screen);
        });
    }

    private static void tick(Minecraft mc) {
        TooltipScroll.tick();
        QuickReconnect.tick();
        KeepCursor.tick();
        SoundVolumes.tick();
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
    }

    /** Crash Catcher: runTick's tick hook is in place (MinecraftQolMixin26). */
    public static void tickHooked() {
        tickHooked = true;
    }

    /** Scrollable Tooltips: where a tooltip goes, from where its positioner put it (in TooltipFit's larger space while it fits one). */
    public static Vector2ic tooltip(Vector2ic placed, int width, int height, int screenWidth, int screenHeight) {
        if (!TooltipScroll.on) return placed;
        float fit = TooltipFit.last;
        TooltipScroll.place(placed.x(), placed.y(), width, height, (int) (screenWidth / fit), (int) (screenHeight / fit), System.nanoTime());
        return TooltipScroll.x == placed.x() && TooltipScroll.y == placed.y() ? placed : PLACED.set(TooltipScroll.x, TooltipScroll.y);
    }

    /**
     * Crash Catcher: true when {@code error}, thrown by Minecraft.tick, was caught: its report is saved and logged as vanilla's would
     * be, the world is left and the Lads crash screen shows. False (the game crashes as vanilla does) when it may not be caught or
     * leaving the world fails too.
     */
    public static boolean crashed(Minecraft mc, RuntimeException error) {
        long now = System.nanoTime();
        CrashReport reported = error instanceof ReportedException exception ? exception.getReport() : null;
        if (!CrashCatcher.catches(error, reported == null ? null : reported.getTitle(), mc.level != null && mc.player != null && mc.gui.overlay() == null, now))
            return false;
        try {
            CrashReport report = reported != null ? reported : new CrashReport("Ticking the game (caught by Lads Crash Catcher)", error);
            mc.fillReport(report);
            report.addCategory(CrashCatcher.MARKER).setDetail(CrashCatcher.MARKER_DETAIL, CrashCatcher.MARKER_TEXT); // the launcher sends these
            Path file = mc.gameDirectory.toPath().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-client.txt");
            report.saveToFile(file, ReportType.CRASH);
            LoggerFactory.getLogger("TheLadsCore").error("Lads Crash Catcher caught a crash and left the world (report: {})", file, error);
            CrashCatcher.caught(error, report.getTitle(), report.getFriendlyReport(ReportType.CRASH), now);
            mc.disconnectFromWorld(Component.literal("The Lads Client caught a crash"));
            mc.gui.setScreen(new CrashScreen26());
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
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
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
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, address, server, false, null)).bounds(x, y, width, 20).build());
    }

    /** Quick Reconnect's Direct Connect ping: the typed address is pinged once typing stops; the result shows under the field. */
    private static void directPing(Minecraft mc, Screen screen) {
        if (!QuickReconnect.ping) return;
        EditBox field = null;
        for (GuiEventListener child : screen.children()) if (child instanceof EditBox box) field = box;
        if (field == null) return;
        EditBox address = field;
        QuickReconnect.reset();
        pinged = null;
        ScreenEvents.afterTick(screen).register(shown -> {
            String due = QuickReconnect.due(address.getValue(), System.nanoTime());
            if (due != null) ping(mc, due);
            PINGER.tick();
        });
        ScreenEvents.afterExtract(screen).register((shown, graphics, mouseX, mouseY, delta) -> drawPing(shown, graphics, address));
        ScreenEvents.remove(screen).register(shown -> {
            PINGER.removeAll();
            QuickReconnect.reset();
            pinged = null;
        });
    }

    private static void ping(Minecraft mc, String address) {
        PINGER.removeAll();
        ServerData data = new ServerData("", address, ServerData.Type.OTHER);
        data.motd = Component.translatable("multiplayer.status.pinging");
        data.ping = -1;
        pinged = data;
        EventLoopGroupHolder network = EventLoopGroupHolder.remote(mc.options.useNativeTransport());
        Util.backgroundExecutor().execute(() -> {
            try {
                PINGER.pingServer(data, () -> {}, () -> {}, network);
            } catch (UnknownHostException unknown) {
                data.motd = Component.translatable("multiplayer.status.cannot_resolve");
            } catch (Exception failed) {
                data.motd = Component.translatable("multiplayer.status.cannot_connect");
            }
        });
    }

    private static void drawPing(Screen screen, GuiGraphicsExtractor graphics, EditBox field) {
        ServerData data = pinged;
        if (data == null || !QuickReconnect.ping) return;
        var font = Minecraft.getInstance().font;
        int y = field.getY() + field.getHeight() + 4, center = screen.width / 2;
        List<FormattedCharSequence> motd = font.split(data.motd == null ? Component.empty() : data.motd, 270);
        for (int i = 0; i < Math.min(2, motd.size()); i++, y += 10) graphics.centeredText(font, motd.get(i), center, y, 0xFFFFFFFF);
        if (data.ping < 0) return;
        Component status = Component.literal(data.ping + " ms").withStyle(s -> s.withColor(data.ping < 150 ? 0x55FF55 : data.ping < 300 ? 0xFFFF55 : 0xFF5555));
        if (data.status != null && !data.status.getString().isEmpty()) status = Component.empty().append(data.status).append("  ").append(status);
        if (data.version != null && !data.version.getString().isEmpty()) status = Component.empty().append(status).append("  ").append(data.version.copy().withStyle(s -> s.withColor(0xAAAAAA)));
        graphics.centeredText(font, status, center, y, 0xFFAAAAAA);
    }

    private static void chunkBorders(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if ((!ChunkBorders.on && !com.thelads.core.client.worldedit.WorldEditCUI.on) || mc.level == null || mc.getCameraEntity() == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        camX = camera.x;
        camY = camera.y;
        camZ = camera.z;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), Qol26::drawChunkBorders);
    }

    private static void drawChunkBorders(PoseStack.Pose at, VertexConsumer buffer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;
        pose = at;
        lines = buffer;
        var block = mc.getCameraEntity().blockPosition();
        if (ChunkBorders.on) ChunkBorders.draw(block.getX() >> 4, block.getZ() >> 4, mc.level.getMinY(), mc.level.getMaxY() + 1, SINK);
        if (com.thelads.core.client.worldedit.WorldEditCUI.on) com.thelads.core.client.worldedit.WorldEditCUI.draw(SINK);
        lines = null;
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        float ax = (float) (x0 - camX), ay = (float) (y0 - camY), az = (float) (z0 - camZ);
        float bx = (float) (x1 - camX), by = (float) (y1 - camY), bz = (float) (z1 - camZ);
        pose.transformNormal(bx - ax, by - ay, bz - az, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        pose.pose().transformPosition(ax, ay, az, VEC);
        lines.addVertex(VEC.x, VEC.y, VEC.z).setColor(argb).setNormal(nx, ny, nz).setLineWidth(2);
        pose.pose().transformPosition(bx, by, bz, VEC);
        lines.addVertex(VEC.x, VEC.y, VEC.z).setColor(argb).setNormal(nx, ny, nz).setLineWidth(2);
    }
}

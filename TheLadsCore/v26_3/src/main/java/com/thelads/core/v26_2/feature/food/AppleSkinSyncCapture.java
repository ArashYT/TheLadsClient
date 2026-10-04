package com.thelads.core.v26_2.feature.food;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.AppleSkinSync;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-appleskin-sync" from the harness's LADS_VERIFY_APPLESKIN_SYNC): an AppleSkin server in the QA
 * world. The module reads as on a remote server (not the integrated one), then saturation 3.5, exhaustion 2 and natural
 * regeneration off arrive as AppleSkin payloads: their bytes through the registered codec into a custom payload packet that the
 * client's own connection handles (Fabric's receiver). The values the module shows are checked and appleskin-sync-1 (survival
 * HUD: half the exhaustion band, saturation outlines over 3.5) is saved; then a payload of the wrong size must change nothing and
 * a new join must forget the values. For the frame the AppleSkin module is on with its default options and Autohide is off, so
 * the HUD shows whatever the QA profile has set. Game mode, the remote flag and both modules are put back.
 */
public final class AppleSkinSyncCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> FAILURES = new ArrayList<>();
    private static int step = -1;
    private static long due;
    private static boolean capturing;
    private static String gameModeWas;
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<Module, Long> MODIFIED = new LinkedHashMap<>();
    private AppleSkinSyncCapture() {}

    public static boolean busy() { return step >= 0 && step < 2; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists. */
    public static void tick(Path game, boolean ready) {
        if (step == 1 && System.nanoTime() >= due) { checkIgnored(); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-appleskin-sync");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.delete(request);
            gameModeWas = mc.gameMode.getPlayerMode().getName();
            Module appleSkin = NativeQualityOfLife.module("AppleSkin"), autohide = NativeQualityOfLife.module("Autohide");
            for (Module module : new Module[] {appleSkin, autohide}) { ENABLED.put(module, module.isEnabled()); MODIFIED.put(module, module.getLastModified()); }
            appleSkin.getOptions().forEach(option -> OPTIONS.put(option, option.save().deepCopy()));
            appleSkin.getOptions().stream().filter(option -> !(option instanceof ActionOption)).forEach(Option::reset);
            appleSkin.setEnabled(true);
            autohide.setEnabled(false);
            command("effect give @a minecraft:resistance 30 255 true");
            command("gamemode survival @a");
            NativeFood.qaRemote = true;
            NativeFood.resetSync();
            check(NativeFood.serverPlayer(mc.player) == null && !NativeFood.exhaustionKnown(null), "remote: no exhaustion before AppleSkin sends one");
            send(AppleSkinSync.SATURATION, AppleSkinSync.encodeFloat(3.5f));
            send(AppleSkinSync.EXHAUSTION, AppleSkinSync.encodeFloat(2f));
            send(AppleSkinSync.NATURAL_REGENERATION, new byte[] {0});
            LOGGER.info("Lads AppleSkin sync capture BEGIN: saturation 3.5, exhaustion 2, natural regeneration off injected");
            step = 0;
            due = System.nanoTime() + 1_500_000_000L;
        } catch (Exception failure) {
            fail("start: " + failure);
            finish();
        }
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    public static void frame(RenderTarget target, Path game) {
        if (step != 0 || capturing || System.nanoTime() < due) return;
        capturing = true;
        Minecraft mc = Minecraft.getInstance();
        check(NativeFood.saturation(mc.player, null) == 3.5f, "saturation from the AppleSkin payload: " + NativeFood.saturation(mc.player, null));
        check(NativeFood.exhaustionKnown(null) && NativeFood.exhaustion(null) == 2f, "exhaustion from the AppleSkin payload: " + NativeFood.exhaustion(null));
        check(!NativeFood.naturalRegeneration(null), "natural regeneration off from the AppleSkin payload");
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve("appleskin-sync-1.png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads AppleSkin sync frame {}", output); }
                catch (Exception failure) { fail("appleskin-sync-1: " + failure); }
                finally { image.close(); mc.execute(AppleSkinSyncCapture::after); }
            });
        } catch (Exception failure) {
            fail("appleskin-sync-1: " + failure);
            after();
        }
    }

    /** After the frame: a short and an impossible payload arrive, checked a moment later. */
    private static void after() {
        step = 1;
        due = System.nanoTime() + 500_000_000L;
        try {
            send(AppleSkinSync.EXHAUSTION, new byte[] {0x40});
            send(AppleSkinSync.SATURATION, AppleSkinSync.encodeFloat(25f));
        } catch (Exception failure) { fail("after: " + failure); }
    }

    /** They changed nothing; a new join forgets everything. */
    private static void checkIgnored() {
        Minecraft mc = Minecraft.getInstance();
        check(NativeFood.exhaustion(null) == 2f && NativeFood.saturation(mc.player, null) == 3.5f, "a short or impossible payload is ignored");
        NativeFood.resetSync();
        check(!NativeFood.exhaustionKnown(null) && NativeFood.naturalRegeneration(null), "a new join forgets the AppleSkin values");
        finish();
    }

    /** The payload's bytes as a server sends them, decoded by the registered codec and handled by the client's own connection. */
    private static void send(String channel, byte[] bytes) {
        var type = AppleSkinPayload.type(channel);
        AppleSkinPayload payload = AppleSkinPayload.codec(type).decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
        new ClientboundCustomPayloadPacket(payload).handle(Minecraft.getInstance().getConnection());
    }

    private static void finish() {
        NativeFood.qaRemote = false;
        NativeFood.resetSync();
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        MODIFIED.forEach(Module::setLastModified);
        if (gameModeWas != null) command("gamemode " + gameModeWas + " @a");
        command("effect clear @a minecraft:resistance");
        step = 2;
        if (FAILURES.isEmpty()) LOGGER.info("Lads AppleSkin sync capture END: 1 frame saved, 0 failed");
        else LOGGER.error("Lads AppleSkin sync capture FAILED: {}", String.join(" | ", FAILURES));
    }

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void check(boolean result, String description) {
        if (result) LOGGER.info("Lads AppleSkin sync capture PASS: {}", description);
        else fail(description);
    }

    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads AppleSkin sync capture check failed: {}", failure);
    }
}

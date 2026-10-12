// SPDX-License-Identifier: Apache-2.0
// Toast layout/behaviour and command names adapted (through the Lads 26.3 port) from SignalLoss 1.2.1+26.2, Copyright Hexandcube.
package com.thelads.core.v1_20_1.hud.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thelads.core.client.SignalLossPolicy;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.TextOption;
import com.thelads.core.modules.SignalLossModule;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * SignalLoss (connection status) on 1.20.1: the "Waiting for server..." toast once received packets stop (packet times from
 * ConnectionActivityMixin, timing from the shared {@link SignalLossPolicy}), and the /signalloss client commands.
 */
public final class SignalLoss1201 {
    private static final SignalLossPolicy POLICY = new SignalLossPolicy();
    private static long lastTenths = -1;
    private static String warning = "";

    private SignalLoss1201() {}

    static SignalLossModule module() { return (SignalLossModule) Hud1201.module("SignalLoss"); }

    static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> POLICY.joined(handler.getConnection(), System.nanoTime()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> POLICY.reset());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommands(dispatcher));
        Path directory = FabricLoader.getInstance().getConfigDir();
        Path upstream = directory.resolve("signalloss.json"), marker = directory.resolve("theladscore/signalloss-imported.txt");
        if (Files.isRegularFile(upstream) && !Files.exists(marker) && defaults(module())) {
            try {
                importLegacy(upstream, module());
                ConfigManager.save();
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "Read-only SignalLoss 1.2.1 preferences imported into Lads.\n");
            } catch (Exception failure) {
                Hud1201.LOG.warn("SignalLoss preferences preserved; import did not complete: {}", failure.toString());
            }
        }
    }

    /** QA (probe step "ui silence <ms>"): pretend the last packet arrived this many nanoseconds ago; 0 = the real connection. */
    private static volatile long qaSilence;

    public static void qaSilence(long nanos) { qaSilence = nanos; }

    private static SignalLossPolicy.Frame frame() {
        Minecraft minecraft = Minecraft.getInstance();
        SignalLossModule module = module();
        var listener = minecraft.getConnection();
        Connection connection = listener == null ? null : listener.getConnection();
        boolean eligible = module.isEnabled() && minecraft.level != null && minecraft.player != null && !minecraft.options.hideGui
            && connection != null && connection.isConnected() && (module.singleplayer.get() || !minecraft.hasSingleplayerServer());
        long received = connection instanceof PacketActivity1201 activity ? activity.lads$lastPacketNanos() : 0;
        if (qaSilence > 0) received = System.nanoTime() - qaSilence; // QA only: a server that has been silent this long
        var settings = new SignalLossPolicy.Settings(SignalLossModule.milliseconds(module.timeout, 2000),
            SignalLossModule.milliseconds(module.minimum, 2000), SignalLossModule.milliseconds(module.linger, 1000));
        return POLICY.update(connection, System.nanoTime(), received, eligible, minecraft.isPaused(),
            minecraft.options.screenEffectScale().get() <= 0, settings);
    }

    /** Every HUD frame (OverlayFeatures1201). */
    static void render(GuiGraphics graphics) {
        SignalLossPolicy.Frame frame = frame();
        if (frame.progress() <= 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        SignalLossModule module = module();
        long tenths = Math.round(frame.seconds() * 10);
        if (tenths != lastTenths) {
            lastTenths = tenths;
            warning = String.format(Locale.ROOT, "⚠ Waiting for server... (%.1fs)", frame.seconds());
        }
        int width = minecraft.font.width(warning), height = minecraft.font.lineHeight;
        int x = switch (module.position.getIndex()) {
            case 0 -> 10;
            case 2 -> graphics.guiWidth() - width - 10;
            default -> (graphics.guiWidth() - width) / 2;
        };
        float eased = 1 - (1 - frame.progress()) * (1 - frame.progress());
        int hiddenY = -height - 17, y = Math.round(hiddenY + (10 - hiddenY) * eased);
        if (module.background.get()) graphics.fill(x - 6, y - 6, x + width + 6, y + height + 6,
            module.backgroundColor.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : module.backgroundColor.getColor());
        graphics.drawString(minecraft.font, warning, x, y,
            module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor(), HudSettings.getInstance().isTextShadow());
    }

    // ----- /signalloss reload | /signalloss config <setting> <value> | /signalloss config reset

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SignalLossModule module = module();
        var root = literal("signalloss");
        root.then(literal("reload").executes(context -> {
            try {
                reloadNative(ClientPaths.getConfigFile().toPath(), module);
                POLICY.reset();
                context.getSource().sendFeedback(Component.literal("Lads SignalLoss preferences reloaded."));
                return 1;
            } catch (Exception failed) {
                context.getSource().sendError(Component.literal("SignalLoss preferences could not be reloaded: " + failed.getMessage()));
                return 0;
            }
        }));
        var config = literal("config");
        config.then(literal("reset").executes(context -> {
            copy(new SignalLossModule(), module);
            ConfigManager.save();
            POLICY.reset();
            context.getSource().sendFeedback(Component.literal("Lads SignalLoss preferences reset."));
            return 1;
        }));
        config.then(bool("enabled", module::setEnabled));
        config.then(bool("drawBackground", module.background::set));
        config.then(bool("showInSingleplayer", module.singleplayer::set));
        config.then(timing("timeoutThreshold", module.timeout));
        config.then(timing("minWarningTime", module.minimum));
        config.then(timing("lingerTime", module.linger));
        config.then(color("textColor", module.textColor));
        config.then(color("backgroundColor", module.backgroundColor));
        config.then(literal("position").then(argument("pos", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[] {"LEFT", "CENTER", "RIGHT"}, builder))
            .executes(context -> {
                String value = StringArgumentType.getString(context, "pos").toUpperCase(Locale.ROOT);
                int index = switch (value) { case "LEFT" -> 0; case "CENTER" -> 1; case "RIGHT" -> 2; default -> -1; };
                if (index < 0) { context.getSource().sendError(Component.literal("Use LEFT, CENTER or RIGHT.")); return 0; }
                module.position.setIndex(index);
                saved(context.getSource(), "position", value);
                return 1;
            })));
        dispatcher.register(root.then(config));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> bool(String name, Consumer<Boolean> setter) {
        return literal(name).then(argument("value", BoolArgumentType.bool()).executes(context -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            setter.accept(value);
            saved(context.getSource(), name, value);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> timing(String name, TextOption option) {
        return literal(name).then(argument("milliseconds", IntegerArgumentType.integer(0)).executes(context -> {
            int value = IntegerArgumentType.getInteger(context, "milliseconds");
            option.setValue(Integer.toString(value));
            saved(context.getSource(), name, value);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> color(String name, ColorOption option) {
        return literal(name).then(argument("hex", StringArgumentType.greedyString()).executes(context -> {
            try {
                int value = parseColor(StringArgumentType.getString(context, "hex"));
                option.setUseGlobal(false);
                option.setColor(value);
                saved(context.getSource(), name, String.format(Locale.ROOT, "#%08X", value));
                return 1;
            } catch (IllegalArgumentException invalid) {
                context.getSource().sendError(Component.literal("Use RGB or ARGB hex, for example #FF5555 or A0000000."));
                return 0;
            }
        }));
    }

    /** #RRGGBB / RRGGBB (opaque) or #AARRGGBB / 0xAARRGGBB. */
    static int parseColor(String text) {
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        else if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        if (!value.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException("Invalid color");
        return (int) (Long.parseLong(value, 16) | (value.length() == 6 ? 0xff000000L : 0));
    }

    private static void saved(FabricClientCommandSource source, String name, Object value) {
        ConfigManager.save();
        source.sendFeedback(Component.literal("SignalLoss " + name + ": " + value));
    }

    // ----- Preferences: the original mod's file is imported read-only; reload touches only the SignalLoss section of the Lads config.

    private static JsonObject read(Path file) throws IOException {
        if (Files.size(file) > 4 * 1024 * 1024) throw new IOException("SignalLoss configuration is too large");
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static void importLegacy(Path file, SignalLossModule destination) throws IOException {
        JsonObject json = read(file);
        SignalLossModule value = new SignalLossModule();
        if (json.has("enabled")) value.setEnabled(json.get("enabled").getAsBoolean());
        if (json.has("timeoutThreshold")) value.timeout.setValue(integer(json, "timeoutThreshold"));
        if (json.has("minWarningTime")) value.minimum.setValue(integer(json, "minWarningTime"));
        if (json.has("lingerTime")) value.linger.setValue(integer(json, "lingerTime"));
        if (json.has("drawBackground")) value.background.set(json.get("drawBackground").getAsBoolean());
        if (json.has("showInSingleplayer")) value.singleplayer.set(json.get("showInSingleplayer").getAsBoolean());
        if (json.has("toastPosition")) value.position.setIndex(switch (json.get("toastPosition").getAsString().toUpperCase(Locale.ROOT)) {
            case "LEFT" -> 0;
            case "CENTER" -> 1;
            case "RIGHT" -> 2;
            default -> throw new IllegalArgumentException("Unknown SignalLoss position");
        });
        if (json.has("textColor")) value.textColor.setColor(json.get("textColor").getAsInt());
        if (json.has("backgroundColor")) value.backgroundColor.setColor(json.get("backgroundColor").getAsInt());
        copy(value, destination);
    }

    private static String integer(JsonObject json, String key) {
        int result = Integer.parseInt(json.get(key).getAsString());
        if (result < 0) throw new IllegalArgumentException("Negative SignalLoss timing");
        return Integer.toString(result);
    }

    private static void reloadNative(Path file, SignalLossModule destination) throws IOException {
        JsonObject modules = read(file).getAsJsonObject("modules");
        JsonObject section = modules == null ? null : modules.getAsJsonObject("SignalLoss");
        if (section == null) throw new IOException("No saved SignalLoss preferences");
        SignalLossModule value = new SignalLossModule();
        if (section.has("enabled")) value.setEnabled(section.get("enabled").getAsBoolean());
        if (section.has("options")) {
            JsonObject options = section.getAsJsonObject("options");
            value.getOptions().forEach(option -> { if (options.has(option.getName())) option.load(options.get(option.getName())); });
        }
        copy(value, destination);
    }

    private static void copy(SignalLossModule source, SignalLossModule destination) {
        source.getOptions().forEach(option -> destination.getOption(option.getName()).load(option.save()));
        destination.setEnabled(source.isEnabled());
    }

    private static boolean defaults(SignalLossModule module) {
        SignalLossModule defaults = new SignalLossModule();
        return module.isEnabled() && module.getOptions().stream().allMatch(option -> option.save().equals(defaults.getOption(option.getName()).save()));
    }
}

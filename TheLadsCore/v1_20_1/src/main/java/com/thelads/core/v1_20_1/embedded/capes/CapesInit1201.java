package com.thelads.core.v1_20_1.embedded.capes;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v1_20_1.embedded.capes.handler.PlayerHandler;
import com.thelads.core.v1_20_1.embedded.capes.menu.CapeButton;
import com.thelads.core.v1_20_1.embedded.capes.menu.SelectorMenu;
import com.thelads.core.v1_20_1.embedded.capes.mixin.AccessorPlayerInfo;
import com.thelads.core.v1_20_1.embedded.capes.render.PlayerPreview;
import com.thelads.core.v1_20_1.embedded.capes.util.Utils;
import com.thelads.core.v1_20_1.perf.PerfProbe1201;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capes for 1.20.1, embedded in Lads Core: capes from OptiFine, LabyMod, Cosmetica, MinecraftCapes and Cloaks+ on other players
 * (and your own pick, in the cape selector next to the skin customization toggles), animated capes, the cape on the elytra and
 * the {@code /capes debug} client command. Called once at client init, only while the original Capes mod is not installed
 * ({@code EmbeddedMods1201}); the texture swap, the cutout render type and the screen button are the mixins of
 * {@code theladscore.v1_20_1.embedded.capes.mixins.json}.
 */
public final class CapesInit1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Capes");

    private CapesInit1201() {}

    public static void init() {
        Capes.init();
        if (PerfProbe1201.passive()) ClientTickEvents.END_CLIENT_TICK.register(new Probe()::tick);
    }

    // ---------------------------------------------------------------- QA probe

    /**
     * Once in a world, with no network: a made-up player (random version-4 UUID with a texture property, so it is a valid profile)
     * gets a generated cape through the real texture path of {@link PlayerHandler}; the real {@code PlayerInfo} must then answer with
     * that cape (and the elytra rule), the skin customization screen must carry the cape button, and the selector's preview player must draw.
     */
    private static final class Probe {
        private int ticks;
        private int phase;
        private int waited;
        private GameProfile profile;
        private PlayerInfo info;
        private PlayerHandler handler;

        void tick(Minecraft mc) {
            if (phase > 1 || mc.level == null || ++ticks < 120) return;
            try {
                if (phase == 0) {
                    UUID uuid = UUID.randomUUID();
                    profile = new GameProfile(uuid, "LadsCapeProbe");
                    String payload = "{\"profileName\":\"LadsCapeProbe\",\"textures\":{}}";
                    profile.getProperties().put("textures", new Property("textures", Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))));
                    info = new PlayerInfo(profile, false);
                    ((AccessorPlayerInfo) info).capes$setPendingTextures(true); // no skin lookup at the session server for a made-up player
                    handler = PlayerHandler.fromProfile(profile);
                    handler.skipLookup(); // no cape provider is asked either
                    byte[] png;
                    try (NativeImage image = new NativeImage(64, 32, true)) {
                        image.setPixelRGBA(1, 1, 0xFF0000FF);
                        png = image.asByteArray();
                    }
                    phase = handler.setCapeTexture(new ByteArrayInputStream(png), false, false) ? 1 : 2;
                    if (phase == 2) LOGGER.info("PERF-PROBE capes FAIL the generated cape was not accepted by the texture path");
                    return;
                }
                if (!handler.getHasCape() && ++waited < 100) return; // the texture is registered on the main thread
                phase = 2;
                ResourceLocation expected = Capes.identifier(profile.getId().toString());
                ResourceLocation cape = info.getCapeLocation();
                ResourceLocation elytra = info.getElytraLocation();
                boolean elytraOk = elytra != null && (Capes.getConfig().getEnableElytraTexture() ? elytra.equals(expected) : elytra.getPath().endsWith("elytra.png"));
                boolean texture = mc.getTextureManager().getTexture(expected) instanceof DynamicTexture;

                SkinCustomizationScreen skin = new SkinCustomizationScreen(null, mc.options);
                skin.init(mc, 427, 240);
                long buttons = skin.children().stream().filter(CapeButton.class::isInstance).count();
                SelectorMenu selector = new SelectorMenu(null, mc.options);
                selector.init(mc, 427, 240);
                GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                PlayerPreview.render(graphics, 187, 65, 287, 365, 0.0F);

                boolean ok = Utils.isValidProfile(profile) && handler.getHasCape() && expected.equals(cape) && elytraOk && texture && buttons == 1
                    && !PlayerPreview.isBroken();
                LOGGER.info("PERF-PROBE capes {} validProfile={} registered={} playerInfoCape={} elytra={} texture={} skinScreenButtons={} previewDrawn={}", ok ? "PASS" : "FAIL",
                    Utils.isValidProfile(profile), handler.getHasCape(), cape, elytra, texture, buttons, !PlayerPreview.isBroken());
            } catch (Exception | LinkageError failure) {
                phase = 2;
                LOGGER.info("PERF-PROBE capes FAIL {}", failure.toString());
            }
        }
    }
}

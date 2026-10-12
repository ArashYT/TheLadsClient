package com.thelads.core.v1_20_1.world;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.WavyCape;
import com.thelads.core.modules.TntTimerModule;
import com.thelads.core.v1_20_1.feature.QaProbe1201;
import com.thelads.core.v1_20_1.world.mixin.PlayerInfoTexturesAccessor;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the script verb {@code world191} (1.9.1 world lane). {@code world191 cape} gives the offline QA player a test cape
 * (common WavyCape.qaPixel) through its real PlayerInfo, {@code world191 nocape} takes it away, {@code world191 chest <item>} puts an
 * item in the chest slot (client and integrated server), {@code world191 clearchat} empties the chat, {@code world191 log} logs the
 * player's cape (links, sway) and every primed TNT and lit TNT minecart with the timer text it shows.
 */
final class WorldQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final ResourceLocation CAPE = new ResourceLocation("theladscore", "qa_cape");

    private WorldQa1201() {}

    static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null)
            QaProbe1201.registerStep("world191", WorldQa1201::step);
    }

    private static boolean step(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "log";
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        switch (what) {
            case "cape" -> {
                if (mc.getTextureManager().getTexture(CAPE, null) == null) {
                    NativeImage image = new NativeImage(64, 32, true);
                    for (int x = 0; x < 64; x++) for (int y = 0; y < 32; y++) {
                        int argb = WavyCape.qaPixel(x, y); // NativeImage wants ABGR
                        image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF);
                    }
                    mc.getTextureManager().register(CAPE, new DynamicTexture(image));
                }
                info.getCapeLocation(); // starts the profile's own texture lookup first, so it cannot overwrite the test cape
                ((PlayerInfoTexturesAccessor) info).lads$textures().put(MinecraftProfileTexture.Type.CAPE, CAPE);
                LOGGER.info("Lads world191 cape: test cape given, player cape now {}", mc.player.getCloakTextureLocation());
            }
            case "nocape" -> ((PlayerInfoTexturesAccessor) info).lads$textures().remove(MinecraftProfileTexture.Type.CAPE);
            case "clearchat" -> mc.gui.getChat().clearMessages(false); // command feedback would cover the next shot
            case "chest" -> { // the chest item on this client's player and on the integrated server's (the QA command's change never reached the client)
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(args.length > 2 ? args[2] : "air"));
                mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(item));
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                if (server != null) server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    if (player != null) player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(item));
                });
            }
            case "log" -> {
                WavyCape cape = WavyCape.of(mc.player);
                StringBuilder links = new StringBuilder();
                for (int i = 0; i < cape.segments(); i++) links.append(i == 0 ? "" : " ").append(Math.round(cape.linkDegrees(i)));
                LOGGER.info("Lads world191 cape: texture={} segments={} links(deg)=[{}] sway={} speed={} chest={}", mc.player.getCloakTextureLocation(),
                    cape.segments(), links, String.format(Locale.ROOT, "%.1f", cape.sway(1)),
                    String.format(Locale.ROOT, "%.3f", mc.player.getDeltaMovement().horizontalDistance()),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
                for (Entity entity : mc.level.entitiesForRendering()) {
                    float left = entity instanceof PrimedTnt tnt ? tnt.getFuse() : entity instanceof MinecartTNT cart && cart.getFuse() > -1 ? cart.getFuse() + 1 : Float.NaN;
                    if (!Float.isNaN(left)) LOGGER.info("Lads world191 tnt: {} id={} fuse ticks left={} timer='{}' distance={}", entity.getType().toShortString(),
                        entity.getId(), left, TntTimerModule.label(left), String.format(Locale.ROOT, "%.1f", entity.distanceTo(mc.player)));
                }
            }
            default -> throw new IllegalArgumentException("world191 cape|nocape|chest <item>|clearchat|log, not " + what);
        }
        return true;
    }
}

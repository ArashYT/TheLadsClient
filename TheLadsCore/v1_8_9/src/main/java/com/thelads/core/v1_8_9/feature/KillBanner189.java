package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.KillBannerModule;
import java.util.Collections;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * KillBanner on 1.8.9, as 26.x NativeKillBanner: banners the moment the client sees a kill (KillDetector), the death of a player,
 * mob or boss the local player hit last, or a server's kill message. Signals: Forge's attack event (the local player's hits),
 * NetHandlerPlayClientMixin (death status and zero health) and Forge's chat event (chat and system lines, not the action bar),
 * all on the client thread. 1.8.9 sends no damage events, so only the local player's own blows credit a kill.
 */
public final class KillBanner189 {
    private static final KillBannerTimeline BANNER = KillBanners.TIMELINE;
    private static NetHandlerPlayClient trackedConnection;
    private static int lastLabelDelta;
    private static boolean lastLabelPreview;
    private static String label = "";
    /** QA only (Probe150): banner frames and picker thumbnails drawn. */
    public static long frames, thumbs;

    private static KillBannerModule module() {
        return Options189.module("KillBanner") instanceof KillBannerModule ? (KillBannerModule) Options189.module("KillBanner") : null;
    }

    private static boolean eligible() {
        Minecraft mc = Minecraft.getMinecraft();
        return Options189.enabled("KillBanner") && mc.theWorld != null && mc.thePlayer != null && mc.getNetHandler() != null
            && mc.getNetHandler().getNetworkManager().isChannelOpen();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!eligible()) { reset(); return; }
        if (trackedConnection != mc.getNetHandler()) { reset(); trackedConnection = mc.getNetHandler(); }
        if (!mc.thePlayer.isEntityAlive()) BANNER.clear();
    }

    /** The local player's attack (sent before Forge's event, so a cancelled event still hit): a crosshair point on the top quarter is a head hit. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void attack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entityPlayer != mc.thePlayer || !eligible()) return;
        MovingObjectPosition hit = mc.objectMouseOver;
        boolean head = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit == event.target
            && hit.hitVec != null && hit.hitVec.yCoord >= event.target.posY + event.target.height * .75;
        EntityLivingBase victim = victim(event.target);
        if (victim != null) KillBanners.DETECTOR.hitByMe(victim.getEntityId(), names(victim), kind(victim), head, System.nanoTime());
    }

    /** A death status, or a health update to zero (NetHandlerPlayClientMixin). */
    public static void died(Entity entity) {
        if (entity != null && eligible()) kill(KillBanners.DETECTOR.died(entity.getEntityId(), System.nanoTime()));
    }

    /** A chat or system line (1.8.9 cannot tell plugin messages from player chat), not the action bar; as the server sent it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void chat(ClientChatReceivedEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type == 2 || event.message == null || !eligible()) return;
        kill(KillBanners.DETECTOR.chat(event.message.getUnformattedText(), names(mc.thePlayer), System.nanoTime()));
    }

    private static void kill(KillDetector.Kill kill) {
        KillBannerModule module = module();
        if (kill == null || module == null) return;
        play(KillBanners.fire(module, kill, System.nanoTime()), (float) module.volume.getValue());
    }

    /** The living entity a hit lands on: the Ender Dragon for its parts. */
    private static EntityLivingBase victim(Entity entity) {
        if (entity instanceof EntityDragonPart && ((EntityDragonPart) entity).entityDragonObj instanceof EntityLivingBase)
            return (EntityLivingBase) ((EntityDragonPart) entity).entityDragonObj;
        return entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand) && entity != Minecraft.getMinecraft().thePlayer
            ? (EntityLivingBase) entity : null;
    }

    private static KillDetector.Kind kind(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) return KillDetector.Kind.PLAYER;
        return entity instanceof EntityDragon || entity instanceof EntityWither || entity instanceof EntityGuardian && ((EntityGuardian) entity).isElder()
            ? KillDetector.Kind.BOSS : KillDetector.Kind.MOB;
    }

    /** A player's account name and the name the tab list shows. */
    private static Set<String> names(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) return Collections.emptySet();
        EntityPlayer player = (EntityPlayer) entity;
        NetworkPlayerInfo info = Minecraft.getMinecraft().getNetHandler().getPlayerInfo(player.getUniqueID());
        return KillDetector.names(player.getGameProfile().getName(),
            info != null && info.getDisplayName() != null ? info.getDisplayName().getUnformattedText() : player.getDisplayName().getUnformattedText());
    }

    static void reset() {
        trackedConnection = null;
        KillBanners.reset();
    }

    /** QA: a banner with the module's look ({@code kills} 1 to 5), its sound included. */
    static void trigger(int kills, boolean preview) {
        KillBannerModule module = module();
        if (!eligible() || module == null) return;
        trackedConnection = Minecraft.getMinecraft().getNetHandler();
        play(KillBanners.show(module, kills, preview, false, module.chosen(), System.nanoTime()), (float) module.volume.getValue());
    }

    /** "theladscore:<skin>_kill_<n>" from the Kill Banner sounds.json, "" the plain chime (1.8.9's orb pickup), at the module's volume. */
    private static void play(String sound, final float level) {
        if (sound == null) return;
        final ResourceLocation id = new ResourceLocation(sound.isEmpty() ? "random.orb" : sound);
        Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSound(id) {
            {
                volume = level;
                pitch = 1.0F;
                attenuationType = ISound.AttenuationType.NONE;
            }
        });
    }

    /** Kill Banner picker: a skin's one-kill sound. */
    public static void previewSound(String skin, float volume) {
        KillBannerStyle style = KillBannerStyle.fromId(skin);
        play("theladscore:" + style.id + "_kill_1", volume);
    }

    /** Kill Banner picker art (LadsGraphics.drawKillBanner). */
    public static boolean drawThumb(String skin, int variant, int x, int y, int w, int h) {
        try {
            KillBannerStyle style = KillBannerStyle.fromId(skin);
            KillBannerArt189.begin();
            try {
                KillBannerArt189.thumb(style, variant, x, y, w, h);
            } finally {
                KillBannerArt189.end();
            }
            thumbs++;
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** After Forge's whole overlay, as the other versions draw at the HUD's end; F1 skips 1.8.9's overlay, hiding it as on 26.x. */
    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post event) {
        KillBannerModule module = module();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !eligible() || module == null) return;
        double age = BANNER.age(System.nanoTime());
        if (age < 0) return;
        // Blending and depth go back as found, as NativeHud leaves them.
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        KillBannerArt189.begin();
        try {
            render(module, age, event.resolution.getScaledWidth(), event.resolution.getScaledHeight());
        } finally {
            KillBannerArt189.end();
            if (!blend) GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth();
        }
    }

    private static void render(KillBannerModule module, double age, int width, int height) {
        KillBannerModule.Pick pick = KillBanners.shown(module);
        KillBannerStyle style = pick.style();
        if (style == null) {
            renderBase(module, age, width, height);
            return;
        }
        KillBannerStrip strip = style.strip(BANNER.sequence());
        KillBannerPlayer.Frame frame = KillBannerPlayer.at(style, strip, age, module.duration.getValue(), BANNER.headshot() && module.headshotText.get());
        if (frame == null) return;
        KillBannerArt189.draw(width, height, style, pick.variant(), BANNER.sequence(), strip, frame, (float) module.size.getValue() / 100f);
        frames++;
    }

    private static void renderBase(KillBannerModule module, double age, int guiWidth, int guiHeight) {
        double opacity = KillBannerTimeline.opacity(age, module.duration.getValue());
        if (opacity <= 0) return;
        if (lastLabelDelta != BANNER.sequence() || lastLabelPreview != BANNER.preview() || label.isEmpty()) {
            lastLabelDelta = BANNER.sequence();
            lastLabelPreview = BANNER.preview();
            label = BANNER.preview() ? "PREVIEW" : BANNER.sequence() == 1 ? "KILL" : BANNER.sequence() + " KILLS";
        }
        KillBannerArt189.Sprite base = KillBannerArt189.sprite(KillBannerArt189.BASE);
        int height = Math.round(74 * (float) module.size.getValue() / 100f), width = Math.round(height * (float) base.width / base.height);
        float entrance = (float) (1 - Math.pow(1 - Math.min(1, age / .22), 3));
        float scale = .85f + .15f * entrance;
        int alpha = (int) Math.round(255 * opacity);
        int chosen = module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor();
        int textAlpha = (int) Math.round(((chosen >>> 24) & 255) * opacity);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(guiWidth / 2f, Math.max(12, guiHeight - 80 - height) + (1 - entrance) * 8, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            KillBannerArt189.blit(base, -width / 2, 0, width, height, alpha << 24 | 0xffffff);
            // 1.8.9's FontRenderer draws alpha below 4 opaque: the label leaves just before the banner.
            if (textAlpha > 3) {
                net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
                font.drawStringWithShadow(label, -font.getStringWidth(label) / 2, height + 3, textAlpha << 24 | chosen & 0xffffff);
            }
        } finally {
            GlStateManager.popMatrix();
        }
        frames++;
    }
}

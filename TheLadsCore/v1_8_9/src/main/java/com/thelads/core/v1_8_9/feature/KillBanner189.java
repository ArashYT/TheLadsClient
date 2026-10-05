package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * KillBanner on 1.8.9, as 26.x NativeKillBanner: banners the moment the client sees a kill (KillDetector), the death of a player,
 * mob or boss the local player hit last, or a server's kill message. Signals: Forge's attack event (the local player's hits),
 * NetHandlerPlayClientMixin (death status and zero health) and Forge's chat event (chat and system lines, not the action bar),
 * all on the client thread. 1.8.9 sends no damage events, so only the local player's own blows credit a death; a player
 * knocked off or shot also counts through the server's kill message. Kills queue their banners (KillBannerTimeline); each client
 * tick starts the next one whose turn has come. The streak ends on death (the death screen, or a server's message that the
 * player died), in a new world (dimension, Hypixel's next game) or on another server.
 */
public final class KillBanner189 {
    private static final KillBannerTimeline BANNER = KillBanners.TIMELINE;
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore");
    /** The last banner failure logged: one line for each different one, not one a frame. */
    private static String failureLogged = "";
    private static NetHandlerPlayClient trackedConnection;
    private static WorldClient trackedWorld;
    private static int lastLabelDelta;
    private static boolean lastLabelPreview;
    private static String label = "";
    /** QA only (Probe150): banner frames and picker thumbnails drawn. */
    public static long frames, thumbs;
    /** QA only (Probe170Misc): each frame's time (ns) while a probe records them. */
    static long[] frameTimes, renderTimes;
    static int frameCount;
    /** QA: the next banner draws fail as a missing asset would (the overlay must skip it, not crash). */
    static boolean qaBreak;
    /** QA: an opaque RGB drawn over the frame behind the banner, so held frames of different runs compare pixel for pixel (0: none). */
    static int backdrop;
    private static long lastFrame;

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
        KillBannerArt189.sweep();
        Minecraft mc = Minecraft.getMinecraft();
        if (!eligible()) { reset(); return; }
        if (trackedConnection != mc.getNetHandler() || trackedWorld != mc.theWorld) {
            reset();
            trackedConnection = mc.getNetHandler();
            trackedWorld = mc.theWorld;
        }
        if (!mc.thePlayer.isEntityAlive()) BANNER.clear();
        KillBannerModule module = module();
        if (module != null) {
            long now = System.nanoTime();
            String sound = KillBanners.poll(module, now);
            if (BANNER.age(now) == 0) prefetch(module); // a queued banner started now: its frames start decoding
            play(sound, (float) module.volume.getValue());
        }
        KillBannerModule.Pick pick = module != null ? module.chosen() : null;
        if (pick == null) return;
        if (pick.style() != null) KillBannerArt189.warm(pick.style(), pick.variant());
        else KillBannerArt189.warmBase();
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (frameTimes == null || event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        if (lastFrame != 0 && frameCount < frameTimes.length) frameTimes[frameCount++] = now - lastFrame;
        lastFrame = now;
    }

    /** QA: records the next {@code frames} frame times (null stops). */
    static void recordFrames(int frames) {
        frameTimes = frames > 0 ? new long[frames] : null;
        renderTimes = frames > 0 ? new long[frames] : null;
        frameCount = 0;
        lastFrame = 0;
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
        // A click on a body still falling over is no new hit: the death already counted.
        if (victim != null && victim.getHealth() > 0) KillBanners.DETECTOR.hitByMe(victim.getEntityId(), names(victim), kind(victim), head, System.nanoTime());
    }

    /** A death status, or a health update to zero (NetHandlerPlayClientMixin). */
    public static void died(Entity entity) {
        if (entity != null && eligible()) kill(KillBanners.DETECTOR.died(entity.getEntityId(), System.nanoTime()));
    }

    /**
     * A chat or system line (1.8.9 cannot tell plugin messages from player chat), not the action bar; as the server sent it:
     * a kill, or the player's own death (it ends the streak). A vanilla death message is read by its translation key and names,
     * so it counts in any client language.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void chat(ClientChatReceivedEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type == 2 || event.message == null || !eligible()) return;
        Set<String> me = names(mc.thePlayer);
        String text = null;
        if (event.message instanceof ChatComponentTranslation) {
            ChatComponentTranslation death = (ChatComponentTranslation) event.message;
            List<String> args = new ArrayList<String>();
            for (Object arg : death.getFormatArgs()) args.add(arg instanceof IChatComponent ? ((IChatComponent) arg).getUnformattedText() : String.valueOf(arg));
            text = KillDetector.deathLine(death.getKey(), args);
        }
        if (text == null) text = event.message.getUnformattedText();
        if (KillDetector.myDeath(text, me)) BANNER.endStreak();
        kill(KillBanners.DETECTOR.chat(text, me, KillBanner189::players, System.nanoTime()));
    }

    /** The other players in the world: a kill message may name one the client saw no hit on (an arrow, a knock into the void). */
    private static Map<Integer, Set<String>> players() {
        Minecraft mc = Minecraft.getMinecraft();
        Map<Integer, Set<String>> players = new HashMap<Integer, Set<String>>();
        for (EntityPlayer player : mc.theWorld.playerEntities)
            if (player != mc.thePlayer) players.put(player.getEntityId(), names(player));
        return players;
    }

    private static void kill(KillDetector.Kill kill) {
        KillBannerModule module = module();
        if (kill == null || module == null) return;
        String sound = KillBanners.fire(module, kill, System.nanoTime());
        prefetch(module);
        play(sound, (float) module.volume.getValue());
    }

    /** A banner has just started: its frames start decoding now. */
    private static void prefetch(KillBannerModule module) {
        if (BANNER.age(System.nanoTime()) >= 0) KillBannerArt189.prefetch(KillBanners.shown(module), BANNER.sequence());
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
        trackedWorld = null;
        KillBanners.reset();
    }

    /** QA: a banner with the module's look ({@code kills} 1 to 5), its sound included. */
    static void trigger(int kills, boolean preview) {
        trigger(kills, preview, false);
    }

    static void trigger(int kills, boolean preview, boolean headshot) {
        KillBannerModule module = module();
        if (module != null) trigger(kills, preview, headshot, module.chosen());
    }

    /** QA: a banner of this pick (a random skin's, say), with its sound. */
    static void trigger(int kills, boolean preview, boolean headshot, KillBannerModule.Pick pick) {
        KillBannerModule module = module();
        if (!eligible() || module == null) return;
        bindCurrent();
        String sound = KillBanners.show(module, kills, preview, headshot, pick, System.nanoTime());
        prefetch(module);
        play(sound, (float) module.volume.getValue());
    }

    /** QA: a banner is on screen. */
    static boolean showing() {
        return BANNER.age(System.nanoTime()) >= 0;
    }

    /** QA: false once the strip frame the last draw wanted was the one drawn (always for stills). */
    static boolean stale() {
        return KillBannerArt189.stale();
    }

    /** QA: binds the current connection and world now, so the next tick does not reset a banner fired on purpose. */
    static void bindCurrent() {
        trackedConnection = Minecraft.getMinecraft().getNetHandler();
        trackedWorld = Minecraft.getMinecraft().theWorld;
    }

    /** QA: the banner on screen stays this many seconds after its kill (KillBannerTimeline.freeze). */
    static void freeze(double age) {
        BANNER.freeze(age);
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

    /** The Kill Banner settings preview (LadsGraphics.drawKillBannerPreview): the skin's banners for 1 to 5 kills in turn. */
    public static boolean drawPreview(String skin, int variant, int x, int y, int w, int h, double clock) {
        try {
            KillBannerArt189.begin();
            try {
                KillBannerArt189.preview(KillBannerStyle.fromId(skin), variant, x, y, w, h, clock);
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
        long started = System.nanoTime(); // QA only: what the banner costs a frame (renderTimes)
        // Blending and depth go back as found, as NativeHud leaves them.
        boolean blend = GlState189.blend(), depth = GlState189.depth();
        GlStateManager.disableDepth();
        KillBannerArt189.begin();
        if (backdrop != 0) drawBackdrop(event.resolution.getScaledWidth(), event.resolution.getScaledHeight());
        try {
            render(module, age, event.resolution.getScaledWidth(), event.resolution.getScaledHeight());
        } catch (RuntimeException | LinkageError failure) {
            // A missing or corrupt asset: this banner is skipped (and logged once), the game goes on.
            BANNER.clear();
            String what = String.valueOf(failure.getMessage());
            if (!what.equals(failureLogged)) {
                failureLogged = what;
                LOGGER.warn("Lads kill banner skipped: its art could not be drawn", failure);
            }
        } finally {
            KillBannerArt189.end();
            if (!blend) GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth();
            if (renderTimes != null && frameCount < renderTimes.length) renderTimes[frameCount] = System.nanoTime() - started;
        }
    }

    private static void drawBackdrop(int width, int height) {
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer buffer = tessellator.getWorldRenderer();
        GlStateManager.disableTexture2D();
        GlStateManager.color((backdrop >> 16 & 255) / 255f, (backdrop >> 8 & 255) / 255f, (backdrop & 255) / 255f, 1.0F);
        buffer.begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        buffer.pos(0, height, 0).endVertex();
        buffer.pos(width, height, 0).endVertex();
        buffer.pos(width, 0, 0).endVertex();
        buffer.pos(0, 0, 0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private static void render(KillBannerModule module, double age, int width, int height) {
        if (qaBreak) throw new IllegalStateException("QA: kill banner art unavailable");
        KillBannerModule.Pick pick = KillBanners.shown(module);
        KillBannerStyle style = pick.style();
        if (style == null) {
            renderBase(module, age, width, height);
            return;
        }
        boolean headshot = BANNER.headshot() && module.headshotText.get();
        float size = (float) module.size.getValue() / 100f;
        if (style.isAnimated()) {
            KillBannerStrip strip = style.strip(BANNER.sequence());
            KillBannerPlayer.Frame frame = KillBannerPlayer.at(style, strip, age, module.duration.getValue(), headshot);
            if (frame == null) return;
            KillBannerArt189.draw(width, height, style, pick.variant(), BANNER.sequence(), strip, frame, size);
        } else {
            KillBannerPlayer.Layers layers = KillBannerPlayer.layers(style, BANNER.sequence(), age, module.duration.getValue(), headshot);
            if (layers == null) return;
            KillBannerArt189.draw(width, height, style, pick.variant(), BANNER.sequence(), layers, size);
        }
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

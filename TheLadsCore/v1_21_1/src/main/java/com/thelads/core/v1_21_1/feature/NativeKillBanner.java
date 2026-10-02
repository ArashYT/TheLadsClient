package com.thelads.core.v1_21_1.feature;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.ServerKillTracker;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.KillBannerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.opengl.GL11;

/** The 26.x Kill Banner on 1.21.1: server-reported player kills, the Base banner and the Reaver and Rogue skins. */
public final class NativeKillBanner {
    private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("theladscore", "textures/gui/base_kill_banner.png");
    private static final ServerKillTracker KILLS = new ServerKillTracker();
    private static final KillBannerTimeline BANNER = new KillBannerTimeline();
    private static ClientPacketListener trackedConnection;
    private static StatsCounter trackedStats;
    private static boolean requested;
    private static long lastRequest;
    private static int lastLabelDelta;
    private static boolean lastLabelPreview;
    private static String label = "";
    private static long lastHit;
    private static boolean lastHitHead;
    private NativeKillBanner() {}

    public static void tick() {
        if (NativeKillBannerPreview.tick()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible()) { reset(); return; }
        bind(minecraft.getConnection());
        if (!minecraft.player.isAlive()) BANNER.clear();
        long now = System.nanoTime();
        // REQUEST_STATS returns changed stats; at most one request per two seconds.
        if (!minecraft.isPaused() && (!requested || now - lastRequest >= 2_000_000_000L)) {
            requested = true;
            lastRequest = now;
            trackedConnection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
    }

    /** Called after vanilla applied an actual server statistics response on the client thread. */
    public static void receivedStats(ClientPacketListener source) {
        if (NativeKillBannerPreview.active()) return;
        if (!eligible()) { reset(); return; }
        if (Minecraft.getInstance().getConnection() != source) return;
        bind(source);
        int total = Minecraft.getInstance().player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
        int delta = KILLS.observe(total);
        if (delta > 0 && Minecraft.getInstance().player.isAlive()) trigger(delta, false);
    }

    /**
     * Called as the local player attacks (MultiPlayerGameMode.attack). A hit whose crosshair point lands on the top
     * quarter of the target's box counts as a head hit; the kill that follows within 3 seconds is a headshot.
     */
    public static void attacked(Entity target) {
        if (Minecraft.getInstance().hitResult instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
            && hit.getEntity() == target) {
            lastHit = System.nanoTime();
            lastHitHead = hit.getLocation().y >= target.getY() + target.getBbHeight() * .75;
        }
    }

    private static boolean eligible() {
        Minecraft minecraft = Minecraft.getInstance();
        return NativeQualityOfLife.enabled("KillBanner") && minecraft.level != null && minecraft.player != null
            && minecraft.getConnection() != null && minecraft.getConnection().getConnection().isConnected();
    }

    private static void bind(ClientPacketListener connection) {
        StatsCounter stats = Minecraft.getInstance().player.getStats();
        // Vanilla retains StatsCounter on respawn. A replacement cache (including
        // modded respawn behavior) must establish a fresh baseline nonetheless.
        if (trackedConnection == connection && trackedStats == stats) return;
        reset();
        trackedConnection = connection;
        trackedStats = stats;
    }

    /** QA: binds the current connection now, so the next tick does not reset a banner fired on purpose. */
    static void bindCurrent() {
        if (eligible()) bind(Minecraft.getInstance().getConnection());
    }

    static void reset() {
        trackedConnection = null;
        trackedStats = null;
        requested = false;
        KILLS.reset();
        BANNER.clear();
    }

    static void trigger(int delta, boolean preview) {
        trigger(delta, preview, !preview && lastHitHead && System.nanoTime() - lastHit < 3_000_000_000L);
    }

    static void trigger(int delta, boolean preview, boolean headshot) {
        if (!eligible() || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        BANNER.trigger(delta, System.nanoTime(), preview, headshot);
        if (module.sound.get() && module.volume.getValue() > 0) {
            KillBannerStyle style = style(module);
            // Each skin's own sound for the kill count, as Valorant plays it.
            SoundEvent sound = style != null
                ? SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("theladscore", style.id + "_kill_" + BANNER.sequence()))
                : SoundEvents.EXPERIENCE_ORB_PICKUP;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1, (float) module.volume.getValue()));
        }
    }

    static KillBannerTimeline timeline() { return BANNER; }

    /** The selected skin, or null for the static Base banner. */
    private static KillBannerStyle style(KillBannerModule module) {
        return switch (module.bannerStyle.getIndex()) {
            case 1 -> KillBannerStyle.REAVER;
            case 2 -> KillBannerStyle.ROGUE;
            default -> null;
        };
    }

    /** At Gui.render TAIL (KillBannerHudMixin), over the rest of the HUD. */
    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible() || minecraft.options.hideGui
            || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        double age = BANNER.age(System.nanoTime());
        if (age < 0) return;
        KillBannerStyle style = style(module);
        if (style == null) {
            renderBase(graphics, minecraft, module, age);
            return;
        }
        KillBannerStrip strip = style.strip(BANNER.sequence());
        KillBannerPlayer.Frame frame = KillBannerPlayer.at(style, strip, age, module.duration.getValue(),
            BANNER.headshot() && module.headshotText.get());
        if (frame == null) return;
        int variant = (style == KillBannerStyle.REAVER ? module.reaverVariant : module.rogueVariant).getIndex();
        KillBannerArt.draw(graphics, style, variant, BANNER.sequence(), strip, frame, (float) module.size.getValue() / 100f);
    }

    private static void renderBase(GuiGraphics graphics, Minecraft minecraft, KillBannerModule module, double age) {
        double opacity = KillBannerTimeline.opacity(age, module.duration.getValue());
        if (opacity <= 0) return;
        if (lastLabelDelta != BANNER.delta() || lastLabelPreview != BANNER.preview() || label.isEmpty()) {
            lastLabelDelta = BANNER.delta();
            lastLabelPreview = BANNER.preview();
            label = BANNER.preview() ? "PREVIEW" : BANNER.delta() == 1 ? "PLAYER KILL" : BANNER.delta() + " PLAYER KILLS";
        }
        // 1.21.1 textures keep no size: read the resource texture's (a resource pack may replace it).
        minecraft.getTextureManager().getTexture(BASE).bind();
        int sourceWidth = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int sourceHeight = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        int height = Math.round(74 * (float) module.size.getValue() / 100f), width = Math.round(height * (float) sourceWidth / sourceHeight);
        float entrance = (float) (1 - Math.pow(1 - Math.min(1, age / .22), 3));
        float scale = .85f + .15f * entrance;
        int alpha = (int) Math.round(255 * opacity);
        int chosenColor = module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor();
        int textAlpha = (int) Math.round(((chosenColor >>> 24) & 255) * opacity);
        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            graphics.pose().translate(graphics.guiWidth() / 2f, Math.max(12, graphics.guiHeight() - 80 - height) + (1 - entrance) * 8, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.setColor(1, 1, 1, alpha / 255f);
            graphics.blit(BASE, -width / 2, 0, width, height, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
            graphics.setColor(1, 1, 1, 1);
            // 1.21.1 draws text with an alpha under 4 opaque.
            if (textAlpha > 3) graphics.drawString(minecraft.font, label, -minecraft.font.width(label) / 2, height + 3,
                textAlpha << 24 | chosenColor & 0xffffff, true);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }
}

package com.thelads.core.v26_2.feature;

import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.KillBannerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;

public final class NativeKillBanner {
    private static final Identifier BASE = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/base_kill_banner.png");
    private static final Identifier REAVER = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_banner.png");
    private static final ServerKillTracker KILLS = new ServerKillTracker();
    private static final KillBannerTimeline BANNER = new KillBannerTimeline();
    private static ClientPacketListener trackedConnection;
    private static StatsCounter trackedStats;
    private static boolean requested;
    private static long lastRequest;
    private static int lastLabelDelta;
    private static boolean lastLabelPreview;
    private static String label = "";
    private NativeKillBanner() {}

    public static void tick() {
        if (NativeKillBannerPreview.tick()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible()) { reset(); return; }
        bind(minecraft.getConnection());
        if (!minecraft.player.isAlive()) BANNER.clear();
        long now = System.nanoTime();
        // REQUEST_STATS returns changed stats in vanilla 26.2; at most one request per two seconds.
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

    static void reset() {
        trackedConnection = null;
        trackedStats = null;
        requested = false;
        KILLS.reset();
        BANNER.clear();
    }

    static void trigger(int delta, boolean preview) {
        if (!eligible() || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        BANNER.trigger(delta, System.nanoTime(), preview);
        if (module.sound.get() && module.volume.getValue() > 0) {
            SoundEvent sound = module.bannerStyle.getIndex() == 1
                ? SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("theladscore", "reaver_kill_" + BANNER.sequence()))
                : SoundEvents.EXPERIENCE_ORB_PICKUP;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1, (float) module.volume.getValue()));
        }
    }

    static KillBannerTimeline timeline() { return BANNER; }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible() || minecraft.gui.hud.isHidden()
            || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        double age = BANNER.age(System.nanoTime());
        double opacity = KillBannerTimeline.opacity(age, module.duration.getValue());
        if (opacity <= 0) return;
        if (lastLabelDelta != BANNER.delta() || lastLabelPreview != BANNER.preview() || label.isEmpty()) {
            lastLabelDelta = BANNER.delta();
            lastLabelPreview = BANNER.preview();
            label = BANNER.preview() ? "PREVIEW" : BANNER.delta() == 1 ? "PLAYER KILL" : BANNER.delta() + " PLAYER KILLS";
        }
        Identifier texture = module.bannerStyle.getIndex() == 1 ? REAVER : BASE;
        var image = minecraft.getTextureManager().getTexture(texture).getTexture();
        int sourceWidth = image.getWidth(0), sourceHeight = image.getHeight(0);
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        int height = 74, width = Math.round(height * (float) sourceWidth / sourceHeight);
        float entrance = (float) (1 - Math.pow(1 - Math.min(1, age / .22), 3));
        float scale = .85f + .15f * entrance;
        int alpha = (int) Math.round(255 * opacity);
        int chosenColor = module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor();
        int textAlpha = (int) Math.round(((chosenColor >>> 24) & 255) * opacity);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(graphics.guiWidth() / 2f, Math.max(12, graphics.guiHeight() - 154) + (1 - entrance) * 8);
            graphics.pose().scale(scale, scale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -width / 2, 0, 0, 0,
                width, height, sourceWidth, sourceHeight, sourceWidth, sourceHeight, alpha << 24 | 0xffffff);
            graphics.text(minecraft.font, label, -minecraft.font.width(label) / 2, height + 3,
                textAlpha << 24 | chosenColor & 0xffffff, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}

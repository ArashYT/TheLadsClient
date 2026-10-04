package com.thelads.core.v1_21_11.feature;

import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.KillBannerModule;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Kill banners the moment the client sees a kill (KillDetector): the death of a player, mob or boss the local player hit last,
 * or a plugin server's kill message. Hooks: KillBannerAttackMixin (attacks) and KillBannerPacketsMixin (damage and death
 * events, health, server chat), all on the client thread as the packet is handled.
 */
public final class NativeKillBanner {
    private static final Identifier BASE = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/base_kill_banner.png");
    private static final KillBannerTimeline BANNER = KillBanners.TIMELINE;
    private static ClientPacketListener trackedConnection;
    private static int lastLabelDelta;
    private static boolean lastLabelPreview;
    private static String label = "";
    private NativeKillBanner() {}

    public static void tick() {
        if (NativeKillBannerPreview.tick()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible()) { reset(); return; }
        if (trackedConnection != minecraft.getConnection()) { reset(); trackedConnection = minecraft.getConnection(); }
        if (!minecraft.player.isAlive()) BANNER.clear();
    }

    /** MultiPlayerGameMode.attack: a hit whose crosshair point lands on the top quarter of the target's box is a head hit. */
    public static void attacked(Entity target) {
        if (!eligible()) return;
        boolean head = Minecraft.getInstance().hitResult instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
            && hit.getEntity() == target && hit.getLocation().y >= target.getY() + target.getBbHeight() * .75;
        LivingEntity victim = victim(target);
        if (victim != null) KillBanners.DETECTOR.hitByMe(victim.getId(), names(victim), kind(victim), head, System.nanoTime());
    }

    /** A damage event: its cause (the attacker, or a projectile's owner) is the local player, someone else, or nobody. */
    public static void damaged(int entityId, int causeId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible() || causeId < 0) return;
        if (causeId == minecraft.player.getId()) {
            if (victim(minecraft.level.getEntity(entityId)) instanceof LivingEntity victim)
                KillBanners.DETECTOR.hitByMe(victim.getId(), names(victim), kind(victim), false, System.nanoTime());
        } else KillBanners.DETECTOR.hitByOther(entityId, System.nanoTime());
    }

    /** A death event, or a health update to zero. */
    public static void died(Entity entity) {
        if (entity != null && eligible()) kill(KillBanners.DETECTOR.died(entity.getId(), System.nanoTime()));
    }

    /** A server (system) chat line, not the action bar. */
    public static void chat(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (message == null || !eligible()) return;
        var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        Set<String> me = KillDetector.names(minecraft.player.getGameProfile().name(),
            info != null && info.getTabListDisplayName() != null ? info.getTabListDisplayName().getString() : minecraft.player.getDisplayName().getString());
        kill(KillBanners.DETECTOR.chat(message.getString(), me, System.nanoTime()));
    }

    private static void kill(KillDetector.Kill kill) {
        if (kill == null || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        play(module, KillBanners.fire(module, kill, System.nanoTime()));
    }

    /** The living entity a hit lands on: the Ender Dragon for its parts. */
    private static LivingEntity victim(Entity entity) {
        if (entity instanceof EnderDragonPart part) return part.parentMob;
        return entity instanceof LivingEntity living && !(living instanceof ArmorStand) && living != Minecraft.getInstance().player ? living : null;
    }

    private static KillDetector.Kind kind(LivingEntity entity) {
        if (entity instanceof Player) return KillDetector.Kind.PLAYER;
        return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof Warden || entity instanceof ElderGuardian
            ? KillDetector.Kind.BOSS : KillDetector.Kind.MOB;
    }

    private static Set<String> names(LivingEntity entity) {
        if (!(entity instanceof Player player)) return Set.of();
        var info = Minecraft.getInstance().getConnection().getPlayerInfo(player.getUUID());
        return KillDetector.names(player.getGameProfile().name(),
            info != null && info.getTabListDisplayName() != null ? info.getTabListDisplayName().getString() : player.getDisplayName().getString());
    }

    private static boolean eligible() {
        Minecraft minecraft = Minecraft.getInstance();
        return NativeQualityOfLife.enabled("KillBanner") && minecraft.level != null && minecraft.player != null
            && minecraft.getConnection() != null && minecraft.getConnection().getConnection().isConnected();
    }

    /** QA: binds the current connection now, so the next tick does not reset a banner fired on purpose. */
    static void bindCurrent() {
        if (eligible()) trackedConnection = Minecraft.getInstance().getConnection();
    }

    static void reset() {
        trackedConnection = null;
        KillBanners.reset();
    }

    /** A preview or QA banner: {@code style} 0 Base, 1 Reaver, 2 Rogue, with the module's variant for that skin. */
    static void trigger(int kills, boolean preview) {
        trigger(kills, preview, false);
    }

    static void trigger(int kills, boolean preview, boolean headshot) {
        if (!eligible() || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        play(module, KillBanners.show(module, kills, preview, headshot, module.chosen(), System.nanoTime()));
    }

    private static void play(KillBannerModule module, String sound) {
        if (sound == null) return;
        SoundEvent event = sound.isEmpty() ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvent.createVariableRangeEvent(Identifier.parse(sound));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1, (float) module.volume.getValue()));
    }

    /** Kill Banner picker art (LadsGraphics.drawKillBanner). */
    public static boolean drawThumb(GuiGraphics g, String skin, int variant, int x, int y, int w, int h) {
        try {
            KillBannerStyle style = KillBannerStyle.fromId(skin);
            KillBannerArt.thumb(g, style, variant, x, y, w, h);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** Kill Banner picker: a skin's one-kill sound. */
    public static void previewSound(String skin, float volume) {
        KillBannerStyle style = KillBannerStyle.fromId(skin);
        SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("theladscore", style.id + "_kill_1"));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1, volume));
    }

    static KillBannerTimeline timeline() { return BANNER; }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible() || minecraft.options.hideGui
            || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        double age = BANNER.age(System.nanoTime());
        if (age < 0) return;
        KillBannerModule.Pick pick = KillBanners.shown(module);
        KillBannerStyle style = pick.style();
        if (style == null) {
            renderBase(graphics, minecraft, module, age);
            return;
        }
        KillBannerStrip strip = style.strip(BANNER.sequence());
        KillBannerPlayer.Frame frame = KillBannerPlayer.at(style, strip, age, module.duration.getValue(),
            BANNER.headshot() && module.headshotText.get());
        if (frame == null) return;
        KillBannerArt.draw(graphics, style, pick.variant(), BANNER.sequence(), strip, frame, (float) module.size.getValue() / 100f);
    }

    private static void renderBase(GuiGraphics graphics, Minecraft minecraft, KillBannerModule module, double age) {
        double opacity = KillBannerTimeline.opacity(age, module.duration.getValue());
        if (opacity <= 0) return;
        if (lastLabelDelta != BANNER.sequence() || lastLabelPreview != BANNER.preview() || label.isEmpty()) {
            lastLabelDelta = BANNER.sequence();
            lastLabelPreview = BANNER.preview();
            label = BANNER.preview() ? "PREVIEW" : BANNER.sequence() == 1 ? "KILL" : BANNER.sequence() + " KILLS";
        }
        var image = minecraft.getTextureManager().getTexture(BASE).getTexture();
        int sourceWidth = image.getWidth(0), sourceHeight = image.getHeight(0);
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        int height = Math.round(74 * (float) module.size.getValue() / 100f), width = Math.round(height * (float) sourceWidth / sourceHeight);
        float entrance = (float) (1 - Math.pow(1 - Math.min(1, age / .22), 3));
        float scale = .85f + .15f * entrance;
        int alpha = (int) Math.round(255 * opacity);
        int chosenColor = module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor();
        int textAlpha = (int) Math.round(((chosenColor >>> 24) & 255) * opacity);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(graphics.guiWidth() / 2f, Math.max(12, graphics.guiHeight() - 80 - height) + (1 - entrance) * 8);
            graphics.pose().scale(scale, scale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BASE, -width / 2, 0, 0, 0,
                width, height, sourceWidth, sourceHeight, sourceWidth, sourceHeight, alpha << 24 | 0xffffff);
            graphics.drawString(minecraft.font, label, -minecraft.font.width(label) / 2, height + 3,
                textAlpha << 24 | chosenColor & 0xffffff, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}

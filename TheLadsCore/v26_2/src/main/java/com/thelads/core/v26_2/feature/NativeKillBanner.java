package com.thelads.core.v26_2.feature;

import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
 * events, health, server chat), all on the client thread as the packet is handled. Kills queue their banners
 * (KillBannerTimeline); each client tick starts the next one whose turn has come. The streak ends on death (the death
 * screen, or a server's message that the player died), in a new world (dimension, Hypixel's next game) or on another server.
 */
public final class NativeKillBanner {
    private static final Identifier BASE = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/base_kill_banner.png");
    private static final KillBannerTimeline BANNER = KillBanners.TIMELINE;
    private static ClientPacketListener trackedConnection;
    private static ClientLevel trackedLevel;
    private static int lastLabelDelta;
    private static boolean lastLabelPreview;
    private static String label = "";
    private NativeKillBanner() {}

    public static void tick() {
        KillBannerArt.sweep();
        if (NativeKillBannerPreview.tick()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible()) { reset(); return; }
        if (trackedConnection != minecraft.getConnection() || trackedLevel != minecraft.level) {
            reset();
            trackedConnection = minecraft.getConnection();
            trackedLevel = minecraft.level;
        }
        if (!minecraft.player.isAlive()) BANNER.clear();
        KillBannerArrows.tick(minecraft);
        if (NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module) {
            play(module, KillBanners.poll(module, System.nanoTime()));
            KillBannerModule.Pick pick = module.chosen();
            if (pick.style() != null) KillBannerArt.warm(pick.style(), pick.variant());
        }
    }

    /** MultiPlayerGameMode.attack: a hit whose crosshair point lands on the top quarter of the target's box is a head hit. */
    public static void attacked(Entity target) {
        if (!eligible()) return;
        boolean head = Minecraft.getInstance().hitResult instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
            && hit.getEntity() == target && KillDetector.headHit(hit.getLocation().y, target.getY(), target.getBbHeight());
        LivingEntity victim = victim(target);
        // A click on a body still falling over is no new hit: the death already counted.
        if (victim != null && !victim.isDeadOrDying()) KillBanners.DETECTOR.hitByMe(victim.getId(), names(victim), kind(victim), head, System.nanoTime());
    }

    /** One of the local player's arrows crossed {@code target}'s box at {@code hitY} (KillBannerArrows): a hit, in the head when up top. */
    static void arrowHit(Entity target, double hitY) {
        if (!eligible()) return;
        LivingEntity victim = victim(target);
        if (victim == null || victim.isDeadOrDying()) return;
        boolean head = KillDetector.headHit(hitY, target.getY(), target.getBbHeight());
        KillBanners.DETECTOR.hitByMe(victim.getId(), names(victim), kind(victim), head, System.nanoTime());
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

    /**
     * A server (system) chat line, not the action bar: a kill, or the player's own death (it ends the streak). A vanilla death
     * message is read by its translation key and names, so it counts in any client language.
     */
    public static void chat(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (message == null || !eligible()) return;
        Set<String> me = names(minecraft.player);
        String text = null;
        if (message.getContents() instanceof TranslatableContents death) {
            List<String> args = new ArrayList<>();
            for (Object arg : death.getArgs()) args.add(arg instanceof Component part ? part.getString() : String.valueOf(arg));
            text = KillDetector.deathLine(death.getKey(), args);
        }
        if (text == null) text = message.getString();
        if (KillDetector.myDeath(text, me)) BANNER.endStreak();
        kill(KillBanners.DETECTOR.chat(text, me, NativeKillBanner::players, System.nanoTime()));
    }

    /** The other players in the world: a kill message may name one the client saw no hit on (a knock into the void). */
    private static Map<Integer, Set<String>> players() {
        Map<Integer, Set<String>> players = new HashMap<>();
        for (Player player : Minecraft.getInstance().level.players())
            if (player != Minecraft.getInstance().player) players.put(player.getId(), names(player));
        return players;
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
        if (!eligible()) return;
        trackedConnection = Minecraft.getInstance().getConnection();
        trackedLevel = Minecraft.getInstance().level;
    }

    static void reset() {
        trackedConnection = null;
        trackedLevel = null;
        KillBanners.reset();
    }

    /** A preview or QA banner: {@code style} 0 Base, 1 Reaver, 2 Rogue, with the module's variant for that skin. */
    static void trigger(int kills, boolean preview) {
        trigger(kills, preview, false);
    }

    static void trigger(int kills, boolean preview, boolean headshot) {
        if (NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module) trigger(kills, preview, headshot, module.chosen());
    }

    /** QA: a banner of this pick (a random skin's, say), with its sound. */
    static void trigger(int kills, boolean preview, boolean headshot, KillBannerModule.Pick pick) {
        if (!eligible() || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        play(module, KillBanners.show(module, kills, preview, headshot, pick, System.nanoTime()));
    }

    private static void play(KillBannerModule module, String sound) {
        if (sound == null) return;
        SoundEvent event = sound.isEmpty() ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvent.createVariableRangeEvent(Identifier.parse(sound));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1, (float) module.volume.getValue()));
    }

    /** Kill Banner picker art (LadsGraphics.drawKillBanner). */
    public static boolean drawThumb(GuiGraphicsExtractor g, String skin, int variant, int x, int y, int w, int h) {
        try {
            KillBannerStyle style = KillBannerStyle.fromId(skin);
            KillBannerArt.thumb(g, style, variant, x, y, w, h);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** The Kill Banner settings preview: the skin's banners for 1 to 5 kills in turn, {@code clock} seconds into the loop. */
    public static boolean drawPreview(GuiGraphicsExtractor g, String skin, int variant, int x, int y, int w, int h, double clock) {
        try {
            KillBannerArt.preview(g, KillBannerStyle.fromId(skin), variant, x, y, w, h, clock);
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

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible() || minecraft.gui.hud.isHidden()
            || !(NativeQualityOfLife.module("KillBanner") instanceof KillBannerModule module)) return;
        double age = BANNER.age(System.nanoTime());
        if (age < 0) return;
        KillBannerModule.Pick pick = KillBanners.shown(module);
        KillBannerStyle style = pick.style();
        if (style == null) {
            renderBase(graphics, minecraft, module, age);
            return;
        }
        boolean headshot = BANNER.headshot() && module.headshotBanner.get(), mark = module.killMark.get();
        float size = (float) module.size.getValue() / 100f;
        if (style.isAnimated()) {
            KillBannerStrip strip = style.strip(BANNER.sequence());
            KillBannerPlayer.Frame frame = KillBannerPlayer.at(style, strip, age, module.duration.getValue(), headshot, BANNER.cutAge());
            if (frame != null) KillBannerArt.draw(graphics, style, pick.variant(), BANNER.sequence(), strip, frame, size, mark);
        } else {
            KillBannerPlayer.Layers layers = KillBannerPlayer.layers(style, BANNER.sequence(), age, module.duration.getValue(), headshot, BANNER.cutAge());
            if (layers != null) KillBannerArt.draw(graphics, style, pick.variant(), BANNER.sequence(), layers, size, mark);
        }
    }

    private static void renderBase(GuiGraphicsExtractor graphics, Minecraft minecraft, KillBannerModule module, double age) {
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
            graphics.text(minecraft.font, label, -minecraft.font.width(label) / 2, height + 3,
                textAlpha << 24 | chosenColor & 0xffffff, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}

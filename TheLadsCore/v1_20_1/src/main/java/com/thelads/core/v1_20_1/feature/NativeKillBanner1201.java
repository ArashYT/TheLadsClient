package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ComboSoundsModule;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class NativeKillBanner1201 {
    private static final ResourceLocation BASE = new ResourceLocation("theladscore", "textures/gui/base_kill_banner.png");
    private static final KillBannerTimeline BANNER = KillBanners.TIMELINE;
    private static ClientPacketListener trackedConnection;
    private static ClientLevel trackedLevel;
    private static int lastLabelDelta;
    private static boolean lastLabelPreview;
    private static String label = "";

    private NativeKillBanner1201() {}

    private static KillBannerModule module() {
        Module m = ModuleManager.getInstance().getModule("KillBanner");
        return m instanceof KillBannerModule ? (KillBannerModule) m : null;
    }

    public static void tick() {
        KillBannerArt1201.sweep();
        Minecraft minecraft = Minecraft.getInstance();
        if (!eligible()) { reset(); return; }
        if (trackedConnection != minecraft.getConnection() || trackedLevel != minecraft.level) {
            reset();
            trackedConnection = minecraft.getConnection();
            trackedLevel = minecraft.level;
        }
        if (minecraft.player != null && !minecraft.player.isAlive()) BANNER.clear();
        KillBannerArrows1201.tick(minecraft);
        KillBannerModule module = module();
        if (module != null) {
            play(module, KillBanners.poll(module, System.nanoTime()));
            KillBannerModule.Pick pick = module.chosen();
            if (pick != null && pick.style() != null) KillBannerArt1201.warm(pick.style(), pick.variant());
        }
    }

    /** MultiPlayerGameMode.attack: where the crosshair's point lands on the target says whether it is a head hit (headHit). */
    public static void attacked(Entity target) {
        LivingEntity struck = victim(target);
        if (struck != null) ComboSoundsModule.onAttack(struck.getId(), struck instanceof Player); // Combo Sounds, with Kill Banner on or off
        if (!eligible()) return;
        boolean head = Minecraft.getInstance().hitResult instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
            && hit.getEntity() == target && headHit(target, Minecraft.getInstance().player.getEyePosition(), hit.getLocation());
        LivingEntity victim = victim(target);
        if (victim != null && !victim.isDeadOrDying()) {
            KillBanners.DETECTOR.hitByMe(victim.getId(), names(victim), kind(victim), head, System.nanoTime());
        }
    }

    /** One of the local player's arrows, coming from {@code from}, crossed {@code target}'s box at {@code at} (KillBannerArrows1201). */
    static void arrowHit(Entity target, Vec3 from, Vec3 at) {
        if (!eligible()) return;
        LivingEntity victim = victim(target);
        if (victim == null || victim.isDeadOrDying()) return;
        KillBanners.DETECTOR.hitByMe(victim.getId(), names(victim), kind(victim), headHit(target, from, at), System.nanoTime());
    }

    /**
     * A hit coming from {@code from} through {@code at} on {@code target}: on a multipart mob's part, a head hit when the part is its head (the dragon's
     * "head", Alex's Mobs' and Caves' headPart, from the field the mob holds it in); on anything else, when it lands in the
     * head about the eyes of its box (KillDetector.headHit), wherever modded mobs keep their bodies.
     */
    private static boolean headHit(Entity target, Vec3 from, Vec3 at) {
        AABB box = target.getBoundingBox();
        float yaw = target instanceof LivingEntity living ? living.yBodyRot : target.getYRot();
        boolean head;
        if (target instanceof EnderDragonPart part) head = KillDetector.headPart(part.name);
        else if (KillDetector.part(target)) head = KillDetector.headPart(KillDetector.partName(KillDetector.parentOf(target), target));
        else head = KillDetector.headHit(from.x, from.y, from.z, at.x, at.y, at.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, target.getEyeY(), yaw);
        if (QaProbe1201.active()) com.thelads.core.v1_20_1.hud.Hud1201.LOG.info("Lads killbanner QA hit: {} at {} on {} eyes {} yaw {}: head={}",
            EntityType.getKey(target.getType()), at, box, target.getEyeY(), yaw, head);
        return head;
    }

    public static void damaged(int entityId, int causeId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) ComboSoundsModule.onHurt(entityId, entityId == minecraft.player.getId());
        if (!eligible() || causeId < 0 || minecraft.player == null || minecraft.level == null) return;
        if (causeId == minecraft.player.getId()) {
            LivingEntity victim = victim(minecraft.level.getEntity(entityId));
            if (victim != null) {
                KillBanners.DETECTOR.damagedByMe(victim.getId(), names(victim), kind(victim), System.nanoTime());
            }
        } else {
            KillBanners.DETECTOR.hitByOther(entityId, System.nanoTime());
        }
    }

    public static void died(Entity entity) {
        if (entity != null && eligible()) {
            kill(KillBanners.DETECTOR.died(entity.getId(), System.nanoTime()));
        }
    }

    public static void chat(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (message == null || !eligible() || minecraft.player == null) return;
        Set<String> me = names(minecraft.player);
        String text = null;
        if (message.getContents() instanceof TranslatableContents death) {
            List<String> args = new ArrayList<>();
            for (Object arg : death.getArgs()) args.add(arg instanceof Component part ? part.getString() : String.valueOf(arg));
            text = KillDetector.deathLine(death.getKey(), args);
        }
        if (text == null) text = message.getString();
        if (KillDetector.myDeath(text, me)) { BANNER.endStreak(); return; } // the player's own death is never also a kill
        kill(KillBanners.DETECTOR.chat(text, me, NativeKillBanner1201::players, System.nanoTime()));
    }

    /** The other players in the world, and in the tab list those the world does not hold (out of sight, already gone). */
    private static Map<Integer, Set<String>> players() {
        Map<Integer, Set<String>> players = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player != mc.player) players.put(player.getId(), names(player));
            }
            if (mc.getConnection() != null) for (var info : mc.getConnection().getOnlinePlayers()) {
                java.util.UUID id = info.getProfile().getId();
                if (id == null || id.equals(mc.player.getUUID()) || mc.level.getPlayerByUUID(id) != null) continue;
                players.put(KillDetector.tabId(id), KillDetector.names(info.getProfile().getName(),
                    info.getTabListDisplayName() != null ? info.getTabListDisplayName().getString() : null));
            }
        }
        return players;
    }

    private static void kill(KillDetector.Kill kill) {
        KillBannerModule module = module();
        if (kill == null || module == null) return;
        com.thelads.core.v1_20_1.hud.Hud1201.LOG.info("Lads killbanner: kill detected ({})", kill);
        play(module, KillBanners.fire(module, kill, System.nanoTime()));
    }

    /** The living entity a hit lands on: a multipart mob for its parts (the Ender Dragon's, Forge's PartEntity). */
    private static LivingEntity victim(Entity entity) {
        if (entity instanceof EnderDragonPart part) return part.parentMob;
        if (KillDetector.parentOf(entity) instanceof LivingEntity mob) entity = mob;
        return entity instanceof LivingEntity living && !(living instanceof ArmorStand) && living != Minecraft.getInstance().player ? living : null;
    }

    private static KillDetector.Kind kind(LivingEntity entity) {
        if (entity instanceof Player) return KillDetector.Kind.PLAYER;
        return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof Warden || entity instanceof ElderGuardian
            ? KillDetector.Kind.BOSS : KillDetector.Kind.MOB;
    }

    /** A player's account name and the names the tab list gives them (the local player's /nick on Hypixel is only there). */
    private static Set<String> names(LivingEntity entity) {
        if (!(entity instanceof Player player)) return Set.of();
        var conn = Minecraft.getInstance().getConnection();
        var info = conn != null ? conn.getPlayerInfo(player.getUUID()) : null;
        Set<String> names = KillDetector.names(player.getGameProfile().getName(),
            info != null && info.getTabListDisplayName() != null ? info.getTabListDisplayName().getString() : player.getDisplayName().getString());
        if (info != null && info.getProfile().getName() != null && !info.getProfile().getName().isEmpty()) names.add(info.getProfile().getName());
        return names;
    }

    private static boolean eligible() {
        Minecraft minecraft = Minecraft.getInstance();
        KillBannerModule mod = module();
        return mod != null && mod.isEnabled() && minecraft.level != null && minecraft.player != null
            && minecraft.getConnection() != null && minecraft.getConnection().getConnection().isConnected();
    }

    static void reset() {
        trackedConnection = null;
        trackedLevel = null;
        KillBanners.reset();
    }

    private static void play(KillBannerModule module, String sound) {
        if (sound == null) return;
        com.thelads.core.v1_20_1.hud.Hud1201.LOG.info("Lads killbanner: banner shown, sound '{}' at volume {}", sound.isEmpty() ? "(base chime)" : sound, module.volume.getValue());
        SoundEvent event = sound.isEmpty() ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvent.createVariableRangeEvent(new ResourceLocation(sound));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, KillBanners.volume(sound, module.volume.getValue())));
    }

    /** A preview or QA banner: kills 1 to 5, with the module's pick (skin and variant) and its sound. */
    public static void trigger(int kills, boolean preview, boolean headshot) {
        KillBannerModule module = module();
        if (module != null) trigger(kills, preview, headshot, module.chosen());
    }

    public static void trigger(int kills, boolean preview, boolean headshot, KillBannerModule.Pick pick) {
        KillBannerModule module = module();
        if (!eligible() || module == null) return;
        play(module, KillBanners.show(module, kills, preview, headshot, pick, System.nanoTime()));
    }

    /** QA: binds the current connection now, so the next tick does not reset a banner fired on purpose. */
    public static void bindCurrent() {
        if (!eligible()) return;
        trackedConnection = Minecraft.getInstance().getConnection();
        trackedLevel = Minecraft.getInstance().level;
    }

    /** Kill Banner picker art (LadsGraphics.drawKillBanner). */
    public static boolean drawThumb(GuiGraphics g, String skin, int variant, int x, int y, int w, int h) {
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            KillBannerArt1201.thumb(g, KillBannerStyle.fromId(skin), variant, x, y, w, h);
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /** The Kill Banner settings preview: the skin's banners for 1 to 5 kills in turn, {@code clock} seconds into the loop. */
    public static boolean drawPreview(GuiGraphics g, String skin, int variant, int x, int y, int w, int h, double clock) {
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            KillBannerArt1201.preview(g, KillBannerStyle.fromId(skin), variant, x, y, w, h, clock);
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /** Kill Banner picker: a skin's one-kill sound. */
    public static void previewSound(String skin, float volume) {
        KillBannerStyle style = KillBannerStyle.fromId(skin);
        SoundEvent event = SoundEvent.createVariableRangeEvent(new ResourceLocation("theladscore", style.id + "_kill_1"));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, volume));
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        KillBannerModule module = module();
        if (!eligible() || minecraft.options.hideGui || module == null) return;
        double age = BANNER.age(System.nanoTime());
        if (age < 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        try {
            KillBannerModule.Pick pick = KillBanners.shown(module);
            KillBannerStyle style = pick != null ? pick.style() : null;
            // Above an action bar that showed over it as it started, for its whole animation (KillBanners.lift).
            graphics.pose().translate(0, -KillBanners.lift(new com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201(graphics), style,
                (float) module.size.getValue() / 100f), 0);
            if (style == null) {
                renderBase(graphics, minecraft, module, age);
                return;
            }
            boolean headshot = BANNER.headshot(), mark = headshot && module.killMark.get(), label = module.headshotBanner.get();
            float size = (float) module.size.getValue() / 100f;
            int variant = pick != null ? pick.variant() : 0;
            if (style.isAnimated()) {
                KillBannerStrip strip = style.strip(BANNER.sequence());
                KillBannerPlayer.Frame frame = KillBannerPlayer.at(style, strip, age, module.duration.getValue(), headshot, BANNER.cutAge());
                if (frame != null) KillBannerArt1201.draw(graphics, style, variant, BANNER.sequence(), strip, frame, size, mark, label);
            } else {
                KillBannerPlayer.Layers layers = KillBannerPlayer.layers(style, BANNER.sequence(), age, module.duration.getValue(), headshot, BANNER.cutAge());
                if (layers != null) KillBannerArt1201.draw(graphics, style, variant, BANNER.sequence(), layers, size, mark, label);
            }
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static void renderBase(GuiGraphics graphics, Minecraft minecraft, KillBannerModule module, double age) {
        double opacity = KillBannerTimeline.opacity(age, module.duration.getValue());
        if (opacity <= 0) return;
        if (lastLabelDelta != BANNER.sequence() || lastLabelPreview != BANNER.preview() || label.isEmpty()) {
            lastLabelDelta = BANNER.sequence();
            lastLabelPreview = BANNER.preview();
            label = BANNER.preview() ? "PREVIEW" : BANNER.sequence() == 1 ? "KILL" : BANNER.sequence() + " KILLS";
        }
        int sourceWidth = 256, sourceHeight = 64;
        int height = Math.round(74 * (float) module.size.getValue() / 100f);
        int width = Math.round(height * (float) sourceWidth / sourceHeight);
        float entrance = (float) (1 - Math.pow(1 - Math.min(1, age / .22), 3));
        float scale = .85f + .15f * entrance;
        float alpha = (float) Math.max(0, Math.min(1, opacity));
        int chosenColor = module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor();
        int textAlpha = (int) Math.round(((chosenColor >>> 24) & 255) * opacity);

        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(graphics.guiWidth() / 2f, Math.max(12, graphics.guiHeight() - 80 - height) + (1 - entrance) * 8, 0);
            pose.scale(scale, scale, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            graphics.blit(BASE, -width / 2, 0, 0, 0, width, height, sourceWidth, sourceHeight);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.drawString(minecraft.font, label, -minecraft.font.width(label) / 2, height + 3,
                textAlpha << 24 | (chosenColor & 0xffffff), true);
        } finally {
            pose.popPose();
        }
    }
}

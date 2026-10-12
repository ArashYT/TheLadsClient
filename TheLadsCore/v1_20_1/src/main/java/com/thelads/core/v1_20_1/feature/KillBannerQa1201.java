package com.thelads.core.v1_20_1.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBannerTemplate;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.KillBannerModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the script verb {@code kbcap [skin id,...]} holds the Kill Banner at the same frames as the 26.3 capture (KillBannerCapture
 * in v26_3: Base, Reaver, Rogue, ... for 1, 3 and 5 kills), crops each to the banner and writes screenshots/kb172/kb-SKIN-vN-kN-fNNN.png,
 * so a 1.20.1 frame can be compared pixel by pixel with the 26.3 frame of the same name (same window size).
 */
final class KillBannerQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private record Shot(KillBannerStyle style, int variant, int kills, boolean headshot, int frame, double seconds) {}
    private static List<Shot> shots;
    private static final List<JsonElement> OPTIONS_BEFORE = new ArrayList<>();
    private static boolean enabledBefore;
    private static int step = -1, wait, saved, failed;

    private KillBannerQa1201() {}

    static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null) {
            QaProbe1201.registerStep("kbcap", KillBannerQa1201::tick);
            QaProbe1201.registerStep("aim", KillBannerQa1201::aim);
            QaProbe1201.registerStep("kb191", KillBanner191Qa1201::tick);
        }
    }

    /**
     * {@code aim head|body|back [type]} or {@code aim part <name> [type]}: the nearest living mob whose id contains {@code type}
     * (32 blocks), seen from 1.6 blocks in front of it (in front of the part, for a part) through a server /tp with a rotation:
     * the crosshair on its eyes, a quarter up its body, the back half of its top (looked down at from the front), or the middle
     * of the part its mob holds in the field {@code name} (Forge PartEntity mobs, the dragon). A {@code click left} then hits it
     * there; NativeKillBanner1201 logs the verdict ("Lads killbanner QA hit").
     */
    private static boolean aim(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        String mode = args[1].toLowerCase(java.util.Locale.ROOT), name = mode.equals("part") ? args[2] : null;
        String type = args.length > (name != null ? 3 : 2) ? args[name != null ? 3 : 2].toLowerCase(java.util.Locale.ROOT) : "";
        LivingEntity mob = null;
        for (Entity entity : mc.level.entitiesForRendering())
            if (entity instanceof LivingEntity living && living != mc.player && living.isAlive() && mc.player.distanceTo(living) < 32
                && EntityType.getKey(living.getType()).toString().contains(type)
                && (mob == null || mc.player.distanceToSqr(living) < mc.player.distanceToSqr(mob))) mob = living;
        if (mob == null) throw new IllegalStateException("aim: no living " + type + " within 32 blocks");
        AABB box = mob.getBoundingBox();
        double yaw = Math.toRadians(mob.yBodyRot), facingX = -Math.sin(yaw), facingZ = Math.cos(yaw);
        if (name != null) {
            Entity part = null;
            for (Entity candidate : parts(mob)) if (KillDetector.partName(mob, candidate).equalsIgnoreCase(name)
                || candidate instanceof net.minecraft.world.entity.boss.EnderDragonPart dragon && dragon.name.equalsIgnoreCase(name)) part = candidate;
            if (part == null) throw new IllegalStateException("aim: " + EntityType.getKey(mob.getType()) + " has no part " + name);
            double dx = part.getBoundingBox().getCenter().x - box.getCenter().x, dz = part.getBoundingBox().getCenter().z - box.getCenter().z;
            if (dx * dx + dz * dz > .01) { double length = Math.sqrt(dx * dx + dz * dz); facingX = dx / length; facingZ = dz / length; }
            box = part.getBoundingBox();
        }
        double half = Math.abs(facingX) >= Math.abs(facingZ) ? box.getXsize() / 2 : box.getZsize() / 2;
        Vec3 centre = box.getCenter();
        // The face toward the player, 5 cm in; "back": the top, a quarter of the box behind the middle.
        Vec3 point = switch (mode) {
            case "head" -> new Vec3(centre.x + facingX * (half - .05), Math.min(mob.getEyeY(), box.maxY - .05), centre.z + facingZ * (half - .05));
            case "back" -> new Vec3(centre.x - facingX * half / 2, box.maxY - .02, centre.z - facingZ * half / 2);
            default -> new Vec3(centre.x + facingX * (half - .05), name != null ? centre.y : box.minY + box.getYsize() * .25, centre.z + facingZ * (half - .05));
        };
        double standX = centre.x + facingX * (half + 1.6), standZ = centre.z + facingZ * (half + 1.6), standY = mob.getBoundingBox().minY;
        double eyeY = standY + mc.player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING);
        double dx = point.x - standX, dy = point.y - eyeY, dz = point.z - standZ;
        float lookYaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), lookPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        LOGGER.info("Lads qa aim {}{} at {} (box {}, eyes {}, body yaw {}): point {}, from ({}, {}, {}) yaw {} pitch {}", mode, name != null ? " " + name : "",
            EntityType.getKey(mob.getType()), box, mob.getEyeY(), mob.yBodyRot, point, standX, standY, standZ, lookYaw, lookPitch);
        mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.4f %.4f %.4f %.3f %.3f", standX, standY, standZ, lookYaw, lookPitch));
        return true;
    }

    /** A multipart mob's parts: the dragon's, or Forge's getParts() (PartEntity mobs under Connector). */
    private static List<Entity> parts(LivingEntity mob) {
        if (mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) return Arrays.asList(dragon.getSubEntities());
        try {
            Object parts = mob.getClass().getMethod("getParts").invoke(mob);
            return parts instanceof Entity[] array ? Arrays.asList(array) : List.of();
        } catch (ReflectiveOperationException | RuntimeException none) {
            return List.of();
        }
    }

    private static KillBannerModule module() { return (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner"); }

    private static List<Shot> shots(String[] filter) {
        List<Shot> all = new ArrayList<>();
        Object[][] skins = {{KillBannerStyle.DEFAULT, 0}, {KillBannerStyle.REAVER, 0}, {KillBannerStyle.ROGUE, 0}, {KillBannerStyle.AEMONDIR, 0},
            {KillBannerStyle.CHAMPIONS2024, 0}, {KillBannerStyle.PHASEGUARD, 1}, {KillBannerStyle.ONI, 0}, {KillBannerStyle.VCT, 0},
            {KillBannerStyle.BOLT, 0}, {KillBannerStyle.GLITCHPOP, 0}, {KillBannerStyle.XEROFANG, 2}};
        for (Object[] skin : skins)
            for (int kills : new int[] {1, 3, 5}) sequence(all, (KillBannerStyle) skin[0], (int) skin[1], kills);
        sequence(all, KillBannerStyle.AEMONDIR, 2, 3);
        all.add(new Shot(KillBannerStyle.AEMONDIR, 0, 1, true, 30, 3));
        all.add(new Shot(KillBannerStyle.REAVER, 0, 1, true, 30, 3));
        all.add(new Shot(KillBannerStyle.CHAMPIONS2024, 0, 1, true, 30, 3));
        if (filter.length == 0) return all;
        return all.stream().filter(s -> Arrays.stream(filter).anyMatch(f -> s.style().id.equals(f))).toList();
    }

    /** The banner's frames: its opening, (an ace's turn), settled, and three moments of its way out (as v26_3's capture). */
    private static void sequence(List<Shot> out, KillBannerStyle style, int variant, int kills) {
        double seconds = kills == 5 ? 5 : 3;
        int total = (int) Math.round(seconds * 60), intro, exit;
        if (style.isAnimated()) {
            var strip = style.strip(kills);
            intro = strip.introEnd + 1;
            exit = (int) Math.round(KillBannerPlayer.minimumSeconds(strip) * 60) - intro;
        } else {
            intro = KillBannerTemplate.of(style, kills).introEnd + 1;
            exit = KillBannerTemplate.of(style, kills).exit;
        }
        List<Integer> frames = new ArrayList<>(Arrays.asList(2, 5, 9, 12, 16, 22, 30, 40, 52, 66, 80));
        if (kills == 5) frames.addAll(Arrays.asList(110, 140, 160, 185, 200));
        frames.add(intro + 10);
        for (float at : new float[] {.2f, .5f, .8f}) frames.add(total - exit + Math.round(exit * at));
        for (int frame : frames) out.add(new Shot(style, variant, kills, false, frame, seconds));
    }

    private static boolean tick(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (step < 0) {
                shots = shots(args.length > 1 ? args[1].split(",") : new String[0]);
                KillBannerModule module = module();
                enabledBefore = module.isEnabled();
                OPTIONS_BEFORE.clear();
                for (Option option : module.getOptions()) OPTIONS_BEFORE.add(option.save());
                mc.setScreen(null);
                LOGGER.info("Lads kill banner capture BEGIN: {} frames", shots.size());
                step = 0;
                fire();
                return false;
            }
            if (wait-- > 0) return false;
            Shot s = shots.get(step);
            String name = String.format("kb172/kb-%s-v%d-k%d%s-f%03d", s.style().id, s.variant(), s.kills(), s.headshot() ? "-hs" : "", s.frame());
            Path output = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                crop(image, output);
                saved++;
            } catch (Exception failure) {
                failed++;
                LOGGER.error("Lads kill banner capture FAILED: {}", name, failure);
            }
            if (++step < shots.size()) {
                fire();
                return false;
            }
            KillBannerModule module = module();
            for (int i = 0; i < OPTIONS_BEFORE.size(); i++) module.getOptions().get(i).load(OPTIONS_BEFORE.get(i));
            module.setEnabled(enabledBefore);
            KillBanners.TIMELINE.clear();
            LOGGER.info("Lads kill banner capture END: {} passed, {} failed", saved, failed);
            return true;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void fire() {
        KillBannerModule module = module();
        module.setEnabled(true);
        module.randomize.setIndex(KillBannerModule.RANDOM_OFF);
        Shot s = shots.get(step);
        module.sound.set(false);
        module.headshotBanner.set(true);
        module.bannerStyle.setIndex(KillBannerModule.styleIndexOf(s.style()));
        module.setVariant(s.style(), s.variant());
        module.duration.setValue(s.seconds());
        NativeKillBanner1201.bindCurrent();
        KillBanners.TIMELINE.clear(); // a fresh streak of exactly s.kills() kills
        NativeKillBanner1201.trigger(s.kills(), false, s.headshot());
        KillBanners.TIMELINE.freeze(s.frame() / 60.0 + 1 / 240.0); // a quarter frame in, as a 60 fps frame shows it
        wait = step == 0 || shots.get(step - 1).style() != s.style() ? 6 : 3; // the first frame of a skin loads its art
    }

    /** The banner's part of the frame: the ring centre sits at the middle, 79.4% down, and the banner scales with the height. */
    private static void crop(NativeImage image, Path output) throws Exception {
        int w = image.getWidth(), h = image.getHeight();
        int x0 = Math.round(w * .5f - h * .3f), y0 = Math.round(h * .594f), cw = Math.min(w - x0, Math.round(h * .6f)), ch = Math.min(h - y0, Math.round(h * .37f));
        try (NativeImage part = new NativeImage(cw, ch, false)) {
            for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) part.setPixelRGBA(x, y, image.getPixelRGBA(x0 + x, y0 + y));
            part.writeToFile(output);
        }
    }
}

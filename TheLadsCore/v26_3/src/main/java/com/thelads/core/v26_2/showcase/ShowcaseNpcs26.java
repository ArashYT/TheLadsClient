package com.thelads.core.v26_2.showcase;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.v26_2.cosmetics.anim.ShowcaseEmote26;
import com.thelads.core.v26_2.feature.NativeKillBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Showcase NPCs: client-only fake players the director places, walks, emotes and "kills" (npc* verbs). They exist only in the
 * recording client (never sent to a server); their skins come by name from the director's skins folder (ShowcaseEmote26.alias).
 */
final class ShowcaseNpcs26 {
    /** npcring takes the cast in this order: name, skin. */
    static final String[][] CAST = {
        {"SweatyGrandma69", "alex"}, {"DadLeftForMilk", "steve"}, {"TouchGrassNever", "kai"}, {"MomsCreditCard", "noor"},
        {"LagMachine9000", "efe"}, {"ThiccVillager", "makena"}, {"ToiletWarrior", "zuri"}, {"NotABot_Trust", "ari"},
        {"KevinFromHR", "sunny"}, {"SkillIssue_Lol", "steve"}, {"DiaperDestroyer", "kai"}, {"YoMamaPvP", "alex"},
        {"NoMaidens_", "efe"}, {"CryBaby_PvP", "sunny"}, {"UncleCreepy", "zuri"}};

    private static final Map<String, Npc> NPCS = new LinkedHashMap<>();
    private static int nextId = -1_000_000;

    private static final class Npc {
        final RemotePlayer entity;
        Vec3 from, to;
        long walkStart, walkEnd;
        int dying = -1; // ticks since npckill, -1 = alive

        Npc(RemotePlayer entity) { this.entity = entity; }
    }

    private ShowcaseNpcs26() {}

    /** Add or move an NPC at a world position; a new one gets its skin by name. Client thread. */
    static void put(Minecraft mc, String name, String skin, double x, double y, double z, float yaw) {
        if (mc.level == null) return;
        Npc npc = NPCS.get(key(name));
        if (npc == null || npc.entity.isRemoved()) {
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            ShowcaseEmote26.alias(name, skin);
            RemotePlayer entity = new RemotePlayer(mc.level, new GameProfile(id, name));
            entity.setId(nextId--);
            entity.snapTo(x, y, z, yaw, 0f);
            npc = new Npc(entity);
            NPCS.put(key(name), npc);
            mc.level.addEntity(entity);
        } else {
            npc.entity.snapTo(x, y, z, yaw, 0f);
            npc.walkEnd = 0;
        }
        turn(npc.entity, yaw, 0f);
    }

    /** n cast NPCs in a circle of radius r around (cx, y, cz), each facing the center. */
    static void ring(Minecraft mc, int n, double r, double cx, double y, double cz) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double x = cx + r * Math.sin(a), z = cz + r * Math.cos(a);
            String[] who = CAST[i % CAST.length];
            String name = i < CAST.length ? who[0] : who[0].substring(0, Math.min(13, who[0].length())) + (i / CAST.length + 1);
            put(mc, name, who[1], x, y, z, yawTowards(x, z, cx, cz));
        }
    }

    static List<String> names(String target) {
        if (!"*".equals(target)) {
            if (!NPCS.containsKey(key(target))) throw new IllegalArgumentException("No NPC named " + target);
            return List.of(target);
        }
        List<String> all = new ArrayList<>();
        for (Npc npc : NPCS.values()) all.add(npc.entity.getPlainTextName());
        return all;
    }

    static void emote(String name, String emoteId) { Emotes.playLocal(npc(name).entity.getUUID(), emoteId); }

    static void stop(String name) { Emotes.stopLocal(npc(name).entity.getUUID()); }

    static void look(String name, float yaw, float pitch) { turn(npc(name).entity, yaw, pitch); }

    static void face(Minecraft mc, String name) {
        RemotePlayer e = npc(name).entity;
        if (mc.player != null) turn(e, yawTowards(e.getX(), e.getZ(), mc.player.getX(), mc.player.getZ()), 0f);
    }

    static void walk(String name, double x, double z, long ms) {
        Npc npc = npc(name);
        npc.from = npc.entity.position();
        npc.to = new Vec3(x, npc.from.y, z);
        npc.walkStart = System.currentTimeMillis();
        npc.walkEnd = npc.walkStart + Math.max(1, ms);
        turn(npc.entity, yawTowards(npc.from.x, npc.from.z, x, z), 0f);
    }

    static void item(String name, String itemId) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        npc(name).entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    static void swing(String name) { npc(name).entity.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false); }

    static void sneak(String name, boolean on) { npc(name).entity.setShiftKeyDown(on); }

    /** The local player kills it: hurt flash and sound, then the death fall; Kill Banner counts it as the player's kill. */
    static void kill(Minecraft mc, String name) {
        Npc npc = npc(name);
        RemotePlayer e = npc.entity;
        if (npc.dying >= 0) return;
        NativeKillBanner.attacked(e); // the crosshair says whether it was a head hit
        e.animateHurt(mc.player == null ? 0f : mc.player.getYRot());
        e.setHealth(0f);
        npc.dying = 0;
        Emotes.stopLocal(e.getUUID());
        mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f, false);
        mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1f, 1f, false);
        NativeKillBanner.died(e);
    }

    static void clear(Minecraft mc) {
        for (Npc npc : NPCS.values()) {
            Emotes.stopLocal(npc.entity.getUUID());
            if (mc.level != null && !npc.entity.isRemoved()) mc.level.removeEntity(npc.entity.getId(), Entity.RemovalReason.DISCARDED);
        }
        NPCS.clear();
    }

    /** Client tick: walking NPCs move (interpolated, so their legs animate); dead ones vanish after the vanilla 20-tick fall (players never poof). */
    static void tick(Minecraft mc) {
        if (NPCS.isEmpty() || mc.level == null) return;
        long now = System.currentTimeMillis();
        for (var it = NPCS.values().iterator(); it.hasNext(); ) {
            Npc npc = it.next();
            if (npc.entity.isRemoved()) { it.remove(); continue; }
            if (npc.walkEnd > 0) {
                double t = Math.min(1.0, (now - npc.walkStart) / (double) (npc.walkEnd - npc.walkStart));
                npc.entity.moveOrInterpolateTo(PositionPath.of(npc.from.lerp(npc.to, t)), npc.entity.getYRot(), npc.entity.getXRot());
                if (t >= 1.0) npc.walkEnd = 0;
            }
            if (npc.dying >= 0 && ++npc.dying >= 20) {
                mc.level.removeEntity(npc.entity.getId(), Entity.RemovalReason.KILLED);
                it.remove();
            }
        }
    }

    private static Npc npc(String name) {
        Npc npc = NPCS.get(key(name));
        if (npc == null) throw new IllegalArgumentException("No NPC named " + name);
        return npc;
    }

    private static void turn(RemotePlayer e, float yaw, float pitch) {
        e.setYRot(yaw); e.yRotO = yaw;
        e.setYHeadRot(yaw); e.yHeadRotO = yaw;
        e.setYBodyRot(yaw); e.yBodyRotO = yaw;
        e.setXRot(pitch); e.xRotO = pitch;
    }

    /** Minecraft yaw that looks from (x, z) toward (tx, tz). */
    private static float yawTowards(double x, double z, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}

package com.thelads.core.v26_2.feature;

import com.thelads.core.client.PredictionMatcher;
import com.thelads.core.modules.ExtraModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mace / Sword / Pearl Optimizer on 26.3: plays the sound, particles or pearl a moment before the server's packet and drops
 * the server's duplicate once (PredictionMatcher). Nothing is sent to the server; damage, reach, cooldown and hit registration
 * stay server-driven. Called from the attack / packet / pearl mixins, client thread only.
 */
public final class PredictionFx26 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static final int PEARL_LIFE_TICKS = 30;
    private static final long PEARL_MATCH_MS = 1000;
    private static final double PEARL_MATCH_RADIUS = 3.0;
    private static final int LEVEL_EVENT_SMASH = 2013;
    private static boolean logged;
    private static final boolean DEBUG = Boolean.getBoolean("lads.predictDebug") || Boolean.getBoolean("lads.predictdebug");

    private static PredictedPearl pearl;
    private static int nextFakeId = -1;

    private PredictionFx26() {}

    private static void logOnce() {
        if (!logged) {
            logged = true;
            LOG.info("[Lads] Prediction modules active: Mace Optimizer + Sword Optimizer (MultiPlayerGameMode.attack), Pearl Optimizer (EnderpearlItem.use); server sound / level-event / pearl packets matched in ClientPacketListener");
        }
    }

    /** Local player's attack, before vanilla sends it. Hooked: MultiPlayerGameMode.attack HEAD. */
    public static void attack(Player player, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!mc.isSameThread() || level == null || player != mc.player
                || player.isSpectator() || !target.isAttackable() || target instanceof Projectile) return;
        if (target instanceof LivingEntity living && (living.isDeadOrDying() || living.getInvulnerableTime() > 10)) return;
        logOnce();
        long now = System.currentTimeMillis();
        boolean smash = ExtraModules.MACE_OPTIMIZER.isEnabled() && target instanceof LivingEntity
                && player.getMainHandItem().getItem() instanceof MaceItem && MaceItem.canSmashAttack(player);
        if (smash) {
            SoundEvent s = target.onGround() ? (player.fallDistance > 5.0 ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND)
                    : SoundEvents.MACE_SMASH_AIR;
            play(level, player, s, now);
            BlockPos pos = target.getOnPos();
            level.levelEvent(null, LEVEL_EVENT_SMASH, pos, 750);
            PredictionMatcher.SHARED.record("level_event.2013", pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, now);
        }
        if (ExtraModules.SWORD_OPTIMIZER.isEnabled()) {
            if (ExtraModules.SWORD_OPTIMIZER.hitSound.get()) {
                play(level, player, attackSound(player, target), now);
            }
            if (ExtraModules.SWORD_OPTIMIZER.hurtFlash.get() && target instanceof LivingEntity living && living != player
                    && living.hurtTime == 0 && !living.isInvulnerable()
                    && !(living instanceof Player p && p.isCreative())) {
                living.hurtTime = 10;
                living.hurtDuration = 10;
            }
        }
    }

    /** Same branches as Player.attack's sound choice. */
    private static SoundEvent attackSound(Player p, Entity target) {
        boolean strong = p.getAttackStrengthScale(0.5f) > 0.9f;
        boolean knockback = p.isSprinting() && strong;
        boolean crit = strong && p.fallDistance > 0.0 && !p.onGround() && !p.onClimbable() && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS) && !p.isPassenger() && target instanceof LivingEntity && !p.isSprinting();
        if (knockback) return SoundEvents.PLAYER_ATTACK_KNOCKBACK;
        if (crit) return SoundEvents.PLAYER_ATTACK_CRIT;
        boolean sweep = strong && p.onGround() && p.getMainHandItem().is(ItemTags.SWORDS)
                && p.getKnownMovement().horizontalDistanceSqr() < Math.pow(p.getSpeed() * 2.5, 2);
        if (sweep) return SoundEvents.PLAYER_ATTACK_SWEEP;
        return strong ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK;
    }

    private static void play(ClientLevel level, Player p, SoundEvent s, long now) {
        if (DEBUG) LOG.info("[Lads] predict: played {}", s.location());
        level.playLocalSound(p.getX(), p.getY(), p.getZ(), s, p.getSoundSource(), 1.0f, 1.0f, false);
        PredictionMatcher.SHARED.record(s.location().toString(), p.getX(), p.getY(), p.getZ(), now);
    }

    /** True when a sound packet is the duplicate of one just predicted. Hooked: ClientPacketListener.handleSoundEvent HEAD. */
    public static boolean suppressSound(SoundEvent s, double x, double y, double z) {
        if (!Minecraft.getInstance().isSameThread()) return false; // the netty-thread pass; the main-thread re-run decides
        boolean drop = PredictionMatcher.SHARED.shouldSuppress(s.location().toString(), x, y, z, System.currentTimeMillis());
        if (DEBUG) LOG.info("[Lads] predict: server sound {} {}", s.location(), drop ? "dropped (duplicate)" : "passed");
        return drop;
    }

    /** Hooked: ClientPacketListener.handleLevelEvent HEAD. */
    public static boolean suppressLevelEvent(int type, BlockPos pos) {
        if (type != LEVEL_EVENT_SMASH || !Minecraft.getInstance().isSameThread()) return false;
        return PredictionMatcher.SHARED.shouldSuppress("level_event.2013", pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, System.currentTimeMillis());
    }

    /** Local player used an ender pearl; hooked: EnderpearlItem.use HEAD (client level only). */
    public static void throwPearl(Level level, Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (!ExtraModules.PEARL_OPTIMIZER.isEnabled() || !(level instanceof ClientLevel cl) || player != mc.player || !mc.isSameThread()) return;
        ItemStack stack = player.getMainHandItem().is(Items.ENDER_PEARL) ? player.getMainHandItem() : player.getOffhandItem();
        if (!stack.is(Items.ENDER_PEARL) || player.getCooldowns().isOnCooldown(stack)) return;
        logOnce();
        removePearl();
        PredictedPearl p = new PredictedPearl(cl, player, stack.copyWithCount(1));
        p.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.5f, 0.0f);
        p.setId(nextFakeId);
        nextFakeId = nextFakeId <= -1_000_000 ? -1 : nextFakeId - 1;
        pearl = p;
        cl.addEntity(p);
        if (DEBUG) LOG.info("[Lads] predict: fake pearl {} added", p.getId());
    }

    /** Real pearl spawn arrived: swap out the fake. Hooked: ClientPacketListener.handleAddEntity TAIL. */
    public static void onAddEntity(ClientboundAddEntityPacket packet) {
        PredictedPearl p = pearl;
        Minecraft mc = Minecraft.getInstance();
        if (p == null || packet.getType() != p.getType() || !mc.isSameThread()) return;
        if (p.isRemoved() || p.level() != mc.level) { pearl = null; return; }
        boolean mine = mc.player != null && packet.getData() == mc.player.getId();
        double dx = packet.getX() - p.startX, dy = packet.getY() - p.startY, dz = packet.getZ() - p.startZ;
        boolean near = System.currentTimeMillis() - p.bornMs <= PEARL_MATCH_MS && dx * dx + dy * dy + dz * dz <= PEARL_MATCH_RADIUS * PEARL_MATCH_RADIUS;
        if (DEBUG) LOG.info("[Lads] predict: real pearl packet id={} mine={} near={} -> {}", packet.getId(), mine, near, mine || near ? "fake removed" : "fake kept");
        if (mine || near) removePearl();
    }

    private static void removePearl() {
        PredictedPearl p = pearl;
        pearl = null;
        if (p != null && !p.isRemoved() && p.level() instanceof ClientLevel cl) cl.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
    }

    /** Client-only pearl: flies like the real one, never hits, never teleports, never targetable, gone after 30 ticks or on any hit. */
    private static final class PredictedPearl extends ThrownEnderpearl {
        final double startX, startY, startZ;
        final long bornMs = System.currentTimeMillis();
        private int age;

        PredictedPearl(ClientLevel level, Player owner, ItemStack stack) {
            super(level, owner, stack);
            startX = getX();
            startY = getY();
            startZ = getZ();
        }

        @Override public void tick() {
            if (++age > PEARL_LIFE_TICKS || level() != Minecraft.getInstance().level) { discard(); return; }
            super.tick();
        }

        @Override protected void onHit(HitResult hit) { discard(); }

        @Override protected boolean canHitEntity(Entity entity) { return false; }

        @Override public boolean isPickable() { return false; }

        @Override public boolean isAttackable() { return false; }
    }
}

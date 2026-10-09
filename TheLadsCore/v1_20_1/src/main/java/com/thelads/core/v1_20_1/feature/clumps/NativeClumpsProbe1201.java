package com.thelads.core.v1_20_1.feature.clumps;

import com.thelads.core.config.Module;
import com.thelads.core.v1_20_1.feature.clumps.api.events.ClumpsEvents;
import com.thelads.core.v1_20_1.feature.clumps.mixin.ExperienceOrbAccess;
import com.thelads.core.v1_20_1.perf.PerfOptions1201;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA probe (-Dthelads.perfProbe=true): drives the real XP orb code on the integrated server, once, about five seconds into a
 * singleplayer world with one local player. It spawns its own orbs in an empty volume above the player, removes them again
 * and restores the player's equipment, XP and the Clumps module switch. Logs {@code PERF-PROBE clumps PASS|FAIL ...}.
 */
public final class NativeClumpsProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Clumps");
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static boolean done;
    private static int ticks, passed;
    private static ServerPlayer eventPlayer;
    private static boolean multiplyValues, consumeRepair;

    private NativeClumpsProbe1201() {}

    static void initialize() {
        if (!Boolean.getBoolean("thelads.perfProbe")) return;
        ClumpsEvents.VALUE_EVENT.register(event -> {
            if (multiplyValues && event.getPlayer() == eventPlayer) event.setValue(event.getValue() * 2);
            return null;
        });
        ClumpsEvents.REPAIR_EVENT.register(event -> {
            if (consumeRepair && event.getPlayer() == eventPlayer) event.setValue(0);
            return null;
        });
        ServerTickEvents.END_SERVER_TICK.register(NativeClumpsProbe1201::tick);
    }

    private static void tick(MinecraftServer server) {
        if (done || !NativeClumps1201.active() || server.isDedicatedServer() || server.getPlayerList().getPlayers().size() != 1 || ++ticks < 110) return;
        done = true;
        if (!ExperienceOrbAccess.class.isAssignableFrom(ExperienceOrb.class)) {
            LOGGER.error("PERF-PROBE clumps FAIL the XP orb mixin was not applied to ExperienceOrb");
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        Module module = PerfOptions1201.module("Clumps");
        boolean enabled = module.isEnabled();
        long modified = module.getLastModified();
        int xp = player.totalExperience, xpLevel = player.experienceLevel, score = player.getScore(), delay = player.takeXpDelay;
        float progress = player.experienceProgress;
        EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) equipment.put(slot, player.getItemBySlot(slot).copy());
        java.util.function.BiPredicate<net.minecraft.world.entity.player.Player, ExperienceOrb> previousPickup = NativeClumps1201.pickupEvent;
        List<ExperienceOrb> owned = new ArrayList<>();
        boolean stateTouched = false;
        try {
            Vec3 position = player.position().add(4, 12, 4);
            AABB area = AABB.ofSize(position, 3, 3, 3);
            check(orbs(level, area).isEmpty(), "test volume contains no pre-existing XP orbs");
            stateTouched = true;
            for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, ItemStack.EMPTY);

            setEnabled(module, false);
            ExperienceOrb first = spawn(level, position, 5, owned), second = spawn(level, position.add(0.1, 0, 0), 7, owned);
            check(!access(first).ladsClumps$canMerge(second), "disabled engine keeps vanilla unequal-value separation");
            setEnabled(module, true);
            check(access(first).ladsClumps$canMerge(second) && !access(first).ladsClumps$canMerge(first), "native merging accepts mixed values but not the same entity");
            access(first).ladsClumps$age(100);
            access(second).ladsClumps$age(20);
            access(first).ladsClumps$scan();
            check(first.isAlive() && !second.isAlive() && first.getValue() == 12 && orbs(level, area).size() == 1, "actual server scan merges two orb entities without losing XP");
            check(((ClumpedOrb) first).ladsClumps$values().equals(Map.of(5, 1, 7, 1)) && access(first).ladsClumps$age() == 20, "original denominations and youngest age survive merging");

            ExperienceOrb.award(level, position, 13);
            for (ExperienceOrb orb : orbs(level, area)) if (!owned.contains(orb)) owned.add(orb);
            check(first.getValue() == 25 && orbs(level, area).size() == 1 && access(first).ladsClumps$age() == 0, "vanilla award path merges every split award and refreshes age");

            player.takeXpDelay = 20;
            int before = player.totalExperience;
            first.playerTouch(player);
            check(!first.isAlive() && player.totalExperience == before + 25 && player.takeXpDelay == 0, "touch collects all original XP immediately despite prior cooldown");
            first.playerTouch(player);
            check(player.totalExperience == before + 25, "discarded orb cannot be collected twice");

            ExperienceOrb stacked = spawn(level, position, 3, owned);
            access(stacked).ladsClumps$count(4);
            check(((ClumpedOrb) stacked).ladsClumps$values().equals(Map.of(3, 4)) && stacked.getValue() == 12 && access(stacked).ladsClumps$count() == 1,
                "adopting an existing vanilla stack preserves all four orb values");
            ExperienceOrb add = spawn(level, position.add(0.1, 0, 0), 2, owned);
            access(stacked).ladsClumps$merge(add);
            check(stacked.getValue() == 14 && !add.isAlive(), "adopted vanilla stack merges without count multiplication");
            stacked.discard();

            ExperienceOrb saved = spawn(level, position, 1, owned);
            ((ClumpedOrb) saved).ladsClumps$values(Map.of(30000, 2));
            CompoundTag data = saved.saveWithoutId(new CompoundTag());
            check(data.contains("clumpedMap") && data.getInt("Count") == 1, "saved aggregate includes the denomination map and a safe vanilla count");
            ExperienceOrb loaded = new ExperienceOrb(level, position.x, position.y, position.z, 0);
            loaded.load(data);
            check(loaded.getValue() == 60000 && ((ClumpedOrb) loaded).ladsClumps$values().equals(Map.of(30000, 2)), "native map round trip preserves XP beyond vanilla's short Value field");
            CompoundTag malformed = data.copy();
            CompoundTag badMap = new CompoundTag();
            badMap.putInt("not-a-value", 2);
            malformed.put("clumpedMap", badMap);
            loaded.load(malformed);
            check(!((ClumpedOrb) loaded).ladsClumps$managed(), "malformed extension data falls back without crashing entity loading");
            saved.discard();

            ExperienceOrb pending = spawn(level, position, 1, owned);
            ((ClumpedOrb) pending).ladsClumps$values(Map.of(3, 2, 7, 1));
            setEnabled(module, false);
            player.takeXpDelay = 2;
            before = player.totalExperience;
            pending.playerTouch(player);
            check(pending.isAlive() && player.totalExperience == before, "disabling restores the pickup delay for already merged orbs");
            int pickups = 0;
            while (pending.isAlive() && pickups++ < 4) {
                player.takeXpDelay = 0;
                pending.playerTouch(player);
            }
            check(!pending.isAlive() && player.totalExperience == before + 13 && pickups == 3, "disabled merged orb releases each original value once without duplication");

            setEnabled(module, true);
            ItemStack mending = new ItemStack(Items.DIAMOND_PICKAXE);
            mending.enchant(Enchantments.MENDING, 1);
            mending.setDamageValue(9);
            player.setItemSlot(EquipmentSlot.MAINHAND, mending);
            ExperienceOrb repair = spawn(level, position, 1, owned);
            ((ClumpedOrb) repair).ladsClumps$values(Map.of(3, 2));
            before = player.totalExperience;
            repair.playerTouch(player);
            check(mending.getDamageValue() == 0 && player.totalExperience == before + 2 && !repair.isAlive(), "per-original-orb Mending repairs nine damage and awards the correct leftover XP");
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

            eventPlayer = player;
            multiplyValues = true;
            ExperienceOrb doubled = spawn(level, position, 4, owned);
            before = player.totalExperience;
            doubled.playerTouch(player);
            check(player.totalExperience == before + 8, "relocated value event modifies the actual awarded XP");
            multiplyValues = false;
            consumeRepair = true;
            ExperienceOrb consumed = spawn(level, position, 4, owned);
            before = player.totalExperience;
            consumed.playerTouch(player);
            check(player.totalExperience == before && !consumed.isAlive(), "repair event can consume XP before vanilla Mending");
            consumeRepair = false;
            NativeClumps1201.pickupEvent = (p, orb) -> p == player;
            ExperienceOrb vetoed = spawn(level, position, 4, owned);
            before = player.totalExperience;
            vetoed.playerTouch(player);
            check(player.totalExperience == before && vetoed.isAlive(), "pickup veto prevents consumption and award");
            NativeClumps1201.pickupEvent = previousPickup;
            vetoed.discard();

            for (ExperienceOrb orb : owned) if (orb.isAlive()) orb.discard();
            check(orbs(level, area).isEmpty(), "all probe XP entities removed after the checks");
            LOGGER.info("PERF-PROBE clumps PASS checks={} failed=0", passed);
        } catch (Throwable failure) {
            LOGGER.error("PERF-PROBE clumps FAIL after {} passed checks: {}", passed, failure.getMessage(), failure);
        } finally {
            for (ExperienceOrb orb : owned) if (orb.isAlive()) orb.discard();
            multiplyValues = false;
            consumeRepair = false;
            eventPlayer = null;
            NativeClumps1201.pickupEvent = previousPickup;
            if (stateTouched) {
                for (EquipmentSlot slot : SLOTS) player.setItemSlot(slot, equipment.get(slot));
                player.totalExperience = xp;
                player.experienceLevel = xpLevel;
                player.experienceProgress = progress;
                player.setScore(score);
                player.takeXpDelay = delay;
                module.setEnabled(enabled);
                module.setLastModified(modified);
                NativeClumps1201.refresh();
            }
        }
    }

    private static ExperienceOrbAccess access(ExperienceOrb orb) { return (ExperienceOrbAccess) orb; }

    private static List<ExperienceOrb> orbs(ServerLevel level, AABB area) {
        return level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), area, ExperienceOrb::isAlive);
    }

    private static ExperienceOrb spawn(ServerLevel level, Vec3 position, int value, List<ExperienceOrb> owned) {
        ExperienceOrb orb = new ExperienceOrb(level, position.x, position.y, position.z, value);
        orb.setNoGravity(true);
        orb.setDeltaMovement(Vec3.ZERO);
        orb.addTag("lads_clumps_probe");
        owned.add(orb);
        if (!level.addFreshEntity(orb)) throw new IllegalStateException("Cannot spawn probe orb");
        return orb;
    }

    private static void setEnabled(Module module, boolean enabled) {
        module.setEnabled(enabled);
        NativeClumps1201.refresh();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
        passed++;
    }
}

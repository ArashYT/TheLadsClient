package com.thelads.core.v26_2.feature.clumps;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.clumps.api.events.ClumpsEvents;
import com.thelads.core.v26_2.feature.clumps.mixin.ExperienceOrbAccess;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Real server entity paths, limited to the exact existing isolated QA save and its single local player. */
public final class NativeClumpsProbe {
    private static boolean done;
    private static int ticks, passed;
    private static ServerPlayer eventPlayer;
    private static boolean multiplyValues, consumeRepair;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    public static void initialize() {
        if (!Boolean.getBoolean("thelads.verifyIntegrations")) return;
        ClumpsEvents.VALUE_EVENT.register(event -> { if (multiplyValues && event.getPlayer() == eventPlayer) event.setValue(event.getValue() * 2); return null; });
        ClumpsEvents.REPAIR_EVENT.register(event -> { if (consumeRepair && event.getPlayer() == eventPlayer) event.setValue(0); return null; });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(NativeClumpsProbe::tick);
    }
    private static void tick(MinecraftServer server) {
        if (done || !NativeClumps.active() || server.isDedicatedServer() || server.getPlayerList().getPlayers().isEmpty() || ++ticks < 110) return;
        done = true;
        var player = server.getPlayerList().getPlayers().getFirst(); var level = player.level();
        var module = NativeQualityOfLife.module("Clumps"); boolean enabled = module.isEnabled(); long modified = module.getLastModified();
        int xp = player.totalExperience, xpLevel = player.experienceLevel, score = player.getScore(), delay = player.takeXpDelay;
        float progress = player.experienceProgress;
        var equipment = new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        for (var slot : SLOTS) equipment.put(slot,player.getItemBySlot(slot).copy());
        var previousPickup = NativeClumps.pickupEvent;
        var owned = new ArrayList<ExperienceOrb>();
        boolean stateTouched = false;
        try {
            Path game = FabricLoader.getInstance().getGameDir().toRealPath();
            Path world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            check(game.getFileName().toString().equals("26.2-title")
                    && game.getParent().getFileName().toString().equals("verification")
                    && game.getParent().getParent().getFileName().toString().equals("artifacts")
                    && Files.isRegularFile(game.getParent().getParent().getParent().resolve("TheLadsCore/settings.gradle"))
                    && world.equals(game.resolve("saves/Client QA 26_2").toRealPath())
                    && (world.startsWith(game) || com.thelads.core.shared.SharedContentPaths.redirectedInside(game.getParent())
                        && world.getParent().equals(com.thelads.core.shared.SharedContentPaths.savesDir().toRealPath()))
                    && server.getWorldData().getLevelName().equals("Client QA 26.2")
                    && server.getPlayerList().getPlayers().size() == 1, "exact isolated Client QA 26.2 save with one local player");
            Vec3 position = player.position().add(4,12,4);
            AABB area = AABB.ofSize(position,3,3,3);
            check(orbs(level,area).isEmpty(), "test volume contains no pre-existing XP orbs");
            stateTouched = true;
            for (var slot : SLOTS) player.setItemSlot(slot,ItemStack.EMPTY);
            setEnabled(false);
            var first = spawn(level,position,5,owned); var second = spawn(level,position.add(.1,0,0),7,owned);
            check(!((ExperienceOrbAccess) first).ladsClumps$canMerge(second), "disabled engine preserves vanilla unequal-value separation");
            setEnabled(true);
            check(((ExperienceOrbAccess) first).ladsClumps$canMerge(second)
                    && !((ExperienceOrbAccess) first).ladsClumps$canMerge(first), "native merging accepts mixed values but not the same entity");
            ((ExperienceOrbAccess) first).ladsClumps$age(100); ((ExperienceOrbAccess) second).ladsClumps$age(20);
            ((ExperienceOrbAccess) first).ladsClumps$scan();
            check(first.isAlive() && !second.isAlive() && first.getValue() == 12 && orbs(level,area).size() == 1,
                    "actual server scan merges two orb entities without losing XP");
            check(((ClumpedOrb) first).ladsClumps$values().equals(Map.of(5,1,7,1))
                    && ((ExperienceOrbAccess) first).ladsClumps$age() == 20, "original denominations and youngest age survive merging");
            ExperienceOrb.award(level,position,13);
            for (var orb : orbs(level,area)) if (!owned.contains(orb)) owned.add(orb);
            check(first.getValue() == 25 && orbs(level,area).size() == 1 && ((ExperienceOrbAccess) first).ladsClumps$age() == 0,
                    "vanilla award path merges every split award and refreshes age");
            player.takeXpDelay = 20; int before = player.totalExperience;
            first.playerTouch(player);
            check(!first.isAlive() && player.totalExperience == before + 25 && player.takeXpDelay == 0,
                    "touch collects all original XP immediately despite prior cooldown");
            first.playerTouch(player);
            check(player.totalExperience == before + 25, "discarded orb cannot be collected twice");

            var stacked = spawn(level,position,3,owned); ((ExperienceOrbAccess) stacked).ladsClumps$count(4);
            check(((ClumpedOrb) stacked).ladsClumps$values().equals(Map.of(3,4)) && stacked.getValue() == 12
                    && ((ExperienceOrbAccess) stacked).ladsClumps$count() == 1,
                    "adopting an existing vanilla stack preserves all four orb values");
            var add = spawn(level,position.add(.1,0,0),2,owned); ((ExperienceOrbAccess) stacked).ladsClumps$merge(add);
            check(stacked.getValue() == 14 && !add.isAlive(), "adopted vanilla stack merges without count multiplication");
            stacked.discard();

            var saved = spawn(level,position,1,owned); ((ClumpedOrb) saved).ladsClumps$values(Map.of(30000,2));
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess()); saved.saveWithoutId(output);
            CompoundTag data = output.buildResult();
            check(data.contains("clumpedMap") && data.getInt("Count").orElse(0) == 1, "saved aggregate includes full denomination map and safe vanilla count");
            var loaded = new ExperienceOrb(level,position.x,position.y,position.z,0); owned.add(loaded);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));
            check(loaded.getValue() == 60000 && ((ClumpedOrb) loaded).ladsClumps$values().equals(Map.of(30000,2)),
                    "native map round trip preserves XP beyond vanilla's short Value field");
            var malformed = data.copy(); var badMap = new CompoundTag(); badMap.putInt("not-a-value",2); malformed.put("clumpedMap",badMap);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),malformed));
            check(!((ClumpedOrb) loaded).ladsClumps$managed(), "malformed extension data falls back without crashing entity loading");
            saved.discard(); loaded.discard();

            var pending = spawn(level,position,1,owned); ((ClumpedOrb) pending).ladsClumps$values(Map.of(3,2,7,1));
            setEnabled(false); player.takeXpDelay=2; before=player.totalExperience;
            pending.playerTouch(player);
            check(pending.isAlive() && player.totalExperience == before, "disabling restores pickup delay for already merged orbs");
            int pickups=0;
            while (pending.isAlive() && pickups++ < 4) { player.takeXpDelay=0; pending.playerTouch(player); }
            check(!pending.isAlive() && player.totalExperience == before+13 && pickups==3,
                    "disabled merged orb releases each original value once without duplication");
            setEnabled(true);
            var mending = new ItemStack(Items.DIAMOND_PICKAXE);
            mending.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING),1);
            mending.setDamageValue(9); player.setItemSlot(EquipmentSlot.MAINHAND,mending);
            var repair = spawn(level,position,1,owned); ((ClumpedOrb) repair).ladsClumps$values(Map.of(3,2)); before=player.totalExperience;
            repair.playerTouch(player);
            check(mending.getDamageValue()==0 && player.totalExperience==before+2 && !repair.isAlive(),
                    "per-original-orb Mending repairs nine damage and awards the correct leftover XP");
            player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            eventPlayer=player; multiplyValues=true;
            var doubled=spawn(level,position,4,owned); before=player.totalExperience; doubled.playerTouch(player);
            check(player.totalExperience==before+8, "relocated value event modifies actual awarded XP");
            multiplyValues=false; consumeRepair=true;
            var consumed=spawn(level,position,4,owned); before=player.totalExperience; consumed.playerTouch(player);
            check(player.totalExperience==before && !consumed.isAlive(), "repair event can consume XP before vanilla Mending");
            consumeRepair=false; NativeClumps.pickupEvent=(p,orb)->p==player;
            var vetoed=spawn(level,position,4,owned); before=player.totalExperience; vetoed.playerTouch(player);
            check(player.totalExperience==before && vetoed.isAlive(), "pickup veto prevents consumption and award");
            NativeClumps.pickupEvent=previousPickup; vetoed.discard();
            for (var orb:owned) if (orb.isAlive()) orb.discard();
            check(orbs(level,area).isEmpty(), "all probe XP entities removed after actual server tests");
            LoggerFactory.getLogger("TheLadsCore").info("Lads clumps server probe END: {} passed, 0 failed",passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native feature probe FAILED: clumps server after {} checks",passed,failure);
        } finally {
            for (var orb:owned) if (orb.isAlive()) orb.discard();
            multiplyValues=false; consumeRepair=false; eventPlayer=null; NativeClumps.pickupEvent=previousPickup;
            if (stateTouched) {
                for (var slot:SLOTS) player.setItemSlot(slot,equipment.get(slot));
                player.totalExperience=xp; player.experienceLevel=xpLevel; player.experienceProgress=progress; player.setScore(score); player.takeXpDelay=delay;
                module.setEnabled(enabled); module.setLastModified(modified); NativeClumps.refresh();
            }
        }
    }
    private static List<ExperienceOrb> orbs(ServerLevel level,AABB area) { return level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class),area,ExperienceOrb::isAlive); }
    private static ExperienceOrb spawn(ServerLevel level,Vec3 position,int value,List<ExperienceOrb> owned) {
        var orb=new ExperienceOrb(level,position,Vec3.ZERO,value); orb.setNoGravity(true); orb.addTag("lads_clumps_probe");
        owned.add(orb); if (!level.addFreshEntity(orb)) throw new IllegalStateException("Cannot spawn probe orb"); return orb;
    }
    private static void setEnabled(boolean enabled) { NativeQualityOfLife.module("Clumps").setEnabled(enabled); NativeClumps.refresh(); }
    private static void check(boolean value,String message) { if(!value) throw new IllegalStateException(message); passed++; }
}

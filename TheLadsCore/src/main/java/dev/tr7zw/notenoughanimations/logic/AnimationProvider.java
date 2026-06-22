/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 */
package dev.tr7zw.notenoughanimations.logic;

import dev.tr7zw.notenoughanimations.NEAnimationsMod;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.animations.fullbody.ActionRotationLockAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.BurningAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.CrawlingAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.FallingAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.FreezingAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.HorseAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.LadderAnimation;
import dev.tr7zw.notenoughanimations.animations.fullbody.PassengerAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.BoatAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.ClampCrossbowAnimations;
import dev.tr7zw.notenoughanimations.animations.hands.CustomBowAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.EatDrinkAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.HugAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.ItemSwapAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.LookAtItemAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.MapHoldingAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.NarutoRunningAnimation;
import dev.tr7zw.notenoughanimations.animations.hands.PetAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.DeathAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.ElytraAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.RiptideAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.SleepAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.SwimAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.VanillaShieldAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.VanillaSingleHandedAnimation;
import dev.tr7zw.notenoughanimations.animations.vanilla.VanillaTwoHandedAnimation;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.api.PoseOverwrite;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;

public class AnimationProvider {
    private Set<BasicAnimation> basicAnimations = new HashSet<BasicAnimation>();
    private Set<BasicAnimation> enabledBasicAnimations = new HashSet<BasicAnimation>();
    private Set<PoseOverwrite> enabledPoseOverwrites = new HashSet<PoseOverwrite>();
    private boolean dumpPrios = false;

    public AnimationProvider() {
        this.loadAnimations();
        this.refreshEnabledAnimations();
    }

    public void applyAnimations(AbstractClientPlayer entity, PlayerModel model, float delta, float swing) {
        int i;
        PlayerData playerData = (PlayerData)entity;
        int[] priorities = new int[BodyPart.values().length];
        BasicAnimation[] animation = new BasicAnimation[priorities.length];
        for (BasicAnimation basicAnimation : this.enabledBasicAnimations) {
            int prio;
            if (!basicAnimation.isValid(entity, playerData) || (prio = basicAnimation.getPriority(entity, playerData)) <= 0) continue;
            for (BodyPart part : basicAnimation.getBodyParts(entity, playerData)) {
                if (prio <= priorities[part.ordinal()]) continue;
                priorities[part.ordinal()] = prio;
                animation[part.ordinal()] = basicAnimation;
            }
        }
        for (i = 0; i < priorities.length; ++i) {
            if (animation[i] == null) continue;
            animation[i].prepare(entity, playerData, model, delta, swing);
            animation[i].apply(entity, playerData, model, BodyPart.values()[i], delta, swing);
        }
        for (i = 0; i < priorities.length; ++i) {
            if (animation[i] == null) continue;
            animation[i].cleanup();
        }
    }

    public void preUpdate(AbstractClientPlayer livingEntity, PlayerModel playerModel) {
        for (PoseOverwrite po : this.enabledPoseOverwrites) {
            po.updateState(livingEntity, (PlayerData)livingEntity, playerModel);
        }
    }

    private void loadAnimations() {
        this.addAnimation(new CrawlingAnimation());
        this.addAnimation(new VanillaSingleHandedAnimation());
        this.addAnimation(new VanillaTwoHandedAnimation());
        this.addAnimation(new ItemSwapAnimation());
        this.addAnimation(new LookAtItemAnimation());
        this.addAnimation(new SleepAnimation());
        this.addAnimation(new MapHoldingAnimation());
        this.addAnimation(new BoatAnimation());
        this.addAnimation(new HorseAnimation());
        this.addAnimation(new LadderAnimation());
        this.addAnimation(new EatDrinkAnimation());
        this.addAnimation(new VanillaShieldAnimation());
        this.addAnimation(new PassengerAnimation());
        this.addAnimation(new RiptideAnimation());
        this.addAnimation(new DeathAnimation());
        this.addAnimation(new ElytraAnimation());
        this.addAnimation(new SwimAnimation());
        this.addAnimation(new PetAnimation());
        this.addAnimation(new FallingAnimation());
        this.addAnimation(new HugAnimation());
        this.addAnimation(new NarutoRunningAnimation());
        this.addAnimation(new CustomBowAnimation());
        this.addAnimation(new ActionRotationLockAnimation());
        this.addAnimation(new BurningAnimation());
        this.addAnimation(new ClampCrossbowAnimations());
        this.addAnimation(new FreezingAnimation());
    }

    public void addAnimation(BasicAnimation animation) {
        this.basicAnimations.add(animation);
    }

    public void refreshEnabledAnimations() {
        this.enabledBasicAnimations.clear();
        this.enabledPoseOverwrites.clear();
        NEAnimationsMod.INSTANCE.heldItemHandler.onLoad();
        for (BasicAnimation basicAnimation : this.basicAnimations) {
            if (!basicAnimation.isEnabled()) continue;
            this.enabledBasicAnimations.add(basicAnimation);
            if (!(basicAnimation instanceof PoseOverwrite)) continue;
            this.enabledPoseOverwrites.add((PoseOverwrite)((Object)basicAnimation));
        }
        if (this.dumpPrios) {
            ArrayList<BasicAnimation> list = new ArrayList<BasicAnimation>(this.basicAnimations);
            list.sort((a, b) -> Integer.compare(a.getPriority(null, null), b.getPriority(null, null)));
            for (BasicAnimation an : list) {
                System.out.println(an.getPriority(null, null) + " " + an.getClass().getSimpleName());
            }
        }
    }
}


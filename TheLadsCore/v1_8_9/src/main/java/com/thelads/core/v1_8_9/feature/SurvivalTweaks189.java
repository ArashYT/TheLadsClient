package com.thelads.core.v1_8_9.feature;

import com.thelads.core.modules.SurvivalTweaksModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * SurvivalTweaks on 1.8.9, as 26.x NativeSurvivalTweaks. Server-side tweaks act in the integrated server (singleplayer, LAN host):
 * 100% creeper block drops (SurvivalExplosionMixin), stable block drops (SurvivalBlockDropsMixin, EntityItemMixin), no Thorns
 * durability loss (SurvivalThornsMixin), faster obsidian mining (SurvivalPlayerMixin, singleplayer only), flammable cobwebs (here) and
 * animals eating dropped food ({@link EatFloorFood}, SurvivalAnimalMixin). Client: dimension brightness (EntityRendererMixin), longer
 * chat history (GuiNewChatMixin), fast IP ping (OldServerPingerMixin) and Keep Bars While Riding (Forge's HUD flags, here).
 * 1.8.9 has no sweet berry bushes, elytra or Soul Speed, and boats do not clamp the rider's yaw: the menu leaves those out.
 */
public final class SurvivalTweaks189 {
    private SurvivalTweaks189() {}

    public static void register() {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (mod != null) mod.hideHere("Berry bush protection", "Elytra water swim", "Boat 360 View");
        if (mod == null || mod.isFlammableCobwebsEnabled()) Blocks.fire.setFireInfo(Blocks.web, 600, 60);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SurvivalTweaks189());
    }

    private static SurvivalTweaksModule on() {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        return mod != null && mod.isEnabled() ? mod : null;
    }

    public static boolean creeperFullDrops() { SurvivalTweaksModule m = on(); return m != null && m.isCreeperFullDropsEnabled(); }
    public static boolean stableDrops() { SurvivalTweaksModule m = on(); return m != null && m.isStableBlockDropsEnabled(); }
    public static boolean noThornsDamage() { SurvivalTweaksModule m = on(); return m != null && m.isNoEnchantDurabilityLossEnabled(); }
    public static boolean animalsEatFloorFood() { SurvivalTweaksModule m = on(); return m != null && m.isAnimalsEatFloorFoodEnabled(); }
    public static boolean longerChatHistory() { SurvivalTweaksModule m = on(); return m != null && m.isLongerChatHistoryEnabled(); }
    public static boolean fastIpPing() { SurvivalTweaksModule m = on(); return m != null && m.isFastIpPingEnabled(); }
    public static boolean keepBars() { SurvivalTweaksModule m = on(); return m != null && m.isKeepBarsWhileRidingEnabled(); }

    /** Both sides compute dig speed: boosted only where this game hosts the world, so a server never sees ghost-fast mining. */
    public static boolean fasterObsidian(EntityPlayer player) {
        SurvivalTweaksModule m = on();
        return m != null && m.isFasterObsidianMiningEnabled() && (!player.worldObj.isRemote || Minecraft.getMinecraft().isIntegratedServerRunning());
    }

    /** The gamma the lightmap uses, scaled by Dimension brightness %, as 26.x scales its brightness option. */
    public static float brightness(float gamma) {
        SurvivalTweaksModule m = on();
        int pct = m == null ? 100 : m.dimensionBrightness.getIntValue();
        return pct == 100 ? gamma : gamma * (pct / 100.0f);
    }

    /** A block or mob drop (EntityItem's stack constructor) keeps still, or drifts to the nearest sneaking player within 2.5. */
    public static void stableMotion(EntityItem item) {
        EntityPlayer near = null;
        double best = 2.5D;
        for (EntityPlayer player : item.worldObj.playerEntities) {
            if (!player.isSneaking() || player.isSpectator()) continue;
            double d = player.getDistanceToEntity(item);
            if (d < best) { best = d; near = player; }
        }
        if (near != null && best > 0.01D) {
            item.motionX = (near.posX - item.posX) * 0.1D;
            item.motionY += (near.posY - item.posY) * 0.1D; // 26.x: dy + lerp(ry / dist, 0, dist / 2.5) * 0.25
            item.motionZ = (near.posZ - item.posZ) * 0.1D;
        } else {
            item.motionX = 0.0D;
            item.motionZ = 0.0D;
        }
    }

    /** Keep Bars While Riding: Forge hides the food bar on any mount and swaps the XP bar for a horse's jump bar; show both. */
    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !keepBars()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.ridingEntity == null) return;
        GuiIngameForge.renderFood = true;
        GuiIngameForge.renderJumpBar = GuiIngameForge.renderJumpBar && mc.thePlayer.getHorseJumpPower() > 0.0F;
    }

    /** As 26.x SearchForFoodGoal: an adult animal not in love walks to its breeding item on the ground and eats it, falling in love. */
    public static final class EatFloorFood extends EntityAIBase {
        private static final double SPEED = 1.15D, WITHIN = 12.0D;
        private final EntityAnimal animal;
        private EntityItem target;

        public EatFloorFood(EntityAnimal animal) {
            this.animal = animal;
            setMutexBits(1);
        }

        private boolean hungry() {
            return animalsEatFloorFood() && !animal.isChild() && animal.getGrowingAge() == 0 && !animal.isInLove();
        }

        @Override
        public boolean shouldExecute() {
            if (!hungry()) return false;
            target = null;
            double best = Double.MAX_VALUE;
            for (EntityItem item : animal.worldObj.getEntitiesWithinAABB(EntityItem.class, animal.getEntityBoundingBox().expand(WITHIN, 4.0D, WITHIN))) {
                if (item.isDead || !animal.isBreedingItem(item.getEntityItem())) continue;
                double d = animal.getDistanceSqToEntity(item);
                if (d < best) { best = d; target = item; }
            }
            return target != null;
        }

        @Override
        public boolean continueExecuting() {
            return hungry() && target != null && !target.isDead && target.getDistanceSqToEntity(animal) < WITHIN * WITHIN;
        }

        @Override
        public void startExecuting() {
            animal.getNavigator().tryMoveToEntityLiving(target, SPEED);
        }

        @Override
        public void updateTask() {
            if (animal.getDistanceSqToEntity(target) > 2.25D) {
                if (animal.getNavigator().noPath()) animal.getNavigator().tryMoveToEntityLiving(target, SPEED);
                return;
            }
            ItemStack stack = target.getEntityItem().copy();
            if (--stack.stackSize <= 0) {
                animal.onItemPickup(target, 1); // the collect packet removes the item on clients: only for the last one
                target.setDead();
            } else {
                target.setEntityItemStack(stack); // a new stack, so the watcher sends the count
            }
            animal.setInLove(target.getThrower() == null ? null : animal.worldObj.getPlayerEntityByName(target.getThrower()));
        }

        @Override
        public void resetTask() {
            target = null;
            animal.getNavigator().clearPathEntity();
        }
    }
}

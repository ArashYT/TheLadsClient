package com.thelads.core.v1_21_1.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_21_1.feature.NativeOldAnimations.Hook;
import com.thelads.core.v1_21_1.feature.qa.mixin.GuiQaInvoker;
import com.thelads.core.v1_21_1.feature.qa.mixin.OldAnimationsQaInvoker;
import com.thelads.core.v1_21_1.feature.qa.mixin.ScreenEffectQaInvoker;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;

/**
 * 1.7 Animations through the real hooks, each option on against off: first-person hands through renderArmWithItem with every
 * item draw recorded and skipped at renderItem, the player and a client-only dropped item through their renderers into a
 * buffer that keeps nothing, then the hand tick, camera tick, HUD hearts and fire overlay. Held items, use, hurt time,
 * tickers, hand and camera heights and the module's settings are restored.
 */
final class NativeOldAnimationsProbe {
    private static final VertexConsumer NO_VERTICES = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
        new Class<?>[] {VertexConsumer.class}, NativeOldAnimationsProbe::nothing);
    private static final MultiBufferSource NOWHERE = type -> NO_VERTICES;
    private static final InteractionHand MAIN = InteractionHand.MAIN_HAND, OFF = InteractionHand.OFF_HAND;
    private static int passed;
    private NativeOldAnimationsProbe() {}

    static int run() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        OldAnimationsModule module = NativeOldAnimations.module();
        Feature[] features = Feature.values();
        boolean[] options = new boolean[features.length];
        for (Feature feature : features) options[feature.ordinal()] = module.option(feature).get();
        boolean enabled = module.isEnabled(), swinging = player.swinging;
        long modified = module.getLastModified();
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem(), chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int hurt = player.hurtTime, ticker = player.attackStrengthTicker, swingTime = player.swingTime;
        InteractionHand swingingArm = player.swingingArm;
        float attackAnim = player.attackAnim, oAttackAnim = player.oAttackAnim;
        Pose pose = player.getPose();
        passed = 0;
        try {
            require(ModuleSupport.isBuiltIn(OldAnimationsModule.NAME), "1.7 Animations is a built-in module");
            module.setEnabled(true);
            for (Feature feature : features) module.option(feature).set(true);
            NativeOldAnimations.recording = true;
            firstPerson(module, player);
            thirdPerson(module, player);
            world(module, player, mc);
            LoggerFactory.getLogger("TheLadsCore").info("Lads 1.7 animations probe END: {} passed, 0 failed (each option against off; held items, use, "
                + "hurt time, tickers, hand and camera heights and the module's settings restored)", passed);
            return passed;
        } finally {
            NativeOldAnimations.recording = false;
            NativeOldAnimations.DRAWN.clear();
            hold(player, main, off);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.hurtTime = hurt;
            player.attackStrengthTicker = ticker;
            player.swinging = swinging;
            player.swingTime = swingTime;
            player.swingingArm = swingingArm;
            player.attackAnim = attackAnim;
            player.oAttackAnim = oAttackAnim;
            player.setPose(pose);
            for (Feature feature : features) module.option(feature).set(options[feature.ordinal()]);
            module.setEnabled(enabled);
            module.setLastModified(modified);
        }
    }

    private static void firstPerson(OldAnimationsModule module, LocalPlayer player) {
        hold(player, new ItemStack(Items.BOW), ItemStack.EMPTY);
        require(icon(hand(MAIN, 0)), "an idle bow takes 1.7's hand and icon placement (no display transform)");
        Matrix4f idle = last();
        module.option(Feature.BOW).set(false);
        require(!icon(hand(MAIN, 0)), "1.7 bow position off: vanilla's first-person bow");
        module.option(Feature.BOW).set(true);
        use(player, MAIN, 72000 - 15);
        require(icon(hand(MAIN, 0)) && !last().equals(idle, 1e-4f), "a drawn bow follows 1.7's draw");
        module.setEnabled(false);
        require(!icon(hand(MAIN, 0)), "module off: vanilla's drawn bow");
        module.setEnabled(true);

        hold(player, new ItemStack(Items.FISHING_ROD), ItemStack.EMPTY);
        require(icon(hand(MAIN, 0)), "a fishing rod takes 1.7's placement");
        module.option(Feature.ROD).set(false);
        require(!icon(hand(MAIN, 0)), "1.7 fishing rod position off: vanilla's rod");
        module.option(Feature.ROD).set(true);

        hold(player, new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY);
        require(icon(hand(MAIN, 0)), "an idle sword sits where 1.7 held it");
        module.option(Feature.HELD_ITEMS).set(false);
        require(!icon(hand(MAIN, 0)), "1.7 held item positions off: vanilla's idle sword");
        module.option(Feature.HELD_ITEMS).set(true);

        hold(player, new ItemStack(Items.APPLE), ItemStack.EMPTY);
        use(player, MAIN, 16);
        require(icon(hand(MAIN, 0)), "eating takes 1.7's eating transform");
        Matrix4f eating = last();
        require(icon(hand(MAIN, 0.5f)) && !last().equals(eating, 1e-4f), "Swing while using items: the swing shows while eating");
        module.option(Feature.SWING_WHILE_USING).set(false);
        hand(MAIN, 0.5f);
        require(last().equals(eating, 1e-4f), "Swing while using items off: no swing while eating");
        require(!swings(player), "Swing while using items off: an attack click while eating does not swing");
        module.option(Feature.SWING_WHILE_USING).set(true);
        require(swings(player), "an attack click while eating swings (ClientTickMixin hands it to attackWhileUsing)");
        module.option(Feature.EAT_DRINK).set(false);
        require(!icon(hand(MAIN, 0)), "1.7 eating and drinking off: vanilla's eating");
        module.option(Feature.EAT_DRINK).set(true);

        hold(player, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.SHIELD));
        use(player, OFF, 72000);
        require(icon(hand(MAIN, 0)), "blocking with a shield while holding a sword: the sword takes 1.7's block pose");
        Matrix4f block = last();
        require(hand(OFF, 0).isEmpty(), "the blocking shield is hidden");
        require(icon(hand(MAIN, 0.5f)) && !last().equals(block, 1e-4f), "Blockhitting: the blocking sword swings");
        require(swings(player), "an attack click while blocking swings");
        module.option(Feature.BLOCKHIT).set(false);
        hand(MAIN, 0.5f);
        require(last().equals(block, 1e-4f) && !swings(player), "Blockhitting off: the blocking sword holds still");
        module.option(Feature.BLOCKHIT).set(true);
        module.setEnabled(false);
        require(!icon(hand(MAIN, 0)) && hand(OFF, 0).size() == 1, "module off: vanilla's sword and shield");
        module.setEnabled(true);

        hold(player, new ItemStack(Items.SHIELD), ItemStack.EMPTY);
        hand(MAIN, 0);
        float low = NativeOldAnimations.LAST.m31();
        module.option(Feature.LOW_SHIELD).set(false);
        hand(MAIN, 0);
        require(Math.abs(NativeOldAnimations.LAST.m31() - low - 0.25f) < 1e-3f, "Low Shield lowers a held shield");
        module.option(Feature.LOW_SHIELD).set(true);
    }

    private static void thirdPerson(OldAnimationsModule module, LocalPlayer player) {
        // The arm is read where the 1.7 hook set it and from the model's arm pose: NotEnoughAnimations (in the pack) smooths
        // player arm angles after setupAnim and replays its stored angles for every further render in the same tick.
        var model = ((PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player)).getModel();
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        player.swinging = false;
        player.swingTime = 0;
        player.attackAnim = player.oAttackAnim = 0; // a swing left by earlier probes would pitch the arm too
        hold(player, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.SHIELD));
        use(player, OFF, 72000);
        int arm = NativeOldAnimations.hits(Hook.TP_ARM);
        List<ItemDisplayContext> drawn = render(player);
        require((right ? model.rightArmPose : model.leftArmPose) == HumanoidModel.ArmPose.BLOCK && NativeOldAnimations.hits(Hook.TP_ARM) > arm
            && NativeOldAnimations.armPitch < -0.7f, "third person: the sword arm blocks as 1.7's did (" + NativeOldAnimations.armPitch + ")");
        require(icon(drawn), "third person: the shield is hidden and the sword takes 1.7's block placement " + drawn);
        module.setEnabled(false);
        arm = NativeOldAnimations.hits(Hook.TP_ARM);
        drawn = render(player);
        require(drawn.size() == 2 && !drawn.contains(ItemDisplayContext.NONE) && (right ? model.rightArmPose : model.leftArmPose) != HumanoidModel.ArmPose.BLOCK
            && NativeOldAnimations.hits(Hook.TP_ARM) == arm, "module off: vanilla's arms, sword and shield " + drawn);
        module.setEnabled(true);

        hold(player, new ItemStack(Items.APPLE), ItemStack.EMPTY);
        require(icon(render(player)), "third person: a held flat item takes 1.7's placement");
        module.option(Feature.THIRD_PERSON).set(false);
        require(!icon(render(player)), "1.7 third-person items off: vanilla's placement");
        module.option(Feature.THIRD_PERSON).set(true);
        hold(player, new ItemStack(Items.STONE), ItemStack.EMPTY);
        require(!icon(render(player)), "a held block keeps vanilla's 3D placement");

        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.hurtTime = 10;
        require(tints(player), "Red armour on hurt: armour takes the hurt tint");
        module.option(Feature.RED_ARMOUR).set(false);
        require(!tints(player), "Red armour on hurt off: armour keeps its colour");
        module.option(Feature.RED_ARMOUR).set(true);
        player.hurtTime = 0;
        require(!tints(player), "unhurt armour keeps its colour");
    }

    private static void world(OldAnimationsModule module, LocalPlayer player, Minecraft mc) {
        ItemEntity drop = new ItemEntity(mc.level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.APPLE));
        require(flatDrop(drop), "2D dropped items: a flat item is drawn as 1.7's camera-facing icon");
        module.option(Feature.DROPPED_2D).set(false);
        require(!flatDrop(drop), "2D dropped items off: vanilla's spinning item");
        module.option(Feature.DROPPED_2D).set(true);
        drop.setItem(new ItemStack(Items.STONE));
        require(!flatDrop(drop), "a dropped block stays 3D");

        ItemInHandRenderer hands = mc.getEntityRenderDispatcher().getItemInHandRenderer();
        ItemStack mainItem = hands.mainHandItem, offItem = hands.offHandItem;
        float[] heights = {hands.mainHandHeight, hands.oMainHandHeight, hands.offHandHeight, hands.oOffHandHeight};
        try {
            hold(player, new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY);
            player.resetAttackStrengthTicker();
            float up = equip(hands, player);
            module.option(Feature.NO_COOLDOWN_DIP).set(false);
            float dipped = equip(hands, player);
            module.option(Feature.NO_COOLDOWN_DIP).set(true);
            require(up == 1 && dipped < 1, "No attack-cooldown dip: the hand stays up after an attack (" + up + ", vanilla " + dipped + ")");
        } finally {
            hands.mainHandItem = mainItem;
            hands.offHandItem = offItem;
            hands.mainHandHeight = heights[0];
            hands.oMainHandHeight = heights[1];
            hands.offHandHeight = heights[2];
            hands.oOffHandHeight = heights[3];
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        float eye = camera.eyeHeight, eyeOld = camera.eyeHeightOld;
        try {
            player.setPose(Pose.CROUCHING);
            float sneak = player.getEyeHeight(), instant = sneakTick(camera, sneak);
            module.option(Feature.INSTANT_SNEAK).set(false);
            float eased = sneakTick(camera, sneak);
            module.option(Feature.INSTANT_SNEAK).set(true);
            require(instant == sneak && eased > sneak, "Instant sneak camera: the eye reaches sneak height in one tick (" + instant + ", vanilla " + eased + ")");
        } finally {
            camera.eyeHeight = eye;
            camera.eyeHeightOld = eyeOld;
        }

        Gui gui = mc.gui;
        long blink = gui.healthBlinkTime;
        try {
            int hearts = NativeOldAnimations.hits(Hook.HEARTS);
            hud(mc);
            require(NativeOldAnimations.hits(Hook.HEARTS) > hearts, "No heart flashing: the damage blink is not drawn");
            module.option(Feature.NO_HEART_FLASH).set(false);
            hearts = NativeOldAnimations.hits(Hook.HEARTS);
            hud(mc);
            require(NativeOldAnimations.hits(Hook.HEARTS) == hearts, "No heart flashing off: the hearts flash");
            module.option(Feature.NO_HEART_FLASH).set(true);
        } finally {
            gui.healthBlinkTime = blink;
        }

        int fire = NativeOldAnimations.hits(Hook.LOW_FIRE);
        ScreenEffectQaInvoker.ladsQaFire(mc, new PoseStack());
        require(NativeOldAnimations.hits(Hook.LOW_FIRE) > fire, "Low Fire lowers the fire overlay");
        module.option(Feature.LOW_FIRE).set(false);
        fire = NativeOldAnimations.hits(Hook.LOW_FIRE);
        ScreenEffectQaInvoker.ladsQaFire(mc, new PoseStack());
        require(NativeOldAnimations.hits(Hook.LOW_FIRE) == fire, "Low Fire off: vanilla's fire overlay");
        module.option(Feature.LOW_FIRE).set(true);
    }

    private static void hold(LocalPlayer player, ItemStack main, ItemStack off) {
        player.stopUsingItem();
        player.setItemInHand(MAIN, main);
        player.setItemInHand(OFF, off);
    }

    private static void use(LocalPlayer player, InteractionHand hand, int remaining) {
        player.startUsingItem(hand);
        player.useItemRemaining = remaining;
    }

    /** One first-person hand through renderArmWithItem; returns the item draws renderItem recorded (and skipped). */
    private static List<ItemDisplayContext> hand(InteractionHand hand, float swing) {
        Minecraft mc = Minecraft.getInstance();
        NativeOldAnimations.DRAWN.clear();
        ((OldAnimationsQaInvoker) mc.getEntityRenderDispatcher().getItemInHandRenderer()).ladsQaArm(mc.player, 0, 0, hand, swing,
            mc.player.getItemInHand(hand), 0, new PoseStack(), NOWHERE, LightTexture.FULL_BRIGHT);
        return List.copyOf(NativeOldAnimations.DRAWN);
    }

    /** The player through its renderer (arm poses, layers); returns the held-item draws recorded at renderItem. */
    private static List<ItemDisplayContext> render(LocalPlayer player) {
        NativeOldAnimations.DRAWN.clear();
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player).render(player, 0, 0, new PoseStack(), NOWHERE, LightTexture.FULL_BRIGHT);
        return List.copyOf(NativeOldAnimations.DRAWN);
    }

    private static boolean tints(LocalPlayer player) {
        int armour = NativeOldAnimations.hits(Hook.ARMOUR);
        render(player);
        return NativeOldAnimations.hits(Hook.ARMOUR) > armour;
    }

    private static boolean flatDrop(ItemEntity drop) {
        int drops = NativeOldAnimations.hits(Hook.DROP);
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(drop).render(drop, 0, 0, new PoseStack(), NOWHERE, LightTexture.FULL_BRIGHT);
        return NativeOldAnimations.hits(Hook.DROP) > drops;
    }

    /** One attack click while using, as ClientTickMixin passes it on: true when the player started swinging. */
    private static boolean swings(LocalPlayer player) {
        player.swinging = false;
        NativeOldAnimations.attackWhileUsing(player);
        boolean swung = player.swinging;
        player.swinging = false;
        return swung;
    }

    /** The real hand tick with the hand up and the item settled: returns the equip height it leaves. */
    private static float equip(ItemInHandRenderer hands, LocalPlayer player) {
        hands.mainHandItem = player.getMainHandItem();
        hands.mainHandHeight = 1;
        hands.tick();
        return hands.mainHandHeight;
    }

    private static float sneakTick(Camera camera, float sneak) {
        camera.eyeHeight = sneak + 0.35f;
        camera.tick();
        return camera.eyeHeight;
    }

    /** The real hotbar and status bars with vanilla's damage blink due ((blink - tick) / 3 odd). */
    private static void hud(Minecraft mc) {
        mc.gui.healthBlinkTime = mc.gui.tickCount + 4;
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        ((GuiQaInvoker) mc.gui).ladsQaHotbar(graphics, mc.getTimer());
        graphics.flush();
    }

    /** A vertex consumer that keeps nothing: chained calls return the proxy, the rest nothing. */
    private static Object nothing(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "nowhere";
            default -> method.getReturnType().isInstance(proxy) ? proxy : null;
        };
    }

    private static boolean icon(List<ItemDisplayContext> drawn) {
        return drawn.equals(List.of(ItemDisplayContext.NONE));
    }

    private static Matrix4f last() {
        return new Matrix4f(NativeOldAnimations.LAST);
    }

    private static void require(boolean value, String name) {
        if (!value) throw new IllegalStateException(name);
        passed++;
    }
}

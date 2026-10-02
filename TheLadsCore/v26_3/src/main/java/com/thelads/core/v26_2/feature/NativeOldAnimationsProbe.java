package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.Option;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;

/**
 * Opt-in QA for 1.7 Animations: drives each hooked game method with the module off, then on, and requires the hook to have run
 * and changed what the game produced (recorded submissions, flags and values). Held items, use, pose and fire are set on this
 * client only and restored; nothing is sent to the server.
 */
final class NativeOldAnimationsProbe {
    private static final int LIGHT = 0xF000F0;
    private static final String[] HEIGHTS = {"mainHandHeight", "oMainHandHeight", "offHandHeight", "oOffHandHeight"};
    private static int passed;
    private NativeOldAnimationsProbe() {}

    /** One recorded submission: the collector method, its display context (items), overlay (models) and matrix. */
    private record Call(String method, ItemDisplayContext context, int overlay, Matrix4f pose) {}

    static int run() throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        OldAnimationsModule module = NativeOldAnimations.module();
        Module legacy = NativeQualityOfLife.module("LegacySwing");
        boolean enabledBefore = module.isEnabled(), legacyBefore = legacy.isEnabled();
        long modifiedBefore = module.getLastModified(), legacyModified = legacy.getLastModified();
        Map<Option, JsonElement> options = new LinkedHashMap<>();
        for (Option option : module.getOptions()) options.put(option, option.save().deepCopy());
        Map<EquipmentSlot, ItemStack> worn = new LinkedHashMap<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST})
            worn.put(slot, player.getItemBySlot(slot));
        FirstPersonHandsAndItems hands = player.firstPersonHandsAndItems();
        Object mainShown = get(hands, "mainHandItem"), offShown = get(hands, "offHandItem");
        float[] heights = new float[HEIGHTS.length];
        for (int i = 0; i < HEIGHTS.length; i++) heights[i] = (float) get(hands, HEIGHTS[i]);
        Camera camera = mc.gameRenderer.mainCamera();
        float eye = (float) get(camera, "eyeHeight"), eyeOld = (float) get(camera, "eyeHeightOld");
        Hud hud = mc.gui.hud;
        Object blinkTime = get(hud, "healthBlinkTime"), lastHealth = get(hud, "lastHealth"), displayHealth = get(hud, "displayHealth"),
            healthTime = get(hud, "lastHealthTime");
        Pose poseBefore = player.getPose();
        Object strength = get(player, "attackStrengthTicker");
        passed = 0;
        try {
            require(ModuleSupport.isBuiltIn(OldAnimationsModule.NAME), "1.7 Animations is built in on this version");
            legacy.setEnabled(false); // the pure 1.7 swing, not Legacy Console's
            module.getOptions().forEach(Option::reset); // every option on
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD), shield = new ItemStack(Items.SHIELD), beef = new ItemStack(Items.COOKED_BEEF);

            // First person: the item draw with the module off is vanilla's; on, the 1.7 placement draws with no display transform.
            List<Call> off = hands(false, new ItemStack(Items.BOW), ItemStack.EMPTY, InteractionHand.MAIN_HAND, 20, 0);
            changed(Feature.BOW, off, hands(true, new ItemStack(Items.BOW), ItemStack.EMPTY, InteractionHand.MAIN_HAND, 20, 0));
            off = hands(false, beef, ItemStack.EMPTY, InteractionHand.MAIN_HAND, 10, 0);
            changed(Feature.EAT_DRINK, off, hands(true, beef, ItemStack.EMPTY, InteractionHand.MAIN_HAND, 10, 0));
            List<Call> swung = hands(true, beef, ItemStack.EMPTY, InteractionHand.MAIN_HAND, 10, 0.5f);
            require(NativeOldAnimations.APPLIED.contains(Feature.SWING_WHILE_USING), "Swing while using items: the swing shows while eating");
            module.option(Feature.SWING_WHILE_USING).set(false);
            require(moved(swung, hands(true, beef, ItemStack.EMPTY, InteractionHand.MAIN_HAND, 10, 0.5f))
                && !NativeOldAnimations.APPLIED.contains(Feature.SWING_WHILE_USING), "Swing while using items: off hides the swing (1.8)");
            module.option(Feature.SWING_WHILE_USING).set(true);
            off = hands(false, new ItemStack(Items.FISHING_ROD), ItemStack.EMPTY, null, 0, 0);
            changed(Feature.ROD, off, hands(true, new ItemStack(Items.FISHING_ROD), ItemStack.EMPTY, null, 0, 0));
            require(!icon(hands(true, sword, ItemStack.EMPTY, null, 0, 0)), "an idle sword keeps vanilla's placement");

            // The modern sword block: a sword while the off hand blocks with a shield; the shield hides and the sword blocks.
            off = hands(false, sword, shield, InteractionHand.OFF_HAND, 5, 0);
            List<Call> block = hands(true, sword, shield, InteractionHand.OFF_HAND, 5, 0);
            changed(Feature.BLOCK_POSE, off, block);
            require(off.size() > block.size() && block.size() == 1, "the shield is hidden while the sword blocks");
            List<Call> blockhit = hands(true, sword, shield, InteractionHand.OFF_HAND, 5, 0.5f);
            require(NativeOldAnimations.APPLIED.contains(Feature.BLOCKHIT) && moved(block, blockhit), "Blockhitting: the swing shows on the blocking sword");
            module.option(Feature.BLOCKHIT).set(false);
            require(!moved(block, hands(true, sword, shield, InteractionHand.OFF_HAND, 5, 0.5f)), "Blockhitting off: no swing while blocking (1.8)");
            module.option(Feature.BLOCKHIT).set(true);
            List<Call> low = hands(true, ItemStack.EMPTY, shield, null, 0, 0);
            require(NativeOldAnimations.APPLIED.contains(Feature.LOW_SHIELD), "Low Shield applies to a held shield");
            module.option(Feature.LOW_SHIELD).set(false);
            require(moved(low, hands(true, ItemStack.EMPTY, shield, null, 0, 0)), "Low Shield lowers the shield");
            module.option(Feature.LOW_SHIELD).set(true);

            // No attack-cooldown dip: just after an attack the equip animation keeps the item up.
            float dipped = equipAfterAttack(false, sword), kept = equipAfterAttack(true, sword);
            require(kept > dipped && NativeOldAnimations.APPLIED.contains(Feature.NO_COOLDOWN_DIP), "No attack-cooldown dip keeps the item up");

            // Instant sneak camera: one camera tick reaches the sneaking eye height.
            require(camera.entity() == player, "the camera follows the player");
            player.setPose(Pose.STANDING);
            float standing = player.getEyeHeight();
            float eased = sneakTick(false, camera, standing), instant = sneakTick(true, camera, standing);
            require(instant == player.getEyeHeight() && eased > instant && NativeOldAnimations.APPLIED.contains(Feature.INSTANT_SNEAK),
                "Instant sneak camera drops within one tick");
            player.setPose(poseBefore);

            // No heart flashing: the health bar's blink flag.
            require(heartsBlink(false, hud) && !heartsBlink(true, hud) && NativeOldAnimations.APPLIED.contains(Feature.NO_HEART_FLASH),
                "No heart flashing clears the hearts' blink");

            // Low Fire: the burning overlay is drawn lower.
            CameraRenderState view = new CameraRenderState();
            camera.extractRenderState(view, DeltaTracker.ONE);
            off = fire(false, view);
            List<Call> lowered = fire(true, view);
            require(NativeOldAnimations.APPLIED.contains(Feature.LOW_FIRE) && moved(off, lowered), "Low Fire lowers the fire overlay");

            // Third person, through the real entity renderer: the sword block, 1.7 held items and red armour on hurt.
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            float[] armOff = new float[2], armOn = new float[2];
            int[] redOff = new int[1], redOn = new int[1];
            off = player(false, sword, shield, view, armOff, redOff);
            List<Call> third = player(true, sword, shield, view, armOn, redOn);
            require(!icon(off) && icon(third) && NativeOldAnimations.APPLIED.contains(Feature.THIRD_PERSON), "1.7 third-person items: the 1.7 held sword");
            // 1.7's blocking arm pitches down further than the held-item arm and keeps no inward turn (vanilla's block turns it 30°).
            require(armOn[0] < armOff[0] - 0.3f && Math.abs(armOn[1] - com.thelads.core.client.OldAnimations.BLOCKING_ARM_YAW) < 1e-3f,
                "1.7 third-person items: the 1.7 blocking arm");
            require(redOn[0] > redOff[0] && NativeOldAnimations.APPLIED.contains(Feature.RED_ARMOUR), "Red armour on hurt tints the armour");

            // 2D dropped items: flat items become the 1.7 icon; blocks stay 3D.
            off = dropped(false, Items.APPLE, view);
            changed(Feature.DROPPED_2D, off, dropped(true, Items.APPLE, view));
            require(!icon(dropped(true, Items.STONE, view)), "dropped blocks keep their 3D model");

            LoggerFactory.getLogger("TheLadsCore").info("Lads 1.7 animations probe END: {} passed, 0 failed (real game methods, module off then on;"
                + " client-only items, use and pose, all restored)", passed);
            return passed;
        } finally {
            player.stopUsingItem();
            worn.forEach(player::setItemSlot);
            set(hands, "mainHandItem", mainShown);
            set(hands, "offHandItem", offShown);
            for (int i = 0; i < HEIGHTS.length; i++) set(hands, HEIGHTS[i], heights[i]);
            set(camera, "eyeHeight", eye);
            set(camera, "eyeHeightOld", eyeOld);
            set(hud, "healthBlinkTime", blinkTime);
            set(hud, "lastHealth", lastHealth);
            set(hud, "displayHealth", displayHealth);
            set(hud, "lastHealthTime", healthTime);
            player.setPose(poseBefore);
            set(player, "attackStrengthTicker", strength);
            options.forEach(Option::load);
            module.setEnabled(enabledBefore);
            module.setLastModified(modifiedBefore);
            legacy.setEnabled(legacyBefore);
            legacy.setLastModified(legacyModified);
            NativeOldAnimations.APPLIED.clear();
        }
    }

    /** First-person hands, extracted and submitted as the game does (FirstPersonHandsAndItemsRenderer), with these items, use ticks and swing. */
    private static List<Call> hands(boolean enabled, ItemStack main, ItemStack offhand, InteractionHand use, int usedTicks, float swing)
            throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = prepare(enabled, main, offhand, use, usedTicks);
        FirstPersonHandsAndItems shown = player.firstPersonHandsAndItems();
        set(shown, "mainHandItem", main);
        set(shown, "offHandItem", offhand);
        for (String height : HEIGHTS) set(shown, height, 1f); // fully raised
        PlayerRenderState state = new PlayerRenderState();
        AvatarRenderState avatar = new AvatarRenderState();
        state.hasPlayer = true;
        state.avatarRenderState = avatar;
        avatar.mainArm = player.getMainArm();
        avatar.isUsingItem = player.isUsingItem();
        avatar.useItemHand = player.getUsedItemHand();
        avatar.lightCoords = LIGHT;
        avatar.swingAnimation = swing;
        shown.extractRenderState(player, 1, state.firstPersonHandsAndItems);
        state.firstPersonHandsAndItems.attackHand = InteractionHand.MAIN_HAND;
        List<Call> calls = new ArrayList<>();
        try { mc.gameRenderer.firstPersonHandsAndItemsRenderer.submitHandsWithItems(1, new PoseStack(), recorder(calls), state, state.firstPersonHandsAndItems); }
        finally { player.stopUsingItem(); }
        return calls;
    }

    /** This client's held items and use for one probe run; clears the applied-feature record. */
    private static LocalPlayer prepare(boolean enabled, ItemStack main, ItemStack offhand, InteractionHand use, int usedTicks)
            throws ReflectiveOperationException {
        LocalPlayer player = Minecraft.getInstance().player;
        NativeOldAnimations.module().setEnabled(enabled);
        NativeOldAnimations.APPLIED.clear();
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        if (use != null) {
            player.startUsingItem(use);
            set(player, "useItemRemaining", player.getUseItem().getUseDuration(player) - usedTicks);
        }
        return player;
    }

    /** The main hand's equip height one tick after an attack. */
    private static float equipAfterAttack(boolean enabled, ItemStack sword) throws ReflectiveOperationException {
        LocalPlayer player = prepare(enabled, sword, ItemStack.EMPTY, null, 0);
        FirstPersonHandsAndItems hands = player.firstPersonHandsAndItems();
        set(hands, "mainHandItem", sword);
        set(hands, "mainHandHeight", 1f);
        set(player, "attackStrengthTicker", 0); // the attack cooldown restarts
        hands.tick(player);
        return (float) get(hands, "mainHandHeight");
    }

    /** The camera's eye height one tick after the player starts sneaking. */
    private static float sneakTick(boolean enabled, Camera camera, float standing) throws ReflectiveOperationException {
        NativeOldAnimations.module().setEnabled(enabled);
        NativeOldAnimations.APPLIED.clear();
        Minecraft.getInstance().player.setPose(Pose.CROUCHING);
        set(camera, "eyeHeight", standing);
        set(camera, "eyeHeightOld", standing);
        camera.tick();
        return (float) get(camera, "eyeHeight");
    }

    /** The blink flag Hud.extractPlayerHealth passes to the hearts while vanilla blinks. */
    private static boolean heartsBlink(boolean enabled, Hud hud) throws ReflectiveOperationException {
        NativeOldAnimations.module().setEnabled(enabled);
        NativeOldAnimations.APPLIED.clear();
        set(hud, "healthBlinkTime", (long) (int) get(hud, "tickCount") + 3); // (blink - tick) / 3 is odd: vanilla blinks
        NativeOldAnimations.lastHeartsBlink = enabled;
        Method health = Hud.class.getDeclaredMethod("extractPlayerHealth", GuiGraphicsExtractor.class);
        health.setAccessible(true);
        health.invoke(hud, new GuiGraphicsExtractor(Minecraft.getInstance(), new GuiRenderState(), 0, 0));
        return NativeOldAnimations.lastHeartsBlink;
    }

    /** The first-person screen effects while burning. */
    private static List<Call> fire(boolean enabled, CameraRenderState view) throws ReflectiveOperationException {
        NativeOldAnimations.module().setEnabled(enabled);
        NativeOldAnimations.APPLIED.clear();
        ScreenEffectRenderer effects = (ScreenEffectRenderer) get(Minecraft.getInstance().gameRenderer, "screenEffectRenderer");
        List<Call> calls = new ArrayList<>();
        PlayerRenderState state = new PlayerRenderState();
        state.hasPlayer = true;
        state.avatarRenderState = new AvatarRenderState();
        state.isOnFire = true;
        view.isFirstPerson = true;
        effects.submit(1, recorder(calls), state, view, true); // true: GUI hidden, no totem animation
        return calls;
    }

    /** This player through the entity render dispatcher, hurt, holding a sword while blocking with a shield. */
    private static List<Call> player(boolean enabled, ItemStack sword, ItemStack shield, CameraRenderState view, float[] arm, int[] red)
            throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = prepare(enabled, sword, shield, InteractionHand.OFF_HAND, 5);
        List<Call> calls = new ArrayList<>();
        try {
            var dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderState state = dispatcher.extractEntity(player, 1);
            AvatarRenderState avatar = (AvatarRenderState) state;
            int flags = ((NativeOldAnimations.State) state).lads$oldAnimations();
            if (enabled) require((flags & NativeOldAnimations.SWORD_BLOCK) != 0 && avatar.leftHandItemState.isEmpty() != avatar.rightHandItemState.isEmpty()
                && (avatar.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT ? avatar.rightArmPose : avatar.leftArmPose) == HumanoidModel.ArmPose.BLOCK,
                "third person: the sword blocks and the shield hides");
            else require(flags == 0, "module off: no 1.7 third-person flags");
            avatar.hasRedOverlay = true;
            dispatcher.submit(state, view, 0, 0, 0, new PoseStack(), recorder(calls));
            var limb = ((HumanoidModel<?>) ((LivingEntityRenderer<?, ?, ?>) dispatcher.getRenderer(state)).getModel()).getArm(avatar.mainArm);
            arm[0] = limb.xRot;
            arm[1] = limb.yRot;
            int hurt = LivingEntityRenderer.getOverlayCoords(avatar, 0);
            red[0] = (int) calls.stream().filter(call -> call.method().equals("submitModel") && call.overlay() == hurt).count();
        } finally { player.stopUsingItem(); }
        return calls;
    }

    /** A client-only dropped item (never added to the level) through the entity render dispatcher. */
    private static List<Call> dropped(boolean enabled, net.minecraft.world.item.Item item, CameraRenderState view) {
        Minecraft mc = Minecraft.getInstance();
        NativeOldAnimations.module().setEnabled(enabled);
        NativeOldAnimations.APPLIED.clear();
        ItemEntity entity = new ItemEntity(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ(), new ItemStack(item));
        List<Call> calls = new ArrayList<>();
        var dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.submit(dispatcher.extractEntity(entity, 1), view, 0, 0, 0, new PoseStack(), recorder(calls));
        return calls;
    }

    private static void changed(Feature feature, List<Call> off, List<Call> on) {
        require(!icon(off) && icon(on) && moved(off, on) && NativeOldAnimations.APPLIED.contains(feature),
            feature.option + ": vanilla with the module off, the 1.7 placement with it on");
    }

    /** An item drawn with no display transform: the 1.7 placement. */
    private static boolean icon(List<Call> calls) {
        return calls.stream().anyMatch(call -> call.context() == ItemDisplayContext.NONE);
    }

    private static boolean moved(List<Call> a, List<Call> b) {
        if (a.size() != b.size()) return true;
        for (int i = 0; i < a.size(); i++) {
            Matrix4f left = a.get(i).pose(), right = b.get(i).pose();
            if (left != null && right != null && !left.equals(right, 1e-4f)) return true;
        }
        return false;
    }

    /** A collector that records each submission instead of drawing it. */
    private static SubmitNodeCollector recorder(List<Call> calls) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("order")) return proxy;
            if (method.getDeclaringClass() == Object.class)
                return method.getName().equals("equals") ? proxy == args[0] : method.getName().equals("hashCode") ? System.identityHashCode(proxy) : "QA recorder";
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            ItemDisplayContext context = null;
            Matrix4f pose = null;
            for (Object arg : args) {
                if (arg instanceof PoseStack stack && pose == null) pose = new Matrix4f(stack.last().pose());
                if (arg instanceof ItemDisplayContext display) context = display;
            }
            calls.add(new Call(method.getName(), context, method.getName().equals("submitModel") ? (int) args[5] : -1, pose));
            return null;
        };
        return (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(), new Class<?>[]{SubmitNodeCollector.class}, handler);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> at = type; at != null; at = at.getSuperclass())
            try {
                Field field = at.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Object get(Object owner, String name) throws ReflectiveOperationException { return field(owner.getClass(), name).get(owner); }

    private static void set(Object owner, String name, Object value) throws ReflectiveOperationException { field(owner.getClass(), name).set(owner, value); }

    private static void require(boolean value, String name) {
        if (!value) throw new IllegalStateException("1.7 Animations: " + name);
        passed++;
    }
}

package com.thelads.smp.features.arise.nb3;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.smp.features.arise.nb3.backweapon.BackWeaponRenderLayer;
import com.thelads.smp.features.arise.nb3.backweapon.SwapBackWeaponPayload;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.CosmeticArmorFeature;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.CosmeticArmorScreen;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.SyncCosmeticArmorPayload;
import com.thelads.smp.features.arise.nb3.immersiveui.ImmersiveUIState;
import com.thelads.smp.features.arise.nb3.obscuretooltips.ClientObscureTooltipComponent;
import com.thelads.smp.features.arise.nb3.obscuretooltips.ObscureTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Native Batch NB3:
 * - Registers BackWeaponRenderLayer on Player entities
 * - Registers ClientObscureTooltipComponent for item tooltips
 * - Registers Target Dummy entity renderer
 * - Updates ImmersiveUI animation tick state and triggers screen shake on hurt
 * - Registers Keybindings and ScreenEvents for Cosmetic Armor and Back Weapon
 */
public final class AriseNb3Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB3Client");

    public static final KeyMapping SWAP_BACK_WEAPON_KEY = new KeyMapping(
            "key.ladssmp.swap_back_weapon",
            InputConstants.KEY_B,
            KeyMapping.Category.GAMEPLAY
    );

    public static final KeyMapping COSMETICS_KEY = new KeyMapping(
            "key.ladssmp.cosmetics",
            InputConstants.UNKNOWN.getValue(), // unbound like Arise; C is the Lads zoom
            KeyMapping.Category.INVENTORY
    );

    private static int lastHurtTime = 0;

    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        LOGGER.info("[Lads SMP] Initializing Native Batch NB3 Client Systems...");

        // Register key mappings
        KeyMappingHelper.registerKeyMapping(SWAP_BACK_WEAPON_KEY);
        KeyMappingHelper.registerKeyMapping(COSMETICS_KEY);

        // Tooltip miniature component callback (Obscure Tooltips)
        ClientTooltipComponentCallback.EVENT.register(tooltipComponent -> {
            if (tooltipComponent instanceof ObscureTooltipComponent obscure) {
                return new ClientObscureTooltipComponent(obscure.stack());
            }
            return null;
        });

        // Back weapon render layer registration on Player
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (entityType == EntityTypes.PLAYER && renderer instanceof AvatarRenderer avatarRenderer) {
                helper.register(new BackWeaponRenderLayer((AvatarRenderer) renderer));
            }
        });

        // Target Dummy entity renderer registration
        EntityRendererRegistry.register(AriseNb3Mod.TARGET_DUMMY_ENTITY_TYPE, ArmorStandRenderer::new);

        // Client Network Receiver for Cosmetic Armor Sync
        ClientPlayNetworking.registerGlobalReceiver(SyncCosmeticArmorPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                var data = CosmeticArmorFeature.getOrCreate(payload.targetPlayerUuid());
                data.set(EquipmentSlot.HEAD, payload.head());
                data.set(EquipmentSlot.CHEST, payload.chest());
                data.set(EquipmentSlot.LEGS, payload.legs());
                data.set(EquipmentSlot.FEET, payload.feet());
                data.setVisibility(EquipmentSlot.HEAD, payload.headVis());
                data.setVisibility(EquipmentSlot.CHEST, payload.chestVis());
                data.setVisibility(EquipmentSlot.LEGS, payload.legsVis());
                data.setVisibility(EquipmentSlot.FEET, payload.feetVis());
            });
        });

        // Inventory Screen hook: adds cosmetic armor button
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof InventoryScreen) {
                if (!AriseNb3Config.get().cosmeticArmor.enabled || AriseNb3Config.get().cosmeticArmor.hideButton) {
                    return;
                }
                int x = (width - 176) / 2 + AriseNb3Config.get().cosmeticArmor.guiButtonLeft;
                int y = (height - 166) / 2 + AriseNb3Config.get().cosmeticArmor.guiButtonTop;
                Button btn = Button.builder(Component.literal("👘"), b -> {
                    client.gui.setScreen(new CosmeticArmorScreen(screen));
                }).bounds(x, y, 16, 16).tooltip(Tooltip.create(Component.literal("Cosmetic Armor"))).build();
                Screens.getWidgets(screen).add(btn);
            }
        });

        // Client tick for Immersive UI animations (screen shake, easing timers) and keybind handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ImmersiveUIState.tick();

            // Track damage for camera screen shake
            if (client.player != null) {
                int hurt = client.player.hurtTime;
                if (hurt > 0 && lastHurtTime == 0) {
                    ImmersiveUIState.onHurt();
                }
                lastHurtTime = hurt;
            } else {
                lastHurtTime = 0;
            }

            // Keybindings
            while (SWAP_BACK_WEAPON_KEY.consumeClick()) {
                if (ClientPlayNetworking.canSend(SwapBackWeaponPayload.TYPE)) {
                    ClientPlayNetworking.send(new SwapBackWeaponPayload());
                }
            }

            while (COSMETICS_KEY.consumeClick()) {
                if (client.gui.screen() == null || client.gui.screen() instanceof InventoryScreen) {
                    client.gui.setScreen(new CosmeticArmorScreen(client.gui.screen()));
                }
            }
        });

        LOGGER.info("[Lads SMP] Native Batch NB3 Client Systems initialized.");
    }
}

package com.thelads.smp.features.arise.nb3;

import com.thelads.smp.features.arise.nb3.backweapon.BackWeaponRenderLayer;
import com.thelads.smp.features.arise.nb3.immersiveui.ImmersiveUIState;
import com.thelads.smp.features.arise.nb3.obscuretooltips.ClientObscureTooltipComponent;
import com.thelads.smp.features.arise.nb3.obscuretooltips.ObscureTooltipComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Native Batch NB3:
 * - Registers BackWeaponRenderLayer on Player entities
 * - Registers ClientObscureTooltipComponent for item tooltips
 * - Registers Target Dummy entity renderer
 * - Updates ImmersiveUI animation tick state
 */
public final class AriseNb3Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB3Client");

    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        LOGGER.info("[Lads SMP] Initializing Native Batch NB3 Client Systems...");

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

        // Client tick for Immersive UI animations (screen shake, easing timers)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ImmersiveUIState.tick();
        });

        LOGGER.info("[Lads SMP] Native Batch NB3 Client Systems initialized.");
    }
}

package com.thelads.smp.mixin;

import dev.architectury.event.Event;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.InteractionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Custom Nether Portals 2.0.0 is built for 26.1.2 against Architectury 20: its BlockEvent.BREAK listener still takes the
 * removed IntValue xp argument and its InteractionEvent.RIGHT_CLICK_BLOCK listener still returns InteractionResult.
 * Architectury 22 calls the new signatures, so those lambdas throw AbstractMethodError on every block break and block
 * right click ("Exception in server tick loop"). Registers correctly typed listeners that run the mod's own handlers.
 * A listener that already implements the current signature (a fixed release of the mod) is registered unchanged.
 */
@Pseudo
@Mixin(targets = "net.night.customnetherportals.portal.event.PortalIgniteHandler", remap = false)
public class MixinCustomNetherPortalsEvents {

    @Redirect(method = "register", remap = false, require = 0, at = @At(value = "INVOKE", ordinal = 0, remap = false,
            target = "Ldev/architectury/event/Event;register(Ljava/lang/Object;)V"))
    private static void ladssmp$registerRightClick(Event<Object> event, Object listener) {
        if (ladssmp$implements(listener, InteractionEvent.RightClickBlock.class)) {
            event.register(listener);
            return;
        }
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) ->
                EventResult.fromMinecraft(MixinCustomNetherPortalsInvoker.ladssmp$onRightClickBlock(player, hand, pos, face)));
    }

    @Redirect(method = "register", remap = false, require = 0, at = @At(value = "INVOKE", ordinal = 1, remap = false,
            target = "Ldev/architectury/event/Event;register(Ljava/lang/Object;)V"))
    private static void ladssmp$registerBreak(Event<Object> event, Object listener) {
        if (ladssmp$implements(listener, BlockEvent.Break.class)) {
            event.register(listener);
            return;
        }
        BlockEvent.BREAK.register((level, pos, state, player) ->
                MixinCustomNetherPortalsInvoker.ladssmp$onBlockBreak(level, pos, state, player, null));
    }

    /** True when the listener class has a method matching the interface's single abstract method exactly (return type included). */
    @Unique
    private static boolean ladssmp$implements(Object listener, Class<?> iface) {
        for (java.lang.reflect.Method want : iface.getMethods()) {
            if (!java.lang.reflect.Modifier.isAbstract(want.getModifiers())) continue;
            for (java.lang.reflect.Method m : listener.getClass().getDeclaredMethods())
                if (m.getName().equals(want.getName()) && m.getReturnType() == want.getReturnType()
                        && java.util.Arrays.equals(m.getParameterTypes(), want.getParameterTypes())) return true;
            return false;
        }
        return false;
    }
}

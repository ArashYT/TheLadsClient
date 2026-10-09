package com.thelads.core.v1_20_1.game.skin;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thelads.core.v1_20_1.game.Gameplay1201;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (nothing is registered outside QA runs): "/ladsqa dummy &lt;distance&gt; &lt;turn&gt; [sneak]" stands a client-side player
 * that many blocks in front of the camera, turned so 0 shows its front, 90 a side and 180 its back, so the 3D layers of another
 * player can be photographed from any side; "/ladsqa nodummy" removes it.
 */
final class SkinLayersQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    private static final int ID = -42_185;

    private SkinLayersQa1201() {}

    static void register() {
        if (!Gameplay1201.qaActive()) return;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("ladsqa")
            .then(literal("nodummy").executes(context -> remove()))
            .then(literal("dummy").then(argument("distance", FloatArgumentType.floatArg(1, 64))
                .then(argument("turn", FloatArgumentType.floatArg(-360, 360))
                    .executes(context -> dummy(context, false))
                    .then(literal("sneak").executes(context -> dummy(context, true))))))));
    }

    private static int dummy(CommandContext<FabricClientCommandSource> context, boolean sneak) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return 0;
        remove();
        float distance = FloatArgumentType.getFloat(context, "distance"), turn = FloatArgumentType.getFloat(context, "turn");
        RemotePlayer dummy = new RemotePlayer(level, new GameProfile(UUID.nameUUIDFromBytes("lads-qa-dummy".getBytes(StandardCharsets.UTF_8)), "LadsQA")) {
            @Override public boolean isModelPartShown(PlayerModelPart part) { return true; } // a fresh RemotePlayer shows none
        };
        dummy.setId(ID); // ClientLevel.addPlayer only uses the id to drop an older entity, it does not assign it
        double yaw = Math.toRadians(player.getYRot());
        float facing = player.getYRot() + 180 + turn;
        dummy.moveTo(player.getX() - Math.sin(yaw) * distance, player.getY(), player.getZ() + Math.cos(yaw) * distance, facing, 0);
        dummy.yHeadRot = dummy.yHeadRotO = dummy.yBodyRot = dummy.yBodyRotO = facing;
        if (sneak) {
            dummy.setShiftKeyDown(true);
            dummy.setPose(Pose.CROUCHING); // RemotePlayer never recomputes its pose itself
        }
        level.addPlayer(ID, dummy);
        LOGGER.info("Lads qa skin dummy: {} blocks ahead, turned {} degrees{}", distance, turn, sneak ? ", sneaking" : "");
        return 1;
    }

    private static int remove() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(ID) != null) level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
        return 1;
    }
}

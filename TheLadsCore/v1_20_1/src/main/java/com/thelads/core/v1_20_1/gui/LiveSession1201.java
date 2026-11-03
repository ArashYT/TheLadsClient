package com.thelads.core.v1_20_1.gui;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.auth.LiveSession;
import com.thelads.core.v1_20_1.mixin.MinecraftSessionAccessor1201;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.network.chat.Component;

/** Minecraft.user and everything built from it at startup, swapped in the running game. */
final class LiveSession1201 implements LiveSession {
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public String username() {
        return mc.getUser().getName();
    }

    @Override
    public String uuid() {
        return mc.getUser().getUuid();
    }

    @Override
    public boolean inWorld() {
        return mc.level != null;
    }

    @Override
    public Runnable prepare(LadsAccount target, String accessToken) throws Exception {
        boolean offline = target.isOffline();
        // Asks Mojang for the account's properties: a token it refuses throws here, before anything changed.
        UserApiService api = offline ? UserApiService.OFFLINE : ((MinecraftSessionAccessor1201) mc).lads$authService().createUserApiService(accessToken);
        User user = new User(target.getUsername(), target.sessionUuid().toString().replace("-", ""), accessToken, Optional.empty(), Optional.empty(),
            offline ? User.Type.LEGACY : User.Type.MSA);
        return () -> {
            if (mc.level != null) leaveWorld();
            MinecraftSessionAccessor1201 game = (MinecraftSessionAccessor1201) mc;
            game.lads$setUser(user);
            game.lads$profileProperties().clear(); // Minecraft refills them for the new user when asked
            game.lads$setUserApiService(api);
            game.lads$setPlayerSocialManager(new PlayerSocialManager(mc, api));
            mc.getTelemetryManager().close();
            game.lads$setTelemetryManager(new ClientTelemetryManager(mc, api, user));
            game.lads$setProfileKeyPairManager(ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath()));
            game.lads$setReportingContext(ReportingContext.create(ReportEnvironment.local(), api));
            game.lads$setRealmsDataFetcher(new RealmsDataFetcher(RealmsClient.create(mc)));
        };
    }

    /** As the pause menu's Save and Quit / Disconnect: a singleplayer world is saved before this returns. */
    private void leaveWorld() {
        boolean local = mc.isLocalServer();
        mc.level.disconnect();
        if (local) mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        else mc.clearLevel();
        mc.setScreen(new TitleScreen());
    }
}

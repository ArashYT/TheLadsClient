package com.thelads.core.v1_21_11.gui;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.auth.LiveSession;
import com.thelads.core.v1_21_11.mixin.MinecraftSessionAccessor12111;
import com.thelads.core.v1_21_11.mixin.RealmsClientAccessor12111;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.util.Util;

/** Minecraft.user and everything built from it at startup, swapped in the running game. */
final class LiveSession12111 implements LiveSession {
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public String username() {
        return mc.getUser().getName();
    }

    @Override
    public String uuid() {
        return mc.getUser().getProfileId().toString();
    }

    @Override
    public boolean inWorld() {
        return mc.level != null;
    }

    @Override
    public Runnable prepare(LadsAccount target, String accessToken) throws Exception {
        boolean offline = target.isOffline();
        UserApiService api = offline ? UserApiService.OFFLINE : new YggdrasilAuthenticationService(mc.getProxy()).createUserApiService(accessToken);
        // Asks Mojang: a token it refuses throws here, before anything changed.
        UserApiService.UserProperties properties = offline ? UserApiService.OFFLINE_PROPERTIES : api.fetchProperties();
        User user = new User(target.getUsername(), target.sessionUuid(), accessToken, Optional.empty(), Optional.empty());
        CompletableFuture<ProfileResult> profile = CompletableFuture.supplyAsync(
            () -> mc.services().sessionService().fetchProfile(user.getProfileId(), true), Util.nonCriticalIoPool());
        return () -> {
            if (mc.level != null) {
                mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE); // saves a singleplayer world before it returns
                mc.setScreen(new TitleScreen());
            }
            MinecraftSessionAccessor12111 game = (MinecraftSessionAccessor12111) mc;
            game.lads$setUser(user);
            game.lads$setProfileFuture(profile);
            game.lads$setUserApiService(api);
            game.lads$setUserPropertiesFuture(CompletableFuture.completedFuture(properties));
            game.lads$setPlayerSocialManager(new PlayerSocialManager(mc, api));
            mc.getTelemetryManager().close();
            game.lads$setTelemetryManager(new ClientTelemetryManager(mc, api, user));
            game.lads$setProfileKeyPairManager(offline ? ProfileKeyPairManager.EMPTY_KEY_MANAGER : ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath()));
            game.lads$setReportingContext(ReportingContext.create(ReportEnvironment.local(), api));
            RealmsClientAccessor12111.lads$setInstance(null);
            game.lads$setRealmsDataFetcher(new RealmsDataFetcher(RealmsClient.getOrCreate(mc)));
        };
    }
}

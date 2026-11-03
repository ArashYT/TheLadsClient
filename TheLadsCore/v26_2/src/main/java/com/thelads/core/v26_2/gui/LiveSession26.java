package com.thelads.core.v26_2.gui;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.auth.LiveSession;
import com.thelads.core.v26_2.mixin.MinecraftSessionAccessor26;
import com.thelads.core.v26_2.mixin.RealmsClientAccessor26;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.util.Util;

/** Minecraft.user and everything built from it at startup, swapped in the running game. */
final class LiveSession26 implements LiveSession {
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
        YggdrasilAuthenticationService services = offline ? YggdrasilAuthenticationService.createOffline(mc.getProxy()) : new YggdrasilAuthenticationService(mc.getProxy());
        UserApiService api = offline ? UserApiService.OFFLINE : services.createUserApiService(accessToken);
        // Asks Mojang: a token it refuses throws here, before anything changed.
        UserApiService.UserProperties properties = offline ? UserApiService.OFFLINE_PROPERTIES : api.fetchProperties();
        FriendsService friends = services.createFriendsService(accessToken);
        User user = new User(target.getUsername(), target.sessionUuid(), accessToken, Optional.empty(), Optional.empty());
        CompletableFuture<ProfileResult> profile = CompletableFuture.supplyAsync(
            () -> mc.services().sessionService().fetchProfile(user.getProfileId(), true), Util.nonCriticalIoPool());
        return () -> {
            if (mc.level != null) {
                mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE); // saves a singleplayer world before it returns
                mc.setScreenAndShow(new TitleScreen());
            }
            MinecraftSessionAccessor26 game = (MinecraftSessionAccessor26) mc;
            game.lads$setUser(user);
            if (mc.gui != null) ((MinecraftSessionAccessor26.Splash) mc.gui.splashManager()).lads$setUser(user);
            game.lads$setProfileFuture(profile);
            game.lads$setUserApiService(api);
            game.lads$setUserPropertiesFuture(CompletableFuture.completedFuture(properties));
            game.lads$friendListHandler().close();
            RemoteFriendListUpdateHandler handler = new RemoteFriendListUpdateHandler(friends, mc);
            game.lads$setFriendListHandler(handler);
            PlayerSocialManager social = new PlayerSocialManager(mc, api, friends, handler);
            game.lads$setPlayerSocialManager(social);
            if (social.isFriendListEnabled()) handler.start();
            mc.getTelemetryManager().close();
            game.lads$setTelemetryManager(new ClientTelemetryManager(mc, api, user));
            game.lads$setProfileKeyPairManager(offline ? ProfileKeyPairManager.EMPTY_KEY_MANAGER : ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath()));
            game.lads$setReportingContext(ReportingContext.create(ReportEnvironment.local(), api));
            RealmsClientAccessor26.lads$setInstance(null);
            game.lads$setRealmsDataFetcher(new RealmsDataFetcher(RealmsClient.getOrCreate(mc)));
        };
    }
}

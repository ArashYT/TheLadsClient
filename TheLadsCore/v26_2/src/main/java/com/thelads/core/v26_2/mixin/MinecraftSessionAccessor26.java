package com.thelads.core.v26_2.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** All of Minecraft that is built from the logged-in account, for an account switch without a restart (LiveSession26). */
@Mixin(Minecraft.class)
public interface MinecraftSessionAccessor26 {
    @Accessor("remoteFriendListUpdateHandler") RemoteFriendListUpdateHandler lads$friendListHandler();
    @Mutable @Accessor("remoteFriendListUpdateHandler") void lads$setFriendListHandler(RemoteFriendListUpdateHandler handler);
    @Accessor("services") Services lads$services();
    @Mutable @Accessor("services") void lads$setServices(Services services);
    @Mutable @Accessor("user") void lads$setUser(User user);
    @Accessor("profileFuture") CompletableFuture<ProfileResult> lads$profileFuture();
    @Mutable @Accessor("profileFuture") void lads$setProfileFuture(CompletableFuture<ProfileResult> future);
    @Accessor("userApiService") UserApiService lads$userApiService();
    @Mutable @Accessor("userApiService") void lads$setUserApiService(UserApiService service);
    @Accessor("userPropertiesFuture") CompletableFuture<UserApiService.UserProperties> lads$userPropertiesFuture();
    @Mutable @Accessor("userPropertiesFuture") void lads$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);
    @Mutable @Accessor("playerSocialManager") void lads$setPlayerSocialManager(PlayerSocialManager manager);
    @Mutable @Accessor("telemetryManager") void lads$setTelemetryManager(ClientTelemetryManager manager);
    @Mutable @Accessor("profileKeyPairManager") void lads$setProfileKeyPairManager(ProfileKeyPairManager manager);
    @Mutable @Accessor("realmsDataFetcher") void lads$setRealmsDataFetcher(RealmsDataFetcher fetcher);
    @Accessor("reportingContext") void lads$setReportingContext(ReportingContext context);

    @Mixin(SplashManager.class)
    interface Splash {
        @Mutable @Accessor("user") void lads$setUser(User user);
    }
}

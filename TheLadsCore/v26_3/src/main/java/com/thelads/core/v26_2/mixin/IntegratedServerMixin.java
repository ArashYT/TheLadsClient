package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.AutoBackupHelper;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.AutoBackupModule;
import com.thelads.core.v26_2.social.P2PHostState26;
import java.io.File;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Inject(method = "stopServer()V", at = @At("TAIL"))
    private void lads$autoBackupOnClose(CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule(AutoBackupModule.NAME);
        if (m instanceof AutoBackupModule abm && abm.isEnabled()) {
            File worldDir = ((IntegratedServer) (Object) this).getWorldPath(LevelResource.ROOT).toFile();
            AutoBackupHelper.backupWorldAsync(worldDir, abm.getMaxBackups());
        }
    }

    @Redirect(
        method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V"),
        require = 1
    )
    private void ladsSuppressLanBroadcast(LanServerPinger pinger) {
        if (!P2PHostState26.isLoopbackScope()) {
            pinger.start();
        }
    }
}

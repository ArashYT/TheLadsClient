package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.client.AutoBackupHelper;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.AutoBackupModule;
import java.io.File;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}

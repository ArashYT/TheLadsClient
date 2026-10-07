package com.thelads.core.v1_8_9.mixin;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerListEntryNormal.class)
public abstract class ServerListEntryNormalMixin {
    @Shadow @Final private ServerData field_148301_e;

    @Unique
    private static final ExecutorService ladsPingerPool = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("Lads Server Pinger #%d").setDaemon(true).build()
    );

    @Unique private String ladsPreviousMOTD;
    @Unique private String ladsPreviousPop;

    @Inject(method = "drawEntry", at = @At("HEAD"))
    private void ladsPreserveBeforeDraw(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        if (this.field_148301_e != null) {
            if (this.field_148301_e.serverMOTD != null && !this.field_148301_e.serverMOTD.isEmpty()) {
                this.ladsPreviousMOTD = this.field_148301_e.serverMOTD;
            }
            if (this.field_148301_e.populationInfo != null && !this.field_148301_e.populationInfo.isEmpty()) {
                this.ladsPreviousPop = this.field_148301_e.populationInfo;
            }
        }
    }

    @Redirect(method = "drawEntry", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ThreadPoolExecutor;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"))
    private Future<?> ladsParallelPing(ThreadPoolExecutor executor, Runnable task) {
        if (this.field_148301_e != null) {
            // Restore previous MOTD and population while the refresh ping is running so the entry does not blank out or flicker
            if ((this.field_148301_e.serverMOTD == null || this.field_148301_e.serverMOTD.isEmpty()) && this.ladsPreviousMOTD != null) {
                this.field_148301_e.serverMOTD = this.ladsPreviousMOTD;
            }
            if ((this.field_148301_e.populationInfo == null || this.field_148301_e.populationInfo.isEmpty()) && this.ladsPreviousPop != null) {
                this.field_148301_e.populationInfo = this.ladsPreviousPop;
            }
        }
        return ladsPingerPool.submit(task);
    }
}

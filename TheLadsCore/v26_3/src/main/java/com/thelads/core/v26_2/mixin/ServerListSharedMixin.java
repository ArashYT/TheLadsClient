package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.shared.ServerListMerge;
import com.thelads.core.shared.SharedContentPaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.objectweb.asm.Opcodes;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * servers.dat lives in the shared Minecraft folder (LADS_GLOBAL_MINECRAFT_DIR) for every Lads version. Each save holds the
 * lock the launcher also takes, re-reads the file and keeps servers another running game added, edited or removed meanwhile.
 * Without the environment variable every handler leaves vanilla behaviour untouched.
 */
@Mixin(ServerList.class)
public abstract class ServerListSharedMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<ServerData> serverList;
    @Shadow @Final private List<ServerData> hiddenServerList;
    /** Address key to fingerprint of every server this list last read from or wrote to disk. */
    @Unique private Map<String, String> ladsLoaded = Map.of();

    @Shadow public abstract void add(ServerData data, boolean hidden);

    // The only gameDirectory read in each method: the temp file and Util.safeReplaceFile stay inside the shared folder.
    @ModifyExpressionValue(method = {"load", "save"}, at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Minecraft;gameDirectory:Ljava/io/File;", opcode = Opcodes.GETFIELD), require = 2)
    private File ladsSharedServersDirectory(File gameDirectory) {
        return SharedContentPaths.redirectEnabled() ? SharedContentPaths.root().toFile() : gameDirectory;
    }

    @Inject(method = "load", at = @At("RETURN"), require = 1)
    private void ladsRememberLoadedServers(CallbackInfo ci) {
        if (!SharedContentPaths.redirectEnabled()) return;
        try {
            ladsLoaded = ladsSnapshot(serverList, hiddenServerList);
        } catch (RuntimeException failure) {
            // Without a snapshot every server counts as added here, so the next save removes and overwrites nothing.
            LoggerFactory.getLogger("TheLadsCore").error("Could not remember the loaded shared server list", failure);
        }
    }

    @WrapMethod(method = "save", require = 1)
    private void ladsSaveSharedServers(Operation<Void> original) {
        if (!SharedContentPaths.redirectEnabled()) {
            original.call();
            return;
        }
        boolean[] saving = {false};
        try {
            SharedContentPaths.withServersLock(() -> {
                ladsMergeFromDisk();
                saving[0] = true;
                original.call();
                ladsRefreshSnapshot();
                return null;
            });
        } catch (IOException | RuntimeException lockFailure) {
            LoggerFactory.getLogger("TheLadsCore").warn(saving[0] ? "Shared server list saved; releasing its lock failed"
                : "Shared server list lock failed; saving without it", lockFailure);
            if (!saving[0]) original.call();
        }
    }

    @Unique
    private void ladsMergeFromDisk() {
        try {
            ServerListAccessor disk = ladsReadDisk();
            List<ServerData> diskHidden = disk == null ? List.of() : disk.ladsHiddenServers();
            List<ServerData> diskEntries = disk == null ? List.of() : ladsEntries(disk.ladsServers(), diskHidden);
            var plan = ServerListMerge.plan(ladsLoaded, ladsEntries(serverList, hiddenServerList), diskEntries, disk != null,
                data -> data.ip, data -> ladsFingerprint(data, hiddenServerList.contains(data) || diskHidden.contains(data)));
            for (ServerData removed : plan.toRemove()) {
                serverList.remove(removed);
                hiddenServerList.remove(removed);
            }
            for (var adopt : plan.toAdopt()) ladsAdopt(adopt.current(), adopt.disk(), diskHidden.contains(adopt.disk()));
            for (ServerData added : plan.toAdd()) add(added, diskHidden.contains(added));
            if (plan.isEmpty()) return;
            LoggerFactory.getLogger("TheLadsCore").info("Shared server list: kept {} server(s) added, took {} edited and dropped {} removed by another game",
                plan.toAdd().size(), plan.toAdopt().size(), plan.toRemove().size());
            ladsRefreshOpenScreen();
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Could not merge the shared server list; saving this game's list unchanged", failure);
        }
    }

    /**
     * Takes another game's version of a server this game left alone. The object stays the same, because the multiplayer
     * screen's rows and an open edit hold it.
     */
    @Unique
    private void ladsAdopt(ServerData local, ServerData newer, boolean hidden) {
        local.copyFrom(newer); // address, name, icon, resource-pack answer and type
        ((ServerDataAccessor) local).ladsSetAcceptedCodeOfConduct(((ServerDataAccessor) newer).ladsAcceptedCodeOfConduct());
        if (hidden != hiddenServerList.contains(local)) {
            (hidden ? serverList : hiddenServerList).remove(local);
            (hidden ? hiddenServerList : serverList).add(local);
        }
    }

    /**
     * The multiplayer screen's rows index into this list (move up/down pass row indices to swap), and swap and the icon
     * update save without rebuilding them. After a merge changed the list while that screen is open, rebuild the rows on
     * the next tick, once the current click has finished, so row indices match the list again.
     */
    @Unique
    private void ladsRefreshOpenScreen() {
        minecraft.schedule(() -> {
            if (minecraft.gui.screen() instanceof JoinMultiplayerScreen screen && screen.getServers() == (Object) this) {
                for (var child : screen.children()) {
                    if (child instanceof ServerSelectionList rows) rows.updateOnlineServers(screen.getServers());
                }
            }
        });
    }

    @Unique
    private void ladsRefreshSnapshot() {
        try {
            ServerListAccessor disk = ladsReadDisk();
            ladsLoaded = ServerListMerge.snapshotAfterSave(ladsLoaded,
                disk == null ? Map.of() : ladsSnapshot(disk.ladsServers(), disk.ladsHiddenServers()), disk != null);
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Could not re-read the shared server list after saving", failure);
        }
    }

    /**
     * A fresh list read from the shared servers.dat, or null when the file is missing or cannot be parsed: vanilla reads both
     * as an empty list, and only a readable empty list means another game removed every server.
     */
    @Unique
    private ServerListAccessor ladsReadDisk() {
        Path file = SharedContentPaths.serversFile();
        try {
            if (!Files.isRegularFile(file) || NbtIo.read(file) == null) return null;
        } catch (IOException | RuntimeException unreadable) {
            LoggerFactory.getLogger("TheLadsCore").warn("Could not read the shared server list {}; nothing is removed from this game's list", file, unreadable);
            return null;
        }
        ServerList disk = new ServerList(minecraft);
        disk.load();
        return (ServerListAccessor) disk;
    }

    @Unique
    private static Map<String, String> ladsSnapshot(List<ServerData> visible, List<ServerData> hidden) {
        return ServerListMerge.snapshot(ladsEntries(visible, hidden), data -> data.ip, data -> ladsFingerprint(data, hidden.contains(data)));
    }

    /** Every stored field and the hidden flag. Not the icon: pings refresh it in every running game, which is no edit. */
    @Unique
    private static String ladsFingerprint(ServerData data, boolean hidden) {
        CompoundTag tag = data.write();
        tag.remove("icon");
        return tag + (hidden ? " hidden" : "");
    }

    @Unique
    private static List<ServerData> ladsEntries(List<ServerData> visible, List<ServerData> hidden) {
        List<ServerData> all = new ArrayList<>(visible);
        all.addAll(hidden);
        return all;
    }
}

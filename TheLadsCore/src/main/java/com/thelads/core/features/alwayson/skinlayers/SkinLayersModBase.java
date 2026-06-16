package com.thelads.core.features.alwayson.skinlayers;

import com.google.common.collect.Sets;
import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerSettings;
import com.thelads.core.features.alwayson.skinlayers.versionless.ModBase;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public abstract class SkinLayersModBase extends ModBase {
    public static SkinLayersModBase instance;
    public static final Set<Item> hideHeadLayers;

    protected SkinLayersModBase() {
        instance = this;
    }

    public void refreshLayers(Player player) {
        if (player == null || !(player instanceof PlayerSettings)) {
            return;
        }
        PlayerSettings settings = (PlayerSettings) player;
        settings.clearMeshes();
        settings.setCurrentSkin(null);
    }

    static {
        hideHeadLayers = Sets.newHashSet(new Item[]{Items.ZOMBIE_HEAD, Items.CREEPER_HEAD, Items.DRAGON_HEAD, Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL});
    }
}

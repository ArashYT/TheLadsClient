package com.thelads.core.v1_8_9.cit.mixin;

import java.io.File;
import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Custom Item Textures: a pack's zip or folder, to list its CIT folders (Minecraft 1.8.9 cannot list resources). */
@Mixin(AbstractResourcePack.class)
public interface AbstractResourcePackCitAccessor189 {
    @Accessor("resourcePackFile")
    File lads$citPackFile();
}

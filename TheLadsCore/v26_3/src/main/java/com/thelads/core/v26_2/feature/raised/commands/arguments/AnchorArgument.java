// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.commands.arguments;

import com.mojang.brigadier.context.CommandContext;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.arguments.StringRepresentableArgument;

public class AnchorArgument extends StringRepresentableArgument<Layer.Anchor> {

    public AnchorArgument() {
        super(Layer.Anchor.CODEC, Layer.Anchor::values);
    }

    public static StringRepresentableArgument<Layer.Anchor> anchor() {
        return new AnchorArgument();
    }

    public static Layer.Anchor getAnchor(CommandContext<FabricClientCommandSource> context, String id) {
        return context.getArgument(id, Layer.Anchor.class);
    }

}

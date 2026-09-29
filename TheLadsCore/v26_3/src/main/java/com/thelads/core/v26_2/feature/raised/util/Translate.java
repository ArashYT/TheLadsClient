// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.util;

import com.thelads.core.v26_2.feature.raised.config.Config;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Translate {

    public static int getX(Identifier layerName) {
        return getX(layerName.toString());
    }

    public static int getX(String layerName) {
        if (!com.thelads.core.v26_2.feature.raised.NativeRaised.enabled() || !Config.getOptions().getLayers().containsKey(layerName)) return 0;
        int offset = findGroupsWithLayer(layerName).stream().mapToInt(groupName -> Optional.of(Config.getOptions().getGroups().get(groupName).getOffset().getX()).orElse(0)).sum();
        int anchor = Config.getOptions().getLayers().get(layerName).getAnchor().getX();
        return offset * anchor;
    }

    public static int getY(Identifier layerName) {
        return getY(layerName.toString());
    }

    public static int getY(String layerName) {
        if (!com.thelads.core.v26_2.feature.raised.NativeRaised.enabled() || !Config.getOptions().getLayers().containsKey(layerName)) return 0;
        int offset = findGroupsWithLayer(layerName).stream().mapToInt(groupName -> Optional.of(Config.getOptions().getGroups().get(groupName).getOffset().getY()).orElse(0)).sum();
        int anchor = Config.getOptions().getLayers().get(layerName).getAnchor().getY();
        return offset * anchor;
    }

    public static void start(Matrix3x2fStack matrix3x2fStack, Identifier layerName) {
        start(matrix3x2fStack, layerName.toString());
    }

    public static void start(Matrix3x2fStack matrix3x2fStack, String layerName) {
        int x = getX(layerName);
        int y = getY(layerName);

        if (!(x == 0 && y == 0)) {
            matrix3x2fStack.pushMatrix();
            matrix3x2fStack.translate(x, y);
        }
    }

    public static void end(Matrix3x2fStack matrix3x2fStack, Identifier layerName) {
        end(matrix3x2fStack, layerName.toString());
    }

    public static void end(Matrix3x2fStack matrix3x2fStack, String layerName) {
        int x = getX(layerName);
        int y = getY(layerName);

        if (!(x == 0 && y == 0)) {
            matrix3x2fStack.popMatrix();
        }
    }

    public static List<String> findGroupsWithLayer(String layerName) {
        return Config.getOptions().getGroups().entrySet().stream()
                .filter(entry -> entry.getValue().getLayers().contains(layerName))
                .map(Map.Entry::getKey)
                .toList();
    }

}

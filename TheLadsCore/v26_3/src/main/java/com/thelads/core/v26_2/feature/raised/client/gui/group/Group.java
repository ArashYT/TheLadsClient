// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client.gui.group;

import java.util.TreeSet;

public class Group {

    public Offset offset;
    public TreeSet<String> layers;

    public Group(Offset offset, TreeSet<String> layers) {
        this.offset = offset;
        this.layers = layers;
    }

    public Offset getOffset() {
        return offset;
    }

    public void setOffset(Offset offset) {
        this.offset = offset;
    }

    public TreeSet<String> getLayers() {
        return layers;
    }

    public void setLayers(TreeSet<String> layers) {
        this.layers = layers;
    }

    public static class Offset {

        public int x;
        public int y;

        public Offset(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

    }

}
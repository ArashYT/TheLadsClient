package com.thelads.core.features.alwayson.skinlayers.versionless.util;

public enum Direction {
    DOWN(Axis.Y, 0, -1, 0),
    UP(Axis.Y, 0, 1, 0),
    NORTH(Axis.Z, 0, 0, -1),
    SOUTH(Axis.Z, 0, 0, 1),
    WEST(Axis.X, -1, 0, 0),
    EAST(Axis.X, 1, 0, 0);

    private static final Direction[] opposite = new Direction[]{UP, DOWN, SOUTH, NORTH, EAST, WEST};
    private final Axis axis;
    private final int x;
    private final int y;
    private final int z;

    private Direction(Axis axis, int x, int y, int z) {
        this.axis = axis;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Direction getOpposite() {
        return opposite[this.ordinal()];
    }

    public Axis getAxis() {
        return this.axis;
    }

    public int getStepX() {
        return this.x;
    }

    public int getStepY() {
        return this.y;
    }

    public int getStepZ() {
        return this.z;
    }

    public int getDirStep() {
        return this.x + this.y + this.z;
    }

    public enum Axis {
        X {
            @Override
            public int choose(int i, int j, int k) {
                return i;
            }

            @Override
            public double choose(double d, double e, double f) {
                return d;
            }
        },
        Y {
            @Override
            public int choose(int i, int j, int k) {
                return j;
            }

            @Override
            public double choose(double d, double e, double f) {
                return e;
            }
        },
        Z {
            @Override
            public int choose(int i, int j, int k) {
                return k;
            }

            @Override
            public double choose(double d, double e, double f) {
                return f;
            }
        };

        public static final Axis[] VALUES = Axis.values();

        public abstract int choose(int var1, int var2, int var3);

        public abstract double choose(double var1, double var3, double var5);
    }
}

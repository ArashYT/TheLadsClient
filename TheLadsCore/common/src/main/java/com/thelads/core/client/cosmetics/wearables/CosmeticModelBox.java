package com.thelads.core.client.cosmetics.wearables;

/**
 * A textured 3D cuboid definition for wearable cosmetics, relative to the attached bone (e.g. head).
 * Coordinates are in Minecraft model space (1 unit = 1 pixel / 1/16 block).
 */
public record CosmeticModelBox(float x, float y, float z, float w, float h, float d, int u, int v) {}

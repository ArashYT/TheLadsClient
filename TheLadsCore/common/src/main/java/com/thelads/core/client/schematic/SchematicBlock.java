package com.thelads.core.client.schematic;

/**
 * A block entry inside a schematic blueprint.
 */
public record SchematicBlock(int relX, int relY, int relZ, int blockId, String blockName, int colorArgb) {}

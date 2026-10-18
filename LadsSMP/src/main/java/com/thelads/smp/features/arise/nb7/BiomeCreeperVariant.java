package com.thelads.smp.features.arise.nb7;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/**
 * All 15 biome-specific creeper variants matching Arise SMP Creeper Overhaul.
 */
public enum BiomeCreeperVariant {
    DESERT("desert", "Desert Creeper", 0xD7BE82, 0x3C2D1E),
    SNOWY("snowy", "Snowy Creeper", 0xD2E6F0, 0x1E324B),
    BADLANDS("badlands", "Badlands Creeper", 0x914128, 0x2D1914),
    JUNGLE("jungle", "Jungle Creeper", 0x2D8728, 0x0F280F),
    SWAMP("swamp", "Swamp Creeper", 0x556932, 0x1E2314),
    CAVE("cave", "Cave Creeper", 0x737373, 0x191919),
    DRIPSTONE("dripstone", "Dripstone Creeper", 0x875F4B, 0x281E19),
    MUSHROOM("mushroom", "Mushroom Creeper", 0x6E6473, 0xB42828),
    SAVANNAH("savannah", "Savanna Creeper", 0xAF8C46, 0x322314),
    BEACH("beach", "Beach Creeper", 0xE1CD91, 0x282D2D),
    OCEAN("ocean", "Ocean Creeper", 0x194B78, 0x0A1423),
    DARK_OAK("dark_oak", "Dark Oak Creeper", 0x3C2D1E, 0x0F0F0F),
    BAMBOO("bamboo", "Bamboo Creeper", 0x8CBC3C, 0x1E3214),
    HILLS("hills", "Hills Creeper", 0x878C91, 0x232328),
    SPRUCE("spruce", "Spruce Creeper", 0x284B32, 0x141E19);

    private final String id;
    private final String englishName;
    private final int primaryEggColor;
    private final int secondaryEggColor;
    private final Identifier textureLocation;

    private EntityType<BiomeCreeperEntity> entityType;
    private Item spawnEggItem;

    BiomeCreeperVariant(String id, String englishName, int primaryEggColor, int secondaryEggColor) {
        this.id = id;
        this.englishName = englishName;
        this.primaryEggColor = primaryEggColor;
        this.secondaryEggColor = secondaryEggColor;
        this.textureLocation = Identifier.fromNamespaceAndPath("ladssmp", "textures/entity/creeper/" + id + "_creeper.png");
    }

    public String getId() {
        return id;
    }

    public String getEnglishName() {
        return englishName;
    }

    public int getPrimaryEggColor() {
        return primaryEggColor;
    }

    public int getSecondaryEggColor() {
        return secondaryEggColor;
    }

    public Identifier getTextureLocation() {
        return textureLocation;
    }

    public EntityType<BiomeCreeperEntity> getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType<BiomeCreeperEntity> entityType) {
        this.entityType = entityType;
    }

    public Item getSpawnEggItem() {
        return spawnEggItem;
    }

    public void setSpawnEggItem(Item spawnEggItem) {
        this.spawnEggItem = spawnEggItem;
    }

    /**
     * Matches the given biome and position to the appropriate variant.
     */
    public static BiomeCreeperVariant findVariantForBiome(Holder<Biome> biomeHolder, BlockPos pos, LevelReader level) {
        if (biomeHolder == null) return null;
        String path = biomeHolder.unwrapKey().map(k -> k.identifier().getPath().toLowerCase()).orElse("");

        // Check ocean first
        if (path.contains("ocean")) {
            return OCEAN;
        }
        if (path.contains("beach")) {
            return BEACH;
        }
        if (path.contains("dripstone")) {
            return DRIPSTONE;
        }
        if (path.contains("mushroom")) {
            return MUSHROOM;
        }
        if (path.contains("badlands") || path.contains("mesa")) {
            return BADLANDS;
        }
        if (path.contains("desert")) {
            return DESERT;
        }
        if (path.contains("snow") || path.contains("frozen") || path.contains("ice") || path.contains("jagged_peaks")) {
            return SNOWY;
        }
        if (path.contains("bamboo")) {
            return BAMBOO;
        }
        if (path.contains("jungle")) {
            return JUNGLE;
        }
        if (path.contains("swamp")) {
            return SWAMP;
        }
        if (path.contains("savanna")) {
            return SAVANNAH;
        }
        if (path.contains("dark_forest")) {
            return DARK_OAK;
        }
        if (path.contains("taiga") || path.contains("spruce") || path.contains("pine")) {
            return SPRUCE;
        }
        if (path.contains("hills") || path.contains("peak") || path.contains("slopes") || path.contains("mountain") || path.contains("windswept")) {
            return HILLS;
        }
        // Subterranean / cave check
        if (pos != null && level != null && pos.getY() < 50 && !level.canSeeSky(pos)) {
            return CAVE;
        }
        return null;
    }
}

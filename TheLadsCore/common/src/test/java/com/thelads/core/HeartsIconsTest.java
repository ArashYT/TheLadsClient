package com.thelads.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.HeartsHudElement;
import com.thelads.core.client.hud.HudExtras;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Hearts widget draws vanilla's heart and armour icons (sprite and icons.png region), not coloured squares. */
class HeartsIconsTest {
    private final List<String> icons = new ArrayList<>();
    private final LadsGraphicsTest.MockGraphics graphics = new LadsGraphicsTest.MockGraphics() {
        @Override public void drawGuiIcon(String sprite, int u, int v, int x, int y, int width, int height) {
            icons.add(sprite + "@" + u + "," + v);
        }
        @Override public LadsGameBridge getGame() { return game; }
    };
    private final Game game = new Game();

    private static class Game extends DefaultGameBridge {
        float health = 7, absorption = 3;
        int armor = 5;
        boolean hardcore;
        @Override public float getHealth() { return health; }
        @Override public float getMaxHealth() { return 20; }
        @Override public float getAbsorption() { return absorption; }
        @Override public int getArmorValue() { return armor; }
        @Override public boolean isHardcore() { return hardcore; }
    }

    @AfterEach
    void restore() {
        HudExtras.VANILLA.setEnabled(false);
        HudExtras.VANILLA.refresh();
    }

    private List<String> draw() {
        icons.clear();
        HeartsHudElement hearts = new HeartsHudElement();
        hearts.setModuleName("Hearts");
        hearts.prepareRender(graphics, false);
        hearts.render(graphics);
        return icons;
    }

    @Test
    void healthAbsorptionAndArmourAreVanillasIcons() {
        List<String> drawn = draw();
        // Armour 5: two full, one half, seven empty.
        assertEquals("hud/armor_full@34,9", drawn.get(0));
        assertEquals("hud/armor_half@25,9", drawn.get(2));
        assertEquals("hud/armor_empty@16,9", drawn.get(3));
        // Absorption 3: a full and a half golden heart, each on a container.
        assertEquals(List.of("hud/heart/container@16,0", "hud/heart/absorbing_full@160,0", "hud/heart/container@16,0",
            "hud/heart/absorbing_half@169,0"), drawn.subList(10, 14));
        // Health 7 of 20: three full, a half, six empty containers.
        List<String> health = drawn.subList(14, drawn.size());
        assertEquals(10, health.stream().filter(s -> s.startsWith("hud/heart/container")).count());
        assertEquals(3, health.stream().filter(s -> s.equals("hud/heart/full@52,0")).count());
        assertEquals(1, health.stream().filter(s -> s.equals("hud/heart/half@61,0")).count());
    }

    @Test
    void hardcoreWorldsAndAlwaysHardcoreHeartsUseTheHardcoreIcons() {
        game.hardcore = true;
        assertTrue(draw().contains("hud/heart/hardcore_full@52,45"));
        assertTrue(icons.contains("hud/heart/absorbing_hardcore_half@169,45"));
        game.hardcore = false;
        HudExtras.VANILLA.setEnabled(true);
        HudExtras.VANILLA.hardcore.set(true);
        HudExtras.VANILLA.refresh();
        assertTrue(draw().contains("hud/heart/container_hardcore@16,45"));
        HudExtras.VANILLA.hardcore.set(false);
    }
}

package com.thelads.core;

import com.thelads.core.client.hud.ArmorHudElement;
import com.thelads.core.client.hud.ArmorHudElement.StripPiece;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.9.14: Unit tests for the Armor HUD hotbar-ends strip UV maths (pure function).
 * The 4-slot strip takes the left 2 slots from the hotbar texture's left end (incl. left outer border)
 * and the right 2 slots from the hotbar texture's right end (incl. right outer border),
 * joined seamlessly with no double border in the middle.
 */
class ArmorStripTest {

    @Test
    void fourSlotStripPiecesAreExact41pxHalves() {
        List<StripPiece> pieces = ArmorHudElement.calculateStripPieces();
        assertEquals(2, pieces.size(), "4-slot strip must consist of exactly 2 pieces");

        StripPiece left = pieces.get(0);
        StripPiece right = pieces.get(1);

        // Left piece: left end cap (1 px) + slot 0 (20 px) + slot 1 (20 px) = 41 px
        assertEquals(0, left.xOffset(), "Left piece begins at screen offset 0");
        assertEquals(0, left.u(), "Left piece source u starts at 0 (left border)");
        assertEquals(41, left.width(), "Left piece width is 41 pixels");

        // Right piece: slot 7 (20 px) + slot 8 (20 px) + right end cap (1 px) = 41 px
        // In the 182 px hotbar, rightmost 41 pixels are 182 - 41 = 141..181
        assertEquals(41, right.xOffset(), "Right piece begins at screen offset 41");
        assertEquals(141, right.u(), "Right piece source u starts at 141");
        assertEquals(41, right.width(), "Right piece width is 41 pixels");

        // Total width matches SLOTS_WIDTH = 82
        assertEquals(82, left.width() + right.width(), "Combined width must equal 82 px");
        assertEquals(left.xOffset() + left.width(), right.xOffset(), "Pieces join seamlessly without gaps or overlaps");
    }

    @Test
    void hotbarTextureBoundsArePreserved() {
        List<StripPiece> pieces = ArmorHudElement.calculateStripPieces();
        for (StripPiece piece : pieces) {
            assertTrue(piece.u() >= 0, "Source u must be non-negative");
            assertTrue(piece.u() + piece.width() <= 182, "Source u + width must not exceed 182 px hotbar width");
            assertTrue(piece.width() > 0, "Piece width must be positive");
        }
        // Right piece must end exactly at the rightmost pixel (column 181, width 182)
        StripPiece right = pieces.get(1);
        assertEquals(182, right.u() + right.width(), "Right piece must terminate exactly at hotbar right border");
    }

    @Test
    void middleJoinHasNoDoubleDivider() {
        List<StripPiece> pieces = ArmorHudElement.calculateStripPieces();
        StripPiece left = pieces.get(0);
        StripPiece right = pieces.get(1);

        // In a vanilla 182 px hotbar:
        // Column 0 is outer border.
        // Columns 1..20 is slot 0 (divider between slot 0 and 1 is at col 20).
        // Columns 21..40 is slot 1 (divider between slot 1 and 2 is at col 40).
        // The left piece spans columns 0..40 (41 px), ending with the 1 px divider at col 40.
        int leftEndCol = left.u() + left.width() - 1;
        assertEquals(40, leftEndCol, "Left piece ends on slot 1's right divider (col 40)");

        // Slot 6 right divider is at col 140.
        // Slot 7 cavity begins at col 141.
        // The right piece begins at col 141, omitting the col 140 divider!
        assertEquals(141, right.u(), "Right piece begins at col 141, omitting col 140 divider");

        // When drawn side-by-side:
        // x = 0..39: left border and slots 0-1 cavity
        // x = 40: exactly 1 px divider from left piece col 40
        // x = 41..80: slots 2-3 cavity and col 60 divider
        // x = 81: right outer border from right piece col 181
        // Thus, there is NO double divider at the seam!
    }

    @Test
    void nonFourSlotFallback() {
        List<StripPiece> pieces = ArmorHudElement.calculateStripPieces(2);
        assertEquals(2, pieces.size());
        assertEquals(0, pieces.get(0).u());
        assertEquals(1 + 2 * 20, pieces.get(0).width()); // 41
        assertEquals(181, pieces.get(1).u());
        assertEquals(1, pieces.get(1).width());
    }
}

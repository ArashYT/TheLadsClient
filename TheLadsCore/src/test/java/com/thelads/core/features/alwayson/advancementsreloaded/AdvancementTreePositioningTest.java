package com.thelads.core.features.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdvancementTreePositioningTest {

    private Map<AdvancementNode, List<AdvancementNode>> childrenMap;
    private Map<AdvancementNode, AdvancementNode> rootMap;

    @BeforeAll
    public static void setupAll() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    public void setup() {
        Configuration.advancementsOrder = Configuration.AdvancementOrder.ALPHABETIC;
        Configuration.customAdvancementsOrder = List.of();
        childrenMap = new HashMap<>();
        rootMap = new HashMap<>();
    }

    private AdvancementNode createMockNode(String id, String title, AdvancementNode parentNode) {
        AdvancementNode node = mock(AdvancementNode.class);
        AdvancementHolder holder = mock(AdvancementHolder.class);
        Advancement advancement = mock(Advancement.class);
        DisplayInfo display = mock(DisplayInfo.class);

        final float[] coords = new float[]{-1f, -1f};
        doAnswer(invocation -> {
            coords[0] = invocation.getArgument(0);
            coords[1] = invocation.getArgument(1);
            return null;
        }).when(display).setLocation(anyFloat(), anyFloat());
        
        when(display.getX()).thenAnswer(inv -> coords[0]);
        when(display.getY()).thenAnswer(inv -> coords[1]);

        Component titleComp = Component.literal(title);
        when(display.getTitle()).thenReturn(titleComp);

        when(advancement.display()).thenReturn(Optional.of(display));
        when(holder.id()).thenReturn(Identifier.fromNamespaceAndPath("minecraft", id));
        when(holder.value()).thenReturn(advancement);

        when(node.holder()).thenReturn(holder);
        when(node.advancement()).thenReturn(advancement);
        when(node.parent()).thenReturn(parentNode);

        List<AdvancementNode> children = new ArrayList<>();
        childrenMap.put(node, children);
        when(node.children()).thenReturn(children);

        if (parentNode != null) {
            childrenMap.get(parentNode).add(node);
            AdvancementNode root = rootMap.get(parentNode);
            rootMap.put(node, root);
            when(node.root()).thenReturn(root);
        } else {
            rootMap.put(node, node);
            when(node.root()).thenReturn(node);
        }

        return node;
    }

    @Test
    public void testSingleNode() {
        AdvancementNode root = createMockNode("root", "Root Title", null);
        AdvancementTreePositioning.run(root);

        DisplayInfo display = root.advancement().display().orElseThrow();
        assertEquals(0.0f, display.getX(), "Root horizontal position should be 0");
        assertEquals(0.0f, display.getY(), "Root vertical position should be 0");
    }

    @Test
    public void testRootWithTwoChildren() {
        AdvancementNode root = createMockNode("root", "Root", null);
        AdvancementNode child1 = createMockNode("child1", "Apple", root);
        AdvancementNode child2 = createMockNode("child2", "Banana", root);

        AdvancementTreePositioning.run(root);

        DisplayInfo rootDisplay = root.advancement().display().orElseThrow();
        DisplayInfo child1Display = child1.advancement().display().orElseThrow();
        DisplayInfo child2Display = child2.advancement().display().orElseThrow();

        // Horizontal positioning: root = 0, children = 1
        assertEquals(0.0f, rootDisplay.getX());
        assertEquals(1.0f, child1Display.getX());
        assertEquals(1.0f, child2Display.getX());

        // Vertical positioning: root should be at the midpoint of children
        // Minimum y should be 0.0f, children spaced by 1.0f
        assertEquals(0.0f, child1Display.getY(), "First child should start at y=0");
        assertEquals(1.0f, child2Display.getY(), "Second child should be at y=1");
        assertEquals(0.5f, rootDisplay.getY(), "Root y should be the midpoint of children");
    }

    @Test
    public void testAlphabeticSorting() {
        Configuration.advancementsOrder = Configuration.AdvancementOrder.ALPHABETIC;

        AdvancementNode root = createMockNode("root", "Root", null);
        // Add children out of alphabetical order
        AdvancementNode childBanana = createMockNode("banana", "Banana", root);
        AdvancementNode childApple = createMockNode("apple", "Apple", root);

        AdvancementTreePositioning.run(root);

        DisplayInfo appleDisplay = childApple.advancement().display().orElseThrow();
        DisplayInfo bananaDisplay = childBanana.advancement().display().orElseThrow();

        // Under Alphabetic sorting, Apple (starts with A) should be sorted before Banana (starts with B).
        // Therefore, Apple's y should be smaller than Banana's y.
        assertTrue(appleDisplay.getY() < bananaDisplay.getY(), "Apple (sorted first) should have smaller Y than Banana");
    }

    @Test
    public void testConfiguredOrderSorting() {
        Configuration.advancementsOrder = Configuration.AdvancementOrder.CONFIGURED_ORDER;
        Configuration.customAdvancementsOrder = List.of("minecraft:banana", "minecraft:apple");

        AdvancementNode root = createMockNode("root", "Root", null);
        AdvancementNode childBanana = createMockNode("banana", "Banana", root);
        AdvancementNode childApple = createMockNode("apple", "Apple", root);

        AdvancementTreePositioning.run(root);

        DisplayInfo appleDisplay = childApple.advancement().display().orElseThrow();
        DisplayInfo bananaDisplay = childBanana.advancement().display().orElseThrow();

        // Under CONFIGURED_ORDER with custom order: banana, then apple:
        // Banana should be sorted before Apple, so banana.y < apple.y
        assertTrue(bananaDisplay.getY() < appleDisplay.getY(), "Banana (configured first) should have smaller Y than Apple");
    }

    @Test
    public void testNoSorting() {
        Configuration.advancementsOrder = Configuration.AdvancementOrder.NONE;

        AdvancementNode root = createMockNode("root", "Root", null);
        // Children added in this order: Banana, Apple
        AdvancementNode childBanana = createMockNode("banana", "Banana", root);
        AdvancementNode childApple = createMockNode("apple", "Apple", root);

        AdvancementTreePositioning.run(root);

        DisplayInfo appleDisplay = childApple.advancement().display().orElseThrow();
        DisplayInfo bananaDisplay = childBanana.advancement().display().orElseThrow();

        // Under NONE sorting, the insertion order should be preserved: Banana (added first) before Apple (added second).
        // So banana.y < apple.y
        assertTrue(bananaDisplay.getY() < appleDisplay.getY(), "Banana (added first) should have smaller Y than Apple under NONE sorting");
    }

    @Test
    public void testTransparentNodesBehavior() {
        // Test how the positioning algorithm behaves when some nodes are invisible (display is empty).
        AdvancementNode root = createMockNode("root", "Root", null);
        
        // Make middle child invisible (no display)
        AdvancementNode invisibleChild = mock(AdvancementNode.class);
        AdvancementHolder invisibleHolder = mock(AdvancementHolder.class);
        Advancement invisibleAdvancement = mock(Advancement.class);
        when(invisibleAdvancement.display()).thenReturn(Optional.empty());
        when(invisibleHolder.id()).thenReturn(Identifier.fromNamespaceAndPath("minecraft", "invisible"));
        when(invisibleHolder.value()).thenReturn(invisibleAdvancement);
        when(invisibleChild.holder()).thenReturn(invisibleHolder);
        when(invisibleChild.advancement()).thenReturn(invisibleAdvancement);
        when(invisibleChild.parent()).thenReturn(root);
        
        List<AdvancementNode> invisibleChildren = new ArrayList<>();
        childrenMap.put(invisibleChild, invisibleChildren);
        when(invisibleChild.children()).thenReturn(invisibleChildren);
        
        // Add invisible child to root children list
        childrenMap.get(root).add(invisibleChild);
        rootMap.put(invisibleChild, root);
        when(invisibleChild.root()).thenReturn(root);

        // Grandchildren of the root (via the invisible child)
        AdvancementNode grandchild1 = createMockNode("grandchild1", "Grandchild 1", invisibleChild);
        AdvancementNode grandchild2 = createMockNode("grandchild2", "Grandchild 2", invisibleChild);

        AdvancementTreePositioning.run(root);

        DisplayInfo gc1Display = grandchild1.advancement().display().orElseThrow();
        DisplayInfo gc2Display = grandchild2.advancement().display().orElseThrow();

        // The grandchildren should be flattened and become positioned as children of the root.
        // Therefore, their X coordinates should be parent.x + 1 (since root is x=0, grandchildren x=1).
        assertEquals(1.0f, gc1Display.getX(), "Grandchild 1 X position should be 1");
        assertEquals(1.0f, gc2Display.getX(), "Grandchild 2 X position should be 1");

        // They should be spaced by 1.0 units.
        assertEquals(0.0f, gc1Display.getY());
        assertEquals(1.0f, gc2Display.getY());
        
        DisplayInfo rootDisplay = root.advancement().display().orElseThrow();
        assertEquals(0.5f, rootDisplay.getY(), "Root Y should be the midpoint of the visible grandchildren");
    }

    @Test
    public void testLargeTreePositioningAndOverlapPrevention() {
        // Construct a more complex tree structure to test shift calculations (overlap prevention).
        // Root
        // -- Child 1 (Apple)
        //    -- Grandchild 1.1 (Cider)
        // -- Child 2 (Banana)
        //    -- Grandchild 2.1 (Donut)
        //    -- Grandchild 2.2 (Eclair)
        
        AdvancementNode root = createMockNode("root", "Root", null);
        AdvancementNode child1 = createMockNode("child1", "Apple", root);
        AdvancementNode gc11 = createMockNode("gc11", "Cider", child1);
        
        AdvancementNode child2 = createMockNode("child2", "Banana", root);
        AdvancementNode gc21 = createMockNode("gc21", "Donut", child2);
        AdvancementNode gc22 = createMockNode("gc22", "Eclair", child2);

        AdvancementTreePositioning.run(root);

        DisplayInfo gc11Display = gc11.advancement().display().orElseThrow();
        DisplayInfo gc21Display = gc21.advancement().display().orElseThrow();
        DisplayInfo gc22Display = gc22.advancement().display().orElseThrow();
        DisplayInfo child1Display = child1.advancement().display().orElseThrow();
        DisplayInfo child2Display = child2.advancement().display().orElseThrow();
        DisplayInfo rootDisplay = root.advancement().display().orElseThrow();

        // Check columns (X positions)
        assertEquals(0.0f, rootDisplay.getX());
        assertEquals(1.0f, child1Display.getX());
        assertEquals(1.0f, child2Display.getX());
        assertEquals(2.0f, gc11Display.getX());
        assertEquals(2.0f, gc21Display.getX());
        assertEquals(2.0f, gc22Display.getX());

        // Check relative alignment
        // child1 should align with gc11: child1.y == gc11.y
        assertEquals(child1Display.getY(), gc11Display.getY(), "Child 1 should align with its single child");

        // child2 should align with midpoint of gc21 and gc22
        assertEquals((gc21Display.getY() + gc22Display.getY()) / 2.0f, child2Display.getY(), "Child 2 should align with midpoint of its children");

        // Check overlap prevention: the subtrees of Child 1 and Child 2 must not overlap.
        // The bottom of Child 1's subtree (gc11.y) and the top of Child 2's subtree (gc21.y) must have at least 1.0 difference.
        assertTrue(gc21Display.getY() - gc11Display.getY() >= 1.0f, "Subtrees should not overlap (gc21.y - gc11.y should be >= 1.0)");
    }
}

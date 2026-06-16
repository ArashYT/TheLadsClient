package com.thelads.core.features.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.Utils;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.advancements.AdvancementNode;
import org.jetbrains.annotations.Nullable;

public class AdvancementTreePositioning {
    private final AdvancementNode node;
    @Nullable
    private final AdvancementTreePositioning parent;
    @Nullable
    private final AdvancementTreePositioning previousSibling;
    private final int childIndex;
    private final List<AdvancementTreePositioning> children = Lists.newArrayList();
    private AdvancementTreePositioning ancestor;
    @Nullable
    private AdvancementTreePositioning thread;
    private int x;
    private float y;
    private float mod;
    private float change;
    private float shift;

    public AdvancementTreePositioning(AdvancementNode node, @Nullable AdvancementTreePositioning parent, @Nullable AdvancementTreePositioning previousSibling, int childIndex, int x) {
        if (node.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position an invisible advancement!");
        }
        this.node = node;
        this.parent = parent;
        this.previousSibling = previousSibling;
        this.childIndex = childIndex;
        this.ancestor = this;
        this.x = x;
        this.y = -1.0f;
        List<AdvancementNode> sortedChildren = this.getSortedChildren(node);
        if (Configuration.advancementsOrder == Configuration.AdvancementOrder.CONFIGURED_ORDER && !sortedChildren.isEmpty()) {
            Utils.LOGGER.info("Sorting children of: " + node.holder().id());
            for (int i = 0; i < sortedChildren.size(); ++i) {
                AdvancementNode child = sortedChildren.get(i);
                String childId = child.holder().id().toString();
                int configPos = Configuration.customAdvancementsOrder.indexOf(childId);
                Utils.LOGGER.info("  [" + i + "] " + childId + " (config position: " + configPos + ")");
            }
        }
        AdvancementTreePositioning previousChild = null;
        for (AdvancementNode child : sortedChildren) {
            previousChild = this.addChild(child, previousChild);
        }
    }

    private List<AdvancementNode> getSortedChildren(AdvancementNode node) {
        ArrayList<AdvancementNode> children = new ArrayList<>();
        node.children().forEach(children::add);
        if (Configuration.advancementsOrder == Configuration.AdvancementOrder.NONE || children.size() <= 1) {
            return children;
        }
        switch (Configuration.advancementsOrder) {
            case ALPHABETIC -> children.sort(Comparator.comparing(child -> child.advancement().display().map(display -> display.getTitle().getString()).orElse(""), String.CASE_INSENSITIVE_ORDER));
            case CONFIGURED_ORDER -> children.sort((node1, node2) -> {
                String id1 = node1.holder().id().toString();
                String id2 = node2.holder().id().toString();
                int pos1 = Configuration.customAdvancementsOrder.indexOf(id1);
                int pos2 = Configuration.customAdvancementsOrder.indexOf(id2);
                if (pos1 != -1 && pos2 != -1) {
                    return Integer.compare(pos1, pos2);
                }
                if (pos1 != -1) {
                    return -1;
                }
                if (pos2 != -1) {
                    return 1;
                }
                String title1 = node1.advancement().display().map(display -> display.getTitle().getString()).orElse("");
                String title2 = node2.advancement().display().map(display -> display.getTitle().getString()).orElse("");
                return title1.compareToIgnoreCase(title2);
            });
        }
        return children;
    }

    @Nullable
    private AdvancementTreePositioning addChild(AdvancementNode child, @Nullable AdvancementTreePositioning previousSibling) {
        if (child.advancement().display().isPresent()) {
            previousSibling = new AdvancementTreePositioning(child, this, previousSibling, this.children.size() + 1, this.x + 1);
            this.children.add(previousSibling);
        } else {
            for (AdvancementNode grandchild : child.children()) {
                previousSibling = this.addChild(grandchild, previousSibling);
            }
        }
        return previousSibling;
    }

    private void firstWalk() {
        if (this.children.isEmpty()) {
            this.y = this.previousSibling != null ? this.previousSibling.y + 1.0f : 0.0f;
        } else {
            AdvancementTreePositioning defaultAncestor = null;
            for (AdvancementTreePositioning child : this.children) {
                child.firstWalk();
                defaultAncestor = child.apportion(defaultAncestor == null ? child : defaultAncestor);
            }
            this.executeShifts();
            float midpoint = (this.children.get(0).y + this.children.get(this.children.size() - 1).y) / 2.0f;
            if (this.previousSibling != null) {
                this.y = this.previousSibling.y + 1.0f;
                this.mod = this.y - midpoint;
            } else {
                this.y = midpoint;
            }
        }
    }

    private float secondWalk(float offsetY, int columnX, float subtreeTopY) {
        this.y += offsetY;
        this.x = columnX;
        if (this.y < subtreeTopY) {
            subtreeTopY = this.y;
        }
        for (AdvancementTreePositioning child : this.children) {
            subtreeTopY = child.secondWalk(offsetY + this.mod, columnX + 1, subtreeTopY);
        }
        return subtreeTopY;
    }

    private void thirdWalk(float y) {
        this.y += y;
        for (AdvancementTreePositioning child : this.children) {
            child.thirdWalk(y);
        }
    }

    private void executeShifts() {
        float shiftAccumulator = 0.0f;
        float changeAccumulator = 0.0f;
        for (int i = this.children.size() - 1; i >= 0; --i) {
            AdvancementTreePositioning child = this.children.get(i);
            child.y += shiftAccumulator;
            child.mod += shiftAccumulator;
            shiftAccumulator += child.shift + (changeAccumulator += child.change);
        }
    }

    @Nullable
    private AdvancementTreePositioning previousOrThread() {
        if (this.thread != null) {
            return this.thread;
        }
        return !this.children.isEmpty() ? this.children.get(0) : null;
    }

    @Nullable
    private AdvancementTreePositioning nextOrThread() {
        if (this.thread != null) {
            return this.thread;
        }
        return !this.children.isEmpty() ? this.children.get(this.children.size() - 1) : null;
    }

    private AdvancementTreePositioning apportion(AdvancementTreePositioning defaultAncestor) {
        if (this.previousSibling == null) {
            return defaultAncestor;
        }
        AdvancementTreePositioning leftContour = this;
        AdvancementTreePositioning rightContour = this;
        AdvancementTreePositioning leftSibling = this.previousSibling;
        AdvancementTreePositioning leftmostSibling = this.parent.children.get(0);
        float leftModSum = this.mod;
        float rightModSum = this.mod;
        float leftSiblingModSum = leftSibling.mod;
        float leftmostSiblingModSum = leftmostSibling.mod;
        while (leftSibling.nextOrThread() != null && leftContour.previousOrThread() != null) {
            leftSibling = leftSibling.nextOrThread();
            leftContour = leftContour.previousOrThread();
            leftmostSibling = leftmostSibling.previousOrThread();
            rightContour = rightContour.nextOrThread();
            rightContour.ancestor = this;
            float shift = leftSibling.y + leftSiblingModSum - (leftContour.y + leftModSum) + 1.0f;
            if (shift > 0.0f) {
                leftSibling.getAncestor(this, defaultAncestor).moveSubtree(this, shift);
                leftModSum += shift;
                rightModSum += shift;
            }
            leftSiblingModSum += leftSibling.mod;
            leftModSum += leftContour.mod;
            leftmostSiblingModSum += leftmostSibling.mod;
            rightModSum += rightContour.mod;
        }
        if (leftSibling.nextOrThread() != null && rightContour.nextOrThread() == null) {
            rightContour.thread = leftSibling.nextOrThread();
            rightContour.mod += leftSiblingModSum - rightModSum;
        } else {
            if (leftContour.previousOrThread() != null && leftmostSibling.previousOrThread() == null) {
                leftmostSibling.thread = leftContour.previousOrThread();
                leftmostSibling.mod += leftModSum - leftmostSiblingModSum;
            }
            defaultAncestor = this;
        }
        return defaultAncestor;
    }

    private void moveSubtree(AdvancementTreePositioning target, float shift) {
        float siblingCount = target.childIndex - this.childIndex;
        if (siblingCount != 0.0f) {
            target.change -= shift / siblingCount;
            this.change += shift / siblingCount;
        }
        target.shift += shift;
        target.y += shift;
        target.mod += shift;
    }

    private AdvancementTreePositioning getAncestor(AdvancementTreePositioning self, AdvancementTreePositioning defaultAncestor) {
        return this.ancestor != null && self.parent.children.contains(this.ancestor) ? this.ancestor : defaultAncestor;
    }

    private void finalizePosition() {
        this.node.advancement().display().ifPresent(displayInfo -> displayInfo.setLocation(this.x, this.y));
        if (!this.children.isEmpty()) {
            for (AdvancementTreePositioning child : this.children) {
                child.finalizePosition();
            }
        }
    }

    public static void run(AdvancementNode rootNode) {
        if (rootNode.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position children of an invisible root!");
        }
        AdvancementTreePositioning root = new AdvancementTreePositioning(rootNode, null, null, 1, 0);
        root.firstWalk();
        float minY = root.secondWalk(0.0f, 0, root.y);
        if (minY < 0.0f) {
            root.thirdWalk(-minY);
        }
        root.finalizePosition();
    }
}

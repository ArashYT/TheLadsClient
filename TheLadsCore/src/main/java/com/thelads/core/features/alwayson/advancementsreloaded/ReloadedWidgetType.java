package com.thelads.core.features.alwayson.advancementsreloaded;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.resources.Identifier;

public enum ReloadedWidgetType {
    OBTAINED(
        Identifier.withDefaultNamespace("advancements/box_obtained"),
        Identifier.withDefaultNamespace("advancements/task_frame_obtained"),
        Identifier.withDefaultNamespace("advancements/challenge_frame_obtained"),
        Identifier.withDefaultNamespace("advancements/goal_frame_obtained"),
        Identifier.parse("advancements_reloaded:box_obtained_dimmed"),
        Identifier.parse("advancements_reloaded:task_frame_obtained_dimmed"),
        Identifier.parse("advancements_reloaded:challenge_frame_obtained_dimmed"),
        Identifier.parse("advancements_reloaded:goal_frame_obtained_dimmed")
    ),
    UNOBTAINED(
        Identifier.withDefaultNamespace("advancements/box_unobtained"),
        Identifier.withDefaultNamespace("advancements/task_frame_unobtained"),
        Identifier.withDefaultNamespace("advancements/challenge_frame_unobtained"),
        Identifier.withDefaultNamespace("advancements/goal_frame_unobtained"),
        Identifier.parse("advancements_reloaded:box_unobtained_dimmed"),
        Identifier.parse("advancements_reloaded:task_frame_unobtained_dimmed"),
        Identifier.parse("advancements_reloaded:challenge_frame_unobtained_dimmed"),
        Identifier.parse("advancements_reloaded:goal_frame_unobtained_dimmed")
    );

    private final Identifier boxSprite;
    private final Identifier taskFrameSprite;
    private final Identifier challengeFrameSprite;
    private final Identifier goalFrameSprite;
    private final Identifier boxSpriteDimmed;
    private final Identifier taskFrameSpriteDimmed;
    private final Identifier challengeFrameSpriteDimmed;
    private final Identifier goalFrameSpriteDimmed;

    ReloadedWidgetType(Identifier box, Identifier task, Identifier challenge, Identifier goal,
                       Identifier boxDimmed, Identifier taskDimmed, Identifier challengeDimmed, Identifier goalDimmed) {
        this.boxSprite = box;
        this.taskFrameSprite = task;
        this.challengeFrameSprite = challenge;
        this.goalFrameSprite = goal;
        this.boxSpriteDimmed = boxDimmed;
        this.taskFrameSpriteDimmed = taskDimmed;
        this.challengeFrameSpriteDimmed = challengeDimmed;
        this.goalFrameSpriteDimmed = goalDimmed;
    }

    public Identifier boxSprite(boolean dimmed) {
        return dimmed ? this.boxSpriteDimmed : this.boxSprite;
    }

    public Identifier frameSprite(AdvancementType type, boolean dimmed) {
        return switch (type) {
            case TASK -> dimmed ? this.taskFrameSpriteDimmed : this.taskFrameSprite;
            case CHALLENGE -> dimmed ? this.challengeFrameSpriteDimmed : this.challengeFrameSprite;
            case GOAL -> dimmed ? this.goalFrameSpriteDimmed : this.goalFrameSprite;
        };
    }
}

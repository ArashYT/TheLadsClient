package com.thelads.core.features.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import com.thelads.core.features.alwayson.advancementsreloaded.utils.Utils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReloadedCriterionProgress {
    private final AdvancementNode advancementNode;
    private final AdvancementProgress progress;
    private final Identifier criterion;
    private final boolean obtained;
    private final List<String> alreadyWarnedTranslations = new ArrayList<>();

    public ReloadedCriterionProgress(AdvancementNode advancementNode, AdvancementProgress progress, String criterionName) {
        this.advancementNode = advancementNode;
        this.progress = progress;
        this.criterion = sanitizeResourceLocationString(criterionName);
        this.obtained = progress.getCriterion(criterionName).isDone();
    }

    public AdvancementNode getAdvancementNode() { return this.advancementNode; }
    public Advancement getAdvancement() { return this.advancementNode.holder().value(); }
    public Identifier getResourceLocation() { return this.advancementNode.holder().id(); }
    public AdvancementProgress getProgress() { return this.progress; }
    public Component getTitle() { return Component.nullToEmpty(this.criterion.toString()); }
    public int getColor() { return this.isObtained() ? 0xFF55FF55 : 0xFFFF5555; }
    public boolean isObtained() { return this.obtained; }

    public Component getHumanCriterionName() {
        String translationKey = this.getTranslationKey();
        switch (Configuration.criteriasTranslationMode) {
            case NONE:
                break;
            case ONLY_COMPATIBLE:
                return Component.translatableWithFallback(translationKey, this.criterion.getPath());
            case TRY_TO_TRANSLATE:
                return Component.translatableWithFallback(translationKey, this.retrieveTranslationOnGame().getString());
        }
        return Component.literal(this.criterion.getPath());
    }

    public String getTranslationKey() {
        String criterionName = this.criterion.getPath();
        List<String> parts = List.of("advancements", this.getAdvancementCategory(), this.getAdvancementIdentifier(), "criteria", criterionName);
        return parts.stream().filter(part -> part != null && !part.isEmpty()).collect(Collectors.joining("."));
    }

    private String getAdvancementIdentifier() {
        Identifier locationId = this.getResourceLocation();
        String path = locationId.getPath();
        String[] pathSegments = path.split("/");
        if (pathSegments.length == 0) {
            Utils.LOGGER.warn("advancement cannot be retrieved from the path: {}", path);
            return "";
        }
        return pathSegments[pathSegments.length - 1];
    }

    private String getAdvancementCategory() {
        Identifier locationId = this.getAdvancementNode().root().holder().id();
        String path = locationId.getPath();
        if (!path.contains("/")) {
            return "";
        }
        String[] pathSegments = path.split("/");
        if (pathSegments.length >= 2) {
            return pathSegments[pathSegments.length - 2];
        }
        return "";
    }

    private Component retrieveTranslationOnGame() {
        String criterionNamespace = this.criterion.getNamespace();
        String criteria = this.criterion.getPath();
        List<String> namespaces = new ArrayList<>();
        namespaces.add(criterionNamespace);
        if (!criterionNamespace.equals("minecraft")) {
            namespaces.add("minecraft");
        }
        String[] keyTypes = new String[]{"biome", "block", "color", "container", "effect", "enchantment", "entity", "instrument", "item", "jukebox_song", "painting", "stat"};
        for (String namespace : namespaces) {
            for (String keyType : keyTypes) {
                String suffix = keyType.equals("painting") ? ".title" : "";
                String translationKey = keyType + "." + namespace + "." + criteria + suffix;
                MutableComponent translation = Component.translatable(translationKey);
                if (!translation.getString().equals(translationKey)) {
                    return translation.withStyle(style -> style.withItalic(true));
                }
            }
        }
        if (this.alreadyWarnedTranslations.contains(criteria)) {
            return Component.literal(criteria);
        }
        Utils.LOGGER.warn("Unable to translate {} to a more meaningful name, adding as is. You can add translation key: `{}`.", criteria, this.getTranslationKey());
        this.alreadyWarnedTranslations.add(criteria);
        return Component.literal(criteria);
    }

    private static Identifier sanitizeResourceLocationString(String str) {
        str = str.toLowerCase();
        try {
            return Identifier.parse(str);
        } catch (Exception e) {
            Utils.LOGGER.error("Failed to parse criterion name: {}, trying to sanitize", str);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < str.length(); ++i) {
                char c = str.charAt(i);
                if (Identifier.validPathChar(c)) {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            }
            String sanitized = sb.toString();
            Utils.LOGGER.warn("Criterion name sanitized to: minecraft:{}", sanitized);
            return Identifier.fromNamespaceAndPath("minecraft", sanitized);
        }
    }
}

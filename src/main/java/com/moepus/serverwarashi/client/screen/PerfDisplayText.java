package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

public final class PerfDisplayText {
    private PerfDisplayText() {
    }

    public static String translated(String key, Object... arguments) {
        return I18n.get(key, arguments);
    }

    public static String resource(String value) {
        if (value == null || value.indexOf(':') < 0) {
            return value;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return value;
        }
        String key = translationKey(id);
        return key == null ? value : I18n.get(key);
    }

    public static String objectName(TickDetailKind kind, String className, String detailId) {
        if (detailId != null && !detailId.isEmpty()) {
            return typedResource(kind, detailId);
        }
        int separator = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return separator < 0 ? className : className.substring(separator + 1);
    }

    public static String sampledEntityName(WeakEntityType type) {
        TickDetailKind detailKind = type.kind() == WeakEntityType.Kind.ITEM
                ? TickDetailKind.ITEM : TickDetailKind.ENTITY;
        return objectName(detailKind, "", type.id());
    }

    private static String typedResource(TickDetailKind kind, String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return value;
        }
        String key = switch (kind) {
            case ITEM -> firstTranslationKey(id, "item", "block");
            case ENTITY -> existingTranslationKey(id, "entity");
            case BLOCK -> existingTranslationKey(id, "block");
            case NONE -> null;
        };
        return key != null && I18n.exists(key) ? I18n.get(key) : value;
    }

    private static String firstTranslationKey(ResourceLocation id, String... prefixes) {
        for (String prefix : prefixes) {
            String key = existingTranslationKey(id, prefix);
            if (key != null) {
                return key;
            }
        }
        return null;
    }

    private static String existingTranslationKey(ResourceLocation id, String prefix) {
        String key = id.toLanguageKey(prefix);
        return I18n.exists(key) ? key : null;
    }

    private static String translationKey(ResourceLocation id) {
        String[] prefixes = {"item", "block", "entity"};
        for (String prefix : prefixes) {
            String key = id.toLanguageKey(prefix);
            if (I18n.exists(key)) {
                return key;
            }
        }
        return null;
    }
}

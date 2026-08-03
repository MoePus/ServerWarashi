package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.api.client.map.MapOverlayApi;
import com.moepus.serverwarashi.api.client.map.MapOverlayIntegration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import xaero.map.gui.GuiMap;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class XaeroMapIntegration implements MapOverlayIntegration {
    private static final int MAX_PARENT_DEPTH = 16;
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ServerWarashi.MODID, "xaero");
    private static final ClassValue<Optional<Field>> PARENT_FIELDS = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            try {
                Field field = type.getField("parent");
                return Screen.class.isAssignableFrom(field.getType()) && field.trySetAccessible()
                        ? Optional.of(field) : Optional.empty();
            } catch (NoSuchFieldException exception) {
                return Optional.empty();
            }
        }
    };

    public static void register() {
        MapOverlayApi.register(new XaeroMapIntegration());
        try {
            XaeroMapAccess.verifyCompatibility();
        } catch (Throwable throwable) {
            disable("compatibility check", throwable);
        }
    }

    public static void disable(String phase, Throwable throwable) {
        MapOverlayApi.disable(ID, phase, throwable);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean isMapOpen(Screen screen) {
        return hasMapAncestor(screen, candidate -> candidate instanceof GuiMap);
    }

    static boolean hasMapAncestor(Screen screen, Predicate<Screen> isMap) {
        Set<Screen> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Screen current = screen;
        for (int depth = 0; current != null && depth < MAX_PARENT_DEPTH; depth++) {
            if (!visited.add(current)) {
                return false;
            }
            if (isMap.test(current)) {
                return true;
            }
            current = parent(current);
        }
        return false;
    }

    private static Screen parent(Screen screen) {
        Optional<Field> field = PARENT_FIELDS.get(screen.getClass());
        if (field.isEmpty()) {
            return null;
        }
        try {
            Object parent = field.get().get(screen);
            return parent instanceof Screen parentScreen ? parentScreen : null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Cannot read Xaero screen parent from " + screen.getClass().getName(), exception);
        }
    }
}

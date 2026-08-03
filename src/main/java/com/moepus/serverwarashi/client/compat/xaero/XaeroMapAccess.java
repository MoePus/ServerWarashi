package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.api.client.map.MapTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

final class XaeroMapAccess {
    private static final Fields FIELDS = resolveFields();

    private XaeroMapAccess() {
    }

    static void verifyCompatibility() {
        requireReturn(method("init"), void.class);
        requireReturn(method("render", GuiGraphics.class, int.class, int.class, float.class),
                void.class);
        requireReturn(method("getRightClickOptions"), ArrayList.class);
    }

    static View view(GuiMap screen) {
        MapProcessor processor = value(FIELDS.processor(), screen, MapProcessor.class);
        var mapWorld = processor.getMapWorld();
        ResourceLocation dimension = mapWorld == null || mapWorld.getCurrentDimensionId() == null
                ? null : mapWorld.getCurrentDimensionId().location();
        ViewFields fields = FIELDS.view();
        Camera camera = new Camera(number(fields.cameraX(), screen),
                number(fields.cameraZ(), screen), number(fields.scale(), screen));
        return new View(dimension, camera);
    }

    static MapTarget rightClickTarget(GuiMap screen) {
        ClickFields fields = FIELDS.click();
        Object dimension = value(fields.dimension(), screen);
        if (!(dimension instanceof ResourceKey<?> key)) {
            return null;
        }
        return new MapTarget(key.location(), integer(fields.x(), screen) >> 4,
                integer(fields.z(), screen) >> 4);
    }

    private static Fields resolveFields() {
        ViewFields view = new ViewFields(field("cameraX", double.class),
                field("cameraZ", double.class), field("scale", double.class));
        ClickFields click = new ClickFields(
                field("rightClickX", int.class), field("rightClickZ", int.class),
                field("rightClickDim", ResourceKey.class));
        return new Fields(view, field("mapProcessor", MapProcessor.class), click);
    }

    private static Field field(String name, Class<?> expectedType) {
        try {
            Field field = GuiMap.class.getDeclaredField(name);
            if (field.getType() != expectedType) {
                throw new IllegalStateException("Unexpected type for Xaero GuiMap field " + name);
            }
            if (!field.trySetAccessible()) {
                throw new IllegalStateException("Cannot access Xaero GuiMap field " + name);
            }
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing Xaero GuiMap field " + name, exception);
        }
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            return GuiMap.class.getDeclaredMethod(name, parameters);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing Xaero GuiMap method " + name, exception);
        }
    }

    private static void requireReturn(Method method, Class<?> expectedType) {
        if (method.getReturnType() != expectedType) {
            throw new IllegalStateException(
                    "Unexpected return type for Xaero GuiMap method " + method.getName());
        }
    }

    private static Object value(Field field, GuiMap screen) {
        try {
            return field.get(screen);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read Xaero GuiMap field " + field.getName(), exception);
        }
    }

    private static <T> T value(Field field, GuiMap screen, Class<T> type) {
        return type.cast(value(field, screen));
    }

    private static double number(Field field, GuiMap screen) {
        try {
            return field.getDouble(screen);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read Xaero GuiMap field " + field.getName(), exception);
        }
    }

    private static int integer(Field field, GuiMap screen) {
        try {
            return field.getInt(screen);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read Xaero GuiMap field " + field.getName(), exception);
        }
    }

    record View(ResourceLocation dimension, Camera camera) {
    }

    record Camera(double x, double z, double scale) {
    }

    private record Fields(ViewFields view, Field processor, ClickFields click) {
    }

    private record ViewFields(Field cameraX, Field cameraZ, Field scale) {
    }

    private record ClickFields(Field x, Field z, Field dimension) {
    }
}

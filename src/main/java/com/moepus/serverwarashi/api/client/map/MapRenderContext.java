package com.moepus.serverwarashi.api.client.map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Objects;

/**
 * Map-neutral state required to render ServerWarashi's world-map overlay.
 */
public record MapRenderContext(ResourceLocation dimension, View view) {
    public MapRenderContext {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(view, "view");
    }

    /** Build the view used by maps whose camera is centered in a full-screen viewport. */
    public static View centered(ScreenSize screen, Matrix4f basePose, CenteredView input) {
        ScreenArea area = new ScreenArea(new ScreenPoint(0, 0),
                Objects.requireNonNull(screen, "screen"));
        return centered(area, basePose, input);
    }

    /** Build the view used by a map camera centered in the supplied GUI area. */
    public static View centered(ScreenArea area, Matrix4f basePose, CenteredView input) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(basePose, "basePose");
        Objects.requireNonNull(input, "input");
        float scale = input.scale();
        if (!(scale > 0.0F) || !Float.isFinite(scale)) {
            throw new IllegalArgumentException("Map scale must be finite and positive");
        }
        WorldPoint center = input.center();
        ScreenPoint mouse = input.pointer();
        int width = Math.max(1, Mth.ceil(area.size().width() / scale));
        int height = Math.max(1, Mth.ceil(area.size().height() / scale));
        var origin = new MapViewport.Origin(
                Mth.floor(center.x() - width / 2.0D),
                Mth.floor(center.z() - height / 2.0D));
        MapViewport viewport = new MapViewport(origin, new MapViewport.Size(width, height));
        WorldPoint mouseWorld = new WorldPoint(
                (mouse.x() - area.centerX()) / scale + center.x(),
                (mouse.y() - area.centerY()) / scale + center.z());
        Matrix4f pose = new Matrix4f(basePose)
                .translate((float) area.centerX(), (float) area.centerY(), 0.0F)
                .scale(scale, scale, 1.0F)
                .translate((float) -center.x(), (float) -center.z(), 0.0F);
        return new View(new VisibleArea(viewport, area), new Transform(pose, scale, center),
                new Pointer(mouse, mouseWorld));
    }

    public record View(VisibleArea visible, Transform transform, Pointer pointer) {
        public View {
            Objects.requireNonNull(visible, "visible");
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(pointer, "pointer");
        }

        public MapViewport viewport() {
            return visible.world();
        }

        public ScreenArea screenArea() {
            return visible.screen();
        }
    }

    /** Matching world and GUI rectangles for the visible portion of a map. */
    public record VisibleArea(MapViewport world, ScreenArea screen) {
        public VisibleArea {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(screen, "screen");
        }
    }

    /** World-to-GUI pose, GUI pixels per block, and the map camera center. */
    public record Transform(Matrix4f pose, float scale, WorldPoint center) {
        public Transform {
            pose = new Matrix4f(Objects.requireNonNull(pose, "pose"));
            Objects.requireNonNull(center, "center");
        }

        @Override
        public Matrix4f pose() {
            return new Matrix4f(pose);
        }
    }

    public record Pointer(ScreenPoint screen, WorldPoint world) {
        public Pointer {
            Objects.requireNonNull(screen, "screen");
            Objects.requireNonNull(world, "world");
        }
    }

    public record ScreenPoint(int x, int y) {
    }

    public record WorldPoint(double x, double z) {
    }

    public record ScreenSize(int width, int height) {
        public ScreenSize {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Screen dimensions must be positive");
            }
        }
    }

    public record ScreenArea(ScreenPoint origin, ScreenSize size) {
        public ScreenArea {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(size, "size");
        }

        public double centerX() {
            return origin.x() + size.width() * 0.5D;
        }

        public double centerY() {
            return origin.y() + size.height() * 0.5D;
        }

        public boolean contains(ScreenPoint point) {
            return point.x() >= origin.x() && point.y() >= origin.y()
                    && point.x() < origin.x() + size.width()
                    && point.y() < origin.y() + size.height();
        }
    }

    public record CenteredView(WorldPoint center, float scale, ScreenPoint pointer) {
        public CenteredView {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(pointer, "pointer");
        }
    }
}

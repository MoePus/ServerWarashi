package com.moepus.serverwarashi.client.map;

import com.moepus.serverwarashi.api.client.map.MapRenderContext;
import com.moepus.serverwarashi.api.client.map.MapViewport;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRenderContextTest {
    @Test
    void projectsCenteredMapViewportAndPointerAtNegativeCoordinates() {
        var screen = new MapRenderContext.ScreenSize(320, 180);
        var center = new MapRenderContext.WorldPoint(-31.5D, 48.25D);
        var input = new MapRenderContext.CenteredView(
                center, 0.5F, new MapRenderContext.ScreenPoint(0, 180));

        MapRenderContext.View view = MapRenderContext.centered(
                screen, new Matrix4f(), input);

        assertEquals(new MapViewport(
                new MapViewport.Origin(-352, -132), new MapViewport.Size(640, 360)),
                view.viewport());
        assertEquals(-351.5D, view.pointer().world().x());
        assertEquals(228.25D, view.pointer().world().z());
        Vector3f projectedCenter = view.transform().pose()
                .transformPosition(new Vector3f((float) center.x(), (float) center.z(), 0.0F));
        assertEquals(160.0F, projectedCenter.x(), 0.001F);
        assertEquals(90.0F, projectedCenter.y(), 0.001F);
    }

    @Test
    void projectsCenteredMapInsideAScreenSubArea() {
        var area = new MapRenderContext.ScreenArea(
                new MapRenderContext.ScreenPoint(40, 20),
                new MapRenderContext.ScreenSize(200, 100));
        var input = new MapRenderContext.CenteredView(
                new MapRenderContext.WorldPoint(0, 0), 1.0F,
                new MapRenderContext.ScreenPoint(140, 70));

        MapRenderContext.View view = MapRenderContext.centered(area, new Matrix4f(), input);

        assertEquals(MapViewport.at(-100, -50).withSize(200, 100), view.viewport());
        assertEquals(area, view.screenArea());
        assertTrue(area.contains(new MapRenderContext.ScreenPoint(40, 20)));
        assertFalse(area.contains(new MapRenderContext.ScreenPoint(240, 70)));
        assertEquals(new MapRenderContext.WorldPoint(0, 0), view.pointer().world());
        Vector3f projectedCenter = view.transform().pose().transformPosition(new Vector3f());
        assertEquals(140.0F, projectedCenter.x(), 0.001F);
        assertEquals(70.0F, projectedCenter.y(), 0.001F);
    }

    @Test
    void rejectsInvalidScreenAndScale() {
        assertThrows(IllegalArgumentException.class,
                () -> new MapRenderContext.ScreenSize(0, 100));
        var input = new MapRenderContext.CenteredView(
                new MapRenderContext.WorldPoint(0, 0), 0.0F,
                new MapRenderContext.ScreenPoint(0, 0));
        assertThrows(IllegalArgumentException.class, () -> MapRenderContext.centered(
                new MapRenderContext.ScreenSize(100, 100), new Matrix4f(), input));
        assertThrows(IllegalArgumentException.class,
                () -> MapViewport.at(0, 0).withSize(-1, 10));
    }

    @Test
    void transformDefensivelyCopiesItsPose() {
        Matrix4f source = new Matrix4f();
        var transform = new MapRenderContext.Transform(source, 1.0F,
                new MapRenderContext.WorldPoint(0, 0));
        source.translate(10.0F, 20.0F, 0.0F);

        Matrix4f exposed = transform.pose();
        exposed.translate(30.0F, 40.0F, 0.0F);

        Vector3f origin = transform.pose().transformPosition(new Vector3f());
        assertEquals(0.0F, origin.x(), 0.001F);
        assertEquals(0.0F, origin.y(), 0.001F);
    }
}

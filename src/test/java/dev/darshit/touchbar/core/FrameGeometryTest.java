package dev.darshit.touchbar.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FrameGeometryTest {
    @Test void nyanHeightScalesEvenly() { // Nyan/Mario: JBUI.scale(20)
        var g = FrameGeometry.of(20, 800, 60);
        assertEquals(3.0, g.scale(), 1e-9);
        assertEquals(267, g.componentWidth()); // round(800 / 3)
        assertEquals(20, g.componentHeight());
    }

    @Test void differentHeightsStillFillTheImage() {
        for (int h : new int[]{4, 20, 25, 30, 40, 60, 90}) {
            var g = FrameGeometry.of(h, 800, 60);
            assertEquals(60, g.componentHeight() * g.scale(), 1e-9);
            assertEquals(800, g.componentWidth() * g.scale(), g.scale()); // within one component pixel
        }
    }

    @Test void nonPositiveHeightUsesThePointSize() { // bad preferred size: 400×30 at 2×
        assertEquals(new FrameGeometry(2.0, 400, 30), FrameGeometry.of(0, 800, 60));
        assertEquals(new FrameGeometry(2.0, 400, 30), FrameGeometry.of(-5, 800, 60));
    }
}

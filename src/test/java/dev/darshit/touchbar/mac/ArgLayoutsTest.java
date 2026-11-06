package dev.darshit.touchbar.mac;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

class ArgLayoutsTest {
    @Test void a_32_byte_buffer_is_still_a_pointer_not_an_NSRect() {
        // a 31-byte UTF-8 task title + NUL is exactly 32 bytes, the size of an NSRect
        try (Arena a = Arena.ofConfined()) {
            var title = a.allocateFrom("x".repeat(31));
            assertEquals(32, title.byteSize());
            assertEquals(ValueLayout.ADDRESS, ArgLayouts.layoutOf(title));
        }
    }

    @Test void a_rect_is_passed_as_a_four_double_struct() {
        var r = ArgLayouts.rect(1, 2, 3, 4);
        assertEquals(ArgLayouts.RECT, ArgLayouts.layoutOf(r));
        var seg = (java.lang.foreign.MemorySegment) ArgLayouts.valueOf(r);
        assertEquals(32, seg.byteSize());
        assertEquals(3.0, seg.get(ValueLayout.JAVA_DOUBLE, 16));
    }

    @Test void scalars_keep_their_native_width() {
        assertEquals(ValueLayout.JAVA_DOUBLE, ArgLayouts.layoutOf(1.5));
        assertEquals(ValueLayout.JAVA_LONG, ArgLayouts.layoutOf(4L));
        assertEquals(ValueLayout.JAVA_BOOLEAN, ArgLayouts.layoutOf(true));
        assertEquals(ValueLayout.JAVA_FLOAT, ArgLayouts.layoutOf(-1000f)); // NSTouchBarItemPriority is a float
    }

    @Test void any_other_segment_is_a_pointer() {
        assertEquals(ValueLayout.ADDRESS, ArgLayouts.layoutOf(java.lang.foreign.MemorySegment.NULL));
    }
}

package dev.darshit.touchbar.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

/**
 * How each Java argument is passed to {@code objc_msgSend}. Decided by TYPE, never by size: a 31-byte string is a
 * 32-byte native buffer, the same size as an NSRect, and passing it as a struct crashes the process.
 */
final class ArgLayouts {
    private ArgLayouts() {}

    static final StructLayout RECT = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

    /** An NSRect, passed by value. */
    record Rect(MemorySegment segment) {}

    static Rect rect(double x, double y, double w, double h) {
        MemorySegment r = Arena.ofAuto().allocate(RECT);
        r.set(ValueLayout.JAVA_DOUBLE, 0, x); r.set(ValueLayout.JAVA_DOUBLE, 8, y);
        r.set(ValueLayout.JAVA_DOUBLE, 16, w); r.set(ValueLayout.JAVA_DOUBLE, 24, h);
        return new Rect(r);
    }

    static MemoryLayout layoutOf(Object arg) {
        return switch (arg) {
            case Double _ -> ValueLayout.JAVA_DOUBLE;
            case Float _ -> ValueLayout.JAVA_FLOAT;   // e.g. NSTouchBarItemPriority
            case Long _ -> ValueLayout.JAVA_LONG;     // NSInteger
            case Boolean _ -> ValueLayout.JAVA_BOOLEAN; // BOOL
            case Rect _ -> RECT;
            default -> ValueLayout.ADDRESS;           // every object, string or C pointer
        };
    }

    /** The value handed to the native call. */
    static Object valueOf(Object arg) {
        return arg instanceof Rect r ? r.segment() : arg;
    }
}

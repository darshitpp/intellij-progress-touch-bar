package dev.darshit.touchbar.core;

import org.jetbrains.annotations.Nullable;

public final class Percent {
    private Percent() {}

    public static String format(@Nullable Double fraction) {
        if (fraction == null || fraction.isNaN()) return "";
        return Math.round(Math.clamp(fraction, 0.0, 1.0) * 100) + "%";
    }
}

package dev.darshit.touchbar.core;

import org.jetbrains.annotations.Nullable;

/** What the Touch Bar shows. {@code fraction == null} = indeterminate; null label / name = item not shown. */
public record BarContent(@Nullable Double fraction, @Nullable String label, @Nullable String name) {}

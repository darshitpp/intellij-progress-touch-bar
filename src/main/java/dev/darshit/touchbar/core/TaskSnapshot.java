package dev.darshit.touchbar.core;

import org.jetbrains.annotations.Nullable;

/** One running task as seen by the status bar. {@code fraction == null} means indeterminate; {@code name} is the title the IDE shows. */
public record TaskSnapshot(long id, @Nullable Double fraction, @Nullable String name) {
    public TaskSnapshot(long id, @Nullable Double fraction) { this(id, fraction, null); }
}

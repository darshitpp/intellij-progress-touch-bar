package dev.darshit.touchbar.core;

import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * Decides whether the Touch Bar should be shown. update() runs on the EDT, hide() on the AppKit main thread
 * (the Hide button), hence the synchronization.
 */
public final class BarState {
    public static final long DEFAULT_DELAY_MS = 500;

    public record Output(boolean show, @Nullable Double fraction, @Nullable String name) {}

    private static final Output OFF = new Output(false, null, null);

    private final LongSupplier delayMs; // read on every update, so a settings change applies at once

    public BarState() { this(() -> DEFAULT_DELAY_MS); }
    public BarState(LongSupplier delayMs) { this.delayMs = delayMs; }

    private Long currentId;
    private long sinceMs;
    private Long hiddenId;

    public synchronized Output update(long nowMs, @Nullable TaskSnapshot current) {
        if (current == null) {
            currentId = null;
            hiddenId = null; // a task that restarts is a new task
            return OFF;
        }
        if (currentId == null || currentId != current.id()) {
            currentId = current.id();
            sinceMs = nowMs;
        }
        Double f = current.fraction();
        if (f != null) f = f.isNaN() ? null : Math.max(0.0, Math.min(1.0, f));
        boolean show = nowMs - sinceMs >= delayMs.getAsLong() && !(hiddenId != null && hiddenId == current.id());
        return new Output(show, f, current.name());
    }

    /** Hide for the task currently tracked; the next task shows again. */
    public synchronized void hide() {
        hiddenId = currentId;
    }
}

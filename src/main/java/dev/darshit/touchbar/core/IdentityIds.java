package dev.darshit.touchbar.core;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/** Stable ids for objects that have none, keyed by identity (not equals). Ids are never reused. */
public final class IdentityIds<T> {
    private final Map<T, Long> ids = new IdentityHashMap<>();
    private long next = 1;

    public synchronized long idFor(T o) {
        return ids.computeIfAbsent(o, k -> next++);
    }

    /** Forget everything not in {@code live}, so finished objects do not leak. */
    public synchronized void retainOnly(Collection<? extends T> live) {
        Map<T, Boolean> keep = new IdentityHashMap<>();
        for (T o : live) keep.put(o, true);
        ids.keySet().removeIf(k -> !keep.containsKey(k));
    }
}

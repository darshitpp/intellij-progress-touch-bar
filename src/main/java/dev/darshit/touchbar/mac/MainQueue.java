package dev.darshit.touchbar.mac;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * FIFO of work for the AppKit main thread. One native callback drains one entry per dispatch, so the number of
 * native callbacks is constant instead of one per call. Never throws: it runs inside a native upcall, where an
 * escaping exception would crash the JVM.
 */
final class MainQueue {
    private record Entry(Runnable task, Consumer<Throwable> onError) {}

    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final Consumer<Throwable> onHandlerFailure;

    MainQueue(Consumer<Throwable> onHandlerFailure) { this.onHandlerFailure = onHandlerFailure; }

    void enqueue(Runnable task, Consumer<Throwable> onError) { queue.add(new Entry(task, onError)); }

    void runOne() {
        Entry e = queue.poll();
        if (e == null) return;
        try { e.task().run(); }
        catch (Throwable t) {
            try { e.onError().accept(t); }
            catch (Throwable t2) {
                try { onHandlerFailure.accept(t2); } catch (Throwable ignored) { /* nothing left to do */ }
            }
        }
    }
}

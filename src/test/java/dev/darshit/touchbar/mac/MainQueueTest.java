package dev.darshit.touchbar.mac;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MainQueueTest {
    final List<String> ran = new ArrayList<>();
    final List<Throwable> handlerFailures = new ArrayList<>();
    final MainQueue q = new MainQueue(handlerFailures::add);

    @Test void runs_one_task_per_drain_in_order() {
        q.enqueue(() -> ran.add("a"), t -> {});
        q.enqueue(() -> ran.add("b"), t -> {});
        q.runOne();
        assertEquals(List.of("a"), ran);
        q.runOne();
        assertEquals(List.of("a", "b"), ran);
    }

    @Test void a_failing_task_goes_to_its_error_handler_and_never_throws() {
        List<Throwable> errors = new ArrayList<>();
        RuntimeException boom = new RuntimeException("boom");
        q.enqueue(() -> { throw boom; }, errors::add);
        q.enqueue(() -> ran.add("next"), errors::add);
        assertDoesNotThrow(q::runOne);
        assertEquals(List.of(boom), errors);
        q.runOne();
        assertEquals(List.of("next"), ran);
    }

    @Test void a_throwing_error_handler_is_contained() {
        q.enqueue(() -> { throw new IllegalStateException("task"); }, t -> { throw new IllegalStateException("handler"); });
        assertDoesNotThrow(q::runOne);
        assertEquals(1, handlerFailures.size());
    }

    @Test void draining_an_empty_queue_is_harmless() {
        assertDoesNotThrow(q::runOne);
    }
}

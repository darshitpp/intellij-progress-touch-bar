package dev.darshit.touchbar.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class BarCoordinatorTest {
    final List<String> events = new ArrayList<>();
    final Map<String, TaskSnapshot> tasks = new HashMap<>();
    final List<Consumer<Throwable>> failures = new ArrayList<>();
    final List<Runnable> hides = new ArrayList<>();
    boolean percent = false;
    long now = 0;
    long delay = 500;
    final List<Throwable> disabledBy = new ArrayList<>();

    final java.util.function.Function<Consumer<Throwable>, BarCoordinator.Port> factory = onFail -> {
        failures.add(onFail);
        return new BarCoordinator.Port() {
            public void show(BarContent c, Runnable onHide) { events.add("show " + fmt(c)); hides.add(onHide); }
            public void update(BarContent c) { events.add("update " + fmt(c)); }
            public void remove() { events.add("remove"); }
        };
    };

    static String fmt(BarContent c) { return c.fraction() + " " + c.label() + (c.name() == null ? "" : " " + c.name()); }

    BarCoordinator<String> withSource(java.util.function.Function<String, TaskSnapshot> src) {
        return new BarCoordinator<>(src, factory, () -> percent, () -> delay, () -> now, disabledBy::add);
    }

    BarCoordinator<String> c = withSource(tasks::get);

    void tick(String focused, String... open) { c.tick(List.of(open), focused); }

    @Test void uses_the_configured_delay() {
        delay = 2000;
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1999; tick("A", "A");
        assertTrue(events.isEmpty());
        now = 2000; tick("A", "A");
        assertEquals(List.of("show 0.5 null"), events);
    }

    @Test void short_task_shows_nothing_then_bar_after_half_a_second() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 499; tick("A", "A");
        assertTrue(events.isEmpty());
        now = 500; tick("A", "A");
        assertEquals(List.of("show 0.5 null"), events);
    }

    @Test void task_end_removes_the_bar() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        tasks.clear(); now = 1100; tick("A", "A");
        assertEquals("remove", events.get(events.size() - 1));
    }

    @Test void native_failure_disables_for_the_session() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        failures.get(0).accept(new RuntimeException("boom"));
        events.clear();
        now = 3000; tick("A", "A");
        tasks.clear(); tick("A", "A");
        tasks.put("A", new TaskSnapshot(2, 0.5)); tick("A", "A"); now = 5000; tick("A", "A");
        assertTrue(events.isEmpty());
    }

    @Test void hide_button_removes_now_and_stays_hidden_for_that_task() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        hides.get(0).run();
        assertEquals("remove", events.get(events.size() - 1));
        events.clear();
        now = 2000; tick("A", "A");
        assertTrue(events.isEmpty());
        tasks.put("A", new TaskSnapshot(2, 0.5)); now = 2100; tick("A", "A"); now = 3100; tick("A", "A");
        assertEquals(List.of("show 0.5 null"), events);
    }

    @Test void two_windows_have_independent_hide_state() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tasks.put("B", new TaskSnapshot(2, 0.7));
        tick("A", "A", "B"); now = 1000; tick("A", "A", "B");
        hides.get(0).run();
        events.clear();
        now = 1100; tick("B", "A", "B"); // B has been running as long; shows without a new 500 ms wait
        assertEquals(List.of("show 0.7 null"), events);
        events.clear();
        now = 1200; tick("A", "A", "B"); // back to A: still hidden
        assertEquals(List.of("remove"), events);
    }

    @Test void task_ending_while_another_app_is_focused_leaves_no_bar() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        now = 1100; tick(null, "A"); // IDE lost focus, task still running: bar stays with its window
        assertEquals(List.of("show 0.5 null"), events);
        tasks.clear(); now = 1200; tick(null, "A");
        assertEquals("remove", events.get(events.size() - 1));
    }

    @Test void closing_the_project_removes_the_bar() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        now = 1100; tick(null);
        assertEquals("remove", events.get(events.size() - 1));
    }

    @Test void percent_setting_change_updates_label_without_reshow() {
        tasks.put("A", new TaskSnapshot(1, 0.426));
        tick("A", "A"); now = 1000; tick("A", "A");
        percent = true; now = 1100; tick("A", "A");
        assertEquals(List.of("show 0.426 null", "update 0.426 43%"), events);
    }

    @Test void unchanged_values_are_not_resent() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A"); now = 1250; tick("A", "A");
        assertEquals(1, events.size());
    }

    @Test void indeterminate_task_never_gets_a_percentage() {
        percent = true;
        tasks.put("A", new TaskSnapshot(1, null));
        tick("A", "A"); now = 1000; tick("A", "A");
        assertEquals(List.of("show null null"), events);
    }

    @Test void a_throwing_source_does_not_break_the_tick() {
        // no Port is ever created here (the source never yields a task), so the null factory is never called
        BarCoordinator<String> bad = withSource(p -> { throw new IllegalStateException(); });
        assertDoesNotThrow(() -> bad.tick(List.of("A"), "A"));
        assertFalse(bad.isDisabled(), "an ordinary exception is transient, not fatal");
    }

    @Test void ticks_with_no_focus_or_projects_are_harmless() {
        assertDoesNotThrow(() -> c.tick(List.of(), null));
        assertDoesNotThrow(() -> c.tick(List.of("A"), null));
        assertTrue(events.isEmpty());
    }

    @Test void task_name_is_shown_and_a_name_change_updates_without_reshow() {
        tasks.put("A", new TaskSnapshot(1, 0.5, "Indexing"));
        tick("A", "A"); now = 1000; tick("A", "A");
        tasks.put("A", new TaskSnapshot(1, 0.5, "Scanning")); now = 1250; tick("A", "A");
        assertEquals(List.of("show 0.5 null Indexing", "update 0.5 null Scanning"), events);
    }

    @Test void a_linkage_error_from_the_source_disables_the_feature_and_restores_the_bar() {
        int[] calls = {0};
        BarCoordinator<String> bc = withSource(p -> {
            if (++calls[0] > 2) throw new NoSuchMethodError("StatusBarEx.getBackgroundProcessModels");
            return new TaskSnapshot(1, 0.5);
        });
        bc.tick(List.of("A"), "A"); now = 1000; bc.tick(List.of("A"), "A");
        assertEquals(List.of("show 0.5 null"), events);
        now = 1250; bc.tick(List.of("A"), "A");
        assertTrue(bc.isDisabled());
        assertEquals("remove", events.get(events.size() - 1));
        assertEquals(1, disabledBy.size());
        assertInstanceOf(NoSuchMethodError.class, disabledBy.get(0));
        int before = calls[0];
        now = 2000; bc.tick(List.of("A"), "A");
        assertEquals(before, calls[0], "a disabled coordinator must not touch the source again");
    }

    @Test void a_native_failure_reports_its_cause_once() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        RuntimeException cause = new RuntimeException("native");
        failures.get(0).accept(cause);
        assertEquals(List.of(cause), disabledBy);
        assertTrue(c.isDisabled());
    }

    @Test void a_native_failure_also_removes_the_failed_port_so_its_frame_loop_stops() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        events.clear();
        failures.get(0).accept(new RuntimeException("main thread"));
        assertEquals(List.of("remove"), events);
    }

    @Test void moving_focus_between_projects_with_bars_removes_the_old_bar_first() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tasks.put("B", new TaskSnapshot(2, 0.7));
        tick("A", "A", "B"); now = 1000; tick("A", "A", "B");
        events.clear();
        now = 1100; tick("B", "A", "B");
        assertEquals(List.of("remove", "show 0.7 null"), events);
    }

    @Test void a_stale_hide_callback_does_not_remove_the_new_bar() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tasks.put("B", new TaskSnapshot(2, 0.7));
        tick("A", "A", "B"); now = 1000; tick("A", "A", "B");
        now = 1100; tick("B", "A", "B");
        events.clear();
        hides.get(0).run(); // A's old button
        assertTrue(events.isEmpty());
    }

    @Test void a_failure_from_an_already_removed_bar_still_disables() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        tasks.clear(); now = 1100; tick("A", "A"); // bar removed
        failures.get(0).accept(new RuntimeException("late"));
        assertTrue(c.isDisabled());
    }

    @Test void one_projects_failing_source_does_not_hide_another_projects_task() {
        BarCoordinator<String> bc = withSource(p -> {
            if (p.equals("A")) throw new IllegalStateException("A is broken");
            return new TaskSnapshot(2, 0.7);
        });
        bc.tick(List.of("A", "B"), "B"); now = 1000; bc.tick(List.of("A", "B"), "B");
        assertEquals(List.of("show 0.7 null"), events);
    }

    @Test void a_reopened_project_starts_with_clean_state() {
        tasks.put("A", new TaskSnapshot(1, 0.5));
        tick("A", "A"); now = 1000; tick("A", "A");
        hides.get(0).run();
        now = 1100; c.tick(List.of(), null);          // project closed
        events.clear();
        c.tick(List.of("A"), "A");                    // reopened, same task id
        assertTrue(events.isEmpty(), "must wait the full delay again");
        now = 2200; tick("A", "A");
        assertEquals(List.of("show 0.5 null"), events, "and the earlier Hide must not apply");
    }
}

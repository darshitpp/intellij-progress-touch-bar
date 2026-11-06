package dev.darshit.touchbar.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BarStateTest {
    private static TaskSnapshot t(long id) { return new TaskSnapshot(id, 0.5); }

    @Test void short_task_never_shows() {
        BarState s = new BarState();
        s.update(0, t(1));
        assertFalse(s.update(499, t(1)).show());
    }

    @Test void shows_after_half_a_second() {
        BarState s = new BarState();
        s.update(0, t(1));
        assertTrue(s.update(500, t(1)).show());
    }

    @Test void delay_comes_from_the_setting_and_is_read_live() {
        long[] delay = {2000};
        BarState s = new BarState(() -> delay[0]);
        s.update(0, t(1));
        assertFalse(s.update(1999, t(1)).show());
        delay[0] = 0;
        assertTrue(s.update(1999, t(1)).show());
    }

    @Test void hides_when_task_ends() {
        BarState s = new BarState();
        s.update(0, t(1));
        s.update(1000, t(1));
        assertFalse(s.update(2000, null).show());
    }

    @Test void hide_lasts_for_that_task_only() {
        BarState s = new BarState();
        s.update(0, t(1));
        assertTrue(s.update(1000, t(1)).show());
        s.hide();
        assertFalse(s.update(3000, t(1)).show());
        s.update(4000, t(2));
        assertTrue(s.update(5000, t(2)).show());
    }

    @Test void task_that_restarts_is_new() {
        BarState s = new BarState();
        s.update(0, t(1));
        s.update(1000, t(1));
        s.update(2000, null);
        s.update(2100, t(1));
        assertFalse(s.update(2200, t(1)).show());
    }

    @Test void hidden_task_that_restarts_is_new() {
        BarState s = new BarState();
        s.update(0, t(1));
        s.update(1000, t(1));
        s.hide();
        s.update(2000, null);
        s.update(2100, t(1));
        assertTrue(s.update(3100, t(1)).show());
    }

    @Test void indeterminate_has_null_fraction() {
        BarState s = new BarState();
        s.update(0, new TaskSnapshot(1, null));
        BarState.Output o = s.update(1000, new TaskSnapshot(1, null));
        assertTrue(o.show());
        assertNull(o.fraction());
    }

    @Test void bad_fractions_are_clamped() {
        BarState s = new BarState();
        s.update(0, new TaskSnapshot(1, -0.5));
        assertEquals(0.0, s.update(1000, new TaskSnapshot(1, -0.5)).fraction());
        assertEquals(1.0, s.update(1001, new TaskSnapshot(1, 1.7)).fraction());
        assertNull(s.update(1002, new TaskSnapshot(1, Double.NaN)).fraction());
    }

    @Test void task_switch_while_shown_resets_timer() {
        BarState s = new BarState();
        s.update(0, t(1));
        assertTrue(s.update(1000, t(1)).show());
        assertFalse(s.update(1100, t(2)).show());
        assertTrue(s.update(2100, t(2)).show());
    }

    @Test void fraction_going_backwards_is_passed_through() {
        BarState s = new BarState();
        s.update(0, new TaskSnapshot(1, 0.8));
        s.update(1000, new TaskSnapshot(1, 0.8));
        assertEquals(0.3, s.update(1100, new TaskSnapshot(1, 0.3)).fraction());
    }

    @Test void hide_with_no_task_is_harmless() {
        BarState s = new BarState();
        s.hide();
        s.update(0, t(1));
        assertTrue(s.update(1000, t(1)).show());
    }

    @Test void name_passes_through_and_follows_the_task() {
        BarState s = new BarState();
        s.update(0, new TaskSnapshot(1, 0.5, "Indexing"));
        assertEquals("Indexing", s.update(1000, new TaskSnapshot(1, 0.5, "Indexing")).name());
        assertEquals("Scanning", s.update(1100, new TaskSnapshot(1, 0.5, "Scanning")).name());
        assertNull(s.update(1200, new TaskSnapshot(1, 0.5)).name());
    }
}

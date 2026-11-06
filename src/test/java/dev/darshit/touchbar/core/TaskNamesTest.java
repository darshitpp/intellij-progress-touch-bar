package dev.darshit.touchbar.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaskNamesTest {
    @Test void first_non_blank_candidate_wins_and_is_stripped() {
        assertEquals("Indexing…", TaskNames.pick(null, "  ", "  Indexing…  ", "later"));
    }

    @Test void nothing_usable_is_null() {
        assertNull(TaskNames.pick());
        assertNull(TaskNames.pick(null, "", " \t "));
    }

    @Test void very_long_names_are_capped_with_an_ellipsis() {
        String n = TaskNames.pick("x".repeat(5000));
        assertEquals(TaskNames.MAX_CHARS, n.length());
        assertTrue(n.endsWith("…"));
    }

    @Test void a_name_at_the_limit_is_untouched() {
        String s = "y".repeat(TaskNames.MAX_CHARS);
        assertEquals(s, TaskNames.pick(s));
    }

    @Test void the_cut_never_splits_a_surrogate_pair() {
        String s = "a".repeat(TaskNames.MAX_CHARS - 2) + "😀😀😀";
        String n = TaskNames.pick(s);
        assertTrue(n.length() <= TaskNames.MAX_CHARS);
        assertFalse(Character.isHighSurrogate(n.charAt(n.length() - 2)));
    }
}

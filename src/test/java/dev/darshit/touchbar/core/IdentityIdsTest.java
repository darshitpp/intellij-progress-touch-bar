package dev.darshit.touchbar.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityIdsTest {
    // equal but distinct objects: ids must follow identity, not equals()
    private static String s(String v) { return new String(v); }

    @Test void same_object_keeps_its_id() {
        IdentityIds<String> ids = new IdentityIds<>();
        String a = s("x");
        assertEquals(ids.idFor(a), ids.idFor(a));
    }

    @Test void equal_but_distinct_objects_get_different_ids() {
        IdentityIds<String> ids = new IdentityIds<>();
        assertNotEquals(ids.idFor(s("x")), ids.idFor(s("x")));
    }

    @Test void forgotten_objects_are_dropped_and_never_reuse_an_id() {
        IdentityIds<String> ids = new IdentityIds<>();
        String a = s("a"), b = s("b");
        long ia = ids.idFor(a), ib = ids.idFor(b);
        ids.retainOnly(List.of(b));
        assertEquals(ib, ids.idFor(b));
        assertNotEquals(ia, ids.idFor(a));
    }
}

package dev.darshit.touchbar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PercentTest {
    @Test void rounds() { assertEquals("43%", Percent.format(0.426)); }
    @Test void null_is_empty() { assertEquals("", Percent.format(null)); }
    @Test void clamps_high() { assertEquals("100%", Percent.format(1.7)); }
    @Test void nan_is_empty() { assertEquals("", Percent.format(Double.NaN)); }
    @Test void clamps_low() { assertEquals("0%", Percent.format(-0.2)); }
}

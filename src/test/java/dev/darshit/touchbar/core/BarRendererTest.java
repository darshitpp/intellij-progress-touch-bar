package dev.darshit.touchbar.core;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BarRendererTest {
    /** A theme like Nyan/Mario: fixed 20 px band, one animation step per paint. */
    public static class StubUi extends BasicProgressBarUI {
        int n;
        public static ComponentUI createUI(JComponent c) { return new StubUi(); }
        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(100, 20); }
        @Override protected void paintDeterminate(Graphics g, JComponent c) {
            g.setColor(Color.RED);
            g.fillRect(0, 0, (int) Math.round(c.getWidth() * progressBar.getPercentComplete()), 20);
        }
        @Override protected void paintIndeterminate(Graphics g, JComponent c) {
            g.setColor(Color.GREEN);
            g.fillRect(n++ % c.getWidth(), 0, 10, 20);
        }
    }

    public static class ThrowingUi extends StubUi {
        public static ComponentUI createUI(JComponent c) { return new ThrowingUi(); }
        @Override protected void paintDeterminate(Graphics g, JComponent c) { throw new IllegalStateException("theme bug"); }
    }

    /** Throws while painting indeterminate; remembers its bar so tests can inspect it. */
    public static class ThrowingIndeterminateUi extends StubUi {
        static JProgressBar last;
        public static ComponentUI createUI(JComponent c) { return new ThrowingIndeterminateUi(); }
        @Override public void installUI(JComponent c) { super.installUI(c); last = (JProgressBar) c; }
        @Override protected void paintIndeterminate(Graphics g, JComponent c) { throw new IllegalStateException("theme bug"); }
    }

    /** Throws when its bar leaves indeterminate mode (theme code runs inside setIndeterminate). */
    public static class ThrowsOnLeavingIndeterminateUi extends StubUi {
        public static ComponentUI createUI(JComponent c) { return new ThrowsOnLeavingIndeterminateUi(); }
        @Override protected void installListeners() {
            super.installListeners();
            progressBar.addPropertyChangeListener("indeterminate", e -> {
                if (Boolean.FALSE.equals(e.getNewValue())) throw new IllegalStateException("theme bug");
            });
        }
    }

    /** A second theme: same band, but green. */
    public static class OtherUi extends StubUi {
        public static ComponentUI createUI(JComponent c) { return new OtherUi(); }
        @Override protected void paintDeterminate(Graphics g, JComponent c) {
            g.setColor(Color.GREEN);
            g.fillRect(0, 0, (int) Math.round(c.getWidth() * progressBar.getPercentComplete()), 20);
        }
    }

    /** Declares no createUI of its own: the inherited one builds a plain StubUi, not this class. */
    public static class NoFactoryUi extends StubUi {}

    private static final String[] KEYS ={"ProgressBarUI", "ProgressBar.progressColor", "ProgressBar.trackColor"};
    private static final java.util.function.Predicate<Class<?>> STUBS_ARE_THEMES = c -> !StubUi.class.isAssignableFrom(c);
    private static final int BLUE = Color.BLUE.getRGB();
    private final Map<String, Object> saved = new HashMap<>();

    @BeforeEach void save() { for (String k : KEYS) saved.put(k, UIManager.get(k)); }
    @AfterEach void restore() { for (String k : KEYS) UIManager.put(k, saved.get(k)); }

    static void install(Class<? extends ComponentUI> cls) {
        UIManager.put("ProgressBarUI", cls.getName());
        UIManager.getDefaults().put(cls.getName(), cls);
    }

    static int px(int[] a, int x, int y) { return a[y * BarRenderer.IMAGE_W + x]; }

    BarRenderer renderer() {
        install(StubUi.class);
        return new BarRenderer(c -> c != StubUi.class && c != ThrowingUi.class, t -> fail(t));
    }

    BarRenderer standard() { return new BarRenderer(c -> true, t -> fail(t)); }

    static BarContent at(Double f) { return new BarContent(f, null, null); }

    @Test void themeFillsTheFullHeight() {
        int[] a = renderer().paint(at(0.5));
        for (int y : new int[]{0, 30, 59}) assertEquals(Color.RED.getRGB(), px(a, 100, y));
    }

    @Test void unpaintedPixelsStayTransparent() {
        assertEquals(0, px(renderer().paint(at(0.5)), 700, 30) >>> 24);
    }

    @Test void switchesModeBetweenFrames() {
        var r = renderer();
        r.paint(at(null));
        assertEquals(Color.RED.getRGB(), px(r.paint(at(1.0)), 790, 30));
    }

    @Test void identicalThemedFrameReturnsNull() {
        var r = renderer();
        assertNotNull(r.paint(at(0.5)));
        assertNull(r.paint(at(0.5)));
    }

    @Test void firstFrameAfterNewBarIsNeverNull() { // a new native bar has no image yet
        var r = renderer();
        r.paint(at(0.5));
        r.newBar();
        assertNotNull(r.paint(at(0.5)));
        var s = standard();
        s.paint(at(0.5));
        s.newBar();
        assertNotNull(s.paint(at(0.5)));
    }

    @Test void noThemeDrawsStandardBarFullHeight() {
        UIManager.put("ProgressBar.progressColor", Color.BLUE);
        UIManager.put("ProgressBar.trackColor", Color.GRAY);
        int[] a = standard().paint(at(0.5));
        assertEquals(BLUE, px(a, 200, 2));                 // near the top edge, mid-fill
        assertEquals(Color.GRAY.getRGB(), px(a, 600, 57)); // near the bottom edge, track
        assertEquals(0, px(a, 0, 0) >>> 24);               // rounded corner: transparent
    }

    @Test void standardDeterminateUnchangedReturnsNull() {
        var r = standard();
        r.paint(at(0.5));
        assertNull(r.paint(at(0.5)));
        assertNotNull(r.paint(at(0.6)));
    }

    @Test void standardIndeterminateMoves() {
        var r = standard();
        assertFalse(Arrays.equals(r.paint(at(null)), r.paint(at(null))));
    }

    @Test void throwingThemeFallsBackOnceForTheSession() {
        UIManager.put("ProgressBar.progressColor", Color.BLUE);
        install(ThrowingUi.class);
        var failures = new ArrayList<Throwable>();
        var r = new BarRenderer(c -> c != ThrowingUi.class, failures::add);
        assertEquals(BLUE, px(r.paint(at(0.5)), 200, 30));
        r.newBar();
        assertEquals(BLUE, px(r.paint(at(0.4)), 200, 30));
        assertEquals(1, failures.size());
        assertFalse(r.isThemed());
    }

    @Test void missingUiClassFallsBackToStandard() { // onThemeFailure may fire, at most once
        UIManager.put("ProgressBar.progressColor", Color.BLUE);
        UIManager.put("ProgressBarUI", "does.not.Exist");
        var failures = new ArrayList<Throwable>();
        int[] a = assertDoesNotThrow(() -> new BarRenderer(c -> false, failures::add).paint(at(0.5)));
        assertEquals(BLUE, px(a, 200, 30));
        assertTrue(failures.size() <= 1);
    }

    @Test void droppedThemeStaysOffUntilTheNextBar() { // a theme plugin is unloading: don't rebuild from its class
        UIManager.put("ProgressBar.progressColor", Color.BLUE);
        var r = renderer();
        assertTrue(r.paint(at(0.5)) != null && r.isThemed());
        r.dropThemeUntilNextBar();
        assertEquals(BLUE, px(r.paint(at(0.5)), 200, 30)); // standard, and never a null first frame
        assertFalse(r.isThemed());
        r.newBar();
        r.paint(at(0.5));
        assertTrue(r.isThemed());
    }

    @Test void standardBarRepaintsWhenThemeColoursChange() { // Light/Dark switch while progress is stalled
        UIManager.put("ProgressBar.progressColor", Color.BLUE);
        var r = standard();
        r.paint(at(0.5));
        UIManager.put("ProgressBar.progressColor", Color.RED);
        int[] a = r.paint(at(0.5));
        assertNotNull(a);
        assertEquals(Color.RED.getRGB(), px(a, 200, 30));
    }

    @Test void themeThrowingWhileBarIsDroppedDoesNotEscape() {
        install(ThrowsOnLeavingIndeterminateUi.class);
        var failures = new ArrayList<Throwable>();
        var r = new BarRenderer(STUBS_ARE_THEMES, failures::add);
        r.paint(at(null));                    // bar is now indeterminate
        assertDoesNotThrow(r::newBar);        // leaving indeterminate throws inside the theme
        assertEquals(1, failures.size());     // reported once, and the session falls back to standard
        assertNotNull(r.paint(at(0.5)));
        assertFalse(r.isThemed());
    }

    @Test void themeFailureDropsTheBarOutOfIndeterminateMode() { // so a UI animation timer can't keep it alive
        install(ThrowingIndeterminateUi.class);
        var r = new BarRenderer(STUBS_ARE_THEMES, t -> {});
        r.paint(at(null));
        assertFalse(ThrowingIndeterminateUi.last.isIndeterminate());
    }

    // Two theme plugins overwrite the one global ProgressBarUI setting, and a JProgressBar keeps the look it was
    // created with, so a new bar can differ from the bar the IDE is showing. We copy the IDE's bar instead.

    @Test void copiesTheThemeOfTheBarTheIdeIsShowing() {
        install(StubUi.class); // what the setting says now, and what a new JProgressBar would get
        var r = new BarRenderer(STUBS_ARE_THEMES, t -> fail(t), () -> OtherUi.class);
        assertEquals(Color.GREEN.getRGB(), px(r.paint(at(0.5)), 100, 30));
        assertEquals(OtherUi.class.getName(), r.themeName());
    }

    @Test void usesTheRegisteredThemeWhenTheIdeShowsNoBar() {
        install(StubUi.class);
        var r = new BarRenderer(STUBS_ARE_THEMES, t -> fail(t), () -> null);
        assertEquals(Color.RED.getRGB(), px(r.paint(at(0.5)), 100, 30));
        assertEquals(StubUi.class.getName(), r.themeName());
    }

    @Test void aBuiltInIdeBarMeansTheStandardBarEvenIfAThemeIsRegistered() {
        UIManager.put("ProgressBar.progressColor", Color.BLUE);
        install(StubUi.class);
        var r = new BarRenderer(c -> c == BasicProgressBarUI.class, t -> fail(t), () -> BasicProgressBarUI.class);
        assertEquals(BLUE, px(r.paint(at(0.5)), 200, 30));
        assertFalse(r.isThemed());
        assertNull(r.themeName());
    }

    @Test void aShownThemeThatCannotBeRebuiltFallsBackToTheRegisteredOne() {
        install(StubUi.class);
        var r = new BarRenderer(STUBS_ARE_THEMES, t -> fail(t), () -> NoFactoryUi.class);
        assertEquals(Color.RED.getRGB(), px(r.paint(at(0.5)), 100, 30));
    }

    @Test void aFailingLookupFallsBackToTheRegisteredTheme() {
        install(StubUi.class);
        var r = new BarRenderer(STUBS_ARE_THEMES, t -> fail(t), () -> { throw new IllegalStateException("no frame"); });
        assertEquals(Color.RED.getRGB(), px(r.paint(at(0.5)), 100, 30));
    }

    @Test void findsTheFirstProgressBarInAComponentTree() {
        var root = new JPanel();
        var inner = new JPanel();
        var bar = new JProgressBar();
        root.add(new JLabel("x"));
        root.add(inner);
        inner.add(bar);
        assertSame(bar, BarRenderer.firstProgressBar(root));
        assertNull(BarRenderer.firstProgressBar(new JPanel()));
    }

    @Test void reportsWhetherThemed() {
        var r = renderer();
        r.paint(at(0.5));
        assertTrue(r.isThemed());
        var s = standard();
        s.paint(at(0.5));
        assertFalse(s.isThemed());
    }

    @Test void builtInUiIsRecognisedByPackage() {
        assertTrue(BarRenderer.isBuiltInUi(javax.swing.plaf.basic.BasicProgressBarUI.class));
        assertTrue(BarRenderer.isBuiltInUiName("com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI"));
        assertTrue(BarRenderer.isBuiltInUiName("com.jetbrains.SomeUi"));
        assertFalse(BarRenderer.isBuiltInUiName("org.intellij.nyan.NyanProgressBarUi"));    // Nyan-like
        assertFalse(BarRenderer.isBuiltInUiName("com.kagof.intellij.plugins.pokeprogress.PokemonProgressBarUi"));
        assertFalse(BarRenderer.isBuiltInUi(StubUi.class));
    }

    @Test void intervalForFps() {
        assertEquals(33, BarRenderer.intervalForFps(30));
        assertEquals(20, BarRenderer.intervalForFps(50));
        assertEquals(17, BarRenderer.intervalForFps(60));
        assertEquals(100, BarRenderer.intervalForFps(10));
        assertEquals(17, BarRenderer.intervalForFps(500)); // clamped to 10..60 fps
        assertEquals(100, BarRenderer.intervalForFps(0));
    }

    @Test void frameInterval() {
        assertEquals(50, BarRenderer.frameIntervalMs(50));
        assertEquals(50, BarRenderer.frameIntervalMs(50L));
        assertEquals(33, BarRenderer.frameIntervalMs(null));
        assertEquals(33, BarRenderer.frameIntervalMs("x"));
        assertEquals(33, BarRenderer.frameIntervalMs(0));
        assertEquals(16, BarRenderer.frameIntervalMs(5));
        assertEquals(200, BarRenderer.frameIntervalMs(1000));
    }
}

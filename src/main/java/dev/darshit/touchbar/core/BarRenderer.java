package dev.darshit.touchbar.core;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.ProgressBarUI;
import org.jetbrains.annotations.Nullable;

/**
 * Paints one Touch Bar frame (EDT only). Uses whatever ProgressBarUI is registered (Nyan, Mario, ... register theirs
 * globally through UIManager) on an offscreen JProgressBar, scaled evenly to fill the height. With no theme, or after
 * a theme failed once, draws the IDE's standard bar itself so it can fill the full height.
 * <p>
 * Themes advance their animation one step per paint, so the caller's frame rate sets the animation speed.
 */
public final class BarRenderer {
    public static final int IMAGE_W = 800, IMAGE_H = 60; // 400 × 30 pt at 2×
    private static final int SWEEP_W = 240, SWEEP_STEP = 24;

    private final Predicate<Class<?>> isPlatformUi;
    private final Consumer<Throwable> onThemeFailure;
    private final Supplier<@Nullable Class<?>> shownUi;
    private final BufferedImage img = new BufferedImage(IMAGE_W, IMAGE_H, BufferedImage.TYPE_INT_ARGB_PRE);
    private final int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

    private @Nullable JProgressBar bar;
    private boolean barTried, themeBroken, themed;
    private int @Nullable [] lastThemed;          // never mutated once returned, so it is handed out as is
    private boolean hasLastStandard;
    private @Nullable Double lastStandard;
    private @Nullable Color lastTrack, lastProgress; // a theme (Light/Dark) switch must repaint even if progress stalls
    private long sweep;

    public BarRenderer(Predicate<Class<?>> isPlatformUi, Consumer<Throwable> onThemeFailure) {
        this(isPlatformUi, onThemeFailure, () -> null);
    }

    /**
     * @param shownUi the ProgressBarUI class of the bar the IDE is showing right now, or null if none is. Two theme
     *                plugins overwrite the one global setting, and a bar keeps the look it was created with, so a
     *                new bar can differ from the IDE's; copying the IDE's bar keeps the Touch Bar the same.
     */
    public BarRenderer(Predicate<Class<?>> isPlatformUi, Consumer<Throwable> onThemeFailure, Supplier<@Nullable Class<?>> shownUi) {
        this.isPlatformUi = isPlatformUi;
        this.onThemeFailure = onThemeFailure;
        this.shownUi = shownUi;
    }

    /** The first JProgressBar below {@code root}, depth first; null if there is none. */
    public static @Nullable JProgressBar firstProgressBar(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JProgressBar b) return b;
            if (c instanceof Container inner && firstProgressBar(inner) instanceof JProgressBar found) return found;
        }
        return null;
    }

    public static final int MIN_FPS = 10, MAX_FPS = 60, DEFAULT_FPS = 30;

    /** Frame interval for the user's frame-rate setting, clamped to {@link #MIN_FPS}..{@link #MAX_FPS}. */
    public static int intervalForFps(int fps) {
        return (int) Math.round(1000.0 / Math.clamp(fps, MIN_FPS, MAX_FPS));
    }

    /** IDE progress repaint interval, clamped to 16..200 ms; 33 ms if unset or invalid. */
    public static int frameIntervalMs(@Nullable Object repaintInterval) {
        return repaintInterval instanceof Number n && n.intValue() > 0 ? Math.clamp(n.intValue(), 16, 200) : 33;
    }

    /**
     * Whether a ProgressBarUI is the IDE's own rather than a theme plugin's, judged by package.
     * ponytail: package prefix, because the plugin-lookup APIs are internal; a theme shipped in these packages would
     * be drawn as the standard bar (the once-per-bar log line shows which was picked).
     */
    public static boolean isBuiltInUi(Class<?> ui) { return isBuiltInUiName(ui.getName()); }

    static boolean isBuiltInUiName(String name) {
        return name.startsWith("com.intellij.") || name.startsWith("com.jetbrains.") || name.startsWith("javax.swing.")
                || name.startsWith("com.apple.") || name.startsWith("com.sun.");
    }

    /** Next paint uses a fresh JProgressBar (current theme) and always returns a frame. */
    public void newBar() {
        dropBar();
        barTried = false;
        lastThemed = null;
        hasLastStandard = false;
    }

    /** A theme plugin is unloading: drop its bar and use the standard bar until {@link #newBar} (the next bar). */
    public void dropThemeUntilNextBar() {
        newBar();
        barTried = true; // themedBar() now returns null until newBar()
    }

    public boolean isThemed() { return themed; }

    /** Class name of the theme in use, or null when the standard bar is drawn. */
    public @Nullable String themeName() {
        return themed && bar != null && bar.getUI() != null ? bar.getUI().getClass().getName() : null;
    }

    /** {@code IMAGE_W * IMAGE_H} ARGB-premultiplied pixels, top row first; null if identical to the previous frame. */
    public int @Nullable [] paint(BarContent content) {
        Double f = content.fraction();
        if (!themeBroken) {
            try {
                JProgressBar b = themedBar();
                if (b != null) {
                    themed = true;
                    return paintThemed(b, f);
                }
            } catch (Throwable t) {
                themeFailed(t);
                dropBar();
            }
        }
        themed = false;
        return paintStandard(f);
    }

    /** Leaves indeterminate mode first (stops a UI animation timer, if one started); theme code can throw here too. */
    private void dropBar() {
        JProgressBar b = bar;
        bar = null;
        if (b != null) {
            try { b.setIndeterminate(false); }
            catch (Throwable t) { themeFailed(t); }
        }
    }

    /** For the session: a theme that throws once will likely throw on every frame. Reported once. */
    private void themeFailed(Throwable t) {
        if (themeBroken) return;
        themeBroken = true;
        onThemeFailure.accept(t);
    }

    private @Nullable JProgressBar themedBar() {
        if (!barTried) {
            barTried = true;
            Class<?> shown = shownUiOrNull();
            if (shown != null && isPlatformUi.test(shown)) return bar = null; // the IDE draws its own bar, so do we
            JProgressBar b = new JProgressBar(SwingConstants.HORIZONTAL); // throws if the registered UI class is gone
            if (shown != null) adopt(b, shown);
            b.setOpaque(false);
            b.setDoubleBuffered(false);
            b.setBorderPainted(false);
            bar = b.getUI() == null || isPlatformUi.test(b.getUI().getClass()) ? null : b;
        }
        return bar;
    }

    private @Nullable Class<?> shownUiOrNull() {
        try { return shownUi.get(); } catch (Throwable t) { return null; } // no frame yet, or the lookup broke: use the setting
    }

    /** Gives the bar the IDE bar's UI class the way UIManager would (its static createUI); keeps the registered one if that fails. */
    private static void adopt(JProgressBar b, Class<?> ui) {
        if (b.getUI() != null && b.getUI().getClass() == ui) return;
        try {
            if (ProgressBarUI.class.isAssignableFrom(ui)
                    && ui.getMethod("createUI", JComponent.class).invoke(null, b) instanceof ProgressBarUI created
                    && created.getClass() == ui) {
                b.setUI(created);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { /* keep the registered theme */ }
    }

    private int @Nullable [] paintThemed(JProgressBar b, @Nullable Double f) {
        b.setIndeterminate(f == null);
        if (f != null) {
            b.setMaximum(1000);
            b.setValue((int) Math.round(f * 1000));
        }
        FrameGeometry geo = FrameGeometry.of(b.getPreferredSize().height, IMAGE_W, IMAGE_H);
        b.setSize(geo.componentWidth(), geo.componentHeight());
        Graphics2D g = clearedGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.scale(geo.scale(), geo.scale());
            b.paint(g);
        } finally { g.dispose(); }
        if (lastThemed != null && Arrays.equals(data, lastThemed)) return null;
        return lastThemed = data.clone();
    }

    private int @Nullable [] paintStandard(@Nullable Double f) {
        Color track = color("ProgressBar.trackColor", Color.GRAY);
        Color progress = color("ProgressBar.progressColor", new Color(0x3574F0));
        if (f != null && hasLastStandard && f.equals(lastStandard) && track.equals(lastTrack) && progress.equals(lastProgress)) return null;
        Graphics2D g = clearedGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(track);
            g.fillRoundRect(0, 0, IMAGE_W, IMAGE_H, IMAGE_H, IMAGE_H);
            if (f != null) {
                g.setClip(0, 0, (int) Math.round(IMAGE_W * f), IMAGE_H);
                g.setColor(progress);
                g.fillRoundRect(0, 0, IMAGE_W, IMAGE_H, IMAGE_H, IMAGE_H);
            } else {
                int span = IMAGE_W - SWEEP_W;
                int x = (int) (sweep++ * SWEEP_STEP % (2L * span));
                if (x > span) x = 2 * span - x;
                g.setPaint(new GradientPaint(x, 0, color("ProgressBar.indeterminateStartColor", progress),
                        x + SWEEP_W, 0, color("ProgressBar.indeterminateEndColor", progress)));
                g.fillRoundRect(x, 0, SWEEP_W, IMAGE_H, IMAGE_H, IMAGE_H);
            }
        } finally { g.dispose(); }
        hasLastStandard = f != null;
        lastStandard = f;
        lastTrack = track;
        lastProgress = progress;
        return data.clone();
    }

    /** Transparent canvas with the normal compositing restored. */
    private Graphics2D clearedGraphics() {
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, IMAGE_W, IMAGE_H);
        g.setComposite(AlphaComposite.SrcOver);
        return g;
    }

    private static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }
}

package dev.darshit.touchbar.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import dev.darshit.touchbar.core.BarContent;
import dev.darshit.touchbar.core.BarCoordinator;
import dev.darshit.touchbar.core.BarRenderer;
import dev.darshit.touchbar.mac.TouchBarBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * One bar's life: the native bar plus its frame loop. A Swing timer at the user's frame rate paints a
 * frame on the EDT and hands it to the native bar. Each port has its own timer and stop flag, so a removed bar never
 * paints into the next one.
 */
final class FramedPort implements BarCoordinator.Port {
    private static final Logger LOG = Logger.getInstance(FramedPort.class);

    private final TouchBarBar bar;
    private final BarRenderer renderer;
    private final Timer timer;
    private volatile boolean stopped;
    private volatile BarContent content;
    private boolean logged; // EDT only

    FramedPort(TouchBarBar bar, BarRenderer renderer, int intervalMs) {
        this.bar = bar;
        this.renderer = renderer;
        timer = new Timer(intervalMs, e -> frame()); // coalesces overdue ticks by default
    }

    /** Called on the EDT (BarCoordinator ticks there). */
    @Override public void show(BarContent initial, Runnable onHide) {
        content = initial;
        renderer.newBar(); // first: never throws (theme failures are reported and fall back to the standard bar)
        bar.show(initial, onHide);
        if (ApplicationManager.getApplication().isActive()) timer.start(); // else resume() starts it
    }

    @Override public void update(BarContent next) {
        content = next;
        bar.update(next); // name and percentage; the picture follows on the next frame
    }

    /** From the EDT tick or the Hide button (AppKit main thread). */
    @Override public void remove() {
        stopped = true;
        onEdt(() -> { timer.stop(); renderer.newBar(); });
        bar.remove();
    }

    void pause() { onEdt(timer::stop); }

    void resume() { onEdt(() -> { if (!stopped) timer.start(); }); }

    private void frame() {
        if (stopped || !ApplicationManager.getApplication().isActive()) { timer.stop(); return; }
        try {
            int[] px = renderer.paint(content);
            if (!logged) {
                logged = true;
                LOG.info("Progress Touch Bar: " + (renderer.isThemed() ? "themed (" + renderer.themeName() + ")" : "standard")
                        + " bar, UI registered in the IDE: " + UIManager.get("ProgressBarUI") + ", frame interval "
                        + timer.getDelay() + " ms");
            }
            bar.frame(px, BarRenderer.IMAGE_W, BarRenderer.IMAGE_H);
        } catch (Throwable t) {
            stopped = true;
            timer.stop();
            bar.fail(t); // restores the IDE's Touch Bar, then tells the coordinator
        }
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else ApplicationManager.getApplication().invokeLater(r, ModalityState.any());
    }
}

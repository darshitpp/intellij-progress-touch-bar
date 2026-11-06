package dev.darshit.touchbar.ide;

import com.intellij.openapi.Disposable;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.darshit.touchbar.core.BarCoordinator;
import dev.darshit.touchbar.core.BarRenderer;
import dev.darshit.touchbar.mac.TouchBarBar;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/** Polls every 250 ms and drives the Touch Bar through {@link BarCoordinator}. Created by {@link BarStartup}. */
@Service(Service.Level.APP)
public final class BarController implements Disposable {
    private static final Logger LOG = Logger.getInstance(BarController.class);

    private final BarCoordinator<Project> coordinator;
    /** One for the session: it remembers that a theme failed, so the rest of the session uses the standard bar. */
    private final BarRenderer renderer = new BarRenderer(BarRenderer::isBuiltInUi,
            t -> LOG.warn("Progress Touch Bar: the progress bar theme failed to paint; using the standard bar for this session", t),
            BarController::uiOfIdeProgressBar);
    private volatile FramedPort current;
    private final ScheduledFuture<?> poll;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean disposed;

    public BarController() {
        boolean supported = TouchBarBar.isSupported();
        TaskSource source = new StatusBarTaskSource();
        coordinator = new BarCoordinator<>(
                source::current,
                onFail -> current = new FramedPort(new TouchBarBar(onFail, TouchBarBundle.message("touchbar.hide")), renderer,
                        BarRenderer.intervalForFps(TouchBarSettings.getInstance().getFps())),
                () -> TouchBarSettings.getInstance().isShowPercent(),
                () -> TouchBarSettings.getInstance().getDelayMs(),
                () -> System.nanoTime() / 1_000_000,
                this::onDisabled);
        if (supported) {
            LOG.info("Progress Touch Bar: started");
            TouchBarBar.warmUp();
            ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(ApplicationActivationListener.TOPIC,
                    new ApplicationActivationListener() { // no frames while another app is in front
                        @Override public void applicationActivated(@NotNull IdeFrame f) { FramedPort p = current; if (p != null) p.resume(); }
                        @Override public void applicationDeactivated(@NotNull IdeFrame f) { FramedPort p = current; if (p != null) p.pause(); }
                    });
            ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(DynamicPluginListener.TOPIC,
                    new DynamicPluginListener() { // don't keep an unloading theme's UI class alive; next frame uses a fresh bar
                        @Override public void beforePluginUnload(@NotNull IdeaPluginDescriptor d, boolean isUpdate) {
                            renderer.dropThemeUntilNextBar(); // already on the EDT; must happen before the unload completes
                        }
                    });
            poll = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(this::requestTick, 250, 250, TimeUnit.MILLISECONDS);
        } else {
            poll = null;
            LOG.info("Progress Touch Bar: not supported here (not macOS, or no NSTouchBar); doing nothing");
        }
    }

    /**
     * The theme of the progress bar in the focused window's status bar (EDT), or null if it shows none. Theme plugins
     * overwrite one global setting and each bar keeps the look it was created with, so this, not the setting, is
     * what the user sees in the IDE.
     */
    private static Class<?> uiOfIdeProgressBar() {
        IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
        StatusBar statusBar = frame == null ? null : frame.getStatusBar();
        JComponent root = statusBar == null ? null : statusBar.getComponent();
        JProgressBar bar = root == null ? null : BarRenderer.firstProgressBar(root);
        return bar == null || bar.getUI() == null ? null : bar.getUI().getClass();
    }

    /** The status bar's task list is mutated on the EDT, so read it there; at most one tick queued at a time. */
    private void requestTick() {
        if (queued.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater(() -> { queued.set(false); tick(); }, ModalityState.any());
        }
    }

    private void tick() {
        if (disposed) return;
        try {
            List<Project> open = Arrays.stream(ProjectManager.getInstance().getOpenProjects()).filter(p -> !p.isDisposed()).toList();
            IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
            Project focused = frame == null ? null : frame.getProject();
            coordinator.tick(open, focused);
        } catch (Throwable t) { // never let the scheduled task die
            LOG.warn("Progress Touch Bar tick failed", t);
        }
    }

    /** Called once, with the cause, when the coordinator gives up for this session. */
    private void onDisabled(Throwable cause) {
        LOG.warn("Progress Touch Bar disabled for this session; the IDE's own Touch Bar has been restored", cause);
        if (poll != null) poll.cancel(false);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (poll != null) poll.cancel(false);
        coordinator.tick(List.of(), null); // removes any bar still up
    }
}

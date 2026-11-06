package dev.darshit.touchbar.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * The decisions, IDE-free: per tick it feeds every open project's task into that project's {@link BarState} and
 * drives one native bar for the focused project. {@code P} is the project type (IntelliJ's {@code Project}).
 * Any fatal problem (native failure, or the platform class behind the task source changing) restores the IDE's
 * Touch Bar, disables the coordinator for the rest of the session and reports the cause once.
 */
public final class BarCoordinator<P> {
    /** The native bar. All calls are asynchronous; failures arrive through the listener given to the factory. */
    public interface Port {
        void show(BarContent content, Runnable onHide);
        void update(BarContent content);
        void remove();
    }

    private final Function<P, @Nullable TaskSnapshot> source;
    private final Function<Consumer<Throwable>, Port> factory;
    private final BooleanSupplier showPercent;
    private final LongSupplier delayMs;
    private final LongSupplier clockMs;
    private final Consumer<Throwable> onDisabled;

    private final Map<P, BarState> states = new HashMap<>();
    private Port port;
    private P barProject;
    private BarContent lastContent;
    private boolean disabled;

    public BarCoordinator(Function<P, @Nullable TaskSnapshot> source, Function<Consumer<Throwable>, Port> factory,
                          BooleanSupplier showPercent, LongSupplier delayMs, LongSupplier clockMs, Consumer<Throwable> onDisabled) {
        this.source = source; this.factory = factory; this.showPercent = showPercent; this.delayMs = delayMs;
        this.clockMs = clockMs; this.onDisabled = onDisabled;
    }

    public synchronized boolean isDisabled() { return disabled; }

    public synchronized void tick(Collection<P> open, @Nullable P focused) {
        if (disabled) return;
        long now = clockMs.getAsLong();
        states.keySet().retainAll(open);
        Map<P, BarState.Output> outputs = new HashMap<>();
        for (P p : open) {
            TaskSnapshot s;
            try { s = source.apply(p); }
            catch (LinkageError e) { disable(e); return; } // the platform class behind the source changed: give up cleanly
            catch (RuntimeException e) { s = null; }       // transient (e.g. list mutated): treat as "no task" this tick
            outputs.put(p, states.computeIfAbsent(p, k -> new BarState(delayMs)).update(now, s));
        }
        // Focus in another app (focused == null): the bar stays with its window until its task ends.
        P target = focused != null && open.contains(focused) ? focused : (barProject != null && open.contains(barProject) ? barProject : null);
        BarState.Output out = target == null ? null : outputs.get(target);
        if (out == null || !out.show()) { removeBar(); return; }

        Double f = out.fraction();
        BarContent content = new BarContent(f, f != null && showPercent.getAsBoolean() ? Percent.format(f) : null, out.name());
        if (port == null || !target.equals(barProject)) {
            removeBar();
            Port[] created = new Port[1];
            created[0] = factory.apply(t -> failed(created[0], t));
            port = created[0]; barProject = target;
            P shownFor = target;
            port.show(content, () -> hidden(shownFor, created[0]));
        } else if (!Objects.equals(content, lastContent)) {
            port.update(content);
        }
        lastContent = content;
    }

    /** Hide button (AppKit main thread): hide this task and give the IDE's Touch Bar back immediately. */
    private synchronized void hidden(P project, Port from) {
        if (port != from) return; // a stale button: its bar is already gone
        BarState s = states.get(project);
        if (s != null) s.hide();
        removeBar();
    }

    /**
     * A native failure (it has already restored the IDE's Touch Bar): stop for the rest of the session. The failed
     * port is always removed too (idempotent), so whatever it runs, such as a frame loop, stops with it.
     */
    private synchronized void failed(Port from, Throwable cause) {
        if (from != null) from.remove();
        if (port == from) { port = null; barProject = null; lastContent = null; }
        disable(cause);
    }

    private void disable(Throwable cause) {
        if (disabled) return;
        disabled = true;
        removeBar();      // restores the IDE's Touch Bar if one of ours is still up
        states.clear();
        onDisabled.accept(cause);
    }

    private void removeBar() {
        if (port == null) return;
        port.remove();
        port = null; barProject = null; lastContent = null;
    }
}

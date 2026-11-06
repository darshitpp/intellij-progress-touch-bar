package dev.darshit.touchbar.mac;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import dev.darshit.touchbar.core.BarContent;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static dev.darshit.touchbar.mac.Objc.*;

/**
 * Our own full Touch Bar (task name, progress image, optional percentage, Hide button) attached to the focused view.
 * The progress item is a layer-backed view whose contents is the latest frame as a CGImage (BarRenderer paints it).
 * All calls are asynchronous (main-thread dispatch); failures go to the failure listener, never thrown.
 * <p>
 * Held across time, all retained and released in {@link #restoreAndRelease}: the bar, the attached view, the view's
 * previous bar, and the progress/label/name views we keep updating. The caller (BarCoordinator) keeps at most one bar alive.
 */
public final class TouchBarBar {
    private static final Logger LOG = Logger.getInstance(TouchBarBar.class);

    // The app's share of the Touch Bar is ~700 pt next to the system buttons: name + bar + percentage + Hide must fit.
    private static final double BAR_W = 400, BAR_H = 30, NAME_MAX_W = 150;
    private static final float PRIORITY_LOW = -1000f, PRIORITY_HIGH = 1000f; // NSTouchBarItemPriorityLow/High
    private static final long NS_LINE_BREAK_TRUNCATING_TAIL = 4;

    private final Consumer<Throwable> failure;
    private final String hideTitle;
    private final AtomicReference<BarContent> pending = new AtomicReference<>();
    private final AtomicReference<Frame> pendingFrame = new AtomicReference<>();

    /** {@code px == null}: picture unchanged, only re-check the attachment. */
    private record Frame(int @Nullable [] px, int w, int h) {}

    // main-thread state
    private MemorySegment bar, view, previous, progressView, labelView, nameView;
    private BarContent content;

    /** {@code hideTitle}: the localized Hide button title (mac/ has no IDE resource access by design). */
    public TouchBarBar(Consumer<Throwable> failure, String hideTitle) { this.failure = failure; this.hideTitle = hideTitle; }

    /** True on macOS when AppKit's NSTouchBar class exists. Cannot detect Touch Bar hardware; without it the bar is simply never drawn. */
    public static boolean isSupported() {
        try { return SystemInfo.isMac && !cls("NSTouchBar").equals(NIL); }
        catch (Throwable t) { LOG.info("Touch Bar unavailable: " + t); return false; }
    }

    /** Pays the one-time FFM setup cost now (off the EDT) instead of when the first bar appears. */
    public static void warmUp() { onMain(() -> {}, t -> LOG.info("warm-up failed: " + t)); }

    public void show(BarContent initial, Runnable onHide) {
        onMain(() -> {
            if (bar != null) return;
            content = initial;
            MemorySegment target = focusedView();

            progressView = p(p(cls("NSView"), "alloc"), "initWithFrame:", rect(0, 0, BAR_W, BAR_H));
            v(progressView, "setWantsLayer:", true);
            v(p(progressView, "layer"), "setContentsGravity:", nsString("resize")); // kCAGravityResize
            v(progressView, "setTranslatesAutoresizingMaskIntoConstraints:", false);
            v(p(p(progressView, "widthAnchor"), "constraintEqualToConstant:", BAR_W), "setActive:", true);
            v(p(p(progressView, "heightAnchor"), "constraintEqualToConstant:", BAR_H), "setActive:", true);
            labelView = p(p(cls("NSTextField"), "labelWithString:", nsString("")), "retain");
            nameView = p(p(cls("NSTextField"), "labelWithString:", nsString("")), "retain");
            v(nameView, "setLineBreakMode:", NS_LINE_BREAK_TRUNCATING_TAIL);
            v(nameView, "setTranslatesAutoresizingMaskIntoConstraints:", false);
            v(p(p(nameView, "widthAnchor"), "constraintLessThanOrEqualToConstant:", NAME_MAX_W), "setActive:", true);

            MemorySegment hide = p(cls("NSButton"), "buttonWithTitle:target:action:", nsString(hideTitle), hideTarget(onHide), sel("hide:"));
            MemorySegment[] items = {
                    item("ptb.name", nameView), item("ptb.progress", progressView), item("ptb.label", labelView), item("ptb.hide", hide)};
            v(items[0], "setVisibilityPriority:", PRIORITY_LOW);  // if space is still short, macOS drops the name first
            v(items[3], "setVisibilityPriority:", PRIORITY_HIGH); // and never the Hide button
            bar = p(p(cls("NSTouchBar"), "alloc"), "init");
            MemorySegment set = p(cls("NSMutableSet"), "set");
            for (MemorySegment it : items) v(set, "addObject:", it);
            v(bar, "setTemplateItems:", set);
            for (MemorySegment it : items) release(it); // the bar's copy of the set holds them now
            applyIdentifiers();
            applyContent();
            if (!target.equals(NIL)) attach(target); // else the next frame attaches once the IDE has a key window
        }, this::fail);
    }

    /** Coalesced: only the latest pending value is applied. */
    public void update(BarContent next) {
        if (pending.getAndSet(next) == null) drainLatest(pending, this::applyUpdate);
    }

    /**
     * Coalesced: only the latest frame is applied. A null frame (picture unchanged) never replaces a pending picture;
     * it only re-checks that our bar is still attached to the focused view. Ignored before show and after remove.
     */
    public void frame(int @Nullable [] px, int w, int h) {
        Frame f = new Frame(px, w, h);
        boolean schedule = px == null ? pendingFrame.compareAndSet(null, f) : pendingFrame.getAndSet(f) == null;
        if (schedule) drainLatest(pendingFrame, this::applyFrame);
    }

    /** Restores the view's previous Touch Bar (on the retained view, so it is always safe) and releases everything. */
    public void remove() {
        onMain(this::restoreAndRelease, this::fail);
    }

    /** Gives the IDE's Touch Bar back, releases this bar, then reports the failure. Safe to call from any thread. */
    public void fail(Throwable t) {
        LOG.warn("touch bar failure", t);
        onMain(this::restoreAndRelease, x -> LOG.warn("restore after failure failed", x));
        failure.accept(t);
    }

    /** Queues one main-thread drain of {@code slot}; applies its latest value if our bar is still up. */
    private <T> void drainLatest(AtomicReference<T> slot, Consumer<T> apply) {
        onMain(() -> {
            T value = slot.getAndSet(null);
            if (value != null && bar != null) apply.accept(value);
        }, this::fail);
    }

    // ---- main thread only below ----

    private void applyUpdate(BarContent c) {
        boolean shapeChanged = (c.label() == null) != (content.label() == null) || (c.name() == null) != (content.name() == null);
        content = c;
        if (shapeChanged) applyIdentifiers();
        applyContent();
    }

    private void applyFrame(Frame f) {
        ensureAttached();
        if (f.px() != null) {
            MemorySegment image = CoreGraphics.imageFromArgbPre(f.px(), f.w(), f.h());
            try { v(p(progressView, "layer"), "setContents:", image); } // the layer retains it
            finally { CoreGraphics.release(image); }
        }
    }

    /** Our bar on the current focused view: the view can be replaced, and the IDE can take the Touch Bar back. */
    private void ensureAttached() {
        MemorySegment now = focusedView();
        if (now.equals(NIL)) return; // IDE is not the active app: nothing to do, not a failure
        if (view == null) { attach(now); }
        else if (!now.equals(view)) { // window/view replaced: put ours on the new focused view
            LOG.info("Touch Bar: focused view changed, re-attaching");
            restoreOn(view, previous); release(previous); previous = NIL;
            release(view); view = NIL;
            attach(now);
        } else if (!p(view, "touchBar").equals(bar)) { // the IDE took the bar back
            LOG.info("Touch Bar: bar was replaced by the IDE, re-applying");
            v(view, "setTouchBar:", bar);
        }
    }

    private void attach(MemorySegment target) {
        view = p(target, "retain");
        MemorySegment old = p(view, "touchBar");
        previous = old.equals(NIL) ? NIL : p(old, "retain");
        v(view, "setTouchBar:", bar);
    }

    private void applyContent() {
        v(labelView, "setStringValue:", nsString(content.label() == null ? "" : content.label()));
        v(nameView, "setStringValue:", nsString(content.name() == null ? "" : content.name()));
    }

    private void applyIdentifiers() {
        MemorySegment ids = p(cls("NSMutableArray"), "array");
        if (content.name() != null) v(ids, "addObject:", nsString("ptb.name"));
        v(ids, "addObject:", nsString("ptb.progress"));
        if (content.label() != null) v(ids, "addObject:", nsString("ptb.label"));
        v(ids, "addObject:", nsString("ptb.hide"));
        v(bar, "setDefaultItemIdentifiers:", ids);
    }

    /** Returns a +1 item; the caller releases it once the bar holds it. */
    private static MemorySegment item(String id, MemorySegment viewObj) {
        MemorySegment it = p(p(cls("NSCustomTouchBarItem"), "alloc"), "initWithIdentifier:", nsString(id));
        v(it, "setView:", viewObj);
        return it;
    }

    private static MemorySegment focusedView() {
        MemorySegment app = p(cls("NSApplication"), "sharedApplication");
        MemorySegment win = p(app, "keyWindow");
        if (win.equals(NIL)) win = p(app, "mainWindow");
        if (win.equals(NIL)) return NIL;
        MemorySegment r = p(win, "firstResponder");
        return r.equals(NIL) ? win : r;
    }

    private static void restoreOn(MemorySegment target, MemorySegment old) {
        if (target != null && !target.equals(NIL)) v(target, "setTouchBar:", old == null ? NIL : old);
    }

    private static void release(MemorySegment o) { if (o != null && !o.equals(NIL)) v(o, "release"); }

    private void restoreAndRelease() {
        if (bar == null) return;
        try { restoreOn(view, previous); }
        finally {
            release(previous); release(view); release(bar); release(progressView); release(labelView); release(nameView);
            bar = view = previous = progressView = labelView = nameView = null;
        }
    }
}

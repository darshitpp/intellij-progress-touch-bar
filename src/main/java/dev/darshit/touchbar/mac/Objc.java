package dev.darshit.touchbar.mac;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Minimal Objective-C runtime bridge over FFM. Only touch AppKit objects from the main thread (see {@link #onMain}). */
final class Objc {
    private static final Logger LOG = Logger.getInstance(Objc.class);
    static final MemorySegment NIL = MemorySegment.NULL;

    private static final Linker L = Linker.nativeLinker();
    private static final Arena A = Arena.global(); // only for things that live as long as the plugin classes do
    private static final SymbolLookup OBJC = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", A);
    private static final MemorySegment MSG_SEND = OBJC.find("objc_msgSend").orElseThrow();
    private static final ConcurrentHashMap<FunctionDescriptor, MethodHandle> HANDLES = new ConcurrentHashMap<>();

    private static final MethodHandle GET_CLASS = fn("objc_getClass", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SEL = fn("sel_registerName", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle ALLOC_CLASS = fn("objc_allocateClassPair",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle REGISTER_CLASS = fn("objc_registerClassPair", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ADD_METHOD = fn("class_addMethod",
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static MethodHandle fn(String name, FunctionDescriptor fd) {
        return L.downcallHandle(OBJC.find(name).orElseThrow(), fd);
    }

    // classes and selectors are interned by the runtime, so cache them (no per-call native allocation)
    private static final ConcurrentHashMap<String, MemorySegment> CLASSES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, MemorySegment> SELECTORS = new ConcurrentHashMap<>();

    static MemorySegment cls(String name) {
        return CLASSES.computeIfAbsent(name, n -> {
            try (Arena a = Arena.ofConfined()) { return (MemorySegment) GET_CLASS.invoke(a.allocateFrom(n)); }
            catch (Throwable t) { throw new RuntimeException(t); }
        });
    }

    static MemorySegment sel(String name) {
        return SELECTORS.computeIfAbsent(name, n -> {
            try (Arena a = Arena.ofConfined()) { return (MemorySegment) SEL.invoke(a.allocateFrom(n)); }
            catch (Throwable t) { throw new RuntimeException(t); }
        });
    }

    private static Object call(MemoryLayout ret, MemorySegment recv, String selector, Object... args) {
        try {
            MemoryLayout[] ls = new MemoryLayout[args.length + 2];
            Object[] vs = new Object[args.length + 2];
            ls[0] = ValueLayout.ADDRESS; vs[0] = recv;
            ls[1] = ValueLayout.ADDRESS; vs[1] = sel(selector);
            for (int i = 0; i < args.length; i++) {
                ls[i + 2] = ArgLayouts.layoutOf(args[i]);
                vs[i + 2] = ArgLayouts.valueOf(args[i]);
            }
            FunctionDescriptor fd = ret == null ? FunctionDescriptor.ofVoid(ls) : FunctionDescriptor.of(ret, ls);
            return HANDLES.computeIfAbsent(fd, k -> L.downcallHandle(MSG_SEND, k)).invokeWithArguments(vs);
        } catch (Throwable t) { throw new RuntimeException(selector, t); }
    }

    /** returns id */
    static MemorySegment p(MemorySegment recv, String sel, Object... args) { return (MemorySegment) call(ValueLayout.ADDRESS, recv, sel, args); }
    static void v(MemorySegment recv, String sel, Object... args) { call(null, recv, sel, args); }

    /** The runtime copies the bytes, so the temporary native string can be freed right away. */
    static MemorySegment nsString(String s) {
        try (Arena a = Arena.ofConfined()) { return p(cls("NSString"), "stringWithUTF8String:", a.allocateFrom(s)); }
    }

    static ArgLayouts.Rect rect(double x, double y, double w, double h) { return ArgLayouts.rect(x, y, w, h); }

    // ---- main-thread dispatch: ONE native callback for the life of the plugin classes, work travels through a queue ----
    private static final MainQueue MAIN = new MainQueue(t -> LOG.warn("touch bar error handler failed", t));
    private static final MemorySegment MAIN_Q = L.defaultLookup().find("_dispatch_main_q").orElseThrow();
    private static final MethodHandle DISPATCH_ASYNC = L.downcallHandle(L.defaultLookup().find("dispatch_async_f").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MemorySegment DRAIN = drainStub();

    private static MemorySegment drainStub() {
        try {
            MethodHandle h = MethodHandles.lookup().findStatic(Objc.class, "drain", MethodType.methodType(void.class, MemorySegment.class));
            return L.upcallStub(h, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), A);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    @SuppressWarnings("unused") // called from native; MainQueue.runOne never throws
    private static void drain(MemorySegment ctx) { MAIN.runOne(); }

    /** Runs on the AppKit main thread, in call order. Never throws; failures go to {@code onError}. */
    static void onMain(Runnable r, Consumer<Throwable> onError) {
        MAIN.enqueue(r, onError);
        try { DISPATCH_ASYNC.invoke(MAIN_Q, NIL, DRAIN); } catch (Throwable t) { onError.accept(t); }
    }

    // ---- hide-button target: one dynamically registered ObjC class per plugin load ----
    // Never freed: NSButton keeps its target unretained, so a late tap on a button that is still on screen would hit
    // freed code. The class and its stub are a few hundred bytes.
    private static volatile Runnable hideAction = () -> {};
    private static MemorySegment hideTarget;

    static synchronized MemorySegment hideTarget(Runnable onHide) {
        hideAction = onHide;
        if (hideTarget != null) return hideTarget;
        try {
            String name = "PTBHideTarget" + System.nanoTime(); // unique per load: objc_allocateClassPair fails on duplicates
            MemorySegment c = (MemorySegment) ALLOC_CLASS.invoke(cls("NSObject"), A.allocateFrom(name), 0L);
            MethodHandle imp = MethodHandles.lookup().findStatic(Objc.class, "hideCalled",
                    MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment stub = L.upcallStub(imp, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS), A);
            boolean ok = (Boolean) ADD_METHOD.invoke(c, sel("hide:"), stub, A.allocateFrom("v@:@"));
            if (!ok) throw new IllegalStateException("class_addMethod failed");
            REGISTER_CLASS.invoke(c);
            hideTarget = p(p(c, "alloc"), "init");
            return hideTarget;
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @SuppressWarnings("unused") // called from native
    private static void hideCalled(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        try { hideAction.run(); } catch (Throwable t) { LOG.warn("hide action failed", t); }
    }
}

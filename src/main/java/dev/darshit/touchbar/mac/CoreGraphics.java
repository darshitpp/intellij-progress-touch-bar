package dev.darshit.touchbar.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** Turns a frame of Java pixels into a CGImage. Main thread or not: these CoreGraphics calls are thread-safe. */
final class CoreGraphics {
    private CoreGraphics() {}

    private static final Linker L = Linker.nativeLinker();
    private static final SymbolLookup CG = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
    private static final SymbolLookup CF = SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());

    // critical: the Java int[] is passed straight through, with no temporary native copy (CFDataCreate copies it)
    private static final MethodHandle CF_DATA_CREATE = L.downcallHandle(CF.find("CFDataCreate").orElseThrow(),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG), Linker.Option.critical(true));
    private static final MethodHandle CF_RELEASE = L.downcallHandle(CF.find("CFRelease").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle PROVIDER = L.downcallHandle(CG.find("CGDataProviderCreateWithCFData").orElseThrow(),
            FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle IMAGE_CREATE = L.downcallHandle(CG.find("CGImageCreate").orElseThrow(),
            FunctionDescriptor.of(ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT,
                    ADDRESS, ADDRESS, JAVA_BOOLEAN, JAVA_INT));
    private static final MethodHandle COLOR_SPACE = L.downcallHandle(CG.find("CGColorSpaceCreateWithName").orElseThrow(),
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    /** Java's TYPE_INT_ARGB_PRE ints are B,G,R,A bytes in memory: kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little. */
    private static final int BITMAP_INFO = 2 | (2 << 12);

    /** sRGB, created once and kept for good (one object). */
    private static final MemorySegment SRGB = srgb();

    private static MemorySegment srgb() {
        try {
            MemorySegment name = CG.find("kCGColorSpaceSRGB").orElseThrow().reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
            return (MemorySegment) COLOR_SPACE.invoke(name);
        } catch (Throwable t) { throw new ExceptionInInitializerError(t); }
    }

    /** Returns a +1 CGImageRef; the caller releases it with {@link #release}. */
    static MemorySegment imageFromArgbPre(int[] px, int w, int h) {
        if (px.length != w * h) throw new IllegalArgumentException("frame is " + px.length + " pixels, expected " + w * h);
        MemorySegment data = Objc.NIL, provider = Objc.NIL;
        try {
            data = (MemorySegment) CF_DATA_CREATE.invoke(Objc.NIL, MemorySegment.ofArray(px), (long) px.length * 4);
            provider = (MemorySegment) PROVIDER.invoke(data);
            MemorySegment image = (MemorySegment) IMAGE_CREATE.invoke((long) w, (long) h, 8L, 32L, (long) w * 4, SRGB, BITMAP_INFO,
                    provider, Objc.NIL, false, 0);
            if (image.equals(Objc.NIL)) throw new IllegalStateException("CGImageCreate failed");
            return image;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable t) { throw new RuntimeException(t); }
        finally { release(provider); release(data); }
    }

    static void release(MemorySegment cf) {
        if (cf == null || cf.equals(Objc.NIL)) return;
        try { CF_RELEASE.invoke(cf); } catch (Throwable t) { throw new RuntimeException(t); }
    }
}

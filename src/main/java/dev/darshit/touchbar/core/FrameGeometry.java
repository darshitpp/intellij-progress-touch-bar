package dev.darshit.touchbar.core;

/**
 * How to fit a themed progress bar into the Touch Bar image: themes draw a fixed-height band, so the component is
 * sized to that height and the graphics scaled evenly until it fills the image height.
 */
public record FrameGeometry(double scale, int componentWidth, int componentHeight) {
    public static FrameGeometry of(int preferredHeight, int imageW, int imageH) {
        if (preferredHeight <= 0) return new FrameGeometry(2.0, imageW / 2, imageH / 2);
        double scale = imageH / (double) preferredHeight;
        return new FrameGeometry(scale, (int) Math.round(imageW / scale), preferredHeight);
    }
}

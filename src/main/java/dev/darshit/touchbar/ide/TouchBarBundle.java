package dev.darshit.touchbar.ide;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/** User-visible text, from {@code messages/ProgressTouchBarBundle.properties}. */
public final class TouchBarBundle extends DynamicBundle {
    @NonNls static final String BUNDLE = "messages.ProgressTouchBarBundle";
    private static final TouchBarBundle INSTANCE = new TouchBarBundle();

    private TouchBarBundle() { super(TouchBarBundle.class, BUNDLE); }

    public static @Nls @NotNull String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}

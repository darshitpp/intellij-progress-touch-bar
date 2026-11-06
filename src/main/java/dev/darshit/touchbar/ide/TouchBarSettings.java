package dev.darshit.touchbar.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import dev.darshit.touchbar.core.BarRenderer;
import dev.darshit.touchbar.core.BarState;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
// UI category: takes part in Backup and Sync (roaming stays at its default, enabled)
@State(name = "ProgressTouchBarSettings", storages = @Storage("progressTouchBar.xml"), category = SettingsCategory.UI)
public final class TouchBarSettings implements PersistentStateComponent<TouchBarSettings.State> {
    public static final class State {
        public boolean showPercent;
        public int delayMs = (int) BarState.DEFAULT_DELAY_MS;
        public int fps = BarRenderer.DEFAULT_FPS;
    }

    public static final int MIN_DELAY_MS = 0, MAX_DELAY_MS = 10_000;

    private State state = new State();

    public static TouchBarSettings getInstance() {
        return ApplicationManager.getApplication().getService(TouchBarSettings.class);
    }

    public boolean isShowPercent() { return state.showPercent; }
    public void setShowPercent(boolean v) { state.showPercent = v; }
    /** Clamped, so a hand-edited settings file can't produce a negative or absurd delay. */
    public int getDelayMs() { return Math.clamp(state.delayMs, MIN_DELAY_MS, MAX_DELAY_MS); }
    public void setDelayMs(int v) { state.delayMs = Math.clamp(v, MIN_DELAY_MS, MAX_DELAY_MS); }
    public int getFps() { return Math.clamp(state.fps, BarRenderer.MIN_FPS, BarRenderer.MAX_FPS); }
    public void setFps(int v) { state.fps = Math.clamp(v, BarRenderer.MIN_FPS, BarRenderer.MAX_FPS); }

    @Override public @NotNull State getState() { return state; }
    @Override public void loadState(@NotNull State s) { state = s; }
}

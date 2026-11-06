package dev.darshit.touchbar.ide;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.dsl.builder.BuilderKt;
import com.intellij.ui.dsl.builder.HyperlinkEventAction;
import com.intellij.ui.dsl.builder.Row;
import com.intellij.ui.dsl.builder.UtilsKt;
import dev.darshit.touchbar.core.BarRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * Built with the platform's Kotlin UI DSL (called from Java): short labels, explanations as comments that word-wrap
 * to the window width, so the page never needs horizontal scrolling.
 */
public final class TouchBarConfigurable implements Configurable {
    private JBCheckBox percent;
    private JBIntSpinner delay;
    private JBIntSpinner fps;

    @Override public @Nls String getDisplayName() { return TouchBarBundle.message("settings.display.name"); }

    @Override public @Nullable JComponent createComponent() {
        TouchBarSettings s = TouchBarSettings.getInstance();
        percent = new JBCheckBox(TouchBarBundle.message("settings.show.percent"));
        delay = new JBIntSpinner(s.getDelayMs(), TouchBarSettings.MIN_DELAY_MS, TouchBarSettings.MAX_DELAY_MS, 100);
        fps = new JBIntSpinner(s.getFps(), BarRenderer.MIN_FPS, BarRenderer.MAX_FPS, 5);
        int ideFps = 1000 / BarRenderer.frameIntervalMs(UIManager.get("ProgressBar.repaintInterval"));

        return BuilderKt.panel(p -> {
            p.row((JLabel) null, r -> { r.cell(percent); return Unit.INSTANCE; }); // no label column: the checkbox starts at the left edge
            comment(p.row(TouchBarBundle.message("settings.delay.label"), r -> { r.cell(delay); return Unit.INSTANCE; }),
                    TouchBarBundle.message("settings.delay.comment"));
            comment(p.row(TouchBarBundle.message("settings.fps.label"), r -> { r.cell(fps); return Unit.INSTANCE; }),
                    TouchBarBundle.message("settings.fps.comment", ideFps));
            return Unit.INSTANCE;
        });
    }

    private static void comment(Row row, @Nls String text) {
        row.rowComment(text, UtilsKt.MAX_LINE_LENGTH_WORD_WRAP, HyperlinkEventAction.HTML_HYPERLINK_INSTANCE);
    }

    @Override public boolean isModified() {
        TouchBarSettings s = TouchBarSettings.getInstance();
        return percent.isSelected() != s.isShowPercent() || delay.getNumber() != s.getDelayMs() || fps.getNumber() != s.getFps();
    }

    @Override public void apply() {
        TouchBarSettings s = TouchBarSettings.getInstance();
        s.setShowPercent(percent.isSelected());
        s.setDelayMs(delay.getNumber());
        s.setFps(fps.getNumber());
    }

    @Override public void reset() {
        TouchBarSettings s = TouchBarSettings.getInstance();
        percent.setSelected(s.isShowPercent());
        delay.setNumber(s.getDelayMs());
        fps.setNumber(s.getFps());
    }

    @Override public void disposeUIResources() { percent = null; delay = null; fps = null; }
}

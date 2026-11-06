package dev.darshit.touchbar.ide;

import com.intellij.openapi.progress.ProgressModel;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import dev.darshit.touchbar.core.IdentityIds;
import dev.darshit.touchbar.core.TaskNames;
import dev.darshit.touchbar.core.TaskSnapshot;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import kotlin.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the status bar's own list of running background tasks (platform-impl {@code StatusBarEx}, internal): the
 * same tasks and fractions the status bar shows, including coroutine-based ones that ProgressManagerListener misses.
 */
public final class StatusBarTaskSource implements TaskSource {
    private final Map<Project, IdentityIds<ProgressModel>> ids = new WeakHashMap<>();

    @Override
    public synchronized @Nullable TaskSnapshot current(Project project) {
        StatusBar bar = WindowManager.getInstance().getStatusBar(project);
        if (!(bar instanceof StatusBarEx ex)) return null;
        List<Pair<TaskInfo, ProgressModel>> running = ex.getBackgroundProcessModels();
        IdentityIds<ProgressModel> projectIds = ids.computeIfAbsent(project, p -> new IdentityIds<>());
        projectIds.retainOnly(running.stream().map(Pair::getSecond).toList());
        for (Pair<TaskInfo, ProgressModel> p : running) { // oldest first
            ProgressModel m = p.getSecond();
            if (!m.isRunning() || !m.getVisibleInStatusBar()) continue;
            return new TaskSnapshot(projectIds.idFor(m), m.isIndeterminate() ? null : m.getFraction(), nameOf(m, p.getFirst()));
        }
        return null;
    }

    /** The title the status bar shows; falls back to the task info's title, then to the current text. Bounded: it is untrusted. */
    private static @Nullable String nameOf(ProgressModel m, TaskInfo info) {
        return TaskNames.pick(m.getTitle(), info.getTitle(), m.getText());
    }
}

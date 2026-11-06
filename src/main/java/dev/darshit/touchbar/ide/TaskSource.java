package dev.darshit.touchbar.ide;

import com.intellij.openapi.project.Project;
import dev.darshit.touchbar.core.TaskSnapshot;
import org.jetbrains.annotations.Nullable;

/** The one place that knows how to observe running IDE tasks; swap the implementation if the platform API changes. */
public interface TaskSource {
    /** The task to show for this project, or null. {@code id} is stable for one task run. */
    @Nullable TaskSnapshot current(Project project);
}

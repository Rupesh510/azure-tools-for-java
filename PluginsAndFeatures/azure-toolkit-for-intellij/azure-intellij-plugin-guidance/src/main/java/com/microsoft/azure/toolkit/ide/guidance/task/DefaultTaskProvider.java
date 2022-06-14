package com.microsoft.azure.toolkit.ide.guidance.task;

import com.microsoft.azure.toolkit.ide.guidance.Context;
import com.microsoft.azure.toolkit.ide.guidance.GuidanceTask;
import com.microsoft.azure.toolkit.ide.guidance.config.TaskConfig;

import javax.annotation.Nonnull;

public class DefaultTaskProvider implements GuidanceTaskProvider {
    @Override
    public GuidanceTask createTask(@Nonnull TaskConfig config, @Nonnull Context context) {
        final TaskContext taskContext = new TaskContext(config, context);
        switch (config.getName()) {
            case "tasks.clone":
                return new GitCloneTask(taskContext);
            case "task.signin":
                return new SignInTask(taskContext);
            case "task.select_subscription":
                return new SelectSubscriptionTask(taskContext);
            case "task.resource.open_in_portal":
                return new OpenResourceInAzureTask(taskContext);
            case "task.resource.clean_up":
                return new CleanUpResourceTask(taskContext);
            default:
                return new EmptyTask();
        }
    }
}

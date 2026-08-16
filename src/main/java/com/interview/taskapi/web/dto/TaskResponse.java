package com.interview.taskapi.web.dto;

import com.interview.taskapi.domain.Task;
import com.interview.taskapi.domain.TaskStatus;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        String projectName
) {
    /**
     * Mapping lives here (or in a dedicated mapper / MapStruct on a real team).
     * NOTE: touching task.getProject().getName() requires an open persistence
     * context or a fetch join - this is exactly where LazyInitializationException
     * bites people. We map inside a @Transactional service method.
     */
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getProject() != null ? task.getProject().getName() : null
        );
    }
}

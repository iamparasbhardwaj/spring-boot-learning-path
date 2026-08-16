package com.interview.taskapi.web.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * INTERVIEW: "How does @Valid work?"
 * @Valid on a @RequestBody parameter triggers the Bean Validation (Jakarta) provider,
 * Hibernate Validator. On failure Spring throws MethodArgumentNotValidException,
 * which we translate in ApiExceptionHandler. @Validated (Spring's own) additionally
 * enables validation groups and method-level validation on beans.
 */
public record CreateTaskRequest(

        @NotBlank(message = "title must not be blank")
        @Size(max = 120)
        String title,

        @Size(max = 2000)
        String description,

        @FutureOrPresent(message = "dueDate cannot be in the past")
        LocalDate dueDate,

        @NotNull
        Long projectId
) { }

package com.interview.taskapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * INTERVIEW: "@Value vs @ConfigurationProperties?"
 *   @Value("${app.x}")        -> single value, SpEL-capable, no relaxed binding,
 *                                no validation, scattered across the codebase.
 *   @ConfigurationProperties  -> type-safe, grouped, relaxed binding
 *                                (app.max-tasks-per-project == APP_MAXTASKSPERPROJECT),
 *                                validatable, shows up in /actuator/configprops,
 *                                and IDE autocomplete in application.yml.
 * Records work as immutable constructor-bound properties.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        @Min(1) @Max(1000)
        int maxTasksPerProject,

        @Min(1) @Max(100)
        int defaultPageSize,

        @NotBlank
        String quotesBaseUrl
) { }

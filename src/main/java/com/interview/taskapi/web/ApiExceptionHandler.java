package com.interview.taskapi.web;

import com.interview.taskapi.exception.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * INTERVIEW: "How do you standardise error responses across an API?"
 * @RestControllerAdvice registers these handlers for every controller. Returning
 * ProblemDetail gives you RFC 7807 (application/problem+json) out of the box -
 * type, title, status, detail, instance - instead of a hand-rolled error shape.
 * Mentioning RFC 7807 by name lands well.
 *
 * Handler resolution: Spring picks the MOST SPECIFIC exception type, so a
 * handler for Exception acts as the catch-all without shadowing the others.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    ProblemDetail handleNotFound(TaskNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Task not found");
        pd.setType(URI.create("https://api.example.com/errors/task-not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** Thrown when @Valid fails on a @RequestBody argument. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        pd.setTitle("Invalid request");
        pd.setProperty("errors", fieldErrors);
        return pd;
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    ProblemDetail handleBadRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Catch-all. Never leak a stack trace to the client - log it, return a generic
     * message plus a correlation id the support team can grep for.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        pd.setProperty("correlationId", java.util.UUID.randomUUID().toString());
        return pd;
    }
}

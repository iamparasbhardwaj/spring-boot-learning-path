package com.interview.taskapi.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * INTERVIEW: "Why not return the entity straight from the controller?"
 *   - Leaks your schema and lazy proxies (LazyInitializationException at serialization time)
 *   - Couples your public API contract to your database
 *   - Publishes fields you never meant to expose
 * Hence the DTO records in web/dto.
 */
@Entity
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)   // never ORDINAL: reordering the enum corrupts existing rows
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    private LocalDate dueDate;

    /**
     * LAZY on purpose. @ManyToOne defaults to EAGER in JPA, which is a classic
     * gotcha worth naming out loud in an interview.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(updatable = false)
    private Instant createdAt = Instant.now();

    protected Task() { }

    public Task(String title, String description, LocalDate dueDate, Project project) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.project = project;
    }

    public void update(String title, String description, TaskStatus status, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public LocalDate getDueDate() { return dueDate; }
    public Project getProject() { return project; }
    public Instant getCreatedAt() { return createdAt; }
}

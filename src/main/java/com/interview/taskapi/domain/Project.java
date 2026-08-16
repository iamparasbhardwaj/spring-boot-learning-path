package com.interview.taskapi.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * INTERVIEW: "What is the N+1 select problem?"
     * Loading N projects then touching getTasks() on each fires 1 + N queries.
     * Fixes: JOIN FETCH in JPQL, @EntityGraph, or @BatchSize.
     * See ProjectRepository for both the broken and the fixed version.
     */
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Task> tasks = new ArrayList<>();

    protected Project() { }   // JPA needs a no-arg constructor

    public Project(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Task> getTasks() { return tasks; }
}

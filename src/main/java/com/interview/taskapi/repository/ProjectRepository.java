package com.interview.taskapi.repository;

import com.interview.taskapi.domain.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * THE N+1 DEMO.
     * Call findAll(), then loop and call p.getTasks().size().
     * Turn on logging.level.org.hibernate.SQL=DEBUG and count the queries: 1 + N.
     * Both methods below collapse it to 1. Run this yourself once - being able to
     * say "I watched the query log go from 5 statements to 1" beats reciting theory.
     */
    @EntityGraph(attributePaths = "tasks")
    List<Project> findAllWithTasksEntityGraph();

    @Query("select distinct p from Project p left join fetch p.tasks")
    List<Project> findAllWithTasksFetchJoin();
}

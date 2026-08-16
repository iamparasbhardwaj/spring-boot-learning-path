package com.interview.taskapi.repository;

import com.interview.taskapi.domain.Task;
import com.interview.taskapi.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * INTERVIEW: "You wrote no implementation. Who implements this interface?"
 * At startup, Spring Data JPA creates a dynamic proxy per repository interface,
 * backed by SimpleJpaRepository. Each method is resolved by a QueryLookupStrategy:
 * either parsed from the method name, or taken from @Query, or from a named query.
 * (In Spring Boot 4, Spring Data AOT can move much of this to build time, which is
 *  where a chunk of the faster-startup story comes from.)
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    // 1. DERIVED QUERY - parsed from the method name. Subject/predicate grammar.
    List<Task> findByStatus(TaskStatus status);

    Page<Task> findByStatusOrderByDueDateAsc(TaskStatus status, Pageable pageable);

    boolean existsByTitleIgnoreCase(String title);

    // 2. EXPLICIT JPQL - use when the derived name would become unreadable.
    @Query("select t from Task t where t.dueDate < :date and t.status <> 'DONE'")
    List<Task> findOverdue(@Param("date") LocalDate date);

    // 3. FETCH JOIN - the fix for N+1 when you know you will touch the association.
    @Query("select t from Task t join fetch t.project where t.status = :status")
    List<Task> findByStatusWithProject(@Param("status") TaskStatus status);

    // 4. PROJECTION - only select what you need. Cheap perf win, good talking point.
    @Query("select t.title from Task t where t.project.id = :projectId")
    List<String> findTitlesByProject(@Param("projectId") Long projectId);
}

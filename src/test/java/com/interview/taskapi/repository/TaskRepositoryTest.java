package com.interview.taskapi.repository;

import com.interview.taskapi.domain.Project;
import com.interview.taskapi.domain.Task;
import com.interview.taskapi.domain.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TIER 3 TEST: the persistence slice. @DataJpaTest configures an in-memory DB,
 * Hibernate, and the repositories - and wraps each test in a transaction that is
 * ROLLED BACK afterwards, so tests do not pollute each other.
 *
 * INTERVIEW: "Is H2 good enough for testing?"
 * It catches mapping errors but lies about dialect-specific SQL, so real teams use
 * Testcontainers to run the actual Postgres/MySQL image. Saying "H2 for speed,
 * Testcontainers for the queries that matter" is the answer they want.
 */
@DataJpaTest
class TaskRepositoryTest {

    @Autowired TaskRepository taskRepository;
    @Autowired TestEntityManager em;

    @Test
    void derivedQuery_findsByStatus() {
        Project project = em.persist(new Project("Prep"));
        em.persist(new Task("A", null, LocalDate.now().plusDays(1), project));
        em.flush();

        List<Task> found = taskRepository.findByStatus(TaskStatus.TODO);

        assertThat(found).hasSize(1).first().extracting(Task::getTitle).isEqualTo("A");
    }

    @Test
    void jpqlQuery_findsOverdue() {
        Project project = em.persist(new Project("Prep"));
        em.persist(new Task("Late", null, LocalDate.now().minusDays(3), project));
        em.persist(new Task("Future", null, LocalDate.now().plusDays(3), project));
        em.flush();
        em.clear();   // force reads to hit the DB rather than the first-level cache

        assertThat(taskRepository.findOverdue(LocalDate.now()))
                .extracting(Task::getTitle)
                .containsExactly("Late");
    }

    /**
     * EXERCISE: run this with logging.level.org.hibernate.SQL=DEBUG and count the
     * SELECT statements, then swap findByStatus for findByStatusWithProject and
     * count again. That before/after is your N+1 interview story.
     */
    @Test
    void fetchJoin_avoidsNPlusOne() {
        Project project = em.persist(new Project("Prep"));
        em.persist(new Task("A", null, LocalDate.now(), project));
        em.persist(new Task("B", null, LocalDate.now(), project));
        em.flush();
        em.clear();

        List<Task> tasks = taskRepository.findByStatusWithProject(TaskStatus.TODO);

        // Project is already initialised - no extra query, no LazyInitializationException.
        assertThat(tasks).allSatisfy(t -> assertThat(t.getProject().getName()).isEqualTo("Prep"));
    }
}

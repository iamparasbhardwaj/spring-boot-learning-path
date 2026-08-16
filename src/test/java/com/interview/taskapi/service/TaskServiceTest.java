package com.interview.taskapi.service;

import com.interview.taskapi.config.AppProperties;
import com.interview.taskapi.domain.Task;
import com.interview.taskapi.exception.TaskNotFoundException;
import com.interview.taskapi.repository.ProjectRepository;
import com.interview.taskapi.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TIER 1 TEST: no Spring at all. Milliseconds. This is the payoff of constructor
 * injection - you can build the object with `new`.
 *
 * INTERVIEW: "How do you decide between a unit test and @SpringBootTest?"
 * Business logic -> plain unit test. Wiring, serialisation, SQL, security ->
 * a slice test. Full @SpringBootTest only for a handful of end-to-end paths,
 * because it boots the whole context and dominates your build time.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;

    // Note: for a record we would normally construct it directly rather than mock it.
    AppProperties properties = new AppProperties(50, 20, "https://example.com");

    @Test
    void findById_returnsMappedResponse() {
        Task task = new Task("Learn DI", "constructor injection", LocalDate.now(), null);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskService service = new TaskService(taskRepository, projectRepository, properties);

        assertThat(service.findById(1L).title()).isEqualTo("Learn DI");
    }

    @Test
    void findById_throwsWhenMissing() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        TaskService service = new TaskService(taskRepository, projectRepository, properties);

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("99");
    }
}

package com.interview.taskapi.service;

import com.interview.taskapi.config.AppProperties;
import com.interview.taskapi.domain.Project;
import com.interview.taskapi.domain.Task;
import com.interview.taskapi.domain.TaskStatus;
import com.interview.taskapi.exception.TaskNotFoundException;
import com.interview.taskapi.repository.ProjectRepository;
import com.interview.taskapi.repository.TaskRepository;
import com.interview.taskapi.web.dto.CreateTaskRequest;
import com.interview.taskapi.web.dto.TaskResponse;
import com.interview.taskapi.web.dto.UpdateTaskRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)   // class-level default; writes override it below
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AppProperties properties;

    /**
     * INTERVIEW: "Constructor vs field vs setter injection?"
     * Constructor wins:
     *   - fields can be final -> immutable, thread-safe
     *   - impossible to construct an object in a half-wired state
     *   - trivially testable with `new TaskService(mockA, mockB, props)` - no Spring needed
     *   - a fat constructor screams "this class does too much" (field injection hides it)
     * Since Spring 4.3, @Autowired is optional when there is exactly one constructor.
     */
    public TaskService(TaskRepository taskRepository,
                       ProjectRepository projectRepository,
                       AppProperties properties) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.properties = properties;
    }

    public Page<TaskResponse> findAll(Pageable pageable) {
        return taskRepository.findAll(pageable).map(TaskResponse::from);
    }

    public TaskResponse findById(Long id) {
        return taskRepository.findById(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    public List<TaskResponse> findOverdue() {
        return taskRepository.findOverdue(LocalDate.now())
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * INTERVIEW: "How does @Transactional work under the hood?"
     * Spring AOP creates a proxy around this bean. The proxy opens a transaction
     * before the method and commits/rolls back after.
     *
     * THE CLASSIC TRAP: a self-invocation (this.someOtherTransactionalMethod())
     * bypasses the proxy entirely, so the annotation is silently ignored.
     * Same reason @Transactional on a private or final method does nothing.
     *
     * Propagation: REQUIRED (default) joins an existing tx; REQUIRES_NEW suspends it
     * and starts a fresh one - the right answer for "write an audit log even if the
     * business transaction rolls back".
     */
    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown project: " + request.projectId()));

        if (project.getTasks().size() >= properties.maxTasksPerProject()) {
            throw new IllegalStateException("Project is full (limit "
                    + properties.maxTasksPerProject() + ")");
        }

        Task task = new Task(request.title(), request.description(), request.dueDate(), project);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));

        // No explicit save() call needed: the entity is MANAGED inside the persistence
        // context, so Hibernate dirty-checks it and flushes an UPDATE at commit.
        // Being able to explain that is a genuine differentiator.
        task.update(request.title(), request.description(), request.status(), request.dueDate());
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }

    public List<TaskResponse> findByStatus(TaskStatus status) {
        return taskRepository.findByStatusWithProject(status)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }
}

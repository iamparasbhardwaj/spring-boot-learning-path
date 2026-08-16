package com.interview.taskapi.web;

import com.interview.taskapi.domain.TaskStatus;
import com.interview.taskapi.service.TaskService;
import com.interview.taskapi.web.dto.CreateTaskRequest;
import com.interview.taskapi.web.dto.TaskResponse;
import com.interview.taskapi.web.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * INTERVIEW: "@Controller vs @RestController?"
 * @RestController = @Controller + @ResponseBody on every method: return values are
 * written to the body by an HttpMessageConverter (Jackson for JSON) instead of being
 * resolved as a view name.
 *
 * INTERVIEW: "How does a request reach this method?"
 * DispatcherServlet (front controller) -> HandlerMapping picks the handler method
 * -> HandlerAdapter resolves arguments (@PathVariable, @RequestBody via converters,
 *    @RequestParam) -> your method runs -> return value handler writes the response
 * -> HandlerExceptionResolver if anything threw.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Page<TaskResponse> list(@PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {
        return taskService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @GetMapping("/overdue")
    public List<TaskResponse> overdue() {
        return taskService.findOverdue();
    }

    @GetMapping(params = "status")
    public List<TaskResponse> byStatus(@RequestParam TaskStatus status) {
        return taskService.findByStatus(status);
    }

    /**
     * INTERVIEW: "What status code does a successful POST return, and what header?"
     * 201 Created + a Location header pointing at the new resource. Returning
     * ResponseEntity (rather than a bare object) is how you control both.
     */
    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request,
                                               UriComponentsBuilder uriBuilder) {
        TaskResponse created = taskService.create(request);
        URI location = uriBuilder.path("/api/tasks/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    // PUT = full replacement and idempotent. PATCH = partial. Know the difference;
    // it gets asked as "is your API idempotent?"
    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();   // 204
    }
}

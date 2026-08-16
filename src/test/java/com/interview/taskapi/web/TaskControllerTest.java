package com.interview.taskapi.web;

import tools.jackson.databind.ObjectMapper;
import com.interview.taskapi.domain.TaskStatus;
import com.interview.taskapi.exception.TaskNotFoundException;
import com.interview.taskapi.service.TaskService;
import com.interview.taskapi.web.dto.CreateTaskRequest;
import com.interview.taskapi.web.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TIER 2 TEST: a SLICE. @WebMvcTest boots ONLY the web layer - controllers,
 * @ControllerAdvice, converters, filters. No JPA, no repositories, no DataSource.
 *
 * INTERVIEW: "@MockBean or @MockitoBean?"
 * @MockBean is deprecated (3.4) and gone in Spring Boot 4. Use @MockitoBean from
 * org.springframework.test.context.bean.override.mockito. Knowing this signals you
 * have actually touched a recent version rather than a 2022 tutorial.
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean TaskService taskService;   // replaces the real bean in this context

    @Test
    void get_returns200AndBody() throws Exception {
        when(taskService.findById(1L)).thenReturn(
                new TaskResponse(1L, "Learn slices", null, TaskStatus.TODO, LocalDate.now(), "Prep"));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Learn slices"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void get_returns404ProblemDetailWhenMissing() throws Exception {
        when(taskService.findById(99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Task not found"));   // from @RestControllerAdvice
    }

    @Test
    void post_returns400WhenTitleBlank() throws Exception {
        CreateTaskRequest invalid = new CreateTaskRequest("", null, LocalDate.now(), 1L);

        mockMvc.perform(post("/api/tasks")
                        .with(user("user").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void post_returns201WithLocationHeader() throws Exception {
        when(taskService.create(any())).thenReturn(
                new TaskResponse(7L, "New", null, TaskStatus.TODO, LocalDate.now(), "Prep"));

        CreateTaskRequest valid = new CreateTaskRequest("New", null, LocalDate.now(), 1L);

        mockMvc.perform(post("/api/tasks")
                        .with(user("user").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/tasks/7")));
    }
}

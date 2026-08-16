package com.interview.taskapi.exception;

public class TaskNotFoundException extends RuntimeException {

    /**
     * INTERVIEW: "Checked or unchecked for domain errors?"
     * Unchecked. Also note: @Transactional rolls back on RuntimeException/Error by
     * default, but NOT on checked exceptions unless you set rollbackFor.
     */
    public TaskNotFoundException(Long id) {
        super("Task not found: " + id);
    }
}

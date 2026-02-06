package com.example.taskmanager.service;

import com.example.taskmanager.dto.*;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.enums.TaskPriority;
import com.example.taskmanager.enums.TaskStatus;
import com.example.taskmanager.exception.BusinessException;
import com.example.taskmanager.exception.ResourceNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    private void validateHighPriorityWithDueDate(TaskPriority priority, LocalDate dueDate) {
        if (priority == TaskPriority.HIGH && dueDate == null) {
            throw new BusinessException("Due date is required for HIGH priority tasks");
        }
    }

    @Transactional
    public TaskResponse createTask(TaskRequest request) {
        // Regla B: Para priority HIGH, dueDate es obligatoria
        validateHighPriorityWithDueDate(request.getPriority(), request.getDueDate());

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .priority(request.getPriority())
                .dueDate(request.getDueDate())
                .build();

        Task saved = taskRepository.save(task);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getAllTasks(TaskStatus status, TaskPriority priority, String search) {
        List<Task> tasks = taskRepository.findWithFilters(status, priority, search);
        return tasks.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        Task task = findTaskById(id);
        return mapToResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskRequest request) {
        Task task = findTaskById(id);

        // Regla B: Para priority HIGH, dueDate es obligatoria
        validateHighPriorityWithDueDate(request.getPriority(), request.getDueDate());

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());

        Task updated = taskRepository.save(task);
        return mapToResponse(updated);
    }

    @Transactional
    public TaskResponse updateTaskStatus(Long id, StatusUpdateRequest request) {
        Task task = findTaskById(id);

        // Regla A: No se puede marcar DONE si la tarea está vencida
        if (request.getStatus() == TaskStatus.DONE &&
                task.getDueDate() != null &&
                task.getDueDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Cannot complete an overdue task");
        }

        task.setStatus(request.getStatus());
        Task updated = taskRepository.save(task);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = findTaskById(id);
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public TaskStatsResponse getStats() {
        List<Task> allTasks = taskRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);

        // Usando Java Streams como se requiere
        long total = allTasks.stream().count();

        Map<TaskStatus, Long> byStatus = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));

        Map<TaskPriority, Long> byPriority = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getPriority, Collectors.counting()));

        long overdue = allTasks.stream()
                .filter(task -> task.getDueDate() != null &&
                        task.getDueDate().isBefore(today) &&
                        task.getStatus() != TaskStatus.DONE)
                .count();

        List<TaskResponse> next7Days = allTasks.stream()
                .filter(task -> task.getDueDate() != null &&
                        !task.getDueDate().isBefore(today) &&
                        task.getDueDate().isBefore(sevenDaysLater))
                .sorted(Comparator.comparing(Task::getDueDate))
                .limit(5)
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return TaskStatsResponse.builder()
                .total(total)
                .byStatus(byStatus)
                .byPriority(byPriority)
                .overdue(overdue)
                .next7Days(next7Days)
                .build();
    }

    private Task findTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
    }

    private TaskResponse mapToResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
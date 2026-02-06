package com.example.taskmanager;

import com.example.taskmanager.dto.TaskRequest;
import com.example.taskmanager.dto.StatusUpdateRequest;
import com.example.taskmanager.enums.TaskPriority;
import com.example.taskmanager.enums.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // Test 1: Crear Task vÃ¡lida (201)
    @Test
    void createTask_ValidRequest_Returns201() throws Exception {
        TaskRequest request = new TaskRequest();
        request.setTitle("Tarea de prueba");
        request.setPriority(TaskPriority.MEDIUM);
        request.setDueDate(LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Tarea de prueba"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    // Test 2: Regla B - priority HIGH requiere dueDate (400)
    @Test
    void createTask_HighPriorityWithoutDueDate_Returns409() throws Exception {
        TaskRequest request = new TaskRequest();
        request.setTitle("Tarea importante");
        request.setPriority(TaskPriority.HIGH);
        // No dueDate - Esto deberÃ­a fallar

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Due date is required for HIGH priority tasks"));
    }

    // Test 3: Regla A - no permitir DONE en tarea vencida (409)
    @Test
    void updateTaskStatus_OverdueTaskToDone_Returns409() throws Exception {
        // Primero crear una tarea vencida
        TaskRequest createRequest = new TaskRequest();
        createRequest.setTitle("Tarea vencida");
        createRequest.setPriority(TaskPriority.LOW);
        createRequest.setDueDate(LocalDate.now().minusDays(5));

        ResultActions createResponse = mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)));

        String responseString = createResponse.andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(responseString).get("id").asLong();

        // Intentar cambiar a DONE
        StatusUpdateRequest statusRequest = new StatusUpdateRequest();
        statusRequest.setStatus(TaskStatus.DONE);

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot complete an overdue task"));
    }

    // Test 4: Stats endpoint (200)
    @Test
    void getStats_ReturnsStatistics() throws Exception {
        // Crear algunas tareas para tener datos
        for (int i = 1; i <= 6; i++) {
            TaskRequest request = new TaskRequest();
            request.setTitle("Tarea " + i);
            request.setPriority(i % 2 == 0 ? TaskPriority.HIGH : TaskPriority.LOW);
            request.setDueDate(LocalDate.now().plusDays(i));
            
            mockMvc.perform(post("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }

        mockMvc.perform(get("/api/tasks/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.byStatus").exists())
                .andExpect(jsonPath("$.byPriority").exists())
                .andExpect(jsonPath("$.overdue").exists())
                .andExpect(jsonPath("$.next7Days").exists());
    }
}
package com.gymcal.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "workout_plans")
public class WorkoutPlan {
    @Id private String id;
    private String userId;
    private String planName;
    private String goal;
    private String difficultyLevel;
    private List<String> healthConditions;
    private List<WorkoutDay> weeklyPlan;
    private String generalAdvice;
    private String safetyNotes;
    private int estimatedWeeklyCaloriesBurned;
    private LocalDateTime createdAt;
    private boolean isActive;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkoutDay {
        private String day;
        private String focus;
        private boolean isRestDay;
        private List<Exercise> exercises;
        private int estimatedDuration;
        private int estimatedCaloriesBurned;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Exercise {
        private String name;
        private String category;
        private String sets;
        private String reps;
        private String duration;
        private String rest;
        private String instructions;
        private String modification;
    }
}

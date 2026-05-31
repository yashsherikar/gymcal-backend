package com.gymcal.dto;

import com.gymcal.model.WorkoutPlan;
import lombok.*;
import java.util.List;

public class WorkoutDTOs {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GeneratePlanRequest {
        private List<String> healthConditions;
        private String fitnessLevel; // BEGINNER, INTERMEDIATE, ADVANCED
        private Integer workoutDaysPerWeek;
        private List<String> equipment;
        private String additionalNotes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkoutPlanResponse {
        private String id;
        private String planName;
        private String goal;
        private String difficultyLevel;
        private List<String> healthConditions;
        private List<WorkoutPlan.WorkoutDay> weeklyPlan;
        private String generalAdvice;
        private String safetyNotes;
        private int estimatedWeeklyCaloriesBurned;
        private String createdAt;
        private boolean isActive;
    }
}

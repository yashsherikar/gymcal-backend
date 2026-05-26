package com.gymcal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public class FoodDTOs {

    // Request to search food nutrition via AI
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodSearchRequest {
        @NotBlank(message = "Food name is required")
        private String foodName;

        @NotNull @Positive
        private Double quantityGrams;
    }

    // AI nutrition result
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionInfo {
        private String foodName;
        private double quantityGrams;
        private double calories;
        private double proteinGrams;
        private double carbsGrams;
        private double fatGrams;
        private double fiberGrams;
        private String aiAnalysis;
        private boolean success;
        private String errorMessage;
    }

    // Request to add food to daily log
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddFoodLogRequest {
        @NotBlank
        private String foodName;

        @NotNull @Positive
        private Double quantityGrams;

        @NotBlank
        private String mealType; // BREAKFAST, LUNCH, DINNER, SNACK

        private LocalDate logDate; // defaults to today if null

        // Pre-calculated nutrition (from search result)
        private Double calories;
        private Double proteinGrams;
        private Double carbsGrams;
        private Double fatGrams;
        private Double fiberGrams;
        private String aiAnalysis;
    }

    // Daily summary response
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySummary {
        private LocalDate date;

        // Targets
        private int targetCalories;
        private double targetProtein;
        private double targetCarbs;
        private double targetFat;

        // Consumed
        private double consumedCalories;
        private double consumedProtein;
        private double consumedCarbs;
        private double consumedFat;
        private double consumedFiber;

        // Remaining
        private double remainingCalories;
        private double remainingProtein;

        // Meal breakdown
        private List<MealGroup> meals;

        // Progress percentages
        private double calorieProgress;
        private double proteinProgress;
        private double carbProgress;
        private double fatProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealGroup {
        private String mealType;
        private List<FoodLogResponse> items;
        private double totalCalories;
        private double totalProtein;
    }

    // Food log entry response
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodLogResponse {
        private String id;
        private String foodName;
        private double quantityGrams;
        private String mealType;
        private double calories;
        private double proteinGrams;
        private double carbsGrams;
        private double fatGrams;
        private double fiberGrams;
        private String logDate;
        private String createdAt;
    }

    // Goal update request
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateGoalRequest {
        private String goal;
        private String activityLevel;
        private Double weightKg;
        private Double heightCm;
        private Integer age;
    }

    // User profile response
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileResponse {
        private String id;
        private String name;
        private String email;
        private double weightKg;
        private double heightCm;
        private int age;
        private String gender;
        private double bmi;
        private String bmiCategory;
        private String goal;
        private String activityLevel;
        private int dailyCalorieTarget;
        private double dailyProteinTarget;
        private double dailyCarbTarget;
        private double dailyFatTarget;
    }
}

package com.gymcal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public class FoodDTOs {

    // ─────────────────────────────────────────────────
    // FOOD SEARCH — now supports both grams AND quantity
    // e.g. {"foodName":"roti","quantityGrams":100}
    //   OR {"foodName":"roti","quantityAmount":2,"quantityUnit":"pieces"}
    //   OR {"foodName":"chicken breast","quantityGrams":150}
    // ─────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodSearchRequest {
        @NotBlank(message = "Food name is required")
        private String foodName;

        // Option A: grams directly
        private Double quantityGrams;

        // Option B: natural quantity (e.g. 2 pieces, 1 cup)
        private Double quantityAmount;   // e.g. 2
        private String quantityUnit;     // e.g. "pieces", "cups", "ml", "serving"
    }

    // ─────────────────────────────────────────────────
    // AI NUTRITION RESULT — includes good/bad calories
    // ─────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NutritionInfo {
        private String foodName;
        private double quantityGrams;
        private String quantityDisplay;  // "2 pieces (100g)" or "150g"

        // Core macros
        private double calories;
        private double proteinGrams;
        private double carbsGrams;
        private double fatGrams;
        private double fiberGrams;

        // NEW: Good vs Bad calories
        private double goodCalories;     // from protein + complex carbs + fiber
        private double badCalories;      // from saturated fat + simple sugars
        private double neutralCalories;  // healthy fats + complex carbs
        private String calorieQuality;   // "EXCELLENT" | "GOOD" | "MODERATE" | "POOR"
        private String qualityReason;    // short explanation

        // Per-100g reference
        private double caloriePer100g;
        private double proteinPer100g;

        private String aiAnalysis;
        private boolean success;
        private String errorMessage;
    }

    // ─────────────────────────────────────────────────
    // ADD FOOD LOG REQUEST
    // ─────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddFoodLogRequest {
        @NotBlank
        private String foodName;

        private Double quantityGrams;
        private String quantityDisplay;
        private String quantityUnit;
        private Double quantityAmount;

        @NotBlank
        private String mealType;

        private LocalDate logDate;

        private Double calories;
        private Double proteinGrams;
        private Double carbsGrams;
        private Double fatGrams;
        private Double fiberGrams;

        // Good/Bad calories (from AI result)
        private Double goodCalories;
        private Double badCalories;
        private Double neutralCalories;
        private String calorieQuality;

        private String aiAnalysis;
    }

    // ─────────────────────────────────────────────────
    // DAILY SUMMARY — includes good/bad calorie totals
    // ─────────────────────────────────────────────────
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

        // Consumed totals
        private double consumedCalories;
        private double consumedProtein;
        private double consumedCarbs;
        private double consumedFat;
        private double consumedFiber;

        // NEW: Good vs Bad calories consumed today
        private double goodCalories;
        private double badCalories;
        private double neutralCalories;
        private double goodCaloriePercent;   // % of total that are "good"
        private double badCaloriePercent;    // % of total that are "bad"

        // Remaining
        private double remainingCalories;
        private double remainingProtein;

        // Meal breakdown
        private List<MealGroup> meals;

        // Progress %
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
        private double goodCalories;
        private double badCalories;
    }

    // ─────────────────────────────────────────────────
    // FOOD LOG RESPONSE
    // ─────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodLogResponse {
        private String id;
        private String foodName;
        private double quantityGrams;
        private String quantityDisplay;
        private String mealType;
        private double calories;
        private double proteinGrams;
        private double carbsGrams;
        private double fatGrams;
        private double fiberGrams;
        private double goodCalories;
        private double badCalories;
        private String calorieQuality;
        private String logDate;
        private String createdAt;
    }

    // ─────────────────────────────────────────────────
    // WEEKLY WORKOUT PLAN
    // ─────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkoutPlan {
        private String goal;
        private String activityLevel;
        private String gender;
        private String planTitle;
        private String planDescription;
        private List<WorkoutDay> days;
        private List<String> generalTips;
        private String weeklyNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkoutDay {
        private String day;           // Monday, Tuesday...
        private String type;          // "TRAINING" | "REST" | "ACTIVE_RECOVERY"
        private String focus;         // "Chest & Triceps", "Cardio", "Rest"
        private String intensity;     // "High", "Medium", "Low", "Rest"
        private List<Exercise> exercises;
        private int estimatedMinutes;
        private int estimatedCaloriesBurn;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Exercise {
        private String name;
        private String sets;          // "3 sets"
        private String reps;          // "8-12 reps" or "30 min"
        private String rest;          // "60 sec"
        private String muscleGroup;
        private String difficulty;    // "Beginner" | "Intermediate" | "Advanced"
    }

    // ─────────────────────────────────────────────────
    // GOAL UPDATE REQUEST
    // ─────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────
    // USER PROFILE RESPONSE
    // ─────────────────────────────────────────────────
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

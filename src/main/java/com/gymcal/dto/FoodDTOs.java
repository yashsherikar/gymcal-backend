package com.gymcal.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public class FoodDTOs {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FoodSearchRequest {
        @NotBlank(message = "Food name is required")
        private String foodName;
        private Double quantityAmount; // e.g. 2 (cups) or 150 (grams)
        private String quantityUnit;   // grams, pieces, cup, bowl, tbsp, tsp, ml
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class NutritionInfo {
        private String foodName;
        private double quantityAmount;
        private String quantityUnit;
        private double quantityGrams;  // converted to grams
        private double calories;
        private double proteinGrams;
        private double carbsGrams;
        private double fatGrams;
        private double fiberGrams;
        private double waterContentMl;
        private double goodCalories;   // protein + fiber cals
        private double badCalories;    // fat cals
        private double carbCalories;   // carb cals
        private String calQuality;     // "Excellent", "Good", "Moderate", "Poor"
        private String aiAnalysis;
        private boolean success;
        private String errorMessage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AddFoodLogRequest {
        @NotBlank private String foodName;
        private Double quantityAmount;
        private String quantityUnit;
        private Double quantityGrams;
        @NotBlank private String mealType;
        private LocalDate logDate;
        private Double calories;
        private Double proteinGrams;
        private Double carbsGrams;
        private Double fatGrams;
        private Double fiberGrams;
        private Double waterContentMl;
        private Double goodCalories;
        private Double badCalories;
        private Double carbCalories;
        private String aiAnalysis;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailySummary {
        private LocalDate date;
        private int targetCalories;
        private double targetProtein;
        private double targetCarbs;
        private double targetFat;
        private double consumedCalories;
        private double consumedProtein;
        private double consumedCarbs;
        private double consumedFat;
        private double consumedFiber;
        private double totalWaterFromFood;
        private double goodCalories;   // total good cals today
        private double badCalories;    // total bad cals today
        private double carbCalories;   // total carb cals today
        private double remainingCalories;
        private double remainingProtein;
        private List<MealGroup> meals;
        private double calorieProgress;
        private double proteinProgress;
        private double carbProgress;
        private double fatProgress;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MealGroup {
        private String mealType;
        private List<FoodLogResponse> items;
        private double totalCalories;
        private double totalProtein;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FoodLogResponse {
        private String id;
        private String foodName;
        private double quantityAmount;
        private String quantityUnit;
        private double quantityGrams;
        private String mealType;
        private double calories;
        private double proteinGrams;
        private double carbsGrams;
        private double fatGrams;
        private double fiberGrams;
        private double waterContentMl;
        private double goodCalories;
        private double badCalories;
        private double carbCalories;
        private String logDate;
        private String createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateGoalRequest {
        private String goal;
        private String activityLevel;
        private Double weightKg;
        private Double heightCm;
        private Integer age;
        private List<String> healthConditions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
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
        private List<String> healthConditions;
    }
}

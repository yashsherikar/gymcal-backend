package com.gymcal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

public class AuthDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Valid email required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @NotNull @Positive
        private Double weightKg;

        @NotNull @Positive
        private Double heightCm;

        @NotNull @Min(10) @Max(100)
        private Integer age;

        @NotBlank
        private String gender; // MALE, FEMALE

        @NotBlank
        private String goal; // WEIGHT_LOSS, MUSCLE_GAIN, MAINTAIN, RECOMPOSITION

        @NotBlank
        private String activityLevel; // SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @Email @NotBlank
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String userId;
        private String name;
        private String email;
        private double bmi;
        private String bmiCategory;
        private String goal;
        private int dailyCalorieTarget;
        private double dailyProteinTarget;
        private double dailyCarbTarget;
        private double dailyFatTarget;
    }
}

package com.gymcal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String name;

    // Physical stats
    private double weightKg;
    private double heightCm;
    private int age;
    private String gender; // MALE, FEMALE

    // Calculated
    private double bmi;
    private String bmiCategory; // Underweight, Normal, Overweight, Obese

    // Goal
    private String goal; // WEIGHT_LOSS, MUSCLE_GAIN, MAINTAIN, RECOMPOSITION
    private String activityLevel; // SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE

    // Calculated daily targets
    private int dailyCalorieTarget;
    private double dailyProteinTarget;   // grams
    private double dailyCarbTarget;      // grams
    private double dailyFatTarget;       // grams

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

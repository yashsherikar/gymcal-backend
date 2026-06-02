package com.gymcal.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id private String id;
    @Indexed(unique = true) private String email;
    private String password;
    private String name;
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
    private List<String> healthConditions; // NEW: heart_disease, asthma, diabetes, etc.
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

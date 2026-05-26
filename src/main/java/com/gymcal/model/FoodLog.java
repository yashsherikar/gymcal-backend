package com.gymcal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "food_logs")
@CompoundIndexes({
    @CompoundIndex(name = "user_date_idx", def = "{'userId': 1, 'logDate': 1}")
})
public class FoodLog {

    @Id
    private String id;

    private String userId;
    private LocalDate logDate;
    private String mealType; // BREAKFAST, LUNCH, DINNER, SNACK

    // Food details from AI
    private String foodName;
    private double quantityGrams;

    // Nutrition per the given quantity
    private double calories;
    private double proteinGrams;
    private double carbsGrams;
    private double fatGrams;
    private double fiberGrams;

    // AI raw response for reference
    private String aiAnalysis;

    private LocalDateTime createdAt;
}

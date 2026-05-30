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

    // NEW: flexible quantity — either grams OR count/unit
    private double quantityGrams;      // resolved grams (always filled after AI analysis)
    private String quantityDisplay;    // what user typed: "2 rotis", "1 cup", "150g"
    private String quantityUnit;       // "grams", "pieces", "cups", "ml", "serving"
    private double quantityAmount;     // the numeric amount e.g. 2 (for "2 rotis")

    // Nutrition per the given quantity
    private double calories;
    private double proteinGrams;
    private double carbsGrams;
    private double fatGrams;
    private double fiberGrams;

    // NEW: Good vs Bad calories breakdown
    private double goodCalories;       // from protein + fiber
    private double badCalories;        // from saturated fat + added sugars estimate
    private double neutralCalories;    // remaining (healthy carbs + unsaturated fat)
    private String calorieQuality;     // "EXCELLENT", "GOOD", "MODERATE", "POOR"

    // AI raw response for reference
    private String aiAnalysis;

    private LocalDateTime createdAt;
}

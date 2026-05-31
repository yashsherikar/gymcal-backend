package com.gymcal.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "food_logs")
@CompoundIndexes({@CompoundIndex(name = "user_date_idx", def = "{'userId': 1, 'logDate': 1}")})
public class FoodLog {
    @Id private String id;
    private String userId;
    private LocalDate logDate;
    private String mealType;
    private String foodName;

    // Quantity — both original unit and gram equivalent
    private double quantityAmount;   // e.g. 2 (cups) or 150 (grams)
    private String quantityUnit;     // grams, pieces, cup, bowl, tbsp, tsp, ml
    private double quantityGrams;    // always in grams for calculations

    // Nutrition
    private double calories;
    private double proteinGrams;
    private double carbsGrams;
    private double fatGrams;
    private double fiberGrams;

    // Good vs Bad calories
    private double goodCalories;   // from protein + fiber
    private double badCalories;    // from fat
    private double carbCalories;   // from carbs (neutral energy)

    private String aiAnalysis;
    private LocalDateTime createdAt;
}

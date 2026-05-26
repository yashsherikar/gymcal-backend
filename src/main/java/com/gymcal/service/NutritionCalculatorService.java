package com.gymcal.service;

import org.springframework.stereotype.Service;

@Service
public class NutritionCalculatorService {

    /**
     * Calculate BMI
     */
    public double calculateBMI(double weightKg, double heightCm) {
        double heightM = heightCm / 100.0;
        return Math.round((weightKg / (heightM * heightM)) * 10.0) / 10.0;
    }

    /**
     * Get BMI category
     */
    public String getBMICategory(double bmi) {
        if (bmi < 18.5) return "Underweight";
        if (bmi < 25.0) return "Normal Weight";
        if (bmi < 30.0) return "Overweight";
        return "Obese";
    }

    /**
     * Calculate BMR using Mifflin-St Jeor equation
     */
    public double calculateBMR(double weightKg, double heightCm, int age, String gender) {
        double bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age);
        if ("MALE".equalsIgnoreCase(gender)) {
            bmr += 5;
        } else {
            bmr -= 161;
        }
        return bmr;
    }

    /**
     * Get activity multiplier
     */
    public double getActivityMultiplier(String activityLevel) {
        return switch (activityLevel.toUpperCase()) {
            case "SEDENTARY"   -> 1.2;
            case "LIGHT"       -> 1.375;
            case "MODERATE"    -> 1.55;
            case "ACTIVE"      -> 1.725;
            case "VERY_ACTIVE" -> 1.9;
            default            -> 1.55;
        };
    }

    /**
     * Calculate TDEE (Total Daily Energy Expenditure)
     */
    public double calculateTDEE(double bmr, String activityLevel) {
        return bmr * getActivityMultiplier(activityLevel);
    }

    /**
     * Calculate daily calorie target based on goal
     */
    public int calculateDailyCalorieTarget(double tdee, String goal) {
        return switch (goal.toUpperCase()) {
            case "WEIGHT_LOSS"    -> (int) Math.round(tdee * 0.80); // 20% deficit
            case "MUSCLE_GAIN"    -> (int) Math.round(tdee * 1.10); // 10% surplus
            case "RECOMPOSITION"  -> (int) Math.round(tdee);        // maintenance
            default               -> (int) Math.round(tdee);        // MAINTAIN
        };
    }

    /**
     * Calculate protein target in grams
     * Weight loss: 2.2g/kg | Muscle gain: 2.5g/kg | Maintain: 1.8g/kg
     */
    public double calculateProteinTarget(double weightKg, String goal) {
        double multiplier = switch (goal.toUpperCase()) {
            case "WEIGHT_LOSS"   -> 2.2;
            case "MUSCLE_GAIN"   -> 2.5;
            case "RECOMPOSITION" -> 2.3;
            default              -> 1.8;
        };
        return Math.round(weightKg * multiplier * 10.0) / 10.0;
    }

    /**
     * Calculate fat target (~25-30% of calories)
     */
    public double calculateFatTarget(int dailyCalories, String goal) {
        double fatPercent = "WEIGHT_LOSS".equalsIgnoreCase(goal) ? 0.25 : 0.28;
        return Math.round((dailyCalories * fatPercent / 9) * 10.0) / 10.0; // 9 cal per gram fat
    }

    /**
     * Calculate carb target (remaining calories after protein + fat)
     */
    public double calculateCarbTarget(int dailyCalories, double proteinGrams, double fatGrams) {
        double proteinCals = proteinGrams * 4;  // 4 cal per gram protein
        double fatCals = fatGrams * 9;           // 9 cal per gram fat
        double carbCals = dailyCalories - proteinCals - fatCals;
        return Math.max(0, Math.round((carbCals / 4) * 10.0) / 10.0); // 4 cal per gram carb
    }
}

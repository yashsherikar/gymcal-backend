package com.gymcal.service;

import com.gymcal.dto.FoodDTOs;
import com.gymcal.model.FoodLog;
import com.gymcal.model.User;
import com.gymcal.repository.FoodLogRepository;
import com.gymcal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodLogService {

    private final FoodLogRepository foodLogRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;   // ← correct: matches bean name

    // Search food nutrition via AI (preview — does NOT add to log)
    public FoodDTOs.NutritionInfo searchFood(String userId, FoodDTOs.FoodSearchRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return geminiService.analyzeFoodNutrition(request.getFoodName(), request.getQuantityGrams());
    }

    // Add food to daily log
    public FoodDTOs.FoodLogResponse addFoodLog(String userId, FoodDTOs.AddFoodLogRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate logDate = request.getLogDate() != null ? request.getLogDate() : LocalDate.now();

        double calories = 0, protein = 0, carbs = 0, fat = 0, fiber = 0;
        String aiAnalysis = request.getAiAnalysis();

        if (request.getCalories() != null) {
            // Nutrition already provided from frontend (user searched first, then added)
            calories = request.getCalories();
            protein  = Optional.ofNullable(request.getProteinGrams()).orElse(0.0);
            carbs    = Optional.ofNullable(request.getCarbsGrams()).orElse(0.0);
            fat      = Optional.ofNullable(request.getFatGrams()).orElse(0.0);
            fiber    = Optional.ofNullable(request.getFiberGrams()).orElse(0.0);
        } else {
            // Fallback: fetch from Gemini if not pre-filled
            FoodDTOs.NutritionInfo nutrition = geminiService.analyzeFoodNutrition(
                    request.getFoodName(), request.getQuantityGrams());
            if (nutrition.isSuccess()) {
                calories   = nutrition.getCalories();
                protein    = nutrition.getProteinGrams();
                carbs      = nutrition.getCarbsGrams();
                fat        = nutrition.getFatGrams();
                fiber      = nutrition.getFiberGrams();
                aiAnalysis = nutrition.getAiAnalysis();
            }
        }

        FoodLog foodLog = FoodLog.builder()
                .userId(userId)
                .logDate(logDate)
                .mealType(request.getMealType().toUpperCase())
                .foodName(request.getFoodName())
                .quantityGrams(request.getQuantityGrams())
                .calories(calories)
                .proteinGrams(protein)
                .carbsGrams(carbs)
                .fatGrams(fat)
                .fiberGrams(fiber)
                .aiAnalysis(aiAnalysis)
                .createdAt(LocalDateTime.now())
                .build();

        FoodLog saved = foodLogRepository.save(foodLog);
        return mapToResponse(saved);
    }

    // Get daily summary
    public FoodDTOs.DailySummary getDailySummary(String userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (date == null) date = LocalDate.now();

        List<FoodLog> logs = foodLogRepository.findByUserIdAndLogDateOrderByCreatedAtDesc(userId, date);

        double totalCals    = logs.stream().mapToDouble(FoodLog::getCalories).sum();
        double totalProtein = logs.stream().mapToDouble(FoodLog::getProteinGrams).sum();
        double totalCarbs   = logs.stream().mapToDouble(FoodLog::getCarbsGrams).sum();
        double totalFat     = logs.stream().mapToDouble(FoodLog::getFatGrams).sum();
        double totalFiber   = logs.stream().mapToDouble(FoodLog::getFiberGrams).sum();

        Map<String, List<FoodLog>> mealGroups = logs.stream()
                .collect(Collectors.groupingBy(FoodLog::getMealType));

        List<String> mealOrder = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");
        List<FoodDTOs.MealGroup> meals = mealOrder.stream()
                .filter(mealGroups::containsKey)
                .map(mealType -> {
                    List<FoodLog> items = mealGroups.get(mealType);
                    return FoodDTOs.MealGroup.builder()
                            .mealType(mealType)
                            .items(items.stream().map(this::mapToResponse).collect(Collectors.toList()))
                            .totalCalories(items.stream().mapToDouble(FoodLog::getCalories).sum())
                            .totalProtein(items.stream().mapToDouble(FoodLog::getProteinGrams).sum())
                            .build();
                })
                .collect(Collectors.toList());

        int    targetCals    = user.getDailyCalorieTarget();
        double targetProtein = user.getDailyProteinTarget();
        double targetCarbs   = user.getDailyCarbTarget();
        double targetFat     = user.getDailyFatTarget();

        return FoodDTOs.DailySummary.builder()
                .date(date)
                .targetCalories(targetCals)
                .targetProtein(targetProtein)
                .targetCarbs(targetCarbs)
                .targetFat(targetFat)
                .consumedCalories(Math.round(totalCals    * 10.0) / 10.0)
                .consumedProtein( Math.round(totalProtein * 10.0) / 10.0)
                .consumedCarbs(   Math.round(totalCarbs   * 10.0) / 10.0)
                .consumedFat(     Math.round(totalFat     * 10.0) / 10.0)
                .consumedFiber(   Math.round(totalFiber   * 10.0) / 10.0)
                .remainingCalories(Math.max(0, targetCals    - totalCals))
                .remainingProtein( Math.max(0, targetProtein - totalProtein))
                .calorieProgress(targetCals    > 0 ? Math.min(100, (totalCals    / targetCals)    * 100) : 0)
                .proteinProgress(targetProtein > 0 ? Math.min(100, (totalProtein / targetProtein) * 100) : 0)
                .carbProgress(   targetCarbs   > 0 ? Math.min(100, (totalCarbs   / targetCarbs)   * 100) : 0)
                .fatProgress(    targetFat     > 0 ? Math.min(100, (totalFat     / targetFat)     * 100) : 0)
                .meals(meals)
                .build();
    }

    // Get last 7 days
    public List<FoodDTOs.DailySummary> getWeeklySummary(String userId) {
        LocalDate today   = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);
        List<FoodDTOs.DailySummary> summaries = new ArrayList<>();
        for (LocalDate d = weekAgo; !d.isAfter(today); d = d.plusDays(1)) {
            summaries.add(getDailySummary(userId, d));
        }
        return summaries;
    }

    // Delete a log entry
    public void deleteFoodLog(String userId, String logId) {
        foodLogRepository.deleteByIdAndUserId(logId, userId);
    }

    private FoodDTOs.FoodLogResponse mapToResponse(FoodLog log) {
        return FoodDTOs.FoodLogResponse.builder()
                .id(log.getId())
                .foodName(log.getFoodName())
                .quantityGrams(log.getQuantityGrams())
                .mealType(log.getMealType())
                .calories(log.getCalories())
                .proteinGrams(log.getProteinGrams())
                .carbsGrams(log.getCarbsGrams())
                .fatGrams(log.getFatGrams())
                .fiberGrams(log.getFiberGrams())
                .logDate(log.getLogDate().toString())
                .createdAt(log.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }
}

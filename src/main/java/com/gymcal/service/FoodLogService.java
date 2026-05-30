package com.gymcal.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gymcal.dto.FoodDTOs;
import com.gymcal.model.FoodLog;
import com.gymcal.model.User;
import com.gymcal.repository.FoodLogRepository;
import com.gymcal.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodLogService {

    @Autowired
    private FoodLogRepository foodLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GeminiService geminiService;

    /**
     * Search food — supports grams OR natural quantity (2 rotis, 1 cup dal)
     */
    public FoodDTOs.NutritionInfo searchFood(String userId, FoodDTOs.FoodSearchRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return geminiService.analyzeFoodNutrition(
                request.getFoodName(),
                request.getQuantityGrams(),
                request.getQuantityAmount(),
                request.getQuantityUnit()
        );
    }

    /**
     * Add food to daily log with good/bad calorie tracking
     */
    public FoodDTOs.FoodLogResponse addFoodLog(String userId, FoodDTOs.AddFoodLogRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate logDate = request.getLogDate() != null ? request.getLogDate() : LocalDate.now();

        double calories = 0, protein = 0, carbs = 0, fat = 0, fiber = 0;
        double goodCals = 0, badCals = 0, neutralCals = 0;
        String calorieQuality = "MODERATE";
        String aiAnalysis = request.getAiAnalysis();
        double resolvedGrams = Optional.ofNullable(request.getQuantityGrams()).orElse(100.0);
        String quantityDisplay = request.getQuantityDisplay();

        if (request.getCalories() != null) {
            // Already has nutrition from search result
            calories     = request.getCalories();
            protein      = Optional.ofNullable(request.getProteinGrams()).orElse(0.0);
            carbs        = Optional.ofNullable(request.getCarbsGrams()).orElse(0.0);
            fat          = Optional.ofNullable(request.getFatGrams()).orElse(0.0);
            fiber        = Optional.ofNullable(request.getFiberGrams()).orElse(0.0);
            goodCals     = Optional.ofNullable(request.getGoodCalories()).orElse(0.0);
            badCals      = Optional.ofNullable(request.getBadCalories()).orElse(0.0);
            neutralCals  = Optional.ofNullable(request.getNeutralCalories()).orElse(calories - goodCals - badCals);
            calorieQuality = Optional.ofNullable(request.getCalorieQuality()).orElse("MODERATE");
        } else {
            // Re-analyze via AI
            FoodDTOs.NutritionInfo n = geminiService.analyzeFoodNutrition(
                    request.getFoodName(),
                    request.getQuantityGrams(),
                    request.getQuantityAmount(),
                    request.getQuantityUnit()
            );
            if (n.isSuccess()) {
                calories       = n.getCalories();
                protein        = n.getProteinGrams();
                carbs          = n.getCarbsGrams();
                fat            = n.getFatGrams();
                fiber          = n.getFiberGrams();
                goodCals       = n.getGoodCalories();
                badCals        = n.getBadCalories();
                neutralCals    = n.getNeutralCalories();
                calorieQuality = n.getCalorieQuality();
                aiAnalysis     = n.getAiAnalysis();
                resolvedGrams  = n.getQuantityGrams();
                quantityDisplay = n.getQuantityDisplay();
            }
        }

        FoodLog foodLog = FoodLog.builder()
                .userId(userId)
                .logDate(logDate)
                .mealType(request.getMealType().toUpperCase())
                .foodName(request.getFoodName())
                .quantityGrams(resolvedGrams)
                .quantityDisplay(quantityDisplay != null ? quantityDisplay : resolvedGrams + "g")
                .quantityUnit(Optional.ofNullable(request.getQuantityUnit()).orElse("grams"))
                .quantityAmount(Optional.ofNullable(request.getQuantityAmount()).orElse(resolvedGrams))
                .calories(calories)
                .proteinGrams(protein)
                .carbsGrams(carbs)
                .fatGrams(fat)
                .fiberGrams(fiber)
                .goodCalories(goodCals)
                .badCalories(badCals)
                .neutralCalories(neutralCals)
                .calorieQuality(calorieQuality)
                .aiAnalysis(aiAnalysis)
                .createdAt(LocalDateTime.now())
                .build();

        return mapToResponse(foodLogRepository.save(foodLog));
    }

    /**
     * Daily summary including good/bad calorie totals
     */
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
        double totalGood    = logs.stream().mapToDouble(FoodLog::getGoodCalories).sum();
        double totalBad     = logs.stream().mapToDouble(FoodLog::getBadCalories).sum();
        double totalNeutral = logs.stream().mapToDouble(FoodLog::getNeutralCalories).sum();

        Map<String, List<FoodLog>> mealGroups = logs.stream()
                .collect(Collectors.groupingBy(FoodLog::getMealType));

        List<FoodDTOs.MealGroup> meals = List.of("BREAKFAST","LUNCH","DINNER","SNACK").stream()
                .filter(mealGroups::containsKey)
                .map(mt -> {
                    List<FoodLog> items = mealGroups.get(mt);
                    return FoodDTOs.MealGroup.builder()
                            .mealType(mt)
                            .items(items.stream().map(this::mapToResponse).collect(Collectors.toList()))
                            .totalCalories(items.stream().mapToDouble(FoodLog::getCalories).sum())
                            .totalProtein(items.stream().mapToDouble(FoodLog::getProteinGrams).sum())
                            .goodCalories(items.stream().mapToDouble(FoodLog::getGoodCalories).sum())
                            .badCalories(items.stream().mapToDouble(FoodLog::getBadCalories).sum())
                            .build();
                })
                .collect(Collectors.toList());

        int    tCal  = user.getDailyCalorieTarget();
        double tProt = user.getDailyProteinTarget();
        double tCarb = user.getDailyCarbTarget();
        double tFat  = user.getDailyFatTarget();

        double goodPct = totalCals > 0 ? round1((totalGood / totalCals) * 100) : 0;
        double badPct  = totalCals > 0 ? round1((totalBad  / totalCals) * 100) : 0;

        return FoodDTOs.DailySummary.builder()
                .date(date)
                .targetCalories(tCal).targetProtein(tProt).targetCarbs(tCarb).targetFat(tFat)
                .consumedCalories(round1(totalCals))
                .consumedProtein(round1(totalProtein))
                .consumedCarbs(round1(totalCarbs))
                .consumedFat(round1(totalFat))
                .consumedFiber(round1(totalFiber))
                .goodCalories(round1(totalGood))
                .badCalories(round1(totalBad))
                .neutralCalories(round1(totalNeutral))
                .goodCaloriePercent(goodPct)
                .badCaloriePercent(badPct)
                .remainingCalories(Math.max(0, tCal - totalCals))
                .remainingProtein(Math.max(0, tProt - totalProtein))
                .calorieProgress(tCal  > 0 ? Math.min(100, (totalCals    / tCal)  * 100) : 0)
                .proteinProgress(tProt > 0 ? Math.min(100, (totalProtein / tProt) * 100) : 0)
                .carbProgress(   tCarb > 0 ? Math.min(100, (totalCarbs   / tCarb) * 100) : 0)
                .fatProgress(    tFat  > 0 ? Math.min(100, (totalFat     / tFat)  * 100) : 0)
                .meals(meals)
                .build();
    }

    public List<FoodDTOs.DailySummary> getWeeklySummary(String userId) {
        LocalDate today = LocalDate.now();
        List<FoodDTOs.DailySummary> list = new ArrayList<>();
        for (LocalDate d = today.minusDays(6); !d.isAfter(today); d = d.plusDays(1)) {
            list.add(getDailySummary(userId, d));
        }
        return list;
    }

    public void deleteFoodLog(String userId, String logId) {
        foodLogRepository.deleteByIdAndUserId(logId, userId);
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private FoodDTOs.FoodLogResponse mapToResponse(FoodLog log) {
        return FoodDTOs.FoodLogResponse.builder()
                .id(log.getId())
                .foodName(log.getFoodName())
                .quantityGrams(log.getQuantityGrams())
                .quantityDisplay(log.getQuantityDisplay() != null ? log.getQuantityDisplay() : log.getQuantityGrams() + "g")
                .mealType(log.getMealType())
                .calories(log.getCalories())
                .proteinGrams(log.getProteinGrams())
                .carbsGrams(log.getCarbsGrams())
                .fatGrams(log.getFatGrams())
                .fiberGrams(log.getFiberGrams())
                .goodCalories(log.getGoodCalories())
                .badCalories(log.getBadCalories())
                .calorieQuality(log.getCalorieQuality())
                .logDate(log.getLogDate().toString())
                .createdAt(log.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }
}

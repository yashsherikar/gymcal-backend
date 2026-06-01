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

@Slf4j @Service @RequiredArgsConstructor
public class FoodLogService {

    private final FoodLogRepository foodLogRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final WaterService waterService;

    public FoodDTOs.NutritionInfo searchFood(String userId, FoodDTOs.FoodSearchRequest req) {
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        String unit   = req.getQuantityUnit()   != null ? req.getQuantityUnit()   : "grams";
        Double amount = req.getQuantityAmount() != null ? req.getQuantityAmount() : 100.0;
        return geminiService.analyzeFoodNutrition(req.getFoodName(), amount, unit);
    }

    public FoodDTOs.FoodLogResponse addFoodLog(String userId, FoodDTOs.AddFoodLogRequest req) {
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate logDate = req.getLogDate() != null ? req.getLogDate() : LocalDate.now();
        String unit   = req.getQuantityUnit()   != null ? req.getQuantityUnit()  : "grams";
        double amount = req.getQuantityAmount() != null ? req.getQuantityAmount() : (req.getQuantityGrams() != null ? req.getQuantityGrams() : 100.0);
        double qGrams = req.getQuantityGrams()  != null ? req.getQuantityGrams() : amount;

        double calories = 0, protein = 0, carbs = 0, fat = 0, fiber = 0, waterMl = 0;
        double goodCal = 0, badCal = 0, carbCal = 0;
        String aiAnalysis = req.getAiAnalysis();

        if (req.getCalories() != null) {
            calories  = req.getCalories();
            protein   = nvl(req.getProteinGrams());
            carbs     = nvl(req.getCarbsGrams());
            fat       = nvl(req.getFatGrams());
            fiber     = nvl(req.getFiberGrams());
            waterMl   = nvl(req.getWaterContentMl());
            goodCal   = nvl(req.getGoodCalories(),  r1(protein * 4 + fiber * 2));
            badCal    = nvl(req.getBadCalories(),    r1(fat * 9));
            carbCal   = nvl(req.getCarbCalories(),   r1(carbs * 4));
        } else {
            FoodDTOs.NutritionInfo n = geminiService.analyzeFoodNutrition(req.getFoodName(), amount, unit);
            if (n.isSuccess()) {
                calories = n.getCalories(); protein = n.getProteinGrams();
                carbs = n.getCarbsGrams(); fat = n.getFatGrams();
                fiber = n.getFiberGrams(); qGrams = n.getQuantityGrams();
                waterMl = n.getWaterContentMl();
                goodCal = n.getGoodCalories(); badCal = n.getBadCalories();
                carbCal = n.getCarbCalories(); aiAnalysis = n.getAiAnalysis();
            }
        }

        FoodLog foodLog = FoodLog.builder()
                .userId(userId).logDate(logDate).mealType(req.getMealType().toUpperCase())
                .foodName(req.getFoodName())
                .quantityAmount(amount).quantityUnit(unit).quantityGrams(qGrams)
                .calories(calories).proteinGrams(protein).carbsGrams(carbs)
                .fatGrams(fat).fiberGrams(fiber).waterContentMl(waterMl)
                .goodCalories(goodCal).badCalories(badCal).carbCalories(carbCal)
                .aiAnalysis(aiAnalysis).createdAt(LocalDateTime.now())
                .build();

        FoodLog saved = foodLogRepository.save(foodLog);

        // AUTO-ADD water from food (juice, fruits, milk etc.)
        if (waterMl > 5) {
            waterService.addWaterFromFood(userId, waterMl);
            log.info("Auto-added {}ml water from food '{}' for user {}", waterMl, req.getFoodName(), userId);
        }

        return mapToResponse(saved);
    }

    public FoodDTOs.DailySummary getDailySummary(String userId, LocalDate date) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (date == null) date = LocalDate.now();
        List<FoodLog> logs = foodLogRepository.findByUserIdAndLogDateOrderByCreatedAtDesc(userId, date);

        double tCals  = logs.stream().mapToDouble(FoodLog::getCalories).sum();
        double tProt  = logs.stream().mapToDouble(FoodLog::getProteinGrams).sum();
        double tCarb  = logs.stream().mapToDouble(FoodLog::getCarbsGrams).sum();
        double tFat   = logs.stream().mapToDouble(FoodLog::getFatGrams).sum();
        double tFib   = logs.stream().mapToDouble(FoodLog::getFiberGrams).sum();
        double tGood  = logs.stream().mapToDouble(FoodLog::getGoodCalories).sum();
        double tBad   = logs.stream().mapToDouble(FoodLog::getBadCalories).sum();
        double tCarbC = logs.stream().mapToDouble(FoodLog::getCarbCalories).sum();
        double tWater = logs.stream().mapToDouble(FoodLog::getWaterContentMl).sum();

        Map<String, List<FoodLog>> grouped = logs.stream().collect(Collectors.groupingBy(FoodLog::getMealType));
        List<FoodDTOs.MealGroup> meals = List.of("BREAKFAST","LUNCH","DINNER","SNACK").stream()
                .filter(grouped::containsKey)
                .map(mt -> { List<FoodLog> items = grouped.get(mt);
                    return FoodDTOs.MealGroup.builder().mealType(mt)
                            .items(items.stream().map(this::mapToResponse).collect(Collectors.toList()))
                            .totalCalories(items.stream().mapToDouble(FoodLog::getCalories).sum())
                            .totalProtein(items.stream().mapToDouble(FoodLog::getProteinGrams).sum())
                            .build(); }).collect(Collectors.toList());

        int cTarget = user.getDailyCalorieTarget();
        double pTarget = user.getDailyProteinTarget(), crTarget = user.getDailyCarbTarget(), fTarget = user.getDailyFatTarget();

        return FoodDTOs.DailySummary.builder()
                .date(date)
                .targetCalories(cTarget).targetProtein(pTarget).targetCarbs(crTarget).targetFat(fTarget)
                .consumedCalories(r1(tCals)).consumedProtein(r1(tProt)).consumedCarbs(r1(tCarb))
                .consumedFat(r1(tFat)).consumedFiber(r1(tFib))
                .goodCalories(r1(tGood)).badCalories(r1(tBad)).carbCalories(r1(tCarbC))
                .totalWaterFromFood(r1(tWater))
                .remainingCalories(Math.max(0, cTarget - tCals))
                .remainingProtein(Math.max(0, pTarget - tProt))
                .calorieProgress(cTarget  > 0 ? Math.min(100, (tCals / cTarget)  * 100) : 0)
                .proteinProgress(pTarget  > 0 ? Math.min(100, (tProt / pTarget)  * 100) : 0)
                .carbProgress(   crTarget > 0 ? Math.min(100, (tCarb / crTarget) * 100) : 0)
                .fatProgress(    fTarget  > 0 ? Math.min(100, (tFat  / fTarget)  * 100) : 0)
                .meals(meals).build();
    }

    public List<FoodDTOs.DailySummary> getWeeklySummary(String userId) {
        LocalDate today = LocalDate.now();
        List<FoodDTOs.DailySummary> list = new ArrayList<>();
        for (LocalDate d = today.minusDays(6); !d.isAfter(today); d = d.plusDays(1))
            list.add(getDailySummary(userId, d));
        return list;
    }

    public void deleteFoodLog(String userId, String logId) {
        foodLogRepository.deleteByIdAndUserId(logId, userId);
    }

    private double r1(double v)  { return Math.round(v * 10.0) / 10.0; }
    private double nvl(Double v) { return v != null ? v : 0.0; }
    private double nvl(Double v, double fallback) { return v != null ? v : fallback; }

    private FoodDTOs.FoodLogResponse mapToResponse(FoodLog l) {
        return FoodDTOs.FoodLogResponse.builder()
                .id(l.getId()).foodName(l.getFoodName())
                .quantityAmount(l.getQuantityAmount())
                .quantityUnit(l.getQuantityUnit() != null ? l.getQuantityUnit() : "grams")
                .quantityGrams(l.getQuantityGrams()).mealType(l.getMealType())
                .calories(l.getCalories()).proteinGrams(l.getProteinGrams())
                .carbsGrams(l.getCarbsGrams()).fatGrams(l.getFatGrams()).fiberGrams(l.getFiberGrams())
                .waterContentMl(l.getWaterContentMl())
                .goodCalories(l.getGoodCalories()).badCalories(l.getBadCalories()).carbCalories(l.getCarbCalories())
                .logDate(l.getLogDate().toString())
                .createdAt(l.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }
}

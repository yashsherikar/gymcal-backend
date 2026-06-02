package com.gymcal.service;

import com.gymcal.model.User;
import com.gymcal.model.WaterLog;
import com.gymcal.repository.UserRepository;
import com.gymcal.repository.WaterLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class WaterService {

    private final WaterLogRepository waterRepo;
    private final UserRepository userRepo;

    public double calculateWaterTarget(User user) {
        double weight   = user.getWeightKg();
        double height   = user.getHeightCm();
        String gender   = user.getGender()        != null ? user.getGender().toUpperCase()        : "MALE";
        String activity = user.getActivityLevel() != null ? user.getActivityLevel().toUpperCase() : "MODERATE";

        double base = gender.equals("FEMALE") ? weight * 31 : weight * 35;
        double multiplier = switch (activity) {
            case "SEDENTARY"   -> 1.0;
            case "LIGHT"       -> 1.1;
            case "MODERATE"    -> 1.2;
            case "ACTIVE"      -> 1.35;
            case "VERY_ACTIVE" -> 1.5;
            default            -> 1.2;
        };
        double heightBonus = height > 170 ? (height - 170) * 5 : 0;
        double target = (base * multiplier) + heightBonus;
        return Math.min(4000, Math.max(1500, Math.round(target / 50.0) * 50));
    }

    public Map<String, Object> getWaterStatus(String userId) {
        User user   = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate today = LocalDate.now();
        double target   = calculateWaterTarget(user);
        WaterLog waterLog = waterRepo.findByUserIdAndLogDate(userId, today).orElse(null);
        double consumed = waterLog != null ? waterLog.getTotalMl()    : 0;
        double fromFood = waterLog != null ? waterLog.getFromFoodMl() : 0;
        int glasses     = waterLog != null ? waterLog.getGlassCount() : 0;
        return buildMap(target, consumed, fromFood, glasses);
    }

    /** Add water manually (glass click, custom ml, or negative to remove) */
    public Map<String, Object> addWater(String userId, double ml) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate today = LocalDate.now();
        double target   = calculateWaterTarget(user);
        WaterLog waterLog = getOrCreate(userId, today, target);

        waterLog.setTotalMl(Math.max(0, waterLog.getTotalMl() + ml));
        if (ml > 0) waterLog.setGlassCount(waterLog.getGlassCount() + 1);
        else        waterLog.setGlassCount(Math.max(0, waterLog.getGlassCount() - 1));
        waterLog.setUpdatedAt(LocalDateTime.now());
        waterRepo.save(waterLog);
        return buildMap(target, waterLog.getTotalMl(), waterLog.getFromFoodMl(), waterLog.getGlassCount());
    }

    /** Auto-add water from food log (called by FoodLogService) */
    public void addWaterFromFood(String userId, double waterMl) {
        if (waterMl <= 0) return;
        try {
            User user = userRepo.findById(userId).orElse(null);
            if (user == null) return;
            LocalDate today  = LocalDate.now();
            double target    = calculateWaterTarget(user);
            WaterLog waterLog = getOrCreate(userId, today, target);
            waterLog.setTotalMl(waterLog.getTotalMl() + waterMl);
            waterLog.setFromFoodMl(waterLog.getFromFoodMl() + waterMl);
            waterLog.setUpdatedAt(LocalDateTime.now());
            waterRepo.save(waterLog);
            log.info("Auto-added {}ml water from food for user {}", waterMl, userId);
        } catch (Exception e) {
            log.error("addWaterFromFood failed: {}", e.getMessage());
        }
    }

    private WaterLog getOrCreate(String userId, LocalDate date, double target) {
        return waterRepo.findByUserIdAndLogDate(userId, date)
                .orElse(WaterLog.builder()
                        .userId(userId).logDate(date)
                        .totalMl(0).glassCount(0).fromFoodMl(0)
                        .targetMl(target).updatedAt(LocalDateTime.now())
                        .build());
    }

    private Map<String, Object> buildMap(double target, double consumed, double fromFood, int glasses) {
        int pct = (int) Math.min(100, Math.round((consumed / target) * 100));
        Map<String, Object> res = new HashMap<>();
        res.put("targetMl",     target);
        res.put("consumedMl",   consumed);
        res.put("fromFoodMl",   fromFood);
        res.put("drinkMl",      Math.max(0, consumed - fromFood));
        res.put("glassCount",   glasses);
        res.put("remainingMl",  Math.max(0, target - consumed));
        res.put("percentage",   pct);
        res.put("glassSize",    250);
        res.put("targetGlasses",(int) Math.ceil(target / 250));
        res.put("date",         LocalDate.now().toString());
        return res;
    }
}

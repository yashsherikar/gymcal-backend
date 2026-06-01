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

    // Calculate daily water target in ml based on user profile
    public double calculateWaterTarget(User user) {
        double weight = user.getWeightKg();
        double height = user.getHeightCm();
        String gender = user.getGender() != null ? user.getGender().toUpperCase() : "MALE";
        String activity = user.getActivityLevel() != null ? user.getActivityLevel().toUpperCase() : "MODERATE";

        // Base: 35ml/kg (men), 31ml/kg (women)
        double base = gender.equals("FEMALE") ? weight * 31 : weight * 35;

        // Activity multiplier
        double multiplier = switch (activity) {
            case "SEDENTARY" -> 1.0;
            case "LIGHT"     -> 1.1;
            case "MODERATE"  -> 1.2;
            case "ACTIVE"    -> 1.35;
            case "VERY_ACTIVE" -> 1.5;
            default          -> 1.2;
        };

        // Height bonus (taller people need more)
        double heightBonus = height > 170 ? (height - 170) * 5 : 0;

        double target = (base * multiplier) + heightBonus;

        // Clamp between 1500ml and 4000ml
        return Math.min(4000, Math.max(1500, Math.round(target / 50.0) * 50));
    }

    public Map<String, Object> getWaterStatus(String userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate today = LocalDate.now();
        double target = calculateWaterTarget(user);
        WaterLog log = waterRepo.findByUserIdAndLogDate(userId, today).orElse(null);
        double consumed = log != null ? log.getTotalMl() : 0;
        int glasses = log != null ? log.getGlassCount() : 0;

        Map<String, Object> res = new HashMap<>();
        res.put("targetMl", target);
        res.put("consumedMl", consumed);
        res.put("glassCount", glasses);
        res.put("remainingMl", Math.max(0, target - consumed));
        res.put("percentage", Math.min(100, Math.round((consumed / target) * 100)));
        res.put("glassSize", 250); // ml per glass
        res.put("targetGlasses", (int) Math.ceil(target / 250));
        res.put("date", today.toString());
        return res;
    }

    public Map<String, Object> addWater(String userId, double ml) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate today = LocalDate.now();
        double target = calculateWaterTarget(user);

        WaterLog log = waterRepo.findByUserIdAndLogDate(userId, today)
                .orElse(WaterLog.builder().userId(userId).logDate(today).totalMl(0).glassCount(0).targetMl(target).build());

        log.setTotalMl(Math.max(0, log.getTotalMl() + ml));
        log.setGlassCount(ml > 0 ? log.getGlassCount() + 1 : Math.max(0, log.getGlassCount() - 1));
        log.setTargetMl(target);
        log.setUpdatedAt(LocalDateTime.now());
        waterRepo.save(log);

        Map<String, Object> res = new HashMap<>();
        res.put("targetMl", target);
        res.put("consumedMl", log.getTotalMl());
        res.put("glassCount", log.getGlassCount());
        res.put("remainingMl", Math.max(0, target - log.getTotalMl()));
        res.put("percentage", Math.min(100, Math.round((log.getTotalMl() / target) * 100)));
        res.put("glassSize", 250);
        res.put("targetGlasses", (int) Math.ceil(target / 250));
        return res;
    }
}

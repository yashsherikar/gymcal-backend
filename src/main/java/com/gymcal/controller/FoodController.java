package com.gymcal.controller;

import com.gymcal.dto.FoodDTOs;
import com.gymcal.service.FoodLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
public class FoodController {

	@Autowired
    private  FoodLogService foodLogService;

    /**
     * Search food nutrition via AI (preview before adding)
     */
    @PostMapping("/search")
    public ResponseEntity<?> searchFood(Authentication auth,
                                        @Valid @RequestBody FoodDTOs.FoodSearchRequest request) {
        try {
            String userId = (String) auth.getPrincipal();
            FoodDTOs.NutritionInfo info = foodLogService.searchFood(userId, request);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add food to daily log
     */
    @PostMapping("/log")
    public ResponseEntity<?> addFoodLog(Authentication auth,
                                        @Valid @RequestBody FoodDTOs.AddFoodLogRequest request) {
        try {
            String userId = (String) auth.getPrincipal();
            FoodDTOs.FoodLogResponse response = foodLogService.addFoodLog(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get daily summary (default: today)
     */
    @GetMapping("/daily")
    public ResponseEntity<?> getDailySummary(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            String userId = (String) auth.getPrincipal();
            FoodDTOs.DailySummary summary = foodLogService.getDailySummary(userId, date);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get weekly summary (last 7 days)
     */
    @GetMapping("/weekly")
    public ResponseEntity<?> getWeeklySummary(Authentication auth) {
        try {
            String userId = (String) auth.getPrincipal();
            List<FoodDTOs.DailySummary> summaries = foodLogService.getWeeklySummary(userId);
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a food log entry
     */
    @DeleteMapping("/log/{logId}")
    public ResponseEntity<?> deleteFoodLog(Authentication auth, @PathVariable String logId) {
        try {
            String userId = (String) auth.getPrincipal();
            foodLogService.deleteFoodLog(userId, logId);
            return ResponseEntity.ok(Map.of("message", "Food log deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

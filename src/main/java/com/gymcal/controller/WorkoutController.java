package com.gymcal.controller;

import com.gymcal.dto.WorkoutDTOs;
import com.gymcal.service.WorkoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/workout")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    @PostMapping("/generate")
    public ResponseEntity<?> generatePlan(Authentication auth, @RequestBody WorkoutDTOs.GeneratePlanRequest req) {
        try { return ResponseEntity.ok(workoutService.generatePlan((String) auth.getPrincipal(), req)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActivePlan(Authentication auth) {
        try {
            WorkoutDTOs.WorkoutPlanResponse plan = workoutService.getActivePlan((String) auth.getPrincipal());
            return plan != null ? ResponseEntity.ok(plan) : ResponseEntity.ok(Map.of("message", "No active plan"));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/regenerate")
    public ResponseEntity<?> regeneratePlan(Authentication auth) {
        try { return ResponseEntity.ok(workoutService.autoRegeneratePlan((String) auth.getPrincipal())); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }
}

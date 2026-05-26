package com.gymcal.controller;

import com.gymcal.dto.FoodDTOs;
import com.gymcal.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication auth) {
        try {
            String userId = (String) auth.getPrincipal();
            FoodDTOs.UserProfileResponse profile = userService.getProfile(userId);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/goal")
    public ResponseEntity<?> updateGoal(Authentication auth,
                                        @RequestBody FoodDTOs.UpdateGoalRequest request) {
        try {
            String userId = (String) auth.getPrincipal();
            FoodDTOs.UserProfileResponse updated = userService.updateGoal(userId, request);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

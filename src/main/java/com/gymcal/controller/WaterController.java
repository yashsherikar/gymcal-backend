package com.gymcal.controller;

import com.gymcal.service.WaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/water")
@RequiredArgsConstructor
public class WaterController {

    private final WaterService waterService;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication auth) {
        try { return ResponseEntity.ok(waterService.getWaterStatus((String) auth.getPrincipal())); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/add")
    public ResponseEntity<?> addWater(Authentication auth, @RequestBody Map<String, Object> body) {
        try {
            double ml = body.containsKey("ml") ? Double.parseDouble(body.get("ml").toString()) : 250;
            return ResponseEntity.ok(waterService.addWater((String) auth.getPrincipal(), ml));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }
}

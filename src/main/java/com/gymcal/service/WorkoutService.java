package com.gymcal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymcal.dto.WorkoutDTOs;
import com.gymcal.model.User;
import com.gymcal.model.WorkoutPlan;
import com.gymcal.repository.UserRepository;
import com.gymcal.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutPlanRepository workoutRepo;
    private final UserRepository userRepo;
    private final GeminiService geminiService;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkoutDTOs.WorkoutPlanResponse generatePlan(String userId, WorkoutDTOs.GeneratePlanRequest req) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        // Merge health conditions
        List<String> conditions = new ArrayList<>(req.getHealthConditions() != null ? req.getHealthConditions() : new ArrayList<>());
        if (user.getHealthConditions() != null)
            user.getHealthConditions().forEach(c -> { if (!conditions.contains(c)) conditions.add(c); });

        // Save conditions to user
        user.setHealthConditions(conditions);
        userRepo.save(user);

        String prompt = buildPrompt(user, req, conditions);
        String raw = geminiService.generateResponse(prompt);
        log.info("Workout plan raw length: {}", raw.length());

        WorkoutPlan plan = parsePlan(raw, user, conditions);
        plan.setUserId(userId);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setActive(true);

        // Deactivate previous
        workoutRepo.findByUserIdAndIsActiveTrue(userId).ifPresent(old -> {
            old.setActive(false); workoutRepo.save(old);
        });

        return toResponse(workoutRepo.save(plan));
    }

    public WorkoutDTOs.WorkoutPlanResponse getActivePlan(String userId) {
        return workoutRepo.findByUserIdAndIsActiveTrue(userId).map(this::toResponse).orElse(null);
    }

    private String buildPrompt(User user, WorkoutDTOs.GeneratePlanRequest req, List<String> conditions) {
        int days = req.getWorkoutDaysPerWeek() != null ? req.getWorkoutDaysPerWeek() : 4;
        String level  = req.getFitnessLevel() != null ? req.getFitnessLevel() : "INTERMEDIATE";
        String equip  = req.getEquipment()    != null ? String.join(", ", req.getEquipment()) : "NONE (bodyweight only)";
        String conds  = conditions.isEmpty() ? "None" : String.join(", ", conditions);
        String notes  = req.getAdditionalNotes() != null ? req.getAdditionalNotes() : "None";

        return String.format("""
You are a certified personal trainer and medical-aware fitness coach. Create a safe, effective weekly workout plan.

USER PROFILE:
- Name: %s | Age: %d | Gender: %s
- Weight: %.1f kg | Height: %.1f cm | BMI: %.1f (%s)
- Goal: %s | Activity: %s | Fitness Level: %s
- Workout days/week: %d | Equipment: %s
- Health Conditions: %s
- Notes: %s

MEDICAL SAFETY RULES (MUST FOLLOW):
- Heart disease/Hypertension: Low-intensity only, HR max 60%%, no heavy lifting, no inverted poses, mandatory 5min warmup/cooldown
- Asthma: Avoid cold air exercises, include rest intervals every 10min, no sprints
- Diabetes: Include post-workout glucose monitoring note, avoid fasting workouts
- Knee/joint issues: No high-impact (jumping, running), use low-impact alternatives (cycling, swimming, walking)
- Obesity (BMI>30): Low-impact cardio, pool exercises recommended, no joint stress
- None: Standard programming based on goal

GOAL-SPECIFIC RULES:
- WEIGHT_LOSS: 60%% cardio + 40%% strength, calorie deficit exercises, HIIT if no heart issues
- MUSCLE_GAIN: 70%% strength + 30%% cardio, progressive overload, compound movements
- MAINTAIN: Balanced 50/50
- RECOMPOSITION: Circuit training, full body 3x/week

Return ONLY valid JSON (no markdown, no explanation):
{
  "planName": "string",
  "difficultyLevel": "string",
  "generalAdvice": "2-3 personalized sentences",
  "safetyNotes": "specific safety notes based on health conditions",
  "estimatedWeeklyCaloriesBurned": number,
  "weeklyPlan": [
    {
      "day": "Monday",
      "focus": "Upper Body Strength",
      "isRestDay": false,
      "estimatedDuration": 45,
      "estimatedCaloriesBurned": 300,
      "exercises": [
        {
          "name": "Push-ups",
          "category": "Strength",
          "sets": "3",
          "reps": "10-12",
          "duration": "",
          "rest": "60 sec",
          "instructions": "Keep core tight, lower chest to floor",
          "modification": "Knee push-ups if needed"
        }
      ]
    }
  ]
}
Generate exactly 7 days (Monday-Sunday). %d days workout, rest on others. Each workout day must have 4-6 exercises.
""", user.getName(), user.getAge(), user.getGender(),
    user.getWeightKg(), user.getHeightCm(), user.getBmi(), user.getBmiCategory(),
    user.getGoal(), user.getActivityLevel(), level, days, equip, conds, notes, days);
    }

    private WorkoutPlan parsePlan(String raw, User user, List<String> conditions) {
        try {
            String cleaned = raw.trim();
            if (cleaned.contains("```"))
                cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
            int s = cleaned.indexOf('{'), e = cleaned.lastIndexOf('}');
            if (s >= 0 && e > s) cleaned = cleaned.substring(s, e + 1);
            JsonNode node = mapper.readTree(cleaned);

            List<WorkoutPlan.WorkoutDay> days = new ArrayList<>();
            for (JsonNode d : node.path("weeklyPlan")) {
                List<WorkoutPlan.Exercise> exList = new ArrayList<>();
                for (JsonNode ex : d.path("exercises")) {
                    exList.add(WorkoutPlan.Exercise.builder()
                            .name(ex.path("name").asText(""))
                            .category(ex.path("category").asText(""))
                            .sets(ex.path("sets").asText(""))
                            .reps(ex.path("reps").asText(""))
                            .duration(ex.path("duration").asText(""))
                            .rest(ex.path("rest").asText(""))
                            .instructions(ex.path("instructions").asText(""))
                            .modification(ex.path("modification").asText(""))
                            .build());
                }
                days.add(WorkoutPlan.WorkoutDay.builder()
                        .day(d.path("day").asText("")).focus(d.path("focus").asText(""))
                        .isRestDay(d.path("isRestDay").asBoolean(false))
                        .estimatedDuration(d.path("estimatedDuration").asInt(0))
                        .estimatedCaloriesBurned(d.path("estimatedCaloriesBurned").asInt(0))
                        .exercises(exList).build());
            }

            return WorkoutPlan.builder()
                    .planName(node.path("planName").asText("My Workout Plan"))
                    .goal(user.getGoal()).difficultyLevel(node.path("difficultyLevel").asText("Intermediate"))
                    .healthConditions(conditions).weeklyPlan(days)
                    .generalAdvice(node.path("generalAdvice").asText(""))
                    .safetyNotes(node.path("safetyNotes").asText(""))
                    .estimatedWeeklyCaloriesBurned(node.path("estimatedWeeklyCaloriesBurned").asInt(0))
                    .build();
        } catch (Exception e) {
            log.error("parsePlan failed: {}", e.getMessage());
            return WorkoutPlan.builder().planName("Workout Plan").goal(user.getGoal())
                    .healthConditions(conditions).weeklyPlan(new ArrayList<>())
                    .generalAdvice("Plan generation failed. Please try again.").build();
        }
    }

    private WorkoutDTOs.WorkoutPlanResponse toResponse(WorkoutPlan p) {
        return WorkoutDTOs.WorkoutPlanResponse.builder()
                .id(p.getId()).planName(p.getPlanName()).goal(p.getGoal())
                .difficultyLevel(p.getDifficultyLevel()).healthConditions(p.getHealthConditions())
                .weeklyPlan(p.getWeeklyPlan()).generalAdvice(p.getGeneralAdvice())
                .safetyNotes(p.getSafetyNotes())
                .estimatedWeeklyCaloriesBurned(p.getEstimatedWeeklyCaloriesBurned())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "")
                .isActive(p.isActive()).build();
    }
}

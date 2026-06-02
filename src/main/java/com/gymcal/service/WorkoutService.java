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

    // Weight change threshold to trigger auto-regeneration (in kg)
    private static final double WEIGHT_CHANGE_THRESHOLD = 2.0;

    public WorkoutDTOs.WorkoutPlanResponse generatePlan(String userId, WorkoutDTOs.GeneratePlanRequest req) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        List<String> conditions = new ArrayList<>(req.getHealthConditions() != null ? req.getHealthConditions() : new ArrayList<>());
        if (user.getHealthConditions() != null)
            user.getHealthConditions().forEach(c -> { if (!conditions.contains(c)) conditions.add(c); });
        user.setHealthConditions(conditions);
        userRepo.save(user);

        String raw = geminiService.generateResponse(buildPrompt(user, req, conditions));
        log.info("Workout plan raw length: {}", raw.length());

        WorkoutPlan plan = parsePlan(raw, user, req, conditions);
        plan.setUserId(userId);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setActive(true);
        plan.setGeneratedAtWeight(user.getWeightKg());

        // Save settings for auto-regeneration
        int days = req.getWorkoutDaysPerWeek() != null ? req.getWorkoutDaysPerWeek() : 4;
        plan.setWorkoutDaysPerWeek(days);
        plan.setFitnessLevel(req.getFitnessLevel() != null ? req.getFitnessLevel() : "INTERMEDIATE");
        plan.setEquipment(req.getEquipment() != null ? req.getEquipment() : List.of("NONE"));

        // Deactivate old plans
        workoutRepo.findByUserIdAndIsActiveTrue(userId).ifPresent(old -> { old.setActive(false); workoutRepo.save(old); });

        return toResponse(workoutRepo.save(plan));
    }

    /**
     * Get active plan — checks if weight has changed significantly.
     * Returns plan with weightChanged=true flag if regeneration is needed.
     */
    public WorkoutDTOs.WorkoutPlanResponse getActivePlan(String userId) {
        User user = userRepo.findById(userId).orElse(null);
        Optional<WorkoutPlan> planOpt = workoutRepo.findByUserIdAndIsActiveTrue(userId);
        if (planOpt.isEmpty()) return null;

        WorkoutPlan plan = planOpt.get();
        WorkoutDTOs.WorkoutPlanResponse resp = toResponse(plan);

        // Check weight change
        if (user != null && plan.getGeneratedAtWeight() > 0) {
            double weightChange = user.getWeightKg() - plan.getGeneratedAtWeight();
            double absChange = Math.abs(weightChange);

            if (absChange >= WEIGHT_CHANGE_THRESHOLD) {
                resp.setWeightChanged(true);
                resp.setWeightChangeDelta(weightChange);
                resp.setCurrentWeight(user.getWeightKg());
                resp.setPlanWeight(plan.getGeneratedAtWeight());

                // Determine if goal is closer or further based on goal type
                String goal = user.getGoal();
                boolean isPositive = false;
                if ("WEIGHT_LOSS".equals(goal) && weightChange < 0)      isPositive = true;
                else if ("MUSCLE_GAIN".equals(goal) && weightChange > 0) isPositive = true;
                resp.setWeightChangePositive(isPositive);
            }
        }
        return resp;
    }

    /**
     * Auto-regenerate plan with same settings after weight update
     */
    public WorkoutDTOs.WorkoutPlanResponse autoRegeneratePlan(String userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Optional<WorkoutPlan> oldPlan = workoutRepo.findByUserIdAndIsActiveTrue(userId);

        WorkoutDTOs.GeneratePlanRequest req = new WorkoutDTOs.GeneratePlanRequest();
        req.setHealthConditions(user.getHealthConditions());
        if (oldPlan.isPresent()) {
            req.setFitnessLevel(oldPlan.get().getFitnessLevel());
            req.setWorkoutDaysPerWeek(oldPlan.get().getWorkoutDaysPerWeek());
            req.setEquipment(oldPlan.get().getEquipment());
        } else {
            req.setFitnessLevel("INTERMEDIATE");
            req.setWorkoutDaysPerWeek(4);
            req.setEquipment(List.of("NONE"));
        }
        req.setAdditionalNotes("Updated plan due to weight change. Current weight: " + user.getWeightKg() + "kg");
        return generatePlan(userId, req);
    }

    private String buildPrompt(User user, WorkoutDTOs.GeneratePlanRequest req, List<String> conditions) {
        int days = req.getWorkoutDaysPerWeek() != null ? req.getWorkoutDaysPerWeek() : 4;
        String level = req.getFitnessLevel() != null ? req.getFitnessLevel() : "INTERMEDIATE";
        String equip = req.getEquipment() != null ? String.join(", ", req.getEquipment()) : "NONE";
        String conds = conditions.isEmpty() ? "None" : String.join(", ", conditions);
        String notes = req.getAdditionalNotes() != null ? req.getAdditionalNotes() : "None";

        // Calculate goal progress message
        String goalNote = "";
        if (req.getAdditionalNotes() != null && req.getAdditionalNotes().contains("weight change")) {
            goalNote = "Note: This is an updated plan due to weight change. Adjust intensity accordingly.";
        }

        return String.format("""
You are a certified personal trainer. Create a safe, effective weekly workout plan.

USER PROFILE:
- Name: %s | Age: %d | Gender: %s
- Current Weight: %.1f kg | Height: %.1f cm | BMI: %.1f (%s)
- Goal: %s | Activity: %s | Fitness Level: %s
- Workout days/week: %d | Equipment: %s
- Health Conditions: %s
- Notes: %s %s

MEDICAL SAFETY RULES:
- Heart disease/Hypertension: Low-intensity only, HR max 60%%, no heavy lifting, mandatory warmup/cooldown
- Asthma: Include rest intervals, no sprints, avoid cold exercises
- Diabetes: Include post-workout glucose note
- Knee/Joint pain: No high-impact, use low-impact alternatives
- Obesity (BMI>30): Low-impact cardio only, no joint stress

GOAL RULES:
- WEIGHT_LOSS: 60%% cardio + 40%% strength, calorie burning focus
- MUSCLE_GAIN: 70%% strength + 30%% cardio, progressive overload
- MAINTAIN: Balanced 50/50
- RECOMPOSITION: Circuit training, full body

Return ONLY valid JSON (no markdown):
{
  "planName": "string",
  "difficultyLevel": "string",
  "generalAdvice": "2-3 personalized sentences mentioning current weight and goal",
  "safetyNotes": "specific safety notes based on health conditions, empty string if none",
  "estimatedWeeklyCaloriesBurned": number,
  "weeklyPlan": [
    {
      "day": "Monday",
      "focus": "Upper Body Strength",
      "isRestDay": false,
      "estimatedDuration": 45,
      "estimatedCaloriesBurned": 350,
      "exercises": [
        {
          "name": "Push-ups",
          "category": "Strength",
          "sets": "3",
          "reps": "12-15",
          "duration": "",
          "rest": "60 sec",
          "instructions": "Keep core tight",
          "modification": "Knee push-ups if needed"
        }
      ]
    }
  ]
}
Generate exactly 7 days (Monday-Sunday). SUNDAY IS ALWAYS A MANDATORY REST DAY (isRestDay: true). Distribute %d workout days across Monday-Saturday only. Each workout day: 4-6 exercises.
""", user.getName(), user.getAge(), user.getGender(),
    user.getWeightKg(), user.getHeightCm(), user.getBmi(), user.getBmiCategory(),
    user.getGoal(), user.getActivityLevel(), level, days, equip, conds, notes, goalNote, days);
    }

    private WorkoutPlan parsePlan(String raw, User user, WorkoutDTOs.GeneratePlanRequest req, List<String> conditions) {
        try {
            String cleaned = raw.trim();
            if (cleaned.contains("```")) cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```","").trim();
            int s = cleaned.indexOf('{'), e = cleaned.lastIndexOf('}');
            if (s >= 0 && e > s) cleaned = cleaned.substring(s, e + 1);
            JsonNode node = mapper.readTree(cleaned);

            List<WorkoutPlan.WorkoutDay> days = new ArrayList<>();
            for (JsonNode d : node.path("weeklyPlan")) {
                List<WorkoutPlan.Exercise> exList = new ArrayList<>();
                for (JsonNode ex : d.path("exercises"))
                    exList.add(WorkoutPlan.Exercise.builder()
                            .name(ex.path("name").asText("")).category(ex.path("category").asText(""))
                            .sets(ex.path("sets").asText("")).reps(ex.path("reps").asText(""))
                            .duration(ex.path("duration").asText("")).rest(ex.path("rest").asText(""))
                            .instructions(ex.path("instructions").asText("")).modification(ex.path("modification").asText(""))
                            .build());
                String dayName = d.path("day").asText("");
                // Sunday is ALWAYS rest — no exceptions
                boolean isSunday = "Sunday".equalsIgnoreCase(dayName);
                boolean isRest = isSunday || d.path("isRestDay").asBoolean(false);
                days.add(WorkoutPlan.WorkoutDay.builder()
                        .day(dayName)
                        .focus(isSunday ? "Rest & Recovery (Sunday)" : d.path("focus").asText(""))
                        .isRestDay(isRest)
                        .estimatedDuration(isRest ? 0 : d.path("estimatedDuration").asInt(0))
                        .estimatedCaloriesBurned(isRest ? 0 : d.path("estimatedCaloriesBurned").asInt(0))
                        .exercises(isRest ? new ArrayList<>() : exList).build());
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
                    .generalAdvice("Generation failed. Please try again.").build();
        }
    }

    private WorkoutDTOs.WorkoutPlanResponse toResponse(WorkoutPlan p) {
        return WorkoutDTOs.WorkoutPlanResponse.builder()
                .id(p.getId()).planName(p.getPlanName()).goal(p.getGoal())
                .difficultyLevel(p.getDifficultyLevel()).healthConditions(p.getHealthConditions())
                .weeklyPlan(p.getWeeklyPlan()).generalAdvice(p.getGeneralAdvice())
                .safetyNotes(p.getSafetyNotes())
                .estimatedWeeklyCaloriesBurned(p.getEstimatedWeeklyCaloriesBurned())
                .generatedAtWeight(p.getGeneratedAtWeight())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "")
                .isActive(p.isActive()).build();
    }
}

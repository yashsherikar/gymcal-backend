package com.gymcal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymcal.dto.FoodDTOs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GeminiService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.1-8b-instant";

    @Value("${groq.api.key}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30)).build();

    @jakarta.annotation.PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("===== GROQ_API_KEY is NOT SET! =====");
        } else {
            log.info("===== GroqService ready. Key: {}... =====",
                apiKey.substring(0, Math.min(8, apiKey.length())));
        }
    }

    /**
     * Analyze food nutrition — supports both grams AND natural quantity
     * e.g. "2 rotis", "1 cup dal", "150g chicken"
     */
    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams,
                                                        Double quantityAmount, String quantityUnit) {
        // Resolve display string and effective grams
        String quantityDisplay;
        double effectiveGrams;

        if (quantityUnit != null && !quantityUnit.isBlank() &&
            !quantityUnit.equalsIgnoreCase("grams") && !quantityUnit.equalsIgnoreCase("g") &&
            quantityAmount != null && quantityAmount > 0) {
            // Natural quantity: "2 pieces", "1 cup"
            quantityDisplay = String.format("%s %s of %s",
                formatNum(quantityAmount), quantityUnit, foodName);
            effectiveGrams = 0; // AI will figure out grams
        } else {
            // Gram-based
            effectiveGrams = (quantityGrams != null && quantityGrams > 0) ? quantityGrams : 100.0;
            quantityDisplay = effectiveGrams + "g of " + foodName;
        }

        try {
            String raw = callGroqNutrition(foodName, effectiveGrams, quantityAmount, quantityUnit);
            log.info("Groq raw for [{}]: [{}]", foodName, raw);
            if (raw == null || raw.startsWith("ERROR:")) {
                return buildError(raw != null ? raw : "API call failed");
            }
            FoodDTOs.NutritionInfo info = parseNutrition(raw, foodName);
            if (info.isSuccess()) {
                info.setQuantityDisplay(quantityDisplay);
            }
            return info;
        } catch (Exception e) {
            log.error("analyzeFoodNutrition failed for [{}]", foodName, e);
            return buildError("Failed: " + e.getMessage());
        }
    }

    // Backward-compatible overload (grams only)
    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams) {
        return analyzeFoodNutrition(foodName, quantityGrams, null, null);
    }

    private String callGroqNutrition(String foodName, double grams,
                                      Double amount, String unit) {
        try {
            String quantityStr;
            if (unit != null && !unit.isBlank() && !unit.equalsIgnoreCase("grams")
                && amount != null && amount > 0) {
                quantityStr = formatNum(amount) + " " + unit;
            } else {
                quantityStr = (int) grams + "g";
            }

            String prompt =
                "Return ONLY a valid JSON object (no markdown, no extra text) with nutrition for: " +
                quantityStr + " of \"" + foodName + "\".\n" +
                "Include these EXACT keys:\n" +
                "- foodName (string): cleaned food name\n" +
                "- resolvedGrams (number): estimated weight in grams for the given quantity\n" +
                "- calories (number): total kcal\n" +
                "- proteinGrams (number)\n" +
                "- carbsGrams (number): total carbohydrates\n" +
                "- fatGrams (number): total fat\n" +
                "- fiberGrams (number)\n" +
                "- saturatedFatGrams (number): saturated fat portion\n" +
                "- sugarGrams (number): added/total sugars\n" +
                "- caloriePer100g (number): calories per 100g reference\n" +
                "- proteinPer100g (number): protein per 100g\n" +
                "- aiAnalysis (string): 1-sentence nutrition tip for gym/fitness context\n" +
                "- isHealthy (boolean): true if overall nutritious food\n\n" +
                "For Indian foods (dal, roti, rice, paneer, sabzi etc.), use standard cooked values. " +
                "For 'X pieces/rotis/cups' estimate weight accordingly (1 roti ≈ 40g, 1 cup cooked dal ≈ 200g). " +
                "Be accurate. Return only JSON.";

            String raw = callGroqRaw(prompt, 400);
            return raw;

        } catch (Exception e) {
            log.error("callGroqNutrition exception", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Generate weekly workout plan based on user profile
     */
    public FoodDTOs.WorkoutPlan generateWorkoutPlan(String goal, String activityLevel,
                                                     String gender, double weightKg,
                                                     double heightCm, int age) {
        try {
            String prompt = buildWorkoutPrompt(goal, activityLevel, gender, weightKg, heightCm, age);
            String raw = callGroqRaw(prompt, 2000);
            log.info("Workout plan raw length: {}", raw != null ? raw.length() : 0);
            if (raw == null || raw.startsWith("ERROR:")) {
                return buildDefaultWorkoutPlan(goal, activityLevel, gender);
            }
            return parseWorkoutPlan(raw, goal, activityLevel, gender);
        } catch (Exception e) {
            log.error("generateWorkoutPlan failed", e);
            return buildDefaultWorkoutPlan(goal, activityLevel, gender);
        }
    }

    private String buildWorkoutPrompt(String goal, String activity, String gender,
                                      double weightKg, double heightCm, int age) {
        String goalDesc = switch (goal.toUpperCase()) {
            case "WEIGHT_LOSS"   -> "fat loss and calorie burning";
            case "MUSCLE_GAIN"   -> "muscle building and hypertrophy";
            case "RECOMPOSITION" -> "body recomposition (lose fat, gain muscle)";
            default              -> "maintaining fitness and health";
        };
        String activityDesc = switch (activity.toUpperCase()) {
            case "SEDENTARY"   -> "beginner, mostly sedentary";
            case "LIGHT"       -> "lightly active, some exercise";
            case "MODERATE"    -> "moderately active, regular exercise";
            case "ACTIVE"      -> "very active, frequent training";
            case "VERY_ACTIVE" -> "athlete level, intense daily training";
            default -> "moderate";
        };

        return String.format(
            "Create a personalized 7-day weekly workout plan for a %s aged %d, weight %skg, height %scm. " +
            "Goal: %s. Activity level: %s. " +
            "Return ONLY a valid JSON object (no markdown) with this EXACT structure:\n" +
            "{\n" +
            "  \"planTitle\": \"string\",\n" +
            "  \"planDescription\": \"string\",\n" +
            "  \"weeklyNote\": \"string\",\n" +
            "  \"generalTips\": [\"tip1\", \"tip2\", \"tip3\"],\n" +
            "  \"days\": [\n" +
            "    {\n" +
            "      \"day\": \"Monday\",\n" +
            "      \"type\": \"TRAINING\",\n" +
            "      \"focus\": \"Chest & Triceps\",\n" +
            "      \"intensity\": \"High\",\n" +
            "      \"estimatedMinutes\": 45,\n" +
            "      \"estimatedCaloriesBurn\": 300,\n" +
            "      \"notes\": \"string\",\n" +
            "      \"exercises\": [\n" +
            "        {\n" +
            "          \"name\": \"Push-ups\",\n" +
            "          \"sets\": \"3 sets\",\n" +
            "          \"reps\": \"12-15 reps\",\n" +
            "          \"rest\": \"60 sec\",\n" +
            "          \"muscleGroup\": \"Chest\",\n" +
            "          \"difficulty\": \"Beginner\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "Include all 7 days (Monday–Sunday). Mix training, rest, and active recovery. " +
            "For REST days, exercises array can be empty. " +
            "Tailor exercise difficulty and volume to the activity level. " +
            "For WEIGHT_LOSS include more cardio. For MUSCLE_GAIN include more resistance training. " +
            "Return only valid JSON.",
            gender.toLowerCase(), age, (int)weightKg, (int)heightCm,
            goalDesc, activityDesc
        );
    }

    private String callGroqRaw(String prompt, int maxTokens) {
        try {
            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);

            ArrayNode messages = mapper.createArrayNode();
            messages.add(message);

            ObjectNode reqBody = mapper.createObjectNode();
            reqBody.put("model", MODEL);
            reqBody.set("messages", messages);
            reqBody.put("temperature", 0.3);
            reqBody.put("max_tokens", maxTokens);
            reqBody.set("response_format", mapper.createObjectNode().put("type", "json_object"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reqBody)))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Groq HTTP status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                log.error("Groq HTTP {}: {}", response.statusCode(), response.body());
                return "ERROR: HTTP " + response.statusCode();
            }

            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText("").trim();

        } catch (Exception e) {
            log.error("callGroqRaw exception", e);
            return "ERROR: " + e.getMessage();
        }
    }

    private FoodDTOs.NutritionInfo parseNutrition(String raw, String foodName) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode node = mapper.readTree(cleaned);

            double calories  = node.path("calories").asDouble(0);
            double protein   = node.path("proteinGrams").asDouble(0);
            double carbs     = node.path("carbsGrams").asDouble(0);
            double fat       = node.path("fatGrams").asDouble(0);
            double fiber     = node.path("fiberGrams").asDouble(0);
            double satFat    = node.path("saturatedFatGrams").asDouble(fat * 0.3);
            double sugar     = node.path("sugarGrams").asDouble(carbs * 0.2);
            double resolvedG = node.path("resolvedGrams").asDouble(100);

            if (calories == 0 && protein == 0 && carbs == 0 && fat == 0) {
                return buildError("AI returned zero values. Try again.");
            }

            // ─── Calculate Good vs Bad calories ───
            // Good = protein calories + fiber-related (filling, not fattening)
            double proteinCals  = protein * 4;
            double fiberBonus   = fiber * 2;          // fiber calories are metabolized less
            double satFatCals   = satFat * 9;
            double sugarCals    = sugar * 4;
            double goodCals     = Math.min(proteinCals + fiberBonus, calories * 0.7);
            double badCals      = Math.min(satFatCals + sugarCals, calories * 0.6);
            double neutralCals  = Math.max(0, calories - goodCals - badCals);

            // Normalize so they add up to total
            double total = goodCals + badCals + neutralCals;
            if (total > 0 && Math.abs(total - calories) > 1) {
                double ratio = calories / total;
                goodCals    = round1(goodCals * ratio);
                badCals     = round1(badCals * ratio);
                neutralCals = round1(calories - goodCals - badCals);
            }

            // Quality rating
            double badPct = calories > 0 ? (badCals / calories) * 100 : 0;
            double goodPct = calories > 0 ? (goodCals / calories) * 100 : 0;
            String quality, qualityReason;
            if (goodPct >= 50 && badPct <= 20) {
                quality = "EXCELLENT";
                qualityReason = "High protein, low saturated fat — great for your goals!";
            } else if (goodPct >= 35 && badPct <= 35) {
                quality = "GOOD";
                qualityReason = "Balanced nutrition — good regular food choice.";
            } else if (badPct <= 50) {
                quality = "MODERATE";
                qualityReason = "Some empty calories — okay in moderation.";
            } else {
                quality = "POOR";
                qualityReason = "High in saturated fat / sugar — limit intake.";
            }

            return FoodDTOs.NutritionInfo.builder()
                    .foodName(node.path("foodName").asText(foodName))
                    .quantityGrams(round1(resolvedG))
                    .calories(round1(calories))
                    .proteinGrams(round1(protein))
                    .carbsGrams(round1(carbs))
                    .fatGrams(round1(fat))
                    .fiberGrams(round1(fiber))
                    .goodCalories(round1(goodCals))
                    .badCalories(round1(badCals))
                    .neutralCalories(round1(neutralCals))
                    .calorieQuality(quality)
                    .qualityReason(qualityReason)
                    .caloriePer100g(round1(node.path("caloriePer100g").asDouble(calories)))
                    .proteinPer100g(round1(node.path("proteinPer100g").asDouble(protein)))
                    .aiAnalysis(node.path("aiAnalysis").asText(""))
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("parseNutrition failed: [{}]", raw, e);
            return buildError("Could not parse AI response.");
        }
    }

    private FoodDTOs.WorkoutPlan parseWorkoutPlan(String raw, String goal,
                                                   String activity, String gender) {
        try {
            String cleaned = cleanJson(raw);
            JsonNode root = mapper.readTree(cleaned);

            List<FoodDTOs.WorkoutDay> days = new ArrayList<>();
            JsonNode daysNode = root.path("days");
            if (daysNode.isArray()) {
                for (JsonNode dayNode : daysNode) {
                    List<FoodDTOs.Exercise> exercises = new ArrayList<>();
                    JsonNode exNode = dayNode.path("exercises");
                    if (exNode.isArray()) {
                        for (JsonNode ex : exNode) {
                            exercises.add(FoodDTOs.Exercise.builder()
                                    .name(ex.path("name").asText(""))
                                    .sets(ex.path("sets").asText("3 sets"))
                                    .reps(ex.path("reps").asText("10 reps"))
                                    .rest(ex.path("rest").asText("60 sec"))
                                    .muscleGroup(ex.path("muscleGroup").asText(""))
                                    .difficulty(ex.path("difficulty").asText("Intermediate"))
                                    .build());
                        }
                    }

                    days.add(FoodDTOs.WorkoutDay.builder()
                            .day(dayNode.path("day").asText())
                            .type(dayNode.path("type").asText("TRAINING"))
                            .focus(dayNode.path("focus").asText("Full Body"))
                            .intensity(dayNode.path("intensity").asText("Medium"))
                            .estimatedMinutes(dayNode.path("estimatedMinutes").asInt(45))
                            .estimatedCaloriesBurn(dayNode.path("estimatedCaloriesBurn").asInt(250))
                            .notes(dayNode.path("notes").asText(""))
                            .exercises(exercises)
                            .build());
                }
            }

            List<String> tips = new ArrayList<>();
            JsonNode tipsNode = root.path("generalTips");
            if (tipsNode.isArray()) {
                for (JsonNode t : tipsNode) tips.add(t.asText());
            }

            return FoodDTOs.WorkoutPlan.builder()
                    .goal(goal)
                    .activityLevel(activity)
                    .gender(gender)
                    .planTitle(root.path("planTitle").asText("Your Weekly Workout Plan"))
                    .planDescription(root.path("planDescription").asText(""))
                    .weeklyNote(root.path("weeklyNote").asText(""))
                    .generalTips(tips)
                    .days(days)
                    .build();

        } catch (Exception e) {
            log.error("parseWorkoutPlan failed", e);
            return buildDefaultWorkoutPlan(goal, activity, gender);
        }
    }

    private FoodDTOs.WorkoutPlan buildDefaultWorkoutPlan(String goal, String activity, String gender) {
        // Fallback plan if AI fails
        List<FoodDTOs.Exercise> pushExercises = List.of(
            FoodDTOs.Exercise.builder().name("Push-ups").sets("3 sets").reps("12-15 reps")
                .rest("60 sec").muscleGroup("Chest").difficulty("Beginner").build(),
            FoodDTOs.Exercise.builder().name("Dumbbell Shoulder Press").sets("3 sets").reps("10-12 reps")
                .rest("60 sec").muscleGroup("Shoulders").difficulty("Intermediate").build(),
            FoodDTOs.Exercise.builder().name("Tricep Dips").sets("3 sets").reps("10-12 reps")
                .rest("60 sec").muscleGroup("Triceps").difficulty("Beginner").build()
        );
        List<FoodDTOs.Exercise> pullExercises = List.of(
            FoodDTOs.Exercise.builder().name("Pull-ups / Lat Pulldown").sets("3 sets").reps("8-10 reps")
                .rest("90 sec").muscleGroup("Back").difficulty("Intermediate").build(),
            FoodDTOs.Exercise.builder().name("Barbell Row").sets("3 sets").reps("10 reps")
                .rest("90 sec").muscleGroup("Back").difficulty("Intermediate").build(),
            FoodDTOs.Exercise.builder().name("Bicep Curls").sets("3 sets").reps("12 reps")
                .rest("60 sec").muscleGroup("Biceps").difficulty("Beginner").build()
        );
        List<FoodDTOs.Exercise> legExercises = List.of(
            FoodDTOs.Exercise.builder().name("Squats").sets("4 sets").reps("10-12 reps")
                .rest("90 sec").muscleGroup("Quads").difficulty("Intermediate").build(),
            FoodDTOs.Exercise.builder().name("Romanian Deadlift").sets("3 sets").reps("10 reps")
                .rest("90 sec").muscleGroup("Hamstrings").difficulty("Intermediate").build(),
            FoodDTOs.Exercise.builder().name("Calf Raises").sets("3 sets").reps("15 reps")
                .rest("45 sec").muscleGroup("Calves").difficulty("Beginner").build()
        );
        List<FoodDTOs.Exercise> cardioExercises = List.of(
            FoodDTOs.Exercise.builder().name("Treadmill / Running").sets("1 session").reps("30 min")
                .rest("N/A").muscleGroup("Cardio").difficulty("Intermediate").build(),
            FoodDTOs.Exercise.builder().name("Jump Rope").sets("5 rounds").reps("2 min")
                .rest("30 sec").muscleGroup("Cardio").difficulty("Intermediate").build()
        );

        return FoodDTOs.WorkoutPlan.builder()
            .goal(goal).activityLevel(activity).gender(gender)
            .planTitle("7-Day Fitness Plan")
            .planDescription("A balanced weekly workout plan tailored to your goals")
            .weeklyNote("Rest when needed. Consistency > intensity.")
            .generalTips(List.of("Stay hydrated — drink 3L water daily",
                "Sleep 7-8 hours for recovery", "Track your workouts to see progress"))
            .days(List.of(
                FoodDTOs.WorkoutDay.builder().day("Monday").type("TRAINING").focus("Push (Chest, Shoulders, Triceps)")
                    .intensity("High").estimatedMinutes(50).estimatedCaloriesBurn(320).exercises(pushExercises)
                    .notes("Start with compound movements").build(),
                FoodDTOs.WorkoutDay.builder().day("Tuesday").type("TRAINING").focus("Pull (Back & Biceps)")
                    .intensity("High").estimatedMinutes(50).estimatedCaloriesBurn(280).exercises(pullExercises)
                    .notes("Focus on mind-muscle connection").build(),
                FoodDTOs.WorkoutDay.builder().day("Wednesday").type("TRAINING").focus("Legs & Core")
                    .intensity("High").estimatedMinutes(55).estimatedCaloriesBurn(380).exercises(legExercises)
                    .notes("Don't skip leg day!").build(),
                FoodDTOs.WorkoutDay.builder().day("Thursday").type("ACTIVE_RECOVERY").focus("Cardio & Stretching")
                    .intensity("Low").estimatedMinutes(35).estimatedCaloriesBurn(200).exercises(cardioExercises)
                    .notes("Light jog or yoga — keep it easy").build(),
                FoodDTOs.WorkoutDay.builder().day("Friday").type("TRAINING").focus("Push (Repeat)")
                    .intensity("Medium").estimatedMinutes(45).estimatedCaloriesBurn(280).exercises(pushExercises)
                    .notes("Slightly lower weight, focus on form").build(),
                FoodDTOs.WorkoutDay.builder().day("Saturday").type("TRAINING").focus("Full Body")
                    .intensity("Medium").estimatedMinutes(50).estimatedCaloriesBurn(300).exercises(legExercises)
                    .notes("Compound movements only").build(),
                FoodDTOs.WorkoutDay.builder().day("Sunday").type("REST").focus("Complete Rest")
                    .intensity("Rest").estimatedMinutes(0).estimatedCaloriesBurn(0).exercises(List.of())
                    .notes("Recovery is when you grow. Rest well.").build()
            )).build();
    }

    private String cleanJson(String raw) {
        String cleaned = raw.trim();
        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1);
        return cleaned;
    }

    private String formatNum(Double d) {
        if (d == null) return "100";
        if (d == Math.floor(d)) return String.valueOf(d.intValue());
        return String.valueOf(d);
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private FoodDTOs.NutritionInfo buildError(String msg) {
        return FoodDTOs.NutritionInfo.builder().success(false).errorMessage(msg).build();
    }
}

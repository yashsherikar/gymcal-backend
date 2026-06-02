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

@Slf4j
@Service
public class GeminiService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    // Fast model for food nutrition
    private static final String FOOD_MODEL    = "llama-3.1-8b-instant";
    // Powerful model for workout plan generation
    private static final String WORKOUT_MODEL = "llama-3.3-70b-versatile";

    @Value("${groq.api.key}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(45)).build();

    @jakarta.annotation.PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-groq-key-here"))
            log.error("===== GROQ_API_KEY is NOT SET! =====");
        else
            log.info("===== GeminiService(Groq) ready. Key: {}... =====",
                apiKey.substring(0, Math.min(8, apiKey.length())));
    }

    // ── FOOD NUTRITION ─────────────────────────────────────────
    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityAmount, String quantityUnit) {
        double amount = (quantityAmount != null && quantityAmount > 0) ? quantityAmount : 100.0;
        String unit   = (quantityUnit  != null && !quantityUnit.isBlank()) ? quantityUnit.toLowerCase() : "grams";
        try {
            String raw = callGroq(buildFoodPrompt(foodName, amount, unit), 500, true, FOOD_MODEL);
            log.info("Food AI raw [{}] [{} {}]: [{}]", foodName, amount, unit, raw);
            if (raw == null || raw.startsWith("ERROR:")) return buildFoodError(raw != null ? raw : "API failed");
            return parseFoodNutrition(raw, foodName, amount, unit);
        } catch (Exception e) {
            log.error("analyzeFoodNutrition failed", e);
            return buildFoodError("Failed: " + e.getMessage());
        }
    }

    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams) {
        return analyzeFoodNutrition(foodName, quantityGrams, "grams");
    }

    private String buildFoodPrompt(String foodName, double amount, String unit) {
        return String.format(
            "Nutrition expert. Return ONLY JSON for %.1f %s of \"%s\". " +
            "Convert to grams/ml first (1 cup≈240ml, 1 bowl≈300ml, 1 piece banana≈120g, 1 tbsp≈15ml, 1 can soda≈355ml). " +
            "IMPORTANT for waterContentMl: beverages (cola, juice, soda, tea, coffee, milk, energy drinks, water) " +
            "have waterContentMl equal to their volume in ml. " +
            "Diet Coke 330ml → waterContentMl=330. Orange juice 1 cup → waterContentMl=240. " +
            "Fruits: watermelon 200g → waterContentMl=184. Dry foods (roti, rice, nuts) → waterContentMl is small (5-20). " +
            "JSON keys: foodName, quantityGrams, calories, proteinGrams, carbsGrams, fatGrams, fiberGrams, " +
            "waterContentMl, goodCalories(proteinGrams*4+fiberGrams*2), badCalories(fatGrams*9), " +
            "carbCalories(carbsGrams*4), calQuality(Excellent/Good/Moderate/Poor), aiAnalysis(one sentence). No markdown.",
            amount, unit, foodName);
    }

    private FoodDTOs.NutritionInfo parseFoodNutrition(String raw, String foodName, double amount, String unit) {
        try {
            String cleaned = extractJson(raw);
            JsonNode node = mapper.readTree(cleaned);

            double calories = node.path("calories").asDouble(0);
            double protein  = node.path("proteinGrams").asDouble(0);
            double carbs    = node.path("carbsGrams").asDouble(0);
            double fat      = node.path("fatGrams").asDouble(0);
            double fiber    = node.path("fiberGrams").asDouble(0);
            double qGrams   = node.path("quantityGrams").asDouble(amount);
            double waterMl  = node.path("waterContentMl").asDouble(0);

            if (calories == 0 && protein == 0 && carbs == 0 && fat == 0)
                return buildFoodError("AI returned zero values. Try again.");

            double goodCal = node.path("goodCalories").asDouble(r1(protein * 4 + fiber * 2));
            double badCal  = node.path("badCalories").asDouble(r1(fat * 9));
            double carbCal = node.path("carbCalories").asDouble(r1(carbs * 4));
            String quality = node.path("calQuality").asText(calcQuality(goodCal, badCal));

            return FoodDTOs.NutritionInfo.builder()
                    .foodName(node.path("foodName").asText(foodName))
                    .quantityAmount(amount).quantityUnit(unit).quantityGrams(r1(qGrams))
                    .calories(r1(calories)).proteinGrams(r1(protein)).carbsGrams(r1(carbs))
                    .fatGrams(r1(fat)).fiberGrams(r1(fiber)).waterContentMl(r1(waterMl))
                    .goodCalories(r1(goodCal)).badCalories(r1(badCal)).carbCalories(r1(carbCal))
                    .calQuality(quality).aiAnalysis(node.path("aiAnalysis").asText(""))
                    .success(true).build();
        } catch (Exception e) {
            log.error("parseFoodNutrition failed: {}", raw, e);
            return buildFoodError("Could not parse AI response.");
        }
    }

    // ── WORKOUT PLAN (uses bigger model) ───────────────────────
    public String generateResponse(String prompt) {
        return callGroq(prompt, 4000, false, WORKOUT_MODEL);
    }

    // ── CORE GROQ CALL ─────────────────────────────────────────
    private String callGroq(String prompt, int maxTokens, boolean jsonMode, String model) {
        try {
            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            ArrayNode messages = mapper.createArrayNode();
            messages.add(message);

            ObjectNode reqBody = mapper.createObjectNode();
            reqBody.put("model", model);
            reqBody.set("messages", messages);
            reqBody.put("temperature", 0.2);
            reqBody.put("max_tokens", maxTokens);
            if (jsonMode) {
                ObjectNode fmt = mapper.createObjectNode();
                fmt.put("type", "json_object");
                reqBody.set("response_format", fmt);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reqBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Groq HTTP {} status: {}", model, response.statusCode());

            if (response.statusCode() != 200) {
                log.error("Groq HTTP {}: {}", response.statusCode(), response.body());
                return "ERROR: HTTP " + response.statusCode();
            }

            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText("").trim();
        } catch (Exception e) {
            log.error("callGroq failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ── HELPERS ────────────────────────────────────────────────
    private String extractJson(String raw) {
        String c = raw.trim();
        if (c.contains("```")) c = c.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
        int s = c.indexOf('{'), e = c.lastIndexOf('}');
        return (s >= 0 && e > s) ? c.substring(s, e + 1) : c;
    }
    private String calcQuality(double good, double bad) {
        if (good > bad * 2)    return "Excellent";
        if (good > bad)        return "Good";
        if (good > bad * 0.5)  return "Moderate";
        return "Poor";
    }
    private double r1(double v) { return Math.round(v * 10.0) / 10.0; }
    private FoodDTOs.NutritionInfo buildFoodError(String msg) {
        return FoodDTOs.NutritionInfo.builder().success(false).errorMessage(msg).build();
    }
}

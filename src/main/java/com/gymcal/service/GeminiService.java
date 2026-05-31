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
    private static final String MODEL    = "llama-3.1-8b-instant";

    @Value("${groq.api.key}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30)).build();

    @jakarta.annotation.PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-groq-key-here")) {
            log.error("===== GROQ_API_KEY is NOT SET! =====");
        } else {
            log.info("===== GeminiService(Groq) ready. Key: {}... =====",
                apiKey.substring(0, Math.min(8, apiKey.length())));
        }
    }

    // ── FOOD NUTRITION ──────────────────────────────────────────
    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityAmount, String quantityUnit) {
        // Defaults
        double amount = (quantityAmount != null && quantityAmount > 0) ? quantityAmount : 100.0;
        String unit   = (quantityUnit != null && !quantityUnit.isBlank()) ? quantityUnit.toLowerCase() : "grams";

        try {
            String raw = callFoodApi(foodName, amount, unit);
            log.info("Food AI raw [{}] [{}{}]: [{}]", foodName, amount, unit, raw);
            if (raw == null || raw.startsWith("ERROR:")) return buildFoodError(raw != null ? raw : "API failed");
            return parseFoodNutrition(raw, foodName, amount, unit);
        } catch (Exception e) {
            log.error("analyzeFoodNutrition failed", e);
            return buildFoodError("Failed: " + e.getMessage());
        }
    }

    // Legacy overload for backward compatibility
    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams) {
        return analyzeFoodNutrition(foodName, quantityGrams, "grams");
    }

    private String callFoodApi(String foodName, double amount, String unit) {
        try {
            String prompt = String.format(
                "You are a nutrition expert. Return ONLY a JSON object for: %.1f %s of %s. " +
                "First convert quantity to grams (e.g. 1 cup rice ≈ 195g, 1 piece banana ≈ 120g, 1 bowl dal ≈ 250g, 1 tbsp oil ≈ 14g). " +
                "Then calculate exact nutrition for that gram amount. " +
                "JSON keys: foodName(string), quantityGrams(number), calories(number), proteinGrams(number), " +
                "carbsGrams(number), fatGrams(number), fiberGrams(number), " +
                "goodCalories(number: proteinGrams*4 + fiberGrams*2), " +
                "badCalories(number: fatGrams*9), " +
                "carbCalories(number: carbsGrams*4), " +
                "calQuality(string: 'Excellent' if goodCal>badCal*2, 'Good' if goodCal>badCal, 'Moderate' if similar, 'Poor' if badCal dominant), " +
                "aiAnalysis(one sentence about this food for fitness). No markdown, just JSON.",
                amount, unit, foodName);

            return callGroq(prompt, 400, true);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private FoodDTOs.NutritionInfo parseFoodNutrition(String raw, String foodName, double amount, String unit) {
        try {
            String cleaned = extractJson(raw);
            JsonNode node = mapper.readTree(cleaned);

            double calories  = node.path("calories").asDouble(0);
            double protein   = node.path("proteinGrams").asDouble(0);
            double carbs     = node.path("carbsGrams").asDouble(0);
            double fat       = node.path("fatGrams").asDouble(0);
            double fiber     = node.path("fiberGrams").asDouble(0);
            double qGrams    = node.path("quantityGrams").asDouble(amount);

            if (calories == 0 && protein == 0 && carbs == 0 && fat == 0)
                return buildFoodError("AI returned zero values. Try again.");

            // Calculate good/bad if AI didn't return them
            double goodCal = node.path("goodCalories").asDouble(r1(protein * 4 + fiber * 2));
            double badCal  = node.path("badCalories").asDouble(r1(fat * 9));
            double carbCal = node.path("carbCalories").asDouble(r1(carbs * 4));
            String quality = node.path("calQuality").asText(calcQuality(goodCal, badCal));

            return FoodDTOs.NutritionInfo.builder()
                    .foodName(node.path("foodName").asText(foodName))
                    .quantityAmount(amount)
                    .quantityUnit(unit)
                    .quantityGrams(r1(qGrams))
                    .calories(r1(calories))
                    .proteinGrams(r1(protein))
                    .carbsGrams(r1(carbs))
                    .fatGrams(r1(fat))
                    .fiberGrams(r1(fiber))
                    .goodCalories(r1(goodCal))
                    .badCalories(r1(badCal))
                    .carbCalories(r1(carbCal))
                    .calQuality(quality)
                    .aiAnalysis(node.path("aiAnalysis").asText(""))
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("parseFoodNutrition failed: {}", raw, e);
            return buildFoodError("Could not parse AI response.");
        }
    }

    // ── WORKOUT PLAN ─────────────────────────────────────────────
    public String generateResponse(String prompt) {
        return callGroq(prompt, 2000, false);
    }

    // ── CORE GROQ CALL ───────────────────────────────────────────
    private String callGroq(String prompt, int maxTokens, boolean jsonMode) {
        try {
            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            ArrayNode messages = mapper.createArrayNode();
            messages.add(message);

            ObjectNode reqBody = mapper.createObjectNode();
            reqBody.put("model", MODEL);
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
            log.error("callGroq failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ── HELPERS ──────────────────────────────────────────────────
    private String extractJson(String raw) {
        String cleaned = raw.trim();
        if (cleaned.contains("```"))
            cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
        int s = cleaned.indexOf('{'), e = cleaned.lastIndexOf('}');
        return (s >= 0 && e > s) ? cleaned.substring(s, e + 1) : cleaned;
    }

    private String calcQuality(double good, double bad) {
        if (good > bad * 2)  return "Excellent";
        if (good > bad)      return "Good";
        if (good > bad * 0.5) return "Moderate";
        return "Poor";
    }

    private double r1(double v) { return Math.round(v * 10.0) / 10.0; }

    private FoodDTOs.NutritionInfo buildFoodError(String msg) {
        return FoodDTOs.NutritionInfo.builder().success(false).errorMessage(msg).build();
    }
}

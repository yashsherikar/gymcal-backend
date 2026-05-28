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

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();

    @jakarta.annotation.PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-gemini-key-here")) {
            log.error("===== GEMINI_API_KEY is NOT SET or invalid! Food search will fail. =====");
        } else {
            log.info("===== GeminiService ready. Key prefix: {} =====",
                apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
        }
    }
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams) {
        double quantity = (quantityGrams != null && quantityGrams > 0) ? quantityGrams : 100.0;
        try {
            String raw = callGemini(foodName, quantity);
            log.info("Gemini raw text for [{}]: [{}]", foodName, raw);

            if (raw == null || raw.startsWith("ERROR:")) {
                return buildError(raw != null ? raw : "Gemini API failed");
            }
            return parseNutrition(raw, foodName, quantity);
        } catch (Exception e) {
            log.error("analyzeFoodNutrition exception for [{}]", foodName, e);
            return buildError("Failed: " + e.getMessage());
        }
    }

    private String callGemini(String foodName, double quantity) {
        try {
            // Clear, unambiguous prompt — no template with 0s that Gemini copies literally
            String prompt =
                "You are a nutrition database. Return ONLY a JSON object with the exact nutrition " +
                "values for " + (int)quantity + " grams of " + foodName + ". " +
                "The JSON must have these exact keys with REAL numeric values (not zeros): " +
                "foodName (string), calories (number), proteinGrams (number), " +
                "carbsGrams (number), fatGrams (number), fiberGrams (number), " +
                "aiAnalysis (one sentence string). " +
                "Do NOT include markdown, backticks, or any text outside the JSON object.";

            // Build request using ObjectMapper — zero risk of escaping bugs
            ObjectNode part = mapper.createObjectNode();
            part.put("text", prompt);

            ArrayNode parts = mapper.createArrayNode();
            parts.add(part);

            ObjectNode contentObj = mapper.createObjectNode();
            contentObj.set("parts", parts);

            ArrayNode contents = mapper.createArrayNode();
            contents.add(contentObj);

            ObjectNode genConfig = mapper.createObjectNode();
            genConfig.put("temperature", 0.2);
            genConfig.put("maxOutputTokens", 400);

            ObjectNode reqBody = mapper.createObjectNode();
            reqBody.set("contents", contents);
            reqBody.set("generationConfig", genConfig);

            String requestJson = mapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Gemini HTTP status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                String errBody = response.body();
                log.error("Gemini HTTP {}: {}", response.statusCode(), errBody);
                // Return full error so frontend can display it
                return "ERROR: HTTP " + response.statusCode() + " — " + errBody;
            }

            JsonNode root = mapper.readTree(response.body());

            // API-level error in body
            if (root.has("error")) {
                String msg = root.path("error").path("message").asText("unknown");
                log.error("Gemini API error: {}", msg);
                return "ERROR: " + msg;
            }

            JsonNode candidates = root.path("candidates");
            if (candidates.isMissingNode() || candidates.isEmpty()) {
                log.error("No candidates: {}", response.body());
                return "ERROR: No candidates in response";
            }

            JsonNode firstCandidate = candidates.get(0);

            // Blocked by safety filter
            String finishReason = firstCandidate.path("finishReason").asText("");
            if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
                return "ERROR: Response blocked by safety filter";
            }

            JsonNode partsNode = firstCandidate.path("content").path("parts");
            if (partsNode.isMissingNode() || partsNode.isEmpty()) {
                return "ERROR: Empty parts in response";
            }

            return partsNode.get(0).path("text").asText("").trim();

        } catch (Exception e) {
            log.error("callGemini exception", e);
            return "ERROR: " + e.getMessage();
        }
    }

    private FoodDTOs.NutritionInfo parseNutrition(String raw, String foodName, double quantity) {
        try {
            String cleaned = raw.trim();

            // Remove markdown fences
            if (cleaned.contains("```")) {
                cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\s*", "")
                                 .replace("```", "").trim();
            }

            // Extract first complete JSON object { ... }
            int start = cleaned.indexOf('{');
            int end   = cleaned.lastIndexOf('}');
            if (start < 0 || end <= start) {
                log.error("No JSON in response: [{}]", raw);
                return buildError("AI returned unexpected format. Try again.");
            }
            cleaned = cleaned.substring(start, end + 1);

            JsonNode node = mapper.readTree(cleaned);

            double calories = node.path("calories").asDouble(0);
            double protein  = node.path("proteinGrams").asDouble(0);
            double carbs    = node.path("carbsGrams").asDouble(0);
            double fat      = node.path("fatGrams").asDouble(0);
            double fiber    = node.path("fiberGrams").asDouble(0);

            // Sanity check — if all macros are 0, something went wrong
            if (calories == 0 && protein == 0 && carbs == 0 && fat == 0) {
                log.warn("All macros are 0 for [{}], raw=[{}]", foodName, raw);
                return buildError("AI returned zero values. Please try again.");
            }

            return FoodDTOs.NutritionInfo.builder()
                    .foodName(node.path("foodName").asText(foodName))
                    .quantityGrams(quantity)
                    .calories(Math.round(calories * 10.0) / 10.0)
                    .proteinGrams(Math.round(protein * 10.0) / 10.0)
                    .carbsGrams(Math.round(carbs * 10.0) / 10.0)
                    .fatGrams(Math.round(fat * 10.0) / 10.0)
                    .fiberGrams(Math.round(fiber * 10.0) / 10.0)
                    .aiAnalysis(node.path("aiAnalysis").asText(""))
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("parseNutrition failed for raw=[{}]", raw, e);
            return buildError("Could not parse AI response. Try again.");
        }
    }

    private FoodDTOs.NutritionInfo buildError(String msg) {
        return FoodDTOs.NutritionInfo.builder()
                .success(false)
                .errorMessage(msg)
                .build();
    }
}

package com.gymcal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    // Use gemini-1.5-flash — free tier, fast, supports JSON output
    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Analyze food nutrition — called by FoodLogService
     */
    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams) {
        double quantity = (quantityGrams != null && quantityGrams > 0) ? quantityGrams : 100.0;

        String prompt = String.format(
            "You are a nutritionist. Analyze the nutrition for %.0f grams of: %s\n\n" +
            "Respond ONLY with a valid JSON object. No markdown, no explanation, no code fences.\n" +
            "Use EXACTLY this format with realistic numeric values:\n" +
            "{\"foodName\":\"%s\",\"calories\":0,\"proteinGrams\":0,\"carbsGrams\":0,\"fatGrams\":0,\"fiberGrams\":0,\"aiAnalysis\":\"brief 1-line description\"}",
            quantity, foodName, foodName
        );

        try {
            String raw = callGemini(prompt);
            if (raw == null || raw.startsWith("ERROR:")) {
                return buildError(raw != null ? raw : "Gemini API failed");
            }
            return parseNutritionJson(raw, foodName, quantity);
        } catch (Exception e) {
            log.error("analyzeFoodNutrition error", e);
            return buildError("Failed to analyze: " + e.getMessage());
        }
    }

    /**
     * Call Gemini API and return raw text response
     */
    private String callGemini(String prompt) {
        try {
            // Escape prompt for JSON string
            String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

            String requestBody = String.format("""
                {
                  "contents": [{"parts": [{"text": "%s"}]}],
                  "generationConfig": {
                    "temperature": 0.1,
                    "maxOutputTokens": 512
                  }
                }
                """, escapedPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("Gemini response status: {}", response.statusCode());
            log.debug("Gemini response body: {}", response.body());

            if (response.statusCode() != 200) {
                log.error("Gemini API error {}: {}", response.statusCode(), response.body());
                return "ERROR: Gemini API returned " + response.statusCode();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isMissingNode() || candidates.isEmpty()) {
                log.error("No candidates in Gemini response: {}", response.body());
                return "ERROR: No response from Gemini";
            }

            String text = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText("");

            return text.trim();

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Parse Gemini's JSON response into NutritionInfo
     */
    private FoodDTOs.NutritionInfo parseNutritionJson(String raw, String foodName, double quantity) {
        try {
            // Strip any markdown fences Gemini might add despite instructions
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
            }

            // Extract JSON object if wrapped in extra text
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            JsonNode node = objectMapper.readTree(cleaned);

            return FoodDTOs.NutritionInfo.builder()
                    .foodName(node.path("foodName").asText(foodName))
                    .quantityGrams(quantity)
                    .calories(node.path("calories").asDouble(0))
                    .proteinGrams(node.path("proteinGrams").asDouble(0))
                    .carbsGrams(node.path("carbsGrams").asDouble(0))
                    .fatGrams(node.path("fatGrams").asDouble(0))
                    .fiberGrams(node.path("fiberGrams").asDouble(0))
                    .aiAnalysis(node.path("aiAnalysis").asText(""))
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON: [{}]", raw, e);
            return buildError("Could not parse nutrition data. Raw: " + raw.substring(0, Math.min(raw.length(), 100)));
        }
    }

    private FoodDTOs.NutritionInfo buildError(String msg) {
        return FoodDTOs.NutritionInfo.builder()
                .success(false)
                .errorMessage(msg)
                .build();
    }
}

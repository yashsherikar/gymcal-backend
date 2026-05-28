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

    // Groq — 100% free, no credit card, 14,400 requests/day
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
            log.info("===== GroqService ready (via GeminiService). Key: {}... =====",
                apiKey.substring(0, Math.min(8, apiKey.length())));
        }
    }

    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams) {
        double quantity = (quantityGrams != null && quantityGrams > 0) ? quantityGrams : 100.0;
        try {
            String raw = callGroq(foodName, quantity);
            log.info("Groq raw for [{}]: [{}]", foodName, raw);
            if (raw == null || raw.startsWith("ERROR:")) {
                return buildError(raw != null ? raw : "API call failed");
            }
            return parseNutrition(raw, foodName, quantity);
        } catch (Exception e) {
            log.error("analyzeFoodNutrition failed for [{}]", foodName, e);
            return buildError("Failed: " + e.getMessage());
        }
    }

    private String callGroq(String foodName, double quantity) {
        try {
            String prompt =
                "Return ONLY a valid JSON object (no markdown, no extra text) with nutrition for " +
                (int) quantity + "g of " + foodName + ". " +
                "Keys: foodName (string), calories (number), proteinGrams (number), " +
                "carbsGrams (number), fatGrams (number), fiberGrams (number), aiAnalysis (string).";

            // Build OpenAI-compatible request
            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);

            ArrayNode messages = mapper.createArrayNode();
            messages.add(message);

            ObjectNode reqBody = mapper.createObjectNode();
            reqBody.put("model", MODEL);
            reqBody.set("messages", messages);
            reqBody.put("temperature", 0.1);
            reqBody.put("max_tokens", 300);
            reqBody.put("response_format", mapper.createObjectNode().put("type", "json_object"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reqBody)))
                    .timeout(Duration.ofSeconds(30))
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
            log.error("callGroq exception", e);
            return "ERROR: " + e.getMessage();
        }
    }

    private FoodDTOs.NutritionInfo parseNutrition(String raw, String foodName, double quantity) {
        try {
            String cleaned = raw.trim();
            if (cleaned.contains("```")) {
                cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
            }
            int start = cleaned.indexOf('{');
            int end   = cleaned.lastIndexOf('}');
            if (start < 0 || end <= start) {
                log.error("No JSON found: [{}]", raw);
                return buildError("Unexpected AI response format.");
            }
            cleaned = cleaned.substring(start, end + 1);
            JsonNode node = mapper.readTree(cleaned);

            double calories = node.path("calories").asDouble(0);
            double protein  = node.path("proteinGrams").asDouble(0);
            double carbs    = node.path("carbsGrams").asDouble(0);
            double fat      = node.path("fatGrams").asDouble(0);
            double fiber    = node.path("fiberGrams").asDouble(0);

            if (calories == 0 && protein == 0 && carbs == 0 && fat == 0) {
                return buildError("AI returned zero values. Try again.");
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
            log.error("parseNutrition failed: [{}]", raw, e);
            return buildError("Could not parse AI response.");
        }
    }

    public String generateResponse(String prompt) {
        try {
            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            ArrayNode messages = mapper.createArrayNode();
            messages.add(message);
            ObjectNode reqBody = mapper.createObjectNode();
            reqBody.put("model", MODEL);
            reqBody.set("messages", messages);
            reqBody.put("temperature", 0.7);
            reqBody.put("max_tokens", 500);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reqBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText("No response");
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private FoodDTOs.NutritionInfo buildError(String msg) {
        return FoodDTOs.NutritionInfo.builder().success(false).errorMessage(msg).build();
    }
}

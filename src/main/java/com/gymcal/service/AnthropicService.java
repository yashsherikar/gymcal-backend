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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AnthropicService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${anthropic.api.model:claude-sonnet-4-20250514}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, double quantityGrams) {
        try {
            String prompt = buildPrompt(foodName, quantityGrams);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseResponse(response.body(), foodName, quantityGrams);
            } else {
                log.error("Anthropic API error: {} - {}", response.statusCode(), response.body());
                return buildError("AI service unavailable. Please try again.");
            }
        } catch (Exception e) {
            log.error("Error calling Anthropic API", e);
            return buildError("Failed to analyze food: " + e.getMessage());
        }
    }

    private String buildPrompt(String foodName, double quantityGrams) {
        return String.format("""
                You are a professional nutritionist. Analyze the nutritional content.
                Food: %s
                Quantity: %.0f grams
                
                Respond ONLY with valid JSON (no markdown, no explanation):
                {"foodName":"standardized name","quantityGrams":%.0f,"calories":0,"proteinGrams":0,"carbsGrams":0,"fatGrams":0,"fiberGrams":0,"summary":"1-line summary"}
                
                All numbers must be realistic and rounded to 1 decimal place.
                """, foodName, quantityGrams, quantityGrams);
    }

    private FoodDTOs.NutritionInfo parseResponse(String body, String foodName, double qty) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("content").get(0).path("text").asText();
            content = content.trim().replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode d = objectMapper.readTree(content);
            return FoodDTOs.NutritionInfo.builder()
                    .foodName(d.path("foodName").asText(foodName))
                    .quantityGrams(qty)
                    .calories(d.path("calories").asDouble())
                    .proteinGrams(d.path("proteinGrams").asDouble())
                    .carbsGrams(d.path("carbsGrams").asDouble())
                    .fatGrams(d.path("fatGrams").asDouble())
                    .fiberGrams(d.path("fiberGrams").asDouble())
                    .aiAnalysis(d.path("summary").asText())
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Parse error: {}", body, e);
            return buildError("Could not parse AI response. Try again.");
        }
    }

    private FoodDTOs.NutritionInfo buildError(String msg) {
        return FoodDTOs.NutritionInfo.builder().success(false).errorMessage(msg).build();
    }
}

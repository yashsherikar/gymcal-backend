package com.gymcal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymcal.dto.FoodDTOs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String generateResponse(String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

            String requestBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "%s" }
                  ]
                }
              ]
            }
            """.formatted(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode jsonNode = objectMapper.readTree(response.body());

            return jsonNode
                    .get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating response";
        }
    }

    public FoodDTOs.NutritionInfo analyzeFoodNutrition(String foodName, Double quantityGrams) {
        try {
            double quantity = quantityGrams != null ? quantityGrams : 100.0;
            String prompt = String.format(
                "Analyze the nutrition for %.1f grams of %s. " +
                "Respond ONLY with a valid JSON object (no markdown, no extra text) in this exact format: " +
                "{\"calories\": 0, \"proteinGrams\": 0, \"carbsGrams\": 0, " +
                "\"fatGrams\": 0, \"fiberGrams\": 0, \"aiAnalysis\": \"brief description\"}",
                quantity, foodName
            );

            String raw = generateResponse(prompt).trim();

            // Strip markdown code fences if Gemini adds them
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            }

            JsonNode node = objectMapper.readTree(raw);

            return FoodDTOs.NutritionInfo.builder()
                    .foodName(foodName)
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
            e.printStackTrace();
            return FoodDTOs.NutritionInfo.builder()
                    .foodName(foodName)
                    .quantityGrams(quantityGrams != null ? quantityGrams : 100.0)
                    .success(false)
                    .errorMessage("Failed to analyze nutrition: " + e.getMessage())
                    .build();
        }
    }
}

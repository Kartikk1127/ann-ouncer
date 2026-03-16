package com.kartikey.vllmbot.analyzer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kartikey.vllmbot.models.AnalyzedUpdate;
import com.kartikey.vllmbot.models.Update;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Analyzes updates using Claude API in batches
 */
public class ClaudeAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ClaudeAnalyzer.class);
    
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final Gson gson;
    
    public ClaudeAnalyzer(String apiKey, String model, int maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.gson = new Gson();
        
        // Increased timeouts for large batch analysis
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)      // 5 minutes for large batches
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Analyze a batch of updates using Claude
     */
    public List<AnalyzedUpdate> analyzeBatch(List<Update> updates) {
        if (updates.isEmpty()) {
            log.info("No updates to analyze");
            return new ArrayList<>();
        }
        
        log.info("Analyzing batch of {} updates with Claude", updates.size());
        
        String prompt = buildBatchPrompt(updates);
        String response = callClaudeAPI(prompt);
        
        return parseAnalysisResponse(response, updates);
    }
    
    private String buildBatchPrompt(List<Update> updates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are analyzing tech updates for relevance to vector search, LLMs, distributed systems, and ML infrastructure.\n\n");
        prompt.append("For each update below, provide:\n");
        prompt.append("1. Relevance score (1-10): How relevant is this to someone building vector search systems, LLMs, or distributed databases from scratch?\n");
        prompt.append("2. Categories (array): Pick from: vector-search, llm-infra, ml-systems, hardware, market-intel\n");
        prompt.append("3. Skills (array): Extract key technical skills mentioned (e.g., HNSW, quantization, CUDA, Raft, etc.)\n");
        prompt.append("4. Summary (string): One sentence summary focusing on what's new or important\n\n");
        prompt.append("Respond ONLY with a JSON array, one object per update, in the same order.\n\n");
        prompt.append("Updates to analyze:\n\n");
        
        for (int i = 0; i < updates.size(); i++) {
            Update update = updates.get(i);
            prompt.append(String.format("[%d] Title: %s\n", i, update.getTitle()));
            prompt.append(String.format("Source: %s\n", update.getSource()));
            prompt.append(String.format("URL: %s\n", update.getUrl()));
            
            String content = update.getContent();
            if (content != null && content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            prompt.append(String.format("Content: %s\n\n", content));
        }
        
        return prompt.toString();
    }
    
    private String callClaudeAPI(String prompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", maxTokens);
        
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);
        
        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        Request request = new Request.Builder()
                .url(ANTHROPIC_API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Claude API returned {}: {}", response.code(), response.message());
                if (response.body() != null) {
                    log.error("Response body: {}", response.body().string());
                }
                return "[]";
            }
            
            String responseBody = response.body().string();
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray content = responseJson.getAsJsonArray("content");
            
            if (content != null && content.size() > 0) {
                JsonObject contentBlock = content.get(0).getAsJsonObject();
                return contentBlock.get("text").getAsString();
            }
            
            return "[]";
            
        } catch (IOException e) {
            log.error("Failed to call Claude API", e);
            return "[]";
        }
    }
    
    private List<AnalyzedUpdate> parseAnalysisResponse(String response, List<Update> originalUpdates) {
        List<AnalyzedUpdate> analyzed = new ArrayList<>();
        
        try {
            // Extract JSON from response (might have markdown code blocks)
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();
            
            JsonArray results = gson.fromJson(jsonStr, JsonArray.class);
            
            for (int i = 0; i < results.size() && i < originalUpdates.size(); i++) {
                JsonObject result = results.get(i).getAsJsonObject();
                Update original = originalUpdates.get(i);
                
                int score = result.has("relevanceScore") ? result.get("relevanceScore").getAsInt() : 
                           (result.has("relevance_score") ? result.get("relevance_score").getAsInt() : 5);
                
                List<String> categories = new ArrayList<>();
                if (result.has("categories")) {
                    JsonArray catArray = result.getAsJsonArray("categories");
                    catArray.forEach(cat -> categories.add(cat.getAsString()));
                }
                
                List<String> skills = new ArrayList<>();
                if (result.has("skills")) {
                    JsonArray skillArray = result.getAsJsonArray("skills");
                    skillArray.forEach(skill -> skills.add(skill.getAsString()));
                }
                
                String summary = result.has("summary") ? result.get("summary").getAsString() : 
                                original.getTitle();
                
                AnalyzedUpdate analyzedUpdate = new AnalyzedUpdate(
                        original, score, categories, skills, summary
                );
                
                // Only include updates with score >= 5
                if (score >= 5) {
                    analyzed.add(analyzedUpdate);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to parse Claude analysis response", e);
            log.debug("Response was: {}", response);
        }
        
        log.info("Successfully analyzed {} updates (filtered to score >= 5)", analyzed.size());
        return analyzed;
    }
}

package com.kartikey.vllmbot.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages state persistence using JSON files on disk
 */
public class StateManager {
    private static final Logger log = LoggerFactory.getLogger(StateManager.class);
    
    private final String stateDir;
    private final Gson gson;
    
    private final Map<String, Instant> processedItems = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> userFeedback = new ConcurrentHashMap<>();
    private final Map<String, Instant> sourceState = new ConcurrentHashMap<>();
    
    public StateManager(String stateDir) {
        this.stateDir = stateDir;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        
        // Ensure state directory exists
        new File(stateDir).mkdirs();
        
        // Load existing state
        loadState();
    }
    
    private void loadState() {
        loadProcessedItems();
        loadUserFeedback();
        loadSourceState();
    }
    
    private void loadProcessedItems() {
        File file = new File(stateDir, "processed_items.json");
        if (!file.exists()) {
            log.info("No processed items file found, starting fresh");
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            
            if (loaded != null) {
                loaded.forEach((key, value) -> 
                    processedItems.put(key, Instant.parse(value))
                );
                log.info("Loaded {} processed items", processedItems.size());
            }
        } catch (IOException e) {
            log.error("Failed to load processed items", e);
        }
    }
    
    private void loadUserFeedback() {
        File file = new File(stateDir, "user_feedback.json");
        if (!file.exists()) {
            log.info("No user feedback file found, starting fresh");
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Map<String, Object>>>(){}.getType();
            Map<String, Map<String, Object>> loaded = gson.fromJson(reader, type);
            
            if (loaded != null) {
                userFeedback.putAll(loaded);
                log.info("Loaded {} user feedback entries", userFeedback.size());
            }
        } catch (IOException e) {
            log.error("Failed to load user feedback", e);
        }
    }
    
    private void loadSourceState() {
        File file = new File(stateDir, "source_state.json");
        if (!file.exists()) {
            log.info("No source state file found, starting fresh");
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            
            if (loaded != null) {
                loaded.forEach((key, value) -> 
                    sourceState.put(key, Instant.parse(value))
                );
                log.info("Loaded state for {} sources", sourceState.size());
            }
        } catch (IOException e) {
            log.error("Failed to load source state", e);
        }
    }
    
    public boolean isProcessed(String id) {
        return processedItems.containsKey(id);
    }
    
    public void markProcessed(String id, Instant timestamp) {
        processedItems.put(id, timestamp);
        saveProcessedItems();
    }
    
    public void recordFeedback(String messageId, String choice) {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("choice", choice);
        feedback.put("timestamp", Instant.now().toString());
        userFeedback.put(messageId, feedback);
        saveUserFeedback();
    }
    
    public void updateSourceState(String sourceName, Instant lastFetch) {
        sourceState.put(sourceName, lastFetch);
        saveSourceState();
    }
    
    public Instant getSourceLastFetch(String sourceName) {
        return sourceState.get(sourceName);
    }
    
    public Map<String, Instant> getRecentProcessedItems(Instant since) {
        Map<String, Instant> recent = new HashMap<>();
        processedItems.forEach((id, timestamp) -> {
            if (timestamp.isAfter(since)) {
                recent.put(id, timestamp);
            }
        });
        return recent;
    }
    
    private void saveProcessedItems() {
        File file = new File(stateDir, "processed_items.json");
        try (FileWriter writer = new FileWriter(file)) {
            Map<String, String> toSave = new HashMap<>();
            processedItems.forEach((key, value) -> 
                toSave.put(key, value.toString())
            );
            gson.toJson(toSave, writer);
        } catch (IOException e) {
            log.error("Failed to save processed items", e);
        }
    }
    
    private void saveUserFeedback() {
        File file = new File(stateDir, "user_feedback.json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(userFeedback, writer);
        } catch (IOException e) {
            log.error("Failed to save user feedback", e);
        }
    }
    
    private void saveSourceState() {
        File file = new File(stateDir, "source_state.json");
        try (FileWriter writer = new FileWriter(file)) {
            Map<String, String> toSave = new HashMap<>();
            sourceState.forEach((key, value) -> 
                toSave.put(key, value.toString())
            );
            gson.toJson(toSave, writer);
        } catch (IOException e) {
            log.error("Failed to save source state", e);
        }
    }
    
    public void cleanOldEntries(int daysToKeep) {
        Instant cutoff = Instant.now().minusSeconds(daysToKeep * 24 * 60 * 60);
        
        processedItems.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        saveProcessedItems();
        
        log.info("Cleaned old entries before {}", cutoff);
    }
}

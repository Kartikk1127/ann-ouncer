package com.kartikey.vllmbot.processor;

import com.kartikey.vllmbot.models.Update;
import com.kartikey.vllmbot.monitors.SourceMonitor;
import com.kartikey.vllmbot.state.StateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Coordinates all source monitors and manages the update queue
 */
public class BatchProcessor {
    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);
    
    private final List<SourceMonitor> monitors;
    private final StateManager stateManager;
    private final ExecutorService executorService;
    private final Queue<Update> updateQueue;
    private final int replayHours;
    
    public BatchProcessor(List<SourceMonitor> monitors, StateManager stateManager, 
                         int threadPoolSize, int replayHours) {
        this.monitors = monitors;
        this.stateManager = stateManager;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.updateQueue = new ConcurrentLinkedQueue<>();
        this.replayHours = replayHours;
    }
    
    /**
     * On startup, replay any items from the last 24 hours that might have been missed
     */
    public void replayRecentItems() {
        Instant replaySince = Instant.now().minusSeconds(replayHours * 3600);
        Map<String, Instant> recentItems = stateManager.getRecentProcessedItems(replaySince);
        
        log.info("Found {} items from last {} hours to potentially replay", recentItems.size(), replayHours);
        
        // For now, we just log this - actual replay would require storing full Update objects
        // In production, you'd store the full update in state and replay it
    }
    
    /**
     * Fetch updates from all monitors concurrently
     */
    public List<Update> fetchAllUpdates() {
        List<Future<List<Update>>> futures = new ArrayList<>();
        
        for (SourceMonitor monitor : monitors) {
            Future<List<Update>> future = executorService.submit(() -> {
                try {
                    log.info("Fetching updates from {}", monitor.getSourceName());
                    List<Update> updates = monitor.fetchUpdates();
                    stateManager.updateSourceState(monitor.getSourceName(), Instant.now());
                    return updates;
                } catch (Exception e) {
                    log.error("Error fetching from {}", monitor.getSourceName(), e);
                    return Collections.emptyList();
                }
            });
            futures.add(future);
        }
        
        List<Update> allUpdates = new ArrayList<>();
        for (Future<List<Update>> future : futures) {
            try {
                allUpdates.addAll(future.get(5, TimeUnit.MINUTES));
            } catch (Exception e) {
                log.error("Failed to get monitor results", e);
            }
        }
        
        return deduplicateAndFilter(allUpdates);
    }
    
    /**
     * Deduplicate updates and filter out already processed items
     */
    private List<Update> deduplicateAndFilter(List<Update> updates) {
        Map<String, Update> uniqueUpdates = new LinkedHashMap<>();
        
        for (Update update : updates) {
            // Skip if already processed
            if (stateManager.isProcessed(update.getId())) {
                continue;
            }
            
            // Deduplicate by ID (later occurrence wins)
            uniqueUpdates.put(update.getId(), update);
        }
        
        List<Update> result = new ArrayList<>(uniqueUpdates.values());
        log.info("After deduplication and filtering: {} new updates", result.size());
        return result;
    }
    
    /**
     * Add updates to the queue for batch processing
     */
    public void enqueueUpdates(List<Update> updates) {
        updateQueue.addAll(updates);
        log.info("Enqueued {} updates, queue size now: {}", updates.size(), updateQueue.size());
    }
    
    /**
     * Get all queued updates and clear the queue
     */
    public List<Update> drainQueue() {
        List<Update> updates = new ArrayList<>(updateQueue);
        updateQueue.clear();
        log.info("Drained {} updates from queue", updates.size());
        return updates;
    }
    
    /**
     * Mark updates as processed
     */
    public void markProcessed(List<Update> updates) {
        for (Update update : updates) {
            stateManager.markProcessed(update.getId(), update.getTimestamp());
        }
    }
    
    public int getQueueSize() {
        return updateQueue.size();
    }
    
    public void shutdown() {
        log.info("Shutting down batch processor");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

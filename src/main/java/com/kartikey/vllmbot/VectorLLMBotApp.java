package com.kartikey.vllmbot;

import com.kartikey.vllmbot.analyzer.ClaudeAnalyzer;
import com.kartikey.vllmbot.models.AnalyzedUpdate;
import com.kartikey.vllmbot.models.Update;
import com.kartikey.vllmbot.monitors.*;
import com.kartikey.vllmbot.processor.BatchProcessor;
import com.kartikey.vllmbot.publisher.SlackPublisher;
import com.kartikey.vllmbot.state.StateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application class for the Vector LLM Intelligence Bot
 */
public class VectorLLMBotApp {
    private static final Logger log = LoggerFactory.getLogger(VectorLLMBotApp.class);
    
    private final Properties config;
    private final StateManager stateManager;
    private final BatchProcessor batchProcessor;
    private final ClaudeAnalyzer claudeAnalyzer;
    private final SlackPublisher slackPublisher;
    private final ScheduledExecutorService scheduler;
    
    public VectorLLMBotApp(String configPath) throws IOException {
        log.info("Initializing Vector LLM Bot...");
        
        // Load configuration
        this.config = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            config.load(fis);
        }
        
        // Initialize components
        this.stateManager = new StateManager(config.getProperty("state.dir", "./state"));
        this.batchProcessor = createBatchProcessor();
        this.claudeAnalyzer = createClaudeAnalyzer();
        this.slackPublisher = createSlackPublisher();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        log.info("Vector LLM Bot initialized successfully");
    }
    
    private BatchProcessor createBatchProcessor() {
        List<SourceMonitor> monitors = new ArrayList<>();
        
        // Add arXiv monitor
        String arxivKeywords = config.getProperty("keywords.vector") + "," +
                              config.getProperty("keywords.llm") + "," +
                              config.getProperty("keywords.ml");
        monitors.add(new ArxivMonitor(arxivKeywords));
        
        // Add GitHub monitor
        String githubRepos = config.getProperty("github.repos");
        String githubToken = config.getProperty("github.token");
        monitors.add(new GitHubMonitor(githubRepos, githubToken));
        
        // Add HackerNews monitor
        String hnKeywords = config.getProperty("keywords.vector") + "," +
                           config.getProperty("keywords.llm");
        monitors.add(new HackerNewsMonitor(hnKeywords));
        
        // Add Reddit monitor
        String subreddits = config.getProperty("reddit.subreddits");
        monitors.add(new RedditMonitor(subreddits));
        
        int threadPoolSize = Integer.parseInt(config.getProperty("monitor.thread.pool.size", "5"));
        int replayHours = Integer.parseInt(config.getProperty("monitor.replay.hours", "24"));
        
        return new BatchProcessor(monitors, stateManager, threadPoolSize, replayHours);
    }
    
    private ClaudeAnalyzer createClaudeAnalyzer() {
        String apiKey = config.getProperty("anthropic.api.key");
        String model = config.getProperty("anthropic.model", "claude-sonnet-4-20250514");
        int maxTokens = Integer.parseInt(config.getProperty("anthropic.max.tokens", "4096"));
        
        return new ClaudeAnalyzer(apiKey, model, maxTokens);
    }
    
    private SlackPublisher createSlackPublisher() {
        String botToken = config.getProperty("slack.bot.token");
        String techChannel = config.getProperty("slack.channel.tech");
        String marketChannel = config.getProperty("slack.channel.market");
        
        return new SlackPublisher(botToken, techChannel, marketChannel, stateManager);
    }
    
    public void start() {
        log.info("Starting Vector LLM Bot...");
        
        // Replay recent items on startup
        batchProcessor.replayRecentItems();
        
        // Schedule hourly collection
        int pollIntervalHours = Integer.parseInt(config.getProperty("monitor.poll.interval.hours", "1"));
        scheduler.scheduleAtFixedRate(
                this::collectUpdates,
                0,
                pollIntervalHours,
                TimeUnit.HOURS
        );
        
        // Schedule daily batch analysis at configured hour (default 21:00 ET)
        int batchHour = Integer.parseInt(config.getProperty("monitor.batch.hour", "21"));
        scheduleDailyBatchAnalysis(batchHour);
        
        // Schedule weekly cleanup
        scheduler.scheduleAtFixedRate(
                () -> stateManager.cleanOldEntries(30),
                1,
                7,
                TimeUnit.DAYS
        );
        
        log.info("Vector LLM Bot is running");
        log.info("- Hourly collection every {} hours", pollIntervalHours);
        log.info("- Daily batch analysis at {}:00 ET", batchHour);
        
        // Keep the application running
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    private void scheduleDailyBatchAnalysis(int targetHour) {
        ZoneId etZone = ZoneId.of("America/New_York");
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        
        ZonedDateTime nowET = ZonedDateTime.now(etZone);
        ZonedDateTime nextRunET = nowET.withHour(targetHour).withMinute(0).withSecond(0);
        
        if (nowET.compareTo(nextRunET) > 0) {
            nextRunET = nextRunET.plusDays(1);
        }
        
        // Convert to IST for logging
        ZonedDateTime nextRunIST = nextRunET.withZoneSameInstant(istZone);
        
        long initialDelay = nextRunET.toEpochSecond() - nowET.toEpochSecond();
        
        scheduler.scheduleAtFixedRate(
                this::analyzeAndPublish,
                initialDelay,
                24 * 60 * 60, // Daily
                TimeUnit.SECONDS
        );
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║ BATCH ANALYSIS SCHEDULED                                       ║");
        log.info("╠════════════════════════════════════════════════════════════════╣");
        log.info("║ ET Time:  {} ║", nextRunET.format(formatter));
        log.info("║ IST Time: {} ║", nextRunIST.format(formatter));
        log.info("║ Runs in:  {} minutes                                      ║", initialDelay / 60);
        log.info("╚════════════════════════════════════════════════════════════════╝");
    }
    
    private void collectUpdates() {
        try {
            log.info("Starting hourly collection...");
            List<Update> updates = batchProcessor.fetchAllUpdates();
            batchProcessor.enqueueUpdates(updates);
            log.info("Collection complete. Queue size: {}", batchProcessor.getQueueSize());
        } catch (Exception e) {
            log.error("Error during collection", e);
        }
    }
    
    private void analyzeAndPublish() {
        try {
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║ STARTING BATCH ANALYSIS & PUBLISHING                          ║");
            log.info("╚════════════════════════════════════════════════════════════════╝");
            
            // Drain the queue
            List<Update> updates = batchProcessor.drainQueue();
            
            if (updates.isEmpty()) {
                log.info("No updates to analyze");
                return;
            }
            
            log.info("Analyzing {} updates with Claude...", updates.size());
            
            // Analyze with Claude
            List<AnalyzedUpdate> analyzed = claudeAnalyzer.analyzeBatch(updates);
            
            // Mark as processed
            batchProcessor.markProcessed(updates);
            
            log.info("Publishing {} relevant updates to Slack...", analyzed.size());
            
            // Publish to Slack
            slackPublisher.publishUpdates(analyzed);
            
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║ BATCH COMPLETE                                                 ║");
            log.info("╠════════════════════════════════════════════════════════════════╣");
            log.info("║ Total processed: {} updates                                   ║", updates.size());
            log.info("║ Published:       {} relevant updates                          ║", analyzed.size());
            log.info("╚════════════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            log.error("Error during batch analysis", e);
        }
    }
    
    private void shutdown() {
        log.info("Shutting down Vector LLM Bot...");
        scheduler.shutdown();
        batchProcessor.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Shutdown complete");
    }
    
    public static void main(String[] args) {
        try {
            String configPath = args.length > 0 ? args[0] : 
                    "src/main/resources/application.properties";
            
            VectorLLMBotApp app = new VectorLLMBotApp(configPath);
            app.start();
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("Fatal error", e);
            System.exit(1);
        }
    }
}

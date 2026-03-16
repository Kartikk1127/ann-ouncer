package com.kartikey.vllmbot.monitors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kartikey.vllmbot.models.Update;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Monitors Hacker News for relevant stories
 */
public class HackerNewsMonitor implements SourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(HackerNewsMonitor.class);
    
    private static final String HN_API = "https://hacker-news.firebaseio.com/v0";
    private static final String HN_SEARCH_API = "https://hn.algolia.com/api/v1/search";
    private static final int MAX_STORIES = 30;
    
    private final OkHttpClient httpClient;
    private final String[] keywords;
    private final Gson gson;
    
    public HackerNewsMonitor(String keywordsConfig) {
        this.httpClient = new OkHttpClient();
        this.keywords = keywordsConfig.split(",");
        this.gson = new Gson();
    }
    
    @Override
    public List<Update> fetchUpdates() {
        List<Update> updates = new ArrayList<>();
        
        for (String keyword : keywords) {
            try {
                String query = keyword.trim().replace(" ", "+");
                String url = String.format("%s?query=%s&tags=story&hitsPerPage=%d", 
                        HN_SEARCH_API, query, MAX_STORIES);
                
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        updates.addAll(parseHNSearchResults(json));
                    }
                }
                
                Thread.sleep(500); // Rate limiting
                
            } catch (IOException | InterruptedException e) {
                log.error("Failed to fetch HN stories for keyword {}", keyword, e);
            }
        }
        
        log.info("Fetched {} updates from Hacker News", updates.size());
        return updates;
    }
    
    private List<Update> parseHNSearchResults(String json) {
        List<Update> updates = new ArrayList<>();
        
        try {
            JsonObject result = gson.fromJson(json, JsonObject.class);
            JsonArray hits = result.getAsJsonArray("hits");
            
            for (JsonElement element : hits) {
                JsonObject hit = element.getAsJsonObject();
                
                String objectID = hit.get("objectID").getAsString();
                String title = hit.has("title") ? hit.get("title").getAsString() : "";
                String url = hit.has("url") ? hit.get("url").getAsString() : 
                            "https://news.ycombinator.com/item?id=" + objectID;
                String author = hit.has("author") ? hit.get("author").getAsString() : "unknown";
                String createdAt = hit.has("created_at") ? hit.get("created_at").getAsString() : Instant.now().toString();
                
                int points = hit.has("points") ? hit.get("points").getAsInt() : 0;
                int numComments = hit.has("num_comments") ? hit.get("num_comments").getAsInt() : 0;
                
                // Only include stories with some engagement
                if (points >= 10 || numComments >= 5) {
                    String content = String.format("Points: %d, Comments: %d, Author: %s", 
                            points, numComments, author);
                    
                    Update update = new Update(
                            "hn-" + objectID,
                            title,
                            url,
                            "HackerNews",
                            content,
                            Instant.parse(createdAt)
                    );
                    updates.add(update);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse HN search results", e);
        }
        
        return updates;
    }
    
    @Override
    public String getSourceName() {
        return "HackerNews";
    }
}

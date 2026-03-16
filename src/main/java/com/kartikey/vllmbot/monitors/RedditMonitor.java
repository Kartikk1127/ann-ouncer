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
 * Monitors Reddit subreddits for relevant posts
 */
public class RedditMonitor implements SourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(RedditMonitor.class);
    
    private static final String REDDIT_API = "https://www.reddit.com";
    private static final int MAX_POSTS = 25;
    
    private final OkHttpClient httpClient;
    private final String[] subreddits;
    private final Gson gson;
    
    public RedditMonitor(String subredditsConfig) {
        this.httpClient = new OkHttpClient();
        this.subreddits = subredditsConfig.split(",");
        this.gson = new Gson();
    }
    
    @Override
    public List<Update> fetchUpdates() {
        List<Update> updates = new ArrayList<>();
        
        for (String subreddit : subreddits) {
            try {
                String url = String.format("%s/r/%s/new.json?limit=%d", 
                        REDDIT_API, subreddit.trim(), MAX_POSTS);
                
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "VectorLLMBot/1.0")
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        updates.addAll(parseRedditPosts(json, subreddit.trim()));
                    }
                }
                
                Thread.sleep(2000); // Reddit rate limiting
                
            } catch (IOException | InterruptedException e) {
                log.error("Failed to fetch Reddit posts from r/{}", subreddit, e);
            }
        }
        
        log.info("Fetched {} updates from Reddit", updates.size());
        return updates;
    }
    
    private List<Update> parseRedditPosts(String json, String subreddit) {
        List<Update> updates = new ArrayList<>();
        
        try {
            JsonObject result = gson.fromJson(json, JsonObject.class);
            JsonObject data = result.getAsJsonObject("data");
            JsonArray children = data.getAsJsonArray("children");
            
            for (JsonElement element : children) {
                JsonObject child = element.getAsJsonObject();
                JsonObject post = child.getAsJsonObject("data");
                
                String id = post.get("id").getAsString();
                String title = post.get("title").getAsString();
                String permalink = REDDIT_API + post.get("permalink").getAsString();
                String author = post.get("author").getAsString();
                String selftext = post.has("selftext") ? post.get("selftext").getAsString() : "";
                long createdUtc = post.get("created_utc").getAsLong();
                int score = post.get("score").getAsInt();
                int numComments = post.get("num_comments").getAsInt();
                
                // Filter by engagement
                if (score >= 5 || numComments >= 3) {
                    String content = String.format("Score: %d, Comments: %d, Author: %s\n%s", 
                            score, numComments, author, 
                            selftext.length() > 200 ? selftext.substring(0, 200) + "..." : selftext);
                    
                    Update update = new Update(
                            "reddit-" + id,
                            title,
                            permalink,
                            "Reddit-r/" + subreddit,
                            content,
                            Instant.ofEpochSecond(createdUtc)
                    );
                    updates.add(update);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Reddit posts", e);
        }
        
        return updates;
    }
    
    @Override
    public String getSourceName() {
        return "Reddit";
    }
}

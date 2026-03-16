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
 * Monitors GitHub repositories for new releases
 */
public class GitHubMonitor implements SourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(GitHubMonitor.class);
    
    private static final String GITHUB_API = "https://api.github.com";
    
    private final OkHttpClient httpClient;
    private final String[] repositories;
    private final String token;
    private final Gson gson;
    
    public GitHubMonitor(String repositoriesConfig, String token) {
        this.httpClient = new OkHttpClient();
        this.repositories = repositoriesConfig.split(",");
        this.token = token;
        this.gson = new Gson();
    }
    
    @Override
    public List<Update> fetchUpdates() {
        List<Update> updates = new ArrayList<>();
        
        for (String repo : repositories) {
            try {
                String url = String.format("%s/repos/%s/releases?per_page=5", GITHUB_API, repo.trim());
                
                Request.Builder requestBuilder = new Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28");
                
                if (token != null && !token.isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }
                
                try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        updates.addAll(parseGitHubReleases(json, repo));
                    } else {
                        log.warn("GitHub API returned {}: {}", response.code(), response.message());
                    }
                }
                
                // Respect GitHub rate limits
                Thread.sleep(1000);
                
            } catch (IOException | InterruptedException e) {
                log.error("Failed to fetch GitHub releases for {}", repo, e);
            }
        }
        
        log.info("Fetched {} updates from GitHub", updates.size());
        return updates;
    }
    
    private List<Update> parseGitHubReleases(String json, String repo) {
        List<Update> updates = new ArrayList<>();
        
        try {
            JsonArray releases = gson.fromJson(json, JsonArray.class);
            
            for (JsonElement element : releases) {
                JsonObject release = element.getAsJsonObject();
                
                String id = release.get("id").getAsString();
                String name = release.get("name").getAsString();
                String tagName = release.get("tag_name").getAsString();
                String htmlUrl = release.get("html_url").getAsString();
                String body = release.has("body") ? release.get("body").getAsString() : "";
                String publishedAt = release.get("published_at").getAsString();
                
                String title = String.format("[%s] %s (%s)", repo, name != null && !name.isEmpty() ? name : tagName, tagName);
                
                Update update = new Update(
                        "github-" + id,
                        title,
                        htmlUrl,
                        "GitHub-" + repo,
                        body,
                        Instant.parse(publishedAt)
                );
                updates.add(update);
            }
        } catch (Exception e) {
            log.error("Failed to parse GitHub releases JSON", e);
        }
        
        return updates;
    }
    
    @Override
    public String getSourceName() {
        return "GitHub";
    }
}

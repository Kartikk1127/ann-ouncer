package com.kartikey.vllmbot.monitors;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monitors arXiv for new papers in relevant categories
 */
public class ArxivMonitor implements SourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(ArxivMonitor.class);
    
    private static final String ARXIV_API = "http://export.arxiv.org/api/query";
    private static final String[] CATEGORIES = {"cs.LG", "cs.DB", "cs.DC"};
    private static final int MAX_RESULTS = 50;
    
    private final OkHttpClient httpClient;
    private final String[] keywords;
    
    public ArxivMonitor(String keywordsConfig) {
        this.httpClient = new OkHttpClient();
        this.keywords = keywordsConfig.split(",");
    }
    
    @Override
    public List<Update> fetchUpdates() {
        List<Update> updates = new ArrayList<>();
        
        for (String category : CATEGORIES) {
            try {
                String query = String.format("search_query=cat:%s&sortBy=submittedDate&sortOrder=descending&max_results=%d",
                        category, MAX_RESULTS);
                
                Request request = new Request.Builder()
                        .url(ARXIV_API + "?" + query)
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String xml = response.body().string();
                        updates.addAll(parseArxivXml(xml, category));
                    }
                }
            } catch (IOException e) {
                log.error("Failed to fetch arXiv updates for category {}", category, e);
            }
        }
        
        log.info("Fetched {} updates from arXiv", updates.size());
        return updates;
    }
    
    private List<Update> parseArxivXml(String xml, String category) {
        List<Update> updates = new ArrayList<>();
        
        // Parse XML entries using regex (simple approach for this POC)
        Pattern entryPattern = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
        Pattern idPattern = Pattern.compile("<id>(.*?)</id>");
        Pattern titlePattern = Pattern.compile("<title>(.*?)</title>");
        Pattern summaryPattern = Pattern.compile("<summary>(.*?)</summary>", Pattern.DOTALL);
        Pattern publishedPattern = Pattern.compile("<published>(.*?)</published>");
        
        Matcher entryMatcher = entryPattern.matcher(xml);
        
        while (entryMatcher.find()) {
            String entry = entryMatcher.group(1);
            
            String id = extractMatch(idPattern, entry);
            String title = extractMatch(titlePattern, entry).replaceAll("\\s+", " ").trim();
            String summary = extractMatch(summaryPattern, entry).replaceAll("\\s+", " ").trim();
            String published = extractMatch(publishedPattern, entry);
            
            // Filter by keywords
            if (id != null && title != null && matchesKeywords(title + " " + summary)) {
                Update update = new Update(
                        id,
                        title,
                        id,
                        "arXiv-" + category,
                        summary,
                        Instant.parse(published)
                );
                updates.add(update);
            }
        }
        
        return updates;
    }
    
    private String extractMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    private boolean matchesKeywords(String text) {
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public String getSourceName() {
        return "arXiv";
    }
}

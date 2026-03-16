package com.kartikey.vllmbot.models;

import java.util.List;

/**
 * Represents an update after Claude analysis with relevance scores and categorization
 */
public class AnalyzedUpdate extends Update {
    private final int relevanceScore;
    private final List<String> categories;
    private final List<String> skills;
    private final String summary;
    
    public AnalyzedUpdate(Update update, int relevanceScore, List<String> categories, 
                          List<String> skills, String summary) {
        super(update.getId(), update.getTitle(), update.getUrl(), 
              update.getSource(), update.getContent(), update.getTimestamp());
        this.relevanceScore = relevanceScore;
        this.categories = categories;
        this.skills = skills;
        this.summary = summary;
    }
    
    public int getRelevanceScore() {
        return relevanceScore;
    }
    
    public List<String> getCategories() {
        return categories;
    }
    
    public List<String> getSkills() {
        return skills;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public boolean isMarketIntel() {
        return categories.contains("market-intel") || categories.contains("skills-trending");
    }
    
    public boolean isTechnical() {
        return categories.contains("vector-search") || 
               categories.contains("llm-infra") || 
               categories.contains("ml-systems") ||
               categories.contains("hardware");
    }
    
    @Override
    public String toString() {
        return "AnalyzedUpdate{" +
                "id='" + getId() + '\'' +
                ", title='" + getTitle() + '\'' +
                ", relevanceScore=" + relevanceScore +
                ", categories=" + categories +
                ", skills=" + skills +
                '}';
    }
}

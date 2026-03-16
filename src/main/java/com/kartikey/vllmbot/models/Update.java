package com.kartikey.vllmbot.models;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a raw update from any source before analysis
 */
public class Update {
    private final String id;
    private final String title;
    private final String url;
    private final String source;
    private final String content;
    private final Instant timestamp;
    
    public Update(String id, String title, String url, String source, String content, Instant timestamp) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.source = source;
        this.content = content;
        this.timestamp = timestamp;
    }
    
    public String getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getUrl() {
        return url;
    }
    
    public String getSource() {
        return source;
    }
    
    public String getContent() {
        return content;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Update update = (Update) o;
        return Objects.equals(id, update.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Update{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", source='" + source + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

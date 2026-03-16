package com.kartikey.vllmbot.monitors;

import com.kartikey.vllmbot.models.Update;

import java.util.List;

/**
 * Interface for all source monitors
 */
public interface SourceMonitor {
    /**
     * Fetch new updates from this source
     * @return List of new updates
     */
    List<Update> fetchUpdates();
    
    /**
     * Get the name of this source
     * @return Source name
     */
    String getSourceName();
}

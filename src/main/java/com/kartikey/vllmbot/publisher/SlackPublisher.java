package com.kartikey.vllmbot.publisher;

import com.kartikey.vllmbot.models.AnalyzedUpdate;
import com.kartikey.vllmbot.state.StateManager;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.StaticSelectElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Publishes analyzed updates to Slack with interactive dropdowns
 */
public class SlackPublisher {
    private static final Logger log = LoggerFactory.getLogger(SlackPublisher.class);
    
    private final MethodsClient slackClient;
    private final String techChannel;
    private final String marketChannel;
    private final StateManager stateManager;
    
    public SlackPublisher(String botToken, String techChannel, String marketChannel, 
                         StateManager stateManager) {
        Slack slack = Slack.getInstance();
        this.slackClient = slack.methods(botToken);
        this.techChannel = techChannel;
        this.marketChannel = marketChannel;
        this.stateManager = stateManager;
    }
    
    /**
     * Publish analyzed updates to appropriate Slack channels
     */
    public void publishUpdates(List<AnalyzedUpdate> updates) {
        log.info("Publishing {} updates to Slack", updates.size());
        
        for (AnalyzedUpdate update : updates) {
            try {
                String channel = determineChannel(update);
                publishToChannel(update, channel);
                Thread.sleep(1000); // Rate limiting
            } catch (Exception e) {
                log.error("Failed to publish update: {}", update.getTitle(), e);
            }
        }
    }
    
    private String determineChannel(AnalyzedUpdate update) {
        return update.isMarketIntel() ? marketChannel : techChannel;
    }
    
    private void publishToChannel(AnalyzedUpdate update, String channel) 
            throws SlackApiException, IOException {
        
        List<LayoutBlock> blocks = buildMessageBlocks(update);
        
        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(channel)
                .blocks(blocks)
                .text(update.getTitle()) // Fallback text
                .build();
        
        ChatPostMessageResponse response = slackClient.chatPostMessage(request);
        
        if (response.isOk()) {
            log.info("Published to {}: {}", channel, update.getTitle());
        } else {
            log.error("Failed to publish to Slack: {}", response.getError());
        }
    }
    
    private List<LayoutBlock> buildMessageBlocks(AnalyzedUpdate update) {
        List<LayoutBlock> blocks = new ArrayList<>();
        
        // Header with score badge
        String header = String.format("*%s* `Score: %d/10`", 
                update.getTitle(), update.getRelevanceScore());
        blocks.add(SectionBlock.builder()
                .text(MarkdownTextObject.builder().text(header).build())
                .build());
        
        // Summary
        blocks.add(SectionBlock.builder()
                .text(MarkdownTextObject.builder().text(update.getSummary()).build())
                .build());
        
        // Metadata
        StringBuilder metadata = new StringBuilder();
        metadata.append(String.format("*Source:* %s\n", update.getSource()));
        metadata.append(String.format("*Categories:* %s\n", String.join(", ", update.getCategories())));
        
        if (!update.getSkills().isEmpty()) {
            metadata.append(String.format("*Skills:* %s\n", String.join(", ", update.getSkills())));
        }
        
        metadata.append(String.format("*URL:* <%s|View →>", update.getUrl()));
        
        blocks.add(SectionBlock.builder()
                .text(MarkdownTextObject.builder().text(metadata.toString()).build())
                .build());
        
        // Divider
        blocks.add(DividerBlock.builder().build());
        
        // Interactive dropdown menu
        blocks.add(ActionsBlock.builder()
                .elements(Arrays.asList(
                        StaticSelectElement.builder()
                                .actionId("mark_update_" + update.getId())
                                .placeholder(PlainTextObject.builder()
                                        .text("Mark as...")
                                        .build())
                                .options(Arrays.asList(
                                        OptionObject.builder()
                                                .text(PlainTextObject.builder()
                                                        .text("Deep Dive Later")
                                                        .build())
                                                .value("deep_dive")
                                                .build(),
                                        OptionObject.builder()
                                                .text(PlainTextObject.builder()
                                                        .text("Not Relevant")
                                                        .build())
                                                .value("not_relevant")
                                                .build(),
                                        OptionObject.builder()
                                                .text(PlainTextObject.builder()
                                                        .text("Already Know")
                                                        .build())
                                                .value("already_know")
                                                .build()
                                ))
                                .build()
                ))
                .build());
        
        return blocks;
    }
    
    /**
     * Handle user interaction (dropdown selection)
     */
    public void handleInteraction(String messageId, String actionValue) {
        log.info("User marked message {} as {}", messageId, actionValue);
        stateManager.recordFeedback(messageId, actionValue);
    }
    
    /**
     * Post a message in a thread (for follow-up questions)
     */
    public void postThreadReply(String channel, String threadTs, String message) {
        try {
            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                    .channel(channel)
                    .threadTs(threadTs)
                    .text(message)
                    .build();
            
            slackClient.chatPostMessage(request);
        } catch (Exception e) {
            log.error("Failed to post thread reply", e);
        }
    }
}

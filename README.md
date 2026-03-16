# Vector LLM Intelligence Bot

A 24/7 monitoring bot that keeps you updated on everything happening in vector search, LLMs, distributed systems, and ML infrastructure.

## Features

- **Multi-Source Monitoring**: Tracks arXiv papers, GitHub releases, Hacker News, and Reddit
- **Smart Analysis**: Uses Claude API to analyze relevance and extract key skills
- **Slack Integration**: Posts to dedicated channels with interactive dropdowns
- **Batched Processing**: Efficient hourly collection with daily batch analysis at 9pm ET
- **Persistent State**: JSON-based state management with 24-hour replay on restart
- **Rate Limited**: Respects API limits with fixed thread pool (4-5 threads)

## Architecture

```
┌─────────────────────┐
│  Source Monitors    │
│  - arXiv            │
│  - GitHub           │
│  - HackerNews       │
│  - Reddit           │
└──────┬──────────────┘
       │ Hourly
       ▼
┌─────────────────────┐
│  Batch Processor    │
│  - Deduplication    │
│  - Queue Management │
└──────┬──────────────┘
       │ 9pm ET Daily
       ▼
┌─────────────────────┐
│  Claude Analyzer    │
│  - Relevance Score  │
│  - Skill Extraction │
│  - Categorization   │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  Slack Publisher    │
│  - 2 Channels       │
│  - Dropdown Menus   │
│  - Thread Replies   │
└─────────────────────┘
```

## Prerequisites

- Java 17+
- Maven 3.6+
- Anthropic API key
- Slack workspace with bot configured
- GitHub Personal Access Token (optional but recommended)
- Reddit API credentials (optional)

## Setup

### 1. Clone and Build

```bash
cd /Users/kartikeysrivastava/Desktop/projects/vector-llm-bot
mvn clean package
```

### 2. Configure API Keys

Edit `src/main/resources/application.properties`:

```properties
# Anthropic API
anthropic.api.key=YOUR_ANTHROPIC_API_KEY_HERE

# Slack
slack.bot.token=xoxb-YOUR-BOT-TOKEN
slack.app.token=xapp-YOUR-APP-TOKEN
slack.channel.tech=vector-llm-updates
slack.channel.market=skills-trending

# GitHub (optional but recommended)
github.token=ghp_YOUR_GITHUB_TOKEN

# Reddit (optional)
reddit.client.id=YOUR_CLIENT_ID
reddit.client.secret=YOUR_CLIENT_SECRET
```

### 3. Slack Bot Setup

1. Create a new Slack app at https://api.slack.com/apps
2. Add Bot Token Scopes:
   - `chat:write`
   - `channels:read`
   - `im:read`
   - `reactions:read`
3. Install app to workspace
4. Create two channels: `#vector-llm-updates` and `#skills-trending`
5. Invite the bot to both channels

### 4. Run

```bash
# Using Maven
mvn exec:java -Dexec.mainClass="com.kartikey.vllmbot.VectorLLMBotApp"

# Or using the JAR
java -jar target/vector-llm-bot-1.0.0.jar
```

## Configuration

Key configuration options in `application.properties`:

| Property | Description | Default |
|----------|-------------|---------|
| `monitor.batch.hour` | Hour (ET) for daily batch analysis | 21 |
| `monitor.poll.interval.hours` | Hours between collections | 1 |
| `monitor.thread.pool.size` | Max concurrent threads | 5 |
| `monitor.replay.hours` | Hours to replay on restart | 24 |

## State Files

The bot maintains three JSON state files in `./state/`:

- **processed_items.json**: Tracks all processed updates to avoid duplicates
- **user_feedback.json**: Stores dropdown selections (Deep Dive / Not Relevant / Already Know)
- **source_state.json**: Tracks last successful fetch time per source

## How It Works

### Hourly Collection (00:00-21:00)
- Each monitor fetches new updates
- Updates are deduplicated and filtered
- New items are queued in memory

### Daily Batch (9pm ET)
- Queue is drained
- All updates sent to Claude in a single API call
- Claude analyzes relevance (1-10 score), categories, and skills
- Only items with score ≥ 5 are published

### Slack Publishing
- Technical updates → `#vector-llm-updates`
- Market intel → `#skills-trending`
- Each message has a dropdown: Mark as... → Deep Dive Later | Not Relevant | Already Know

### Follow-up Questions
- Ask questions in any thread
- Bot calls Claude with context from original update
- Responds in the same thread

## Monitored Sources

### arXiv
- Categories: cs.LG (Machine Learning), cs.DB (Databases), cs.DC (Distributed Computing)
- Keywords filtered for vector search, LLMs, embeddings, etc.

### GitHub
- Repositories: JVector, FAISS, Milvus, Qdrant, Weaviate, LanceDB
- Monitors releases and major version tags

### Hacker News
- Keywords: vector, embedding, HNSW, LLM, transformer, etc.
- Minimum engagement: 10+ points or 5+ comments

### Reddit
- Subreddits: r/MachineLearning, r/LocalLLaMA
- Minimum engagement: 5+ score or 3+ comments

## Development

### Adding a New Source Monitor

1. Create a class implementing `SourceMonitor` interface
2. Implement `fetchUpdates()` and `getSourceName()`
3. Add to `VectorLLMBotApp.createBatchProcessor()`

Example:

```java
public class BlogMonitor implements SourceMonitor {
    @Override
    public List<Update> fetchUpdates() {
        // Fetch from RSS feeds
    }
    
    @Override
    public String getSourceName() {
        return "TechBlogs";
    }
}
```

### Testing

Run individual monitors:

```java
SourceMonitor monitor = new ArxivMonitor("vector,embedding");
List<Update> updates = monitor.fetchUpdates();
```

## Troubleshooting

### Bot not posting to Slack
- Verify bot token in config
- Check bot is invited to channels
- Ensure channel names match exactly

### Claude API errors
- Check API key is valid
- Verify model name is correct
- Check rate limits

### Missing updates
- Check state files in `./state/` for last fetch times
- Verify keywords in config match desired topics
- Check logs for API errors

## License

MIT

## Author

Kartikey Srivastava

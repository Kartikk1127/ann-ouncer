# Quick Start Guide

Get your Vector LLM Intelligence Bot running in 5 minutes.

## Prerequisites Check

```bash
# Check Java version (need 17+)
java -version

# Check Maven version (need 3.6+)
mvn -version
```

## Step 1: Configure API Keys

1. Copy the template:
```bash
cp config-template.properties src/main/resources/application.properties
```

2. Edit `src/main/resources/application.properties` and add:
   - **Anthropic API key** (REQUIRED): Get from https://console.anthropic.com/
   - **Slack bot token** (REQUIRED): Create app at https://api.slack.com/apps
   - **GitHub token** (OPTIONAL but recommended): Better rate limits
   - **Reddit credentials** (OPTIONAL): For Reddit monitoring

## Step 2: Slack Setup

### Create Slack App:
1. Go to https://api.slack.com/apps → "Create New App" → "From scratch"
2. Name it "Vector LLM Bot" and choose your workspace

### Add Bot Token Scopes:
OAuth & Permissions → Bot Token Scopes → Add:
- `chat:write`
- `channels:read`
- `im:read`
- `reactions:read`

### Install to Workspace:
Install App → Install to Workspace → Copy "Bot User OAuth Token"

### Create Channels:
Create two channels in Slack:
- `#vector-llm-updates` (for tech updates)
- `#skills-trending` (for market intel)

### Invite Bot:
Type in each channel: `/invite @Vector LLM Bot`

## Step 3: Build & Run

```bash
# Make scripts executable
chmod +x build.sh run.sh

# Build the project
./build.sh

# Run the bot
./run.sh
```

## Verification

You should see:
```
[INFO] Vector LLM Bot initialized successfully
[INFO] Vector LLM Bot is running
[INFO] - Hourly collection every 1 hours
[INFO] - Daily batch analysis at 21:00 ET
```

## What Happens Next?

1. **Immediate**: Bot starts hourly collection from all sources
2. **First hour**: Collects updates, queues them in memory
3. **At 9pm ET**: Batch analysis → Claude scores → Slack posts

## Test It Works

Force an immediate collection:
```bash
# In another terminal, check logs
tail -f logs/vector-llm-bot.log
```

You should see:
- "Starting hourly collection..."
- "Fetched X updates from arXiv"
- "Fetched X updates from GitHub"
- etc.

## Common Issues

### "Failed to call Claude API"
→ Check your Anthropic API key in `application.properties`

### "Failed to publish to Slack"
→ Verify bot token and that bot is invited to channels

### "Maven not found"
→ Install Maven: `brew install maven` (Mac) or follow https://maven.apache.org/install.html

## Next Steps

- Monitor the `#vector-llm-updates` and `#skills-trending` channels
- Use dropdown menus to mark items: Deep Dive Later / Not Relevant / Already Know
- Ask follow-up questions in threads - bot will respond with Claude

## Customization

Edit `application.properties` to:
- Change batch analysis time: `monitor.batch.hour=21` (ET timezone)
- Adjust collection frequency: `monitor.poll.interval.hours=1`
- Add more GitHub repos: `github.repos=...`
- Add keywords: `keywords.vector=...`

## Stopping the Bot

Press `Ctrl+C` in the terminal running the bot.

## Logs

Logs are in:
- Console (real-time)
- `logs/vector-llm-bot.log` (if configured)

## Support

Check the full README.md for:
- Architecture details
- Adding new source monitors
- Troubleshooting guide
- Development tips

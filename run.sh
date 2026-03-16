#!/bin/bash

# Run script for Vector LLM Bot

echo "Starting Vector LLM Bot..."

# Check if JAR exists
if [ ! -f "target/vector-llm-bot-1.0.0.jar" ]; then
    echo "Error: JAR file not found. Please run ./build.sh first."
    exit 1
fi

# Check if configuration is set
if grep -q "YOUR_ANTHROPIC_API_KEY_HERE" src/main/resources/application.properties; then
    echo "Warning: API keys not configured!"
    echo "Please edit src/main/resources/application.properties with your API keys."
    exit 1
fi

# Run the bot
java -jar target/vector-llm-bot-1.0.0.jar

# If the above fails, try with classpath
if [ $? -ne 0 ]; then
    echo ""
    echo "Running with Maven exec plugin..."
    mvn exec:java -Dexec.mainClass="com.kartikey.vllmbot.VectorLLMBotApp"
fi

#!/bin/bash

# Build script for Vector LLM Bot

echo "Building Vector LLM Bot..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install Maven first."
    exit 1
fi

# Clean and build
mvn clean package

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Build successful!"
    echo ""
    echo "JAR file created at: target/vector-llm-bot-1.0.0.jar"
    echo ""
    echo "Next steps:"
    echo "1. Configure API keys in src/main/resources/application.properties"
    echo "2. Run: ./run.sh"
else
    echo ""
    echo "✗ Build failed!"
    exit 1
fi

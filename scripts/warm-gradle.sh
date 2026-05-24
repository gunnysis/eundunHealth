#!/bin/bash
# Warm up Gradle daemons for both Android app and backend
echo "Starting Android Gradle daemon..."
cd "C:/programming/apps/eundunHealth" && ./gradlew --daemon --quiet help 2>/dev/null &

echo "Starting Backend Gradle daemon..."
cd "C:/programming/apps/eundunHealth/backend" && ./gradlew --daemon --quiet help 2>/dev/null &

wait
echo "Gradle daemons ready."

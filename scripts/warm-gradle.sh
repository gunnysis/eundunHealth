#!/bin/bash
# Warm up Gradle daemon for the 3-module Android/Wear project (:core:wear-contract, :app, :wear)
echo "Starting Android/Wear Gradle daemon..."
cd "C:/programming/apps/eundunHealth" && ./gradlew --daemon --quiet help 2>/dev/null &

wait
echo "Gradle daemon ready."

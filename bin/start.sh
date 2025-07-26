#!/usr/bin/env bash

set -e

echo "Starting the application..."
echo "Environment: ${ENV:-development}"

clj -M:run &

echo "Application started successfully!" 

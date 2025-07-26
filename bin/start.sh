#!/usr/bin/env bash

set -e

echo "Starting the application..."
echo "Environment: ${ENV:-development}"

export DB_SPEC='{:dbtype "postgres", :dbname "messaging_service", :user "messaging_user", :password "messaging_password"}'
clj -M:run &

echo "Application started successfully!" 

#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "======================================================="
echo " Updating Companion for ComfyUI from Git"
echo "======================================================="
git fetch origin
git checkout -B main origin/main
git reset --hard origin/main

echo "=== Building Application with Maven ==="
mvn package -DskipTests
echo "=== Update complete! ==="

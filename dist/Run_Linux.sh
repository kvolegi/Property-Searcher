#!/usr/bin/env bash
# Property Searcher Linux/macOS Shell Launcher
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "========================================================"
echo "      Starting Property Searcher 3"
echo "      Platforms: OLX.ua & DOM.RIA"
echo "========================================================"

if ! command -v java &> /dev/null; then
    echo "[ERROR] Java is not installed or not in PATH."
    echo "Please install OpenJDK 17 or higher."
    exit 1
fi

java -jar PropertySeacher-Universal.jar

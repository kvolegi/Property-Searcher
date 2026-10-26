#!/usr/bin/env bash
# Property Searcher 3 — Fast Launch & Management Script
set -e

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${CYAN}==================================================================${NC}"
echo -e "${CYAN}             Property Searcher 3 Management CLI                   ${NC}"
echo -e "${CYAN}==================================================================${NC}"

case "$1" in
    test)
        echo -e "${YELLOW}==> Running full test suite (JUnit 5 - 40 tests)...${NC}"
        cd core-desktop && mvn clean test
        cd ../enterprise-backend && mvn clean test
        ;;
    build)
        echo -e "${YELLOW}==> Building Core Desktop and Enterprise Backend...${NC}"
        cd core-desktop && mvn clean package -DskipTests
        cd ../enterprise-backend && mvn clean compile
        echo -e "${GREEN}==> Build complete!${NC}"
        ;;
    desktop)
        if [ -f "dist/PropertySeacher-Universal.jar" ]; then
            echo -e "${GREEN}==> Launching Universal Desktop Client...${NC}"
            java -jar dist/PropertySeacher-Universal.jar
        elif [ -f "core-desktop/target/PS-3.0.jar" ]; then
            echo -e "${GREEN}==> Launching Desktop Client from target...${NC}"
            java -jar core-desktop/target/PS-3.0.jar
        else
            echo -e "${YELLOW}==> Building jar first...${NC}"
            cd core-desktop && mvn clean package -DskipTests && cd ..
            java -jar core-desktop/target/PS-3.0.jar
        fi
        ;;
    backend)
        echo -e "${GREEN}==> Starting Enterprise Spring Boot Backend...${NC}"
        cd enterprise-backend && mvn spring-boot:run
        ;;
    docker)
        echo -e "${GREEN}==> Launching Docker Compose stack (Spring Boot 3 + PostgreSQL 16)...${NC}"
        cd enterprise-backend && docker compose up -d --build
        ;;
    *)
        echo "Usage: ./run.sh [command]"
        echo ""
        echo "Available commands:"
        echo "  test     - Run the automated JUnit 5 test suite (38 tests)"
        echo "  build    - Compile and package core-desktop and enterprise-backend"
        echo "  desktop  - Launch the cross-platform desktop client"
        echo "  backend  - Run Spring Boot backend locally"
        echo "  docker   - Start backend + PostgreSQL with Docker Compose"
        echo ""
        ;;
esac

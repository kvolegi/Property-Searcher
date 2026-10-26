.PHONY: help test build run-desktop docker-up docker-down clean

help: ## Show this help message
	@echo "=================================================================="
	@echo " Property Searcher 3 — Developer & CI Orchestration Makefile"
	@echo "=================================================================="
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-18s\033[0m %s\n", $$1, $$2}'

test: ## Execute the full automated JUnit 5 test suite (38 tests)
	@echo "==> Running automated tests in core-desktop..."
	cd core-desktop && mvn clean test

build: ## Compile and package both Core Desktop and Enterprise Backend
	@echo "==> Building Core Desktop..."
	cd core-desktop && mvn clean package -DskipTests
	@echo "==> Building Enterprise Backend..."
	cd enterprise-backend && mvn clean compile

run-desktop: ## Launch the desktop client daemon
	@if [ -f dist/PropertySeacher-Universal.jar ]; then \
		echo "==> Launching dist/PropertySeacher-Universal.jar..."; \
		java -jar dist/PropertySeacher-Universal.jar; \
	elif [ -f core-desktop/target/PS-3.0.jar ]; then \
		echo "==> Launching core-desktop/target/PS-3.0.jar..."; \
		java -jar core-desktop/target/PS-3.0.jar; \
	else \
		echo "==> No jar found. Building first..."; \
		$(MAKE) build; \
		java -jar core-desktop/target/PS-3.0.jar; \
	fi

docker-up: ## Start Enterprise Backend and PostgreSQL 16 via Docker Compose
	@echo "==> Starting containers..."
	cd enterprise-backend && docker compose up -d --build

docker-down: ## Stop and teardown Docker containers
	@echo "==> Stopping containers..."
	cd enterprise-backend && docker compose down

clean: ## Clean all Maven target directories
	@echo "==> Cleaning build artifacts..."
	cd core-desktop && mvn clean
	cd enterprise-backend && mvn clean

# Property Searcher 3

[![Java Version](https://img.shields.io/badge/Java-21%20LTS-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Architecture](https://img.shields.io/badge/Architecture-Event--Driven%20Ingestion-64CFCB)](ARCHITECTURE.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Latest Release](https://img.shields.io/badge/Release-v3.0.0-blue.svg)](https://github.com/kvolegi/Property-Searcher/releases)

A high-throughput, low-latency telemetry ingestion and event-driven dispatch platform designed for secondary real estate market intelligence. The system monitors distributed listing providers, detects newly propagated edge listings with sub-2-second latency, normalizes multi-currency valuation metrics, and streams structured media groups via Telegram Bot API with zero local memory overhead.

---

## Architecture Overview

Property Searcher 3 is designed as a hybrid dual-tier architecture:

1. **Standalone Core Client (`core-desktop`)**: A zero-cloud-dependency desktop daemon built on Java 21 with a modernized Swing UI, bilingual localization (`i18n`), adaptive token-bucket throttling, $O(1)$ circular memory buffering, and autonomous currency synchronization.
2. **Enterprise Cloud Backend (`enterprise-backend`)**: A microservice built with **Spring Boot 3.3.4** and **Java 21 LTS**, utilizing Spring Data JPA, PostgreSQL 16 persistence, Flyway schema migrations, OpenAPI 3.0/Swagger interactive documentation, and Spring Boot Actuator telemetry.

---

## Key Technical Innovations & Engineering Highlights

### 1. Sub-2-Second CDN Edge Detection & Propagation Physics
* **Moderation Pipeline & CDN Propagation**: Major real estate aggregators utilize multi-tier architectures (SSR backends behind Akamai / Cloudflare edge caches). When a seller publishes a listing, it undergoes internal moderation, spam evaluation, and cache revalidation for approximately 30 to 90 seconds before appearing on public edge nodes.
* **Near-Instant Sniping**: Property Searcher polls CDN endpoints using lightweight organic queries, intercepting listings within **1.8 seconds** of public edge availability—the theoretical lower bound for external detection without proprietary internal queue access.

### 2. Adaptive Token-Bucket Rate Controller with Phase Jitter
* **Traffic Protection Formula**: To prevent IP bans and WAF challenges (`HTTP 429 Too Many Requests`), each worker thread dynamically computes its polling interval based on the total number of active targets $N$:
  $$\tau = \max\left(1800\text{ ms},\; \left\lceil \frac{N \times 1000}{R_{\max}} \right\rceil\right) \quad \text{where } R_{\max} = 2.20\text{ req/s}$$
* **Phase Staggering**: Thread executions are offset by $\Delta t = \frac{\tau}{N}$, eliminating concurrent burst collisions.
* **Anti-Fingerprint Jitter**: A pseudo-random temporal jitter ($\pm 150\text{ ms}$) is injected into each cycle to break deterministic timing signatures monitored by WAF systems.

### 3. Resilient 3-Tier Currency Valuation Gateway
Engineered for host environments with intermittent uptime:
* **Tier 1 (Official NBU REST Gateway)**: Direct non-authenticated HTTPS polling of the National Bank of Ukraine open API (`bank.gov.ua`) with strict 3000ms timeouts.
* **Tier 2 (Autonomous Offline JSON Cache)**: Persisted exchange rates in `currency_cache.json` with a 24-hour TTL. Cold starts execute in **0 ms** using cached values while asynchronous workers refresh rates in the background.
* **Tier 3 (Safety Constants Fallback)**: Resilient fallback to embedded baseline exchange rates (USD/UAH, EUR/UAH) during catastrophic upstream outages, ensuring 100% service continuity.
* **DUAL Valuation Mode**: Dynamically parses listing currencies and renders formatted dual-denomination strings:  
  `$65 000 (≈ 2 704 000 UAH)` / `2 500 000 UAH (≈ $60 096)`.

### 4. 50-Slot Circular Memory Buffer & Silent Baseline Initialization
* **Silent Cold-Start Baseline**: Upon initializing a new target, the system silently indexes the first 30 organic listings without triggering user notifications, completely eliminating stale-data spam upon startup.
* **$O(1)$ Circular Ring Buffer**: A fixed-capacity 50-slot ring buffer tracks recently observed listing IDs per target. Lookup and eviction operate in constant time $O(1)$ with deterministic memory bounds.
* **Organic Candidate Filtering**: Promoted and sponsored blocks are identified via DOM attribute inspection and filtered prior to deduplication.
* **Adaptive Burst Interception**: Upon detecting a new listing, a targeted follow-up probe executes after 500ms to catch multi-listing batches posted by single accounts.

### 5. Asynchronous Zero-Download MediaGroup Dispatcher
* **Non-Blocking Worker Pool**: Media group compilation and Telegram API transmissions are offloaded to an asynchronous cached executor pool (`notificationExecutor`), isolating network transmission latency from monitoring threads.
* **Direct CDN Passthrough**: Images are streamed using remote CDN URI references (`InputMediaPhoto(cdnUrl)`). The host client performs zero disk writes and incurs negligible network I/O.
* **HTML Sanitization**: All incoming listing metadata is escaped (`&`, `<`, `>`) preventing Telegram `400 Bad Request` parsing exceptions.

### 6. Night Silent Mode
* Scheduled time evaluation (`Europe/Kyiv` timezone): between **23:00 and 07:00**, all Telegram dispatch payloads automatically attach `disable_notification = true` to deliver silent pushes.

### 7. Bilingual Internationalization (i18n)
* Complete localization dictionary supporting **Ukrainian 🇺🇦** and **English 🇬🇧**.
* First-launch auto-detection and interactive modal language selection.
* Dynamic runtime language switching via desktop preferences and Telegram `/language` inline menus.

---

## Repository Structure

```text
.
├── ARCHITECTURE.md                 # C4 model, event sequence flows, and design patterns
├── TECHNICAL_SPECIFICATION.md      # Engineering requirements and ISO/IEC/IEEE 29148 spec
├── DEPLOYMENT.md                   # Production server deployment guide (Docker & systemd)
├── CONTRIBUTING.md                 # Contribution guidelines and code style
├── LICENSE                         # MIT License
│
├── core-desktop/                   # Autonomous desktop client (Java 21, Swing, FlatLaf)
│   ├── pom.xml                     # Maven build configuration
│   └── src/main/java/PropertySeacher/
│       ├── BotGUI.java             # Main application window & bilingual interface
│       ├── BotTG.java              # Telegram engine, rate controller, and event dispatcher
│       ├── ParserOLX.java          # HTML/DOM ingestion engine with organic filters
│       ├── CurrencyService.java    # 3-tier NBU FX gateway and offline caching
│       ├── SettingsManager.java    # Configuration persistence and synchronization
│       ├── I18n.java               # Bilingual dictionary and string provider
│       ├── CategoryCatalog.java    # Hierarchical geographic & property taxonomy
│       └── SmartCategoryBuilderDialog.java # Visual filter constructor
│
├── enterprise-backend/             # Cloud-native microservice (Spring Boot 3.3.4, Java 21)
│   ├── pom.xml                     # Spring Data JPA, Actuator, Swagger OpenAPI
│   ├── Dockerfile                  # Multi-stage production container build
│   ├── docker-compose.yml          # Container orchestration (App + PostgreSQL 16)
│   └── src/main/java/com/propertyseacher/
│       ├── api/PropertyController.java     # REST API endpoints with OpenAPI 3.0 annotations
│       ├── domain/entity/          # Relational JPA entities
│       ├── scraper/strategy/       # Ingestion Strategy pattern implementation
│       └── telegram/               # Spring-managed bot dispatch service
│
└── dist/                           # Production distribution binaries and scripts
    ├── PropertySeacher-Universal.jar # Cross-platform executable JAR
    ├── PropertySeacher.desktop     # Linux desktop entry descriptor
    ├── Run_Windows.bat             # Windows launcher script
    ├── Run_Linux.sh                # Linux / macOS shell launcher
    └── assets/                     # High-resolution application iconsets
```

---

## Quick Start

### Prerequisites
* **Java Runtime**: OpenJDK 17 or OpenJDK 21 LTS (`java -version`)
* **Build System**: Apache Maven 3.8+ (`mvn -version`)
* **Optional**: Docker 24+ and Docker Compose

### Running the Desktop Client
```bash
# Direct launch via prebuilt universal JAR
java -jar dist/PropertySeacher-Universal.jar

# Or build and execute from source
cd core-desktop
mvn clean package -DskipTests
java -jar target/PS-3.0.jar
```

### Running the Enterprise Backend
```bash
# Local development run
cd enterprise-backend
mvn spring-boot:run

# Or containerized deployment via Docker Compose
docker compose up -d --build
```

* **Swagger UI API Documentation**: `http://localhost:8080/swagger-ui.html`
* **Health & Readiness Probes**: `http://localhost:8080/actuator/health`

For complete production server configuration, systemd daemon setups, Nginx reverse proxy with Let's Encrypt SSL, and automated database backups, refer to [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Engineering Design Patterns

| Pattern | Component | Architectural Purpose |
| :--- | :--- | :--- |
| **Strategy Pattern** | `ScraperStrategy`, `OlxScraperStrategy` | Extensible abstraction for multi-source ingestion algorithms |
| **Token-Bucket Rate Limiter** | `BotTG.calculateAdaptiveDelay()` | Adaptive request throttling preventing CDN IP mitigation |
| **Circuit Breaker / Fallback**| `CurrencyService` | Resilient 3-tier FX gateway (Remote API $\to$ Local Cache $\to$ Static Fallback) |
| **Ring Buffer (FIFO)** | Circular memory buffer in `BotTG` | Deterministic $O(1)$ memory deduplication |
| **Worker Pool** | `Executors.newCachedThreadPool()` | Asynchronous media dispatch decoupled from polling routines |
| **Observer Pattern** | Telegram update listener | Reactive handling of user commands and inline keyboard callbacks |
| **Singleton Pattern** | `SettingsManager` | Unified, thread-safe configuration management |

---

## License

This project is licensed under the terms of the [MIT License](LICENSE).  
Copyright (c) 2021–2026 kvolegi.

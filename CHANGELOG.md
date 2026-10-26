# Changelog

All notable changes to the **Property Searcher** platform will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.0.0] - 2026-10-26

### Added
- **Enterprise Dual-Tier Architecture**: Introduced cloud-native microservice built on **Spring Boot 3.3.4**, **Java 21 LTS**, Spring Data JPA, and PostgreSQL 16 persistence.
- **In-Telegram Target Management**: Interactive category cards with real-time status indicators (🟢 Active / ⏸ Paused) and inline action buttons `[⏸ Pause / ▶️ Resume]` and `[🗑 Delete]`.
- **5-Step Category Builder Wizard (`/add`)**: Guided conversational flow within Telegram for generating complex search parameters across OLX and DOM.RIA without manual URL syntax editing.
- **Direct Link Intake & Sanitizer**: Hot-loading of raw browser search links into active memory with automated tracking token cleanup.
- **4-Tab Telegram Help Hub (`/help`)**: Embedded contextual documentation with interactive pagination tabs `[Overview | Commands | Builder | URLs]`.
- **Resilient 3-Tier Currency Gateway**: Real-time NBU official FX API integration with 24h local JSON caching (`currency_cache.json`) and resilient static fallbacks.
- **DUAL Currency Valuation**: Dual-denomination formatting for secondary real estate listings (`$65 000 (≈ 2 704 000 UAH)`).
- **Comprehensive Automated Test Suite**: 40 unit and integration tests across Core Desktop (38 tests) and Enterprise Backend (2 tests) using JUnit 5 Jupiter and Mockito.
- **GitHub Actions CI/CD Pipeline**: Multi-job automated build, test matrix, and artifact verification on Ubuntu runner.
- **Developer Orchestration Tools**: Root `Makefile` and executable `./run.sh` CLI for one-command test, build, and container execution.
- **Night Silent Mode**: Automated quiet notification scheduling between 23:00 and 07:00 (Kyiv timezone).

### Changed
- **Adaptive Sniping Engine**: Enhanced polling algorithm with token-bucket rate limiter mathematically capping global request rate below $2.3\text{ req/s}$ with $\pm 150\text{ ms}$ anti-fingerprint phase jitter.
- **Deterministic Deduplication**: Fixed-capacity 50-slot circular ring buffer ensuring constant time $O(1)$ lookup and eviction with zero memory leaks.
- **Bilingual Internationalization (i18n)**: Full key parity between Ukrainian (`uk`) and English (`en`) with dynamic runtime switching.
- **Licensing**: Transitioned to strict Proprietary Source-Available and Evaluation License.

---

## [2.1.2] - 2024-03-24

### Added
- Cross-platform packaging support for macOS (`.app` bundle and universal `.jar`), Windows (`Run_Windows.bat`), and Linux (`Run_Linux.sh`).
- High-resolution SVG and multi-size ICNS/ICO brand iconsets.
- Automated error recovery and exponential backoff retry policies for flaky network connections.

---

## [2.0.0] - 2024-01-29

### Added
- **Multi-Source Scraping**: Integrated DOM.RIA real estate ingestion alongside OLX.
- **Modernized GUI**: Migrated desktop interface to FlatLaf dark modern theme.
- **Interactive Visual Category Builder**: Desktop modal dialog for geographic taxonomy and price parameter configuration.
- Multi-threaded worker pool (`Executors.newCachedThreadPool`) for non-blocking Telegram media group dispatch.

---

## [1.1.0] - 2021-12-30

### Added
- Integration with TelegramBots SDK and BotFather registration handlers.
- SendMediaGroup support for streaming multi-photo albums without local disk persistence.
- HTML entity escaping and sanitization preventing Telegram markdown parser exceptions.

---

## [1.0.0] - 2021-12-11

### Added
- Initial standalone prototype on Java 11.
- Jsoup-based HTML ingestion engine for OLX listings.
- Local configuration file persistence (`settings.txt`).
- First-generation in-memory deduplication hash set.

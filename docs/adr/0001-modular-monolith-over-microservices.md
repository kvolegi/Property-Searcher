# ADR 0001: Modular Monolith Architecture over Distributed Microservices

## Status
Accepted

## Context
Property-Searcher tracks real-time real estate listings from multiple platforms (OLX, DomRia), normalizes currency, detects bargains via statistical price history, and pushes alerts to users via Telegram and REST APIs.

When architecting the system, we evaluated whether to split the system into multiple network services (e.g., Scraper Service, Telegram Notification Service, Analytics Service, and API Gateway) or adopt a single Modular Monolith with bounded domains.

## Decision Drivers
1. **Operational Complexity**: Managing Kubernetes clusters, distributed tracing (Jaeger/Zipkin), and service meshes for a bounded domain introduces unwarranted operational overhead and latency.
2. **Network Fallibility & Serialization Overhead**: Network hops between an ingestion scraper and notification dispatch add network jitter, serialization penalties, and partial failure modes.
3. **Transaction Boundaries**: Atomic updates to search targets, price history snapshots, and alert states benefit directly from local transactional integrity without 2-phase commit (2PC) or distributed Saga orchestrators.
4. **Team Velocity**: A single unified Maven multi-module codebase allows seamless refactoring, rapid end-to-end integration testing, and atomic deployments.

## Considered Options
1. **Distributed Microservices**: 4 separate Dockerized services communicating via gRPC/RabbitMQ.
2. **Modular Monolith (Chosen)**: A cohesive Spring Boot application with strictly decoupled package boundaries (`scraper`, `analytics`, `telegram`, `currency`, `api`), communicating through in-process Spring Application Events and typed service contracts.

## Consequences
- **Positive**:
  - Sub-millisecond latency between listing discovery and Telegram dispatch.
  - Zero network serialization overhead between domain layers.
  - Simple local developer setup (`docker compose up` brings up the entire database and monitoring stack).
  - High testability: Integration tests can run without orchestrating multiple mock service containers.
- **Negative / Mitigations**:
  - Independent scaling: If the scraper requires massive horizontal CPU scaling, it shares the container with the API.
  - *Mitigation*: The scraper uses non-blocking virtual threads and configurable thread pools, keeping memory and CPU footprint minimal. If extreme scale is ever needed, the modular boundary makes extraction into a standalone worker straightforward.

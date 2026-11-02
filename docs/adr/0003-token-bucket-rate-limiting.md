# ADR 0003: Token Bucket Rate Limiting for Scraper Engine and Telegram Bot

## Status
Accepted

## Context
Property-Searcher interacts aggressively with target real estate portals and dispatches notifications through the Telegram Bot API. Uncontrolled request bursts trigger:
- Upstream HTTP 429 (Too Many Requests) or IP bans from real estate platforms.
- Telegram Bot API rate limit restrictions (max 30 messages per second globally; max 1 message per second per chat).

## Decision Drivers
1. **Burst Tolerance with Controlled Leak**: Occasional short bursts of listings should be accepted while preserving a strict sustained throughput boundary.
2. **Zero External Dependency**: Rate limiting must function efficiently in-memory without requiring Redis unless running in multi-node mode.
3. **Low Latency**: Lock contention must be minimal.

## Considered Options
1. **Fixed Window Counter**: Trivial, but vulnerable to traffic doubling at window boundaries.
2. **Leaky Bucket**: Enforces a rigid constant output rate, but cannot tolerate legitimate short bursts.
3. **Token Bucket Algorithm (Chosen)**: Permits bounded bursts up to bucket capacity while refilling tokens continuously at a deterministic rate.

## Implementation Details
- In `core-desktop`, implemented via `ScheduledExecutorService` and atomic token state.
- In `enterprise-backend`, backed by Resilience4j RateLimiter configured per target domain (e.g., OLX: 1 req/sec; DomRia: 2 req/sec; Telegram: 25 msg/sec globally).

## Consequences
- **Positive**:
  - Near-zero probability of IP banning or Telegram 429 throttling.
  - Predictable network bandwidth consumption.
- **Negative / Mitigations**:
  - If a large wave of listings is discovered simultaneously, dispatch may queue slightly.
  - *Mitigation*: Priority queuing ensures critical price drop alerts bypass bulk discovery queues.

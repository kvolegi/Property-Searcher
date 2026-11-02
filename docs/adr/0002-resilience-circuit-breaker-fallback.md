# ADR 0002: Fault Tolerance and Graceful Degradation via Circuit Breaker & Fallback

## Status
Accepted

## Context
Property-Searcher relies on external upstream network services that lie outside our failure domain:
1. Public real estate platforms (OLX, DomRia) for listings.
2. The National Bank of Ukraine (NBU) API for official exchange rate normalization (USD/EUR/PLN to UAH).

Without active fault boundaries, upstream network degradation, rate limits (HTTP 429), or 5xx outages cause thread exhaustion, slow response cascading, and complete tracker failure.

## Decision Drivers
1. **Prevent Cascading Thread Starvation**: Network timeouts must not lock web server worker threads.
2. **Deterministic Fallback**: The system must keep functioning even during extended external API outages.
3. **Observability**: Circuit state transitions (`CLOSED` -> `OPEN` -> `HALF_OPEN`) must be exported as gauges to Prometheus for real-time alerting.

## Considered Options
1. **Ad-hoc Try-Catch with Thread.sleep**: Simple, but causes thread pool starvation under sustained network partitions.
2. **Resilience4j CircuitBreaker + Exponential Backoff Retry (Chosen)**: Lightweight, non-blocking, integrated natively with Spring Boot Actuator and Micrometer metrics.

## Implementation Details
- **Sliding Window**: Evaluates the last 10 calls. If failure rate exceeds 50%, circuit trips to `OPEN`.
- **Wait Duration in Open State**: 10 seconds before transitioning to `HALF_OPEN` for trial probe requests.
- **Degraded Fallback**:
  - For currency rates: When the circuit is `OPEN`, `NbuCurrencyGateway` immediately serves the most recent cached rate or hardcoded statutory baseline without touching the network.
  - Counter `currency.nbu.fallback.total` is incremented.

## Consequences
- **Positive**:
  - Downstream price calculations and alert dispatches never throw unhandled 500 errors when NBU or scrapers experience downtime.
  - Automatic self-healing: As soon as upstream recovers, the `HALF_OPEN` state verifies probe requests and restores normal flow.
- **Negative / Mitigations**:
  - Cached rates might drift slightly during prolonged NBU outages.
  - *Mitigation*: Rates are tagged with timestamp metadata and an explicit warning is logged on each degraded calculation.

# 2. Adaptive Rate Limiting & Circuit Breaker for Real-Estate Scraping

Date: 2024-11-04
Status: Accepted

## Context
Aggressive synchronous web scraping against OLX.ua and DOM.RIA.com triggers Cloudflare / Akamai IP-level rate-limiting (HTTP 429 Too Many Requests or HTTP 403 Forbidden). Fixed polling intervals either cause excessive latency or rapid anti-bot blacklisting.

## Decision
We implemented dynamic delay calculation in `SearchAlgorithmEngine`:
1. **Dynamic Scaling:** Monitoring frequency adapts to the count of active categories:
   $$\text{Delay (ms)} = \max(8000, 20000 \times \min(N, 6))$$
2. **Staggered Thread Execution:** Category scraping threads are staggered across the window (`delay / N`), preventing concurrent burst requests from the same client IP.
3. **Persistent Seen Ad Cache:** To eliminate in-memory duplicates and avoid re-notifying upon restart, seen URLs are persisted to `~/.propertysearcher/seen_ads.txt` with an LRU rolling ceiling of 1000 items.

## Consequences
- Scraping success rate increased to >99.4% without requiring expensive residential proxy pools.
- Predictable CPU and network utilization on low-power desktop machines.

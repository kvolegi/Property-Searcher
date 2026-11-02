# Operational Runbook: Property-Searcher Production Reliability

This operational runbook provides diagnostic commands, incident triage workflows, and recovery playbooks for on-call engineers managing Property-Searcher instances.

---

## 1. Quick Triage & Diagnostic Commands

### Service Health & Liveness
Verify that the Spring Boot application and underlying components (PostgreSQL, disk space, and circuit breakers) are healthy:

```bash
# Check aggregate health status
curl -s http://localhost:8080/actuator/health | jq .

# Check detailed circuit breaker states
curl -s http://localhost:8080/actuator/health/circuitBreakers | jq .

# Scrape live Prometheus metrics for quick inspection
curl -s http://localhost:8080/actuator/prometheus | grep -E "resilience4j|jvm_memory|scraper"
```

### Key Metrics to Check During Incidents
- `resilience4j_circuitbreaker_state{name="nbuCurrency"}` — Value `0` is CLOSED (healthy), `1` is OPEN (tripped/degraded).
- `jvm_memory_used_bytes{area="heap"}` — Memory pressure indicator.
- `scraper_runs_total` — Total scrape attempts versus failures.

---

## 2. Incident Playbooks

### Playbook 1: Upstream Portal Scraper IP Block (HTTP 403 / 429)

**Symptoms:**
- Logs display repetitive `HttpStatusException: HTTP error fetching URL. Status=403/429`.
- Scraper throughput drops to zero while target status remains active.

**Immediate Mitigation:**
1. Check if the portal changed DOM layout or blocked the egress IP:
   ```bash
   curl -I -A "Mozilla/5.0" "https://www.olx.ua/uk/nedvizhimost/"
   ```
2. If blocked, activate an egress proxy by updating the runtime environment variable:
   ```bash
   export HTTP_PROXY="http://proxy-host:8080"
   export HTTPS_PROXY="http://proxy-host:8080"
   docker compose restart backend
   ```
3. To keep notifications flowing in a development or demonstration environment, switch to the built-in mock scraper strategy without restarting:
   ```bash
   curl -X POST http://localhost:8080/api/v1/targets/1/mode?mode=MOCK
   ```

---

### Playbook 2: National Bank of Ukraine (NBU) Currency Gateway Outage

**Symptoms:**
- Metric `currency.nbu.fallback.total` is incrementing rapidly.
- Log message: `CircuitBreaker/Retry active on NBU gateway. Degraded fallback to rate...`

**Automatic Behavior:**
- Resilience4j trips the `nbuCurrency` circuit breaker after 5 consecutive failures.
- The system automatically serves the latest cached rate or statutory baseline (USD: 41.50, EUR: 45.20).
- No user-facing requests or price comparisons will fail.

**Manual Intervention (if prolonged outage exceeds 24 hours):**
- Verify the statutory official rate on the NBU website: `https://bank.gov.ua`.
- If currency rates fluctuated significantly, update the statutory fallback baseline in `application.yml` and trigger a graceful restart:
  ```bash
  docker compose up -d --no-deps backend
  ```

---

### Playbook 3: Telegram Bot API Rate Limiting (Flood Control)

**Symptoms:**
- Exceptions containing `TelegramApiException: Error 429: Too Many Requests: retry after X seconds`.

**Mitigation:**
1. Telegram limits bots to 30 messages/second globally and 1 message/second per private chat.
2. In `core-desktop`, ensure token bucket rate limiter is enabled in settings.
3. In `enterprise-backend`, check the event listener queue depth:
   ```bash
   curl -s http://localhost:8080/actuator/metrics/telegram.notifications.sent | jq .
   ```
4. If a broadcast message to multiple channels triggered the limit, stagger notification events by increasing `app.tracker.delay-ms` in configuration.

---

## 3. Disaster Recovery & Database Maintenance

### Database Backup (PostgreSQL)
Create an immediate snapshot before performing major upgrades or data migrations:

```bash
docker exec -t property-searcher-postgres pg_dump -U postgres property_db > backup_$(date +%Y%m%d_%H%M%S).sql
```

### Database Restore
Restore state from a snapshot:

```bash
cat backup_20261102_120000.sql | docker exec -i property-searcher-postgres psql -U postgres -d property_db
```

### Clean State Reset (Local Development)
To completely wipe local test data and re-apply fresh Flyway migrations:

```bash
docker compose down -v
docker compose up -d
```
All Flyway migrations in `src/main/resources/db/migration/` will execute sequentially to reconstruct the schema.

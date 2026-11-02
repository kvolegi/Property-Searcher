# ADR 0004: Hybrid Relational & Document Schema (PostgreSQL JSONB)

## Status
Accepted

## Context
Real estate listings from OLX, DomRia, and private feeds have highly polymorphic schemas:
- Standard attributes: `id`, `url`, `price`, `currency`, `rooms`, `area`, `city`, `discovered_at`.
- Platform-specific dynamic attributes: heating type, floor number, wall material, agency commission, photos array, seller reputation score.

We needed a storage strategy that provides relational query power for indexing and statistical price history, while flexibly storing varied listing metadata without costly schema migrations.

## Decision Drivers
1. **Strong Typing for Core Queries**: Filtering by price range, city, and date must be indexable via standard B-Tree indexes.
2. **Polymorphic Flexibility**: Adding a new platform scraper must not require altering SQL tables.
3. **Audit Trail Integrity**: Foreign keys must bind price history revisions to property entities.

## Considered Options
1. **Pure Document DB (e.g. MongoDB)**: High schema flexibility, but weak relational constraints for user subscriptions, search targets, and complex aggregation queries.
2. **Entity-Attribute-Value (EAV) Pattern**: Relational, but notorious for poor query performance and painful multi-join queries.
3. **Hybrid PostgreSQL (Relational Core + JSONB Extension) (Chosen)**: Core normalized tables (`users`, `search_targets`, `discovered_properties`, `price_history`) with a `raw_payload JSONB` column on property records.

## Consequences
- **Positive**:
  - Full ACID guarantees on search targets, user accounts, and price tracking history.
  - Flexibility to store unstructured raw scraped fields and run GIN index queries on specific JSON keys when required.
  - Development mode remains seamless with an H2 dialect fallback.
- **Negative / Mitigations**:
  - Developers must avoid dumping query-critical fields exclusively into JSONB.
  - *Mitigation*: The domain mapping layer explicitly projects common attributes (`price`, `location`, `sqm`) into strongly typed entity columns.

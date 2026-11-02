# ADR 0004: Hybrid Relational & Document Schema (JSON Storage Pattern)

## Status
Accepted & Implemented

## Context
Real estate listings from OLX, DomRia, and private feeds have highly polymorphic schemas:
- Standard attributes: `id`, `external_id`, `platform`, `url`, `price`, `currency`, `category`, `discovered_at`.
- Platform-specific dynamic attributes: heating type, floor number, wall material, agency commission, photos array, seller reputation score.

We needed a storage strategy that provides relational query power for indexing and statistical price history, while flexibly storing varied listing metadata without costly schema migrations or dialect lock-in.

## Decision Drivers
1. **Strong Typing for Core Queries**: Filtering by price range, platform, and date must be indexable via standard B-Tree indexes.
2. **Polymorphic Flexibility**: Adding a new platform scraper must not require altering SQL tables.
3. **Audit Trail Integrity**: Foreign keys must bind price history revisions to property entities.
4. **Dialect Compatibility**: Seamless execution across production PostgreSQL and zero-configuration H2 in-memory test suites without proprietary driver conflicts.

## Considered Options
1. **Pure Document DB (e.g. MongoDB)**: High schema flexibility, but weak relational constraints for user subscriptions, search targets, and complex aggregation queries.
2. **Entity-Attribute-Value (EAV) Pattern**: Relational, but notorious for poor query performance and painful multi-join queries.
3. **Hybrid Relational Core + Jackson JSON Document Extension (Chosen)**: Core normalized tables (`users`, `search_targets`, `discovered_properties`, `price_history`) with a `raw_payload TEXT` column on property records storing Jackson-serialized JSON documents. In PostgreSQL, this allows `raw_payload::jsonb` operations and GIN indexing, while running cleanly in H2 for automated integration tests without proprietary extension dependencies.

## Migration Pipeline
- **Flyway V1__init_schema.sql**: Establishes the initial relational schema (`users`, `search_targets`, `discovered_properties`, `price_history`) and B-Tree indexes.
- **Flyway V2__add_raw_payload.sql**: Incrementally applies schema evolution by adding `raw_payload TEXT` to `discovered_properties`.

## Consequences
- **Positive**:
  - Full ACID guarantees on search targets, user accounts, and price tracking history.
  - Flexibility to store unstructured raw scraped fields via Jackson `ObjectMapper`.
  - Dialect portability: 100% of automated tests execute out-of-the-box on H2 without requiring external Docker services.
- **Negative / Mitigations**:
  - Developers must avoid dumping query-critical fields exclusively into unstructured payloads.
  - *Mitigation*: The domain mapping layer explicitly projects common attributes (`price`, `externalId`, `platform`, `category`) into strongly typed entity columns.

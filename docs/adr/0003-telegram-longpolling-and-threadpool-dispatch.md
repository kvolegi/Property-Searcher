# 3. Decoupled Telegram Notification Dispatcher via Asynchronous Thread Pool

Date: 2024-11-04
Status: Accepted

## Context
When new real-estate listings are detected, downloading ad galleries (up to 8 full-resolution photos) and sending Telegram Media Groups over network connections blocks the scraper thread. If Telegram encounters network jitter or rate limits, the entire search engine thread freezes.

## Decision
We extracted notification formatting and delivery into an asynchronous `TelegramNotificationDispatcher` backed by an unbounded cached thread pool (`Executors.newCachedThreadPool()`):
1. **Fire-and-Forget Dispatch:** Scraping loops pass found listing models into `dispatchAsync` and immediately continue parsing the next page or sleeping.
2. **Graceful Fallbacks:** The dispatcher attempts `SendMediaGroup` first. If media groups fail due to invalid photo formats, it seamlessly falls back to `SendPhoto`, and ultimately to clean HTML `SendMessage`.
3. **Night Mode Silencing:** Notifications dispatched between 23:00 and 07:00 automatically set `disableNotification = true`.

## Consequences
- Guaranteed zero GUI freeze and zero scraper stalling during Telegram API communication.
- Robust delivery of rich photo carousels even under intermittent mobile network latency.

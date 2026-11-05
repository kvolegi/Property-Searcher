# 📋 Agile User Stories & Sprint Roadmap (Property Searcher)

This document tracks all user stories, architectural epics, and acceptance criteria implemented in Property Searcher.

---

## 🏆 Epic 1: Enterprise GUI & Customization Architecture

### US-101: Dual-Theme Architecture (Mint & Slate Gray)
- **As a** real-estate analyst,
- **I want** to switch between Signature Mint and Corporate Slate Gray themes with customized typography,
- **So that** I can work comfortably in corporate office environments without cartoonish distraction.
- **Story Points:** 5 SP
- **Status:** `DONE` (Verified in v3.1.3)
- **Acceptance Criteria:**
  - [x] Selecting "Corporate Slate Gray" instantly re-renders all active windows, borders, and controls to slate gray `#CCD0D6`.
  - [x] All child dialogs (Token, Category Catalog, Smart Category Builder, Instructions) inherit active color theme.
  - [x] Button hover and pressed states maintain dynamic theme palette without reverting to inverted colors on mouse exit.

### US-102: Cross-Platform Atomic Settings Persistence
- **As a** user running Property Searcher on macOS, Windows, or Linux,
- **I want** my bot token, theme preferences, and search categories to persist permanently across restarts,
- **So that** I never have to re-enter credentials or recreate complex search filters.
- **Story Points:** 5 SP
- **Status:** `DONE` (Verified in v3.1.3)
- **Acceptance Criteria:**
  - [x] On first launch with no prior settings, the welcome dialog prompts for language & style, and credential inputs are pristine blank.
  - [x] Settings are stored in `~/.propertysearcher/settings.txt` (or `%USERPROFILE%\.propertysearcher`).
  - [x] Corrupted directives on lines 1 & 2 are automatically sanitized.

---

## 🚀 Epic 2: High-Performance Scraping & Sniping Engine

### US-201: Smart Category Builder for OLX & DOM.RIA
- **As a** property hunter,
- **I want** an intuitive category constructor with platform, city, deal type, and price range filters,
- **So that** I do not have to manually formulate and test complex search query strings in my browser.
- **Story Points:** 8 SP
- **Status:** `DONE` (Verified in v3.1.0)
- **Acceptance Criteria:**
  - [x] Selecting filters dynamically generates valid, sort-optimized search URLs (`view=list`, `created_at:desc`, `price_cur=1`).
  - [x] Direct owner / "Without agents" filter can be toggled on both platforms.

### US-202: Persistent Seen-Ad Deduplication & Anti-Ban Throttling
- **As a** continuous monitor,
- **I want** the search engine to remember seen listings across application restarts,
- **So that** I never receive duplicate Telegram notifications for listings I already reviewed.
- **Story Points:** 5 SP
- **Status:** `DONE` (Verified in v3.1.3)
- **Acceptance Criteria:**
  - [x] Found listing hashes are saved to `~/.propertysearcher/seen_ads.txt`.
  - [x] On startup, seen URLs are loaded into memory and deduplicated instantly.
  - [x] Polling frequency adapts dynamically based on the number of active categories to prevent IP blacklisting.

---

## 📱 Epic 3: Telegram Bot & Rich Media Delivery

### US-301: Asynchronous Media Group & Carousel Notification
- **As a** Telegram user,
- **I want** incoming property alerts to include high-resolution photo carousels and direct links,
- **So that** I can assess property condition at a glance on my smartphone.
- **Story Points:** 5 SP
- **Status:** `DONE` (Verified in v3.1.2)
- **Acceptance Criteria:**
  - [x] Up to 8 photos are packaged into a native `SendMediaGroup`.
  - [x] Automatic fallback to `SendPhoto` or HTML text if gallery parsing fails.
  - [x] Quiet night-mode delivery between 23:00 and 07:00.

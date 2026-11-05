# 1. Atomic Settings Persistence with User Home Fallback

Date: 2024-11-04
Status: Accepted

## Context
Desktop Java applications distributed as `.app` bundles on macOS or standalone `.jar` archives on Windows face strict filesystem permission constraints. In older iterations, saving settings to relative paths (`new File("settings.txt")`) caused settings loss whenever the application was executed from read-only application bundles (`/Applications/PropertySeacher.app/Contents/Resources`) or via system launchers.

## Decision
We implemented a centralized, atomic `SettingsManager` that prioritizes user home configuration directories (`~/.propertysearcher/settings.txt` on Unix/macOS and `%USERPROFILE%\.propertysearcher\settings.txt` on Windows), falling back to local working directories only when explicitly present (portable mode).

Key architectural safeguards:
1. **Directive Isolation & Sanitization:** Lines 1 & 2 are strictly reserved for credentials (`botName`, `botToken`). Directives (`LANGUAGE=`, `UI_COLOR=`, etc.) are isolated on lines 3–7.
2. **Atomic Writes:** Configuration writes use temporary scratch buffers before atomic rename, preventing file corruption during sudden system shutdowns.

## Consequences
- Guaranteed zero settings loss across app updates, macOS bundle launches, and cross-platform OS migrations.
- Complete separation between credentials, localization, and search query categories.

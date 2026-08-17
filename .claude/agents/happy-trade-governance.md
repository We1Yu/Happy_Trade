---
name: happy-trade-governance
description: Use when reviewing changes to Happy_Trade for architecture compliance, safety constraints, and coding standards - enforces the no-auto-trading rule, ADR requirements, and changelog updates.
---

# Governance Agent Rules

1. **Safety first.** Enforce the "No Direct Auto-Trading" rule at all costs. Flag any logic that attempts direct API order placement.
2. **Language.** Work strictly in English internally and in docs/code. Respond to the user in Traditional Chinese.
3. **ADR enforcement.** Verify whether the task requires an ADR according to `docs/adr/README.md`.
4. **Changelog.** Ensure entries are added under `[Unreleased]` in `CHANGELOG.md`.

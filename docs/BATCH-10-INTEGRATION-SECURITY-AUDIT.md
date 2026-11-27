# Batch 10 integration security audit

Checkpoint b270a8426cf9f9dbb10c4e1aaf42afc6ee6aa080. Code inspection only; CI pending.
Targets 16–20 add offline learning, a single research publication slot, a bounded analysis worker, four safety tests and this audit. This is not completion of operational integration.

## Release blockers
- Active scanner and Android foreground service do not yet own or feed the new worker. No additional polling was enabled. Locate actual service source (attempted desk/ScreeningService.kt returned 404).
- Repository must expose trusted clock bounds and atomically captured data; retain original kline taker volumes and synchronize depth with sequence checks and symmetric bands. Consolidate rate budget before adding network requests.
- Master assessment currently lacks immutable symbol/source identity; entry planner can accept an assessment from another symbol. Bind assessment, quote, account, venue, filters and liquidation estimate before any execution. Research publisher computes its own assessment and retains source but does not establish external input provenance.
- Revalidation checks time bounds, not new market events. Recheck latest book and market generation. Worker currently rejects overlapping submissions; audit read/submit/close races, callback failures and lock contention. No main-thread analysis.
- Learning data must originate from reconciled ledger, separated by venue and strategy. Wilson statistics are not live probabilities. No automatic promotion.
- Risk economics must be recomputed from verified costs and filters; atomic exposure reservation and durable execution remain mandatory.
- Four new tests exercise missing/expired evidence, duplicate groups, empty learning and closed worker; no positive end-to-end market fixture or Android service lifecycle test yet.
- Existing REAL entry lock unchanged. No production deployment, order authorization, signed release or proven win rate.

## Acceptance
CI for exact code SHA; positive and negative integrated fixtures; real public data and lifecycle wiring; device resource and reconnect testing; backtest and paper validation; final execution and auth audit. Batch 10 roadmap artifacts may be counted as drafted, but operational acceptance remains open. Do not mark the application ready based on compilation.

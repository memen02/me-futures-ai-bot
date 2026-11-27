# Batch 06 — learning foundation audit

Status: foundation code only; no live promotion or execution authorization.

- Experience evaluation uses reconciled net outcomes supplied by caller; authoritative ledger adapter is missing.
- Memory cache and SQLite derived store reject conflicting duplicate IDs and overflow. SQLite cap is 10,000 total rows. No silent deletion. Account-scoped identity, backup exclusion, retention, worker lifecycle, migration and database failure handling must be integrated and tested.
- Strategy versions are metadata in RAM; immutable configuration hashes and durable approval records are missing.
- Shadow comparison covers matched trades only, not skipped opportunities; selection bias, costs, independent holdout and walk-forward validation must be addressed.
- Review guard checks supplied duration and summary; does not prove data coverage or independence. No automatic promotion.
- Confidence interval assumes independent samples; not a calibrated live probability. Score bins and temporal validation required.
- Swing context requires D1/D3/W1; monthly context, entry timing, scenario targets and UI integration remain pending.
- Rollback advisor does not block orders itself. Entry freeze and reconciliation must be enforced by execution layer.
- All risk limits, independent evidence requirements and duplicate-order protections remain mandatory.
- Release gate: integrate scanner and ledger, validate persistence and restart, run full tests, DEMO recovery and at least 30 days paper evaluation with sufficient samples. No promise of 80% win rate.
- CI verification pending for final commits; this document is not a certification.

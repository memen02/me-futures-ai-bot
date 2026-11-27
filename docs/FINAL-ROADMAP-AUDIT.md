# Final roadmap audit — foundation milestone, not release approval

180/180 planned targets is a bookkeeping milestone, not measured product readiness. Latest verified pre-audit Android checkpoint a065073, run 45995961745: 3566 test executions across four variants, zero failures/errors/skips. Commit 20713fa adds two tests; CI pending. No full repository security certification has been performed. This review combines current backtest code inspection and prior batch audit records.

## Integration and release gates
1. Monitoring: verify credential reads off main thread, lifecycle cancellation, stale callbacks, account/venue isolation, screen lock and reboot on device.
2. Indicators: wire bounded closed-candle cache and scheduler to actual scanner; validate freshness and exchange timestamps.
3. Trend: validate regime, structure and multi-timeframe signals on historical and live public data.
4. Decision: integrate independent evidence, stale/conflict vetoes and explanations; no correlated indicator double-counting.
5. Risk: enforce net RR >=2, risk 0.5–1%, leverage <=20 at actual entry; exchange filters, account exposure, liquidation and fill reconciliation.
6. Learning: see BATCH-06-LEARNING-AUDIT.md. Durable versioning, authoritative outcomes, shadow comparison and independent validation; no automatic promotion.
7. Execution: see BATCH-07-EXECUTION-AUDIT.md. New submission engine deliberately fail closed. Durable immutable order payload, stable account identity, exclusive ownership, serialized entry authorization, reconciliation and protective exits required. Legacy path requires separate audit. No blind retry or duplicate orders.
8. Authentication: see BATCH-08-SECURITY-AUDIT.md. Backend not deployed; HTTP service, verified recovery, renewal/revocation, abuse protection, startup/admin UI and server-enforced entry authorization outstanding. Preserve safe position management on auth loss.
9. Backtest: offline adapter and coordinator exist. No authenticated historical download or proven funding coverage. Trainer callback can access external data; chronological API alone does not prove absence of leakage. Require immutable dataset/config hashes, trusted trainer, independent holdout, actual strategy integration, funding completeness, realistic liquidation/margin, intrabar drawdown, exchange rounding and gap stress. Next-open entry filter is an idealized execution assumption; no fill guarantee. Funding model conservatively excludes credits and may count boundary debits twice across trades. Capital simulation can become negative and does not model liquidation. Fold capital resets; do not combine as compounded returns. 1500-bar cap limits research coverage. Reports are caller-supplied metadata, not authenticated evidence. Guardian returns only BLOCKED or RESEARCH_REVIEW_ONLY. Three folds with 100 trades each and 80% win rate are provisional research thresholds, not proof of future performance.

## Acceptance sequence
- Integrate public scanner and AI decision path; test DEV APK on device.
- Integrate auth backend and UI; select hosting with owner approval.
- Integrate durable execution and risk protection in DEMO; test crash, network loss, partial fills, duplicate attempts, restart and account isolation.
- Verify resource budgets and 24–72 hour device stability; run at least 30 days paper research with sufficient independent samples before production strategy release.
- Review dependency vulnerabilities, secrets, manifest, backup, signing, network security and all actual trading entry paths; independent review and device tests required.
- One production APK may expose DEMO and REAL after release gates, with explicit user opt-in; no mandatory per-user demo duration or automatic activation from API key. No REAL authorization change in this milestone.

No production deployment, actual backtest result, signed release APK, or guaranteed win rate is claimed. Remaining work must be tracked as integration milestones, not hidden by the foundation target count.

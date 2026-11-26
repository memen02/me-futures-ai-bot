# ME Futures Adaptive Intelligence

Status: proposed development requirements; not implemented or validated by this document.

## Intelligence modules

- Market regime, trend strength, continuation, reversal, SMC, liquidity, order flow, large trade detection, funding and open interest, fake breakout and liquidation-risk filters.
- Multi-timeframe analysis, independent evidence groups, correlation guard and master LONG/SHORT/WAIT decision.
- Self evaluation and market memory: persist timestamped features, decisions, outcomes and net costs; avoid future-data leakage.
- Adaptive weights, strategy switching and multi-strategy consensus: bounded updates, minimum sample requirements and versioned configurations.
- Confidence calibration, loss pattern detection, market anomaly detection and model drift detection.
- Entry timing and exit intelligence; reject stale data, excessive spread and slippage.
- Shadow AI and champion/challenger: evaluate candidates offline and in paper mode before explicit promotion; rollback on degradation.
- Explainable AI: record evidence, vetoes, uncertainty and strategy version.

## Safety and acceptance gates

- Five to six sufficiently independent evidence groups; correlated indicators must not be double-counted. WAIT when evidence is insufficient.
- Risk per trade 0.5–1% of equity, leverage ceiling 20x, minimum planned net reward/risk 2:1; reject infeasible trades. Include fees, funding, spread and slippage.
- Position sizing, trailing protection, daily loss and drawdown limits, reconciliation and emergency entry stop. Do not weaken existing execution or approval safeguards.
- Walk-forward and held-out evaluation with realistic execution, no look-ahead, minimum sample sizes, net expectancy, drawdown, calibration and uncertainty reporting.
- Target win rate 80%+ is aspirational, not a promise or sole promotion criterion.
- At least 30 days of paper validation, extended when sample size is insufficient. No automatic promotion to real-money trading.
- Shared Kotlin implementation for DEV and REAL; preserve venue isolation and credential security. No credentials or private account data in logs or repository.
- Android resource budgets: bounded caches, queues and workers; rate-limit handling, battery and memory tests on target hardware.

## Delivery order

Complete account monitoring integration/concurrency tests and security lifecycle first. Audit existing AI modules to avoid duplication. Implement feature/data contracts, deterministic engines, risk vetoes, backtest and replay, shadow evaluation, bounded adaptation, UI explanations, then paper validation. Each increment requires source review, tests and CI evidence before being marked complete.

## Smart Resource Manager and Auto Cleaner

Proposed requirements, not implemented yet. Target Android devices with limited RAM, including 4 GB devices.

- Bound memory caches by size, age and priority. Respond to Android memory-pressure callbacks by releasing nonessential cached objects; do not force garbage collection or clear other apps.
- Expire disposable market cache entries with per-data-type TTLs; preserve indicator warm-up windows and mark missing or stale data unavailable until refreshed.
- Bound worker queues, cancel obsolete tasks, close unused sockets and executors, and prevent duplicate connections. Preserve active position monitoring and reconciliation.
- Rotate sanitized logs by size and age. Deduplicate market data using stable identifiers. Perform database maintenance off the UI thread and only when safe; use transactional retention policies.
- Monitor app heap, cache sizes, queue depths, storage, reconnect counts, thermal status and task latency. Use supported Android APIs and avoid requesting root or broad storage access.
- Reduce nonessential scan frequency and parallelism under thermal, memory or battery pressure; never silently relax risk checks. Block new entries when critical data or monitoring is degraded.
- Schedule bounded, non-overlapping cleanup during idle opportunities; use monotonic clocks and backoff. Android background execution is not guaranteed.
- Never delete API credentials, consent records, active orders, positions, reconciliation state, trade ledger, audit evidence, strategy versions or training data needed for reproducibility. No automatic clearing of all app data.
- Provide a resource-health dashboard, last cleanup time, reclaimed cache bytes, and a safe manual cleanup action. Report unavailable metrics honestly.
- Test low-memory behavior, repeated start/stop, reconnect storms, cleanup during active monitoring, database integrity, bounded storage and long-duration device stability.
- Audit existing cache and worker ownership before implementation. Preserve all intelligence modules and safety gates above.

## Premium Trading Dashboard and UX requirements

Proposed UI requirements; audit existing screens and preserve working features before implementation.

- Dark black/gold theme, modern consistent icons, Indonesian plain-language labels, accessible contrast, scalable text, touch targets and screen-reader descriptions. Do not rely on color alone.
- Five primary tabs: Home, Market, AI, Trading, Account. Progressive disclosure keeps the overview simple and advanced detail available.
- Home: explicit PAPER/DEMO/REAL labels, connection and freshness, venue-specific balance/equity/available margin, realized and unrealized PnL, fees, performance, active positions, bot controls and monitoring health. Never mix simulated and real balances.
- Market: searchable/filterable token scanner, watchlist, 15 timeframe analysis, candles, trend/regime, liquidity, volume, funding/OI, spread and data freshness.
- AI: LONG/SHORT/WAIT, independent evidence groups, explanations, veto reasons, calibrated confidence only when validated, strategy version, learning progress, shadow evaluation, drift alerts and per-engine details. Distinguish evidence count from win probability.
- Trading: separate signal monitoring and auto-trading modes, position size, leverage, entry/SL/TP, net RR, risk, fees, orders, reconciliation, history and emergency entry stop. Confirm sensitive actions and preserve existing real-trading approval gates.
- Account: credential security, App Lock, venue settings, reports, paper statistics and Device Health (heap/cache/storage/thermal/connection/worker metrics, last cleanup and safe cleanup action).
- Reports: equity curve, realized net PnL, drawdown, win rate with sample count, profit factor, expectancy, costs and strategy breakdown; show unavailable data explicitly.
- Use real measured values only in production; distinguish loading, stale, unavailable, error and empty states. Never display fake account metrics or expose secrets.
- Optimize rendering for 4 GB Android devices: bounded lists, lifecycle-aware updates, lazy detail screens, no unnecessary animations, no blocking UI thread. Test accessibility, small screens, lock/background transitions, venue switching and low-memory behavior.

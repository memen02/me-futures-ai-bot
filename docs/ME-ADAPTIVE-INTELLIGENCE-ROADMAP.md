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

## Swing Trading Intelligence and Token Detail

Proposed requirements; no forecast accuracy or implementation is established by this document.

- Use closed 1D, 3D and 1W candles for primary directional bias and multi-day swing scenarios; 1M for broader context and 4H/1H for entry refinement. Preserve existing intraday modes and timeframe support. Explicitly label incomplete candles and never use future data.
- Token detail: verified original project logo mapped to exchange instrument identity, cached with bounded storage and safe fallback; actual Binance OHLCV candlestick chart, zoom/pan/crosshair, timeframe selector, OHLC and volume, EMA/SMA, optional overlays, support/resistance, liquidity, BOS/CHoCH, entry/SL/TP and invalidation levels.
- Candlestick registry: single-candle hammer, inverted hammer, hanging man, shooting star, doji variants, spinning top, marubozu; two-candle bullish/bearish engulfing, harami, piercing line, dark cloud cover, tweezers, inside/outside bars; three/multi-candle morning/evening star, three white soldiers/black crows, rising/falling three methods. Extend with documented definitions, tolerances, mirrored cases, context and deterministic tests; avoid claiming exhaustive coverage of all named variants.
- Explain every token decision: bullish/bearish/sideways regime, trend strength, continuation/reversal/pullback/countertrend/breakout/fake breakout, candle pattern and timestamp, evidence for and against LONG/SHORT, independent confirmations, vetoes, uncertainty, data freshness and strategy version.
- Multi-day scenarios for 1–3, 3–7 and 7–14 days: conditional bullish/base/bearish paths, price zones, invalidation and calibrated uncertainty when supported by held-out validation. No guaranteed future prices or exact entry/exit times.
- Entry planner: conditional price zone, closed-candle confirmation, retest/pullback or breakout trigger, order expiry and WAIT state; cancel plan if regime changes, data stale, invalidation reached or net RR below minimum.
- Stop loss set before entry from market structure and volatility, with exchange filters and liquidation buffer. Position sizing based on stop distance and all costs; reject infeasible risk. Protective orders and reconciliation must follow existing execution safety gates. Never widen SL merely to avoid realizing loss.
- Exit planner: target zones, optional tested partial exits and trailing stops, time-based expiry and thesis invalidation; account for multi-day funding costs, gaps/slippage, margin and liquidation risk. Distinguish planned versus actual fills.
- Evaluate swing strategies separately from intraday strategies with walk-forward tests, realistic costs, non-overlapping or appropriately purged labels, sufficient independent trades and paper validation longer than 30 days when needed. No automatic real-money promotion.
- Use lazy chart loading, bounded candle history and incremental indicator calculations for 4 GB devices. Preserve all existing intelligence, cleaner, UI and safety requirements.

## Universal Trend Intelligence Engine

Proposed implementation requirements; preserve all preceding modules.

- Analyze every supported timeframe independently using closed candles, then reconcile higher-timeframe context and lower-timeframe entry evidence. Support intraday and multi-day swing horizons separately.
- Represent direction (bullish, bearish, neutral, unknown), strength (weak/moderate/strong), regime (trending/ranging/transitional), structure (HH/HL, LH/LL, BOS/CHoCH), phase (emerging, established, accelerating, decelerating, exhausted), and event (continuation, reversal, pullback, countertrend, breakout, failed breakout) as separate dimensions rather than mutually exclusive labels.
- Detect volatility compression/expansion, possible accumulation/distribution and liquidity sweeps as conditional hypotheses, not certainty about hidden participant intent.
- Combine structure, EMA/SMA slopes, ADX/DMI, momentum, ATR-normalized movement, volume/order flow and liquidity evidence; avoid double-counting correlated indicators. Use hysteresis, minimum history and explicit unknown/stale states.
- Detect timeframe disagreement, distinguish a lower-timeframe correction from higher-timeframe reversal, and require strategy-specific confirmation before entry. WAIT when conflict or uncertainty is unresolved.
- Token detail must show trend per timeframe, dominant trend, strength, phase, evidence for/against, invalidation, conditional entry/SL/TP, estimated holding horizon, timestamp and strategy version in plain Indonesian.
- Validate labels on synthetic and historical sequences, mirrored trends, flat/choppy markets, gaps, missing data and transitions; test no look-ahead, walk-forward net expectancy and calibration by regime. Do not treat trend classification as a guaranteed forecast.
- Bound computation and cache usage on 4 GB Android devices. No automatic promotion to REAL; preserve risk and execution gates.

## Full 15-Timeframe Analysis Matrix

Require 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M for each eligible instrument. Maintain independent timestamped results per timeframe: direction, strength, phase, structure, candle patterns, applicable indicators, support/resistance, volatility, volume and available liquidity evidence, LONG/SHORT/WAIT rationale and freshness. Distinguish unsupported or insufficient data from neutral signals. Do not invent historical order-flow features when data is unavailable.

Aggregate by strategy horizon, not majority vote: monthly/weekly/daily context for swing, intermediate structure and lower-timeframe entry timing; separate intraday alignment rules. Avoid correlated evidence duplication across timeframes. Analyze all intervals logically concurrently using bounded scheduling, incremental calculations, closed-candle updates and prioritized active-position monitoring, not 15 unrestricted workers per symbol. Respect exchange rate limits and device budgets. Show coverage, last candle time, conflicts and missing data in token detail. Block entries when required evidence is stale or absent. Test all interval boundaries including calendar months, warm-up, reconnect, missing candles, no-look-ahead and conflicting trends. Proposed requirements only; implementation and CI verification pending.

## Private Distribution and Owner-Approved Licensing

Distribute signed APKs via GitHub Releases; access restricted to owner-approved users even if APK is copied. Prefer private repository/release access if download itself must be restricted. Implement server-side entitlement verification with authenticated owner-only administration, revocable per-user licenses, optional expiry, device limits and authorized device reset. Use cryptographically random activation codes, rate limiting, signed short-lived entitlements, TLS and Android Keystore-backed installation keys; never embed admin secrets or signing private keys in APK/repository. No invasive hardware identifiers. Separate license service from Binance credentials; never transmit exchange secrets to licensing server. Fail closed for new entries on expired/revoked entitlement; preserve safe management/reconciliation of existing positions and access to emergency exit. Document offline grace and revocation latency, server outages, privacy, account recovery and release signing. Local-only checks cannot guarantee resistance to modified APKs. Backend provider, operating cost, offline policy and device limit require owner approval before implementation. Add activation UI and owner administration UI with audit log. Test activation, replay, expiry, revocation, copied APK, device migration and outage. Proposed only; no backend or licensing implementation deployed.

### Owner-confirmed single APK access model

Use one signed APK and one GitHub release channel for all users. The owner has a server-verified Super Admin role and additional in-app administration screens; ordinary users cannot self-assign this role. Bootstrap the owner securely outside public registration and require strong authentication for administrative actions. Registration requests remain pending until owner approval. Separate application entitlement from automated trading permission: denied application entitlement blocks normal features and new entries; approved application entitlement with automated trading denied permits authorized scanner/chart/signal features but no automated entries. Enforce permissions in service/entry paths as well as UI. Preserve safe position management, reconciliation and emergency exits after revocation or expiry. Owner can approve, reject, revoke and restore access, manage device limits and license duration, and grant feature permissions. Audit administrative changes; do not expose or control users' Binance secrets or balances through the licensing panel. Server and offline policy remain undecided. This is a documented requirement, not implemented functionality.

### Simplified access model — supersedes previous licensing feature requirements

Owner decision: one APK, one owner-only Super Admin role, registration and owner approval before application login. Pending or rejected users receive no authenticated application session. Approved users may log in and use all normally available features subject to existing trading safety gates. Admin UI lists requests and users with approve, reject, revoke and restore actions. No activation codes, license expiry, device limits or separate feature/trading permissions required. Server-side authentication, authorization and session revocation remain mandatory; securely bootstrap owner and protect admin account. Revocation prevents new sessions and new entries, while preserving narrowly scoped safe management/reconciliation and emergency exit of existing positions without granting normal app access. No Binance secrets transmitted to auth server. Backend and session revalidation/outage policy remain undecided. Documentation only, not implemented.

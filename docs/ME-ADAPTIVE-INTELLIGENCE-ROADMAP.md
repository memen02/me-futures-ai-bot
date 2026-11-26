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

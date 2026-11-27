package com.mefuturesaibot.app.ai.backtest

internal enum class BacktestSide { LONG, SHORT }
internal data class BacktestSignal(val side: BacktestSide, val stop: Double, val target: Double)
internal data class BacktestTrade(val entryTime: Long, val exitTime: Long,
    val side: BacktestSide, val entry: Double, val exit: Double, val netReturn: Double)

// Research-only, single position, next-bar open entry, stop-first ambiguity.
// Fixed per-side fee and adverse slippage assumptions; no leverage or funding model.
internal object BacktestSimulationEngine {
    fun run(data: HistoricalMarketData, fee: Double, slippage: Double,
        strategy: (List<HistoricalBar>) -> BacktestSignal?): List<BacktestTrade> {
        require(fee.isFinite() && fee in 0.0..0.01)
        require(slippage.isFinite() && slippage in 0.0..0.01)
        val bars = data.bars
        val trades = mutableListOf<BacktestTrade>()
        var i = 1
        while (i < bars.size) {
            val signal = strategy(bars.subList(0,i))
            if (signal == null) { i++; continue }
            val long = signal.side == BacktestSide.LONG
            val entry = bars[i].open * (if (long) 1+slippage else 1-slippage)
            if (!entry.isFinite() || !signal.stop.isFinite() || !signal.target.isFinite() ||
                signal.stop <= 0 || signal.target <= 0 ||
                !(if (long) signal.stop < entry && signal.target > entry else signal.target < entry && signal.stop > entry)) {
                i++
                continue
            }
            val start = i
            while (i < bars.size) {
                val bar = bars[i]
                val stopped = if (long) bar.low <= signal.stop else bar.high >= signal.stop
                val targeted = if (long) bar.high >= signal.target else bar.low <= signal.target
                val raw = when {
                    stopped -> if (long) minOf(bar.open,signal.stop) else maxOf(bar.open,signal.stop)
                    targeted -> signal.target
                    i == bars.lastIndex -> bar.close
                    else -> null
                }
                if (raw != null) {
                    val exit = raw * (if (long) 1-slippage else 1+slippage)
                    val net = (if (long) exit-entry else entry-exit)/entry - fee*(1+exit/entry)
                    check(net.isFinite())
                    trades.add(BacktestTrade(bars[start].openTime,bar.openTime+data.intervalMs,
                        signal.side,entry,exit,net))
                    i++
                    break
                }
                i++
            }
        }
        return trades
    }
}

package com.mefuturesaibot.app.ai

import java.math.BigDecimal
import java.util.TreeMap

internal data class DepthDelta(val first: Long,val last: Long,val previous: Long,val time: Long,
    val bids: List<Pair<BigDecimal,BigDecimal>>,val asks: List<Pair<BigDecimal,BigDecimal>>)

internal class ResearchDepthBook {
    private val guard = UnifiedDepthSequenceGuard()
    private val bids = TreeMap<BigDecimal,BigDecimal>(reverseOrder())
    private val asks = TreeMap<BigDecimal,BigDecimal>()
    private val pending = ArrayDeque<DepthDelta>()
    private var anchored = false
    private var time = 0L
    @Synchronized fun clear() {
        guard.invalidate(); bids.clear(); asks.clear(); pending.clear(); anchored=false; time=0
    }
    private fun valid(levels: List<Pair<BigDecimal,BigDecimal>>) = levels.size <= 1000 &&
        levels.all { (p,q) -> p.signum()>0 && q.signum()>=0 && p.precision()<=32 && q.precision()<=32 &&
            p.scale() in -16..16 && q.scale() in -16..16 }
    private fun apply(map: MutableMap<BigDecimal,BigDecimal>, levels: List<Pair<BigDecimal,BigDecimal>>) {
        for ((p,q) in levels) if (q.signum()==0) map.remove(p) else map[p]=q
    }
    @Synchronized fun delta(d: DepthDelta) {
        if (!valid(d.bids)||!valid(d.asks)||d.time<=0) { clear(); return }
        if (!anchored) {
            if (pending.size>=64) clear()
            pending.addLast(d); return
        }
        if (!guard.accept(d.first,d.last,d.previous)) {
            if (!guard.synchronized()) clear()
            return
        }
        if (d.time<time) { clear(); return }
        apply(bids,d.bids); apply(asks,d.asks); time=d.time
        if (bids.size>1000||asks.size>1000||bids.isEmpty()||asks.isEmpty()||bids.firstKey()>=asks.firstKey()) clear()
    }
    @Synchronized fun needsSnapshot() = !anchored && pending.isNotEmpty()
    @Synchronized fun anchor(id: Long, buy: List<Pair<BigDecimal,BigDecimal>>,sell: List<Pair<BigDecimal,BigDecimal>>) {
        if (id<=0||!valid(buy)||!valid(sell)||buy.isEmpty()||sell.isEmpty()) { clear(); return }
        val buffered=pending.toList()
        clear(); guard.reset(id); anchored=true
        apply(bids,buy); apply(asks,sell)
        for (d in buffered) {
            if (d.last<id) continue
            delta(d)
            if (!anchored) break
        }
    }
    @Synchronized fun liquidity(): com.mefuturesaibot.app.ai.decision.LiquiditySnapshot? {
        if (!guard.synchronized()) return null
        return ResearchLiquidityBand.calculate("BTCUSDT",time,bids.entries.map { it.key.toDouble() to it.value.toDouble() },
            asks.entries.map { it.key.toDouble() to it.value.toDouble() },true)
    }
}

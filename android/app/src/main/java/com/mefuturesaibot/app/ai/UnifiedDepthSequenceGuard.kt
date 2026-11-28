package com.mefuturesaibot.app.ai

// Sequence validation only. The transport must buffer events and rebuild depth
// from REST on gaps. This class alone does not establish a synchronized book.
internal class UnifiedDepthSequenceGuard {
    private var anchor: Long? = null
    private var last: Long? = null
    @Synchronized fun reset(snapshotId: Long) {
        anchor = snapshotId.takeIf { it > 0 }
        last = null
    }
    @Synchronized fun accept(first: Long, final: Long, previous: Long): Boolean {
        val start = anchor ?: return false
        val prior = last
        if (first <= 0 || final < first || previous < 0) {
            invalidate()
            return false
        }
        if (prior == null && final < start) return false
        val valid = if (prior == null) first <= start && final >= start
            else previous == prior && final > prior
        if (!valid) { invalidate(); return false }
        last = final
        return true
    }
    @Synchronized fun invalidate() { anchor = null; last = null }
    @Synchronized fun synchronized(): Boolean = last != null
}

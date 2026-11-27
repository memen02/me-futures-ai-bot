package com.mefuturesaibot.app.ai.learning

internal enum class RollbackAction { KEEP, FREEZE_NEW_ENTRIES, REVIEW_PARENT }
internal data class RollbackAdvice(val action: RollbackAction, val candidateVersion: String? = null)

// Advice only. A parent is not automatically safe or approved for live trading.
internal object StrategyRollbackAdvisor {
    fun assess(activeVersion: String, registry: StrategyVersionRegistry,
               drift: DriftStatus, reconciliationComplete: Boolean): RollbackAdvice {
        val active = registry.find(activeVersion)
        if (!reconciliationComplete || active == null || drift == DriftStatus.INSUFFICIENT)
            return RollbackAdvice(RollbackAction.FREEZE_NEW_ENTRIES)
        if (drift == DriftStatus.STABLE) return RollbackAdvice(RollbackAction.KEEP)
        val parent = active.parentId?.let { registry.find(it) }
            ?: return RollbackAdvice(RollbackAction.FREEZE_NEW_ENTRIES)
        return RollbackAdvice(RollbackAction.REVIEW_PARENT,parent.id)
    }
}

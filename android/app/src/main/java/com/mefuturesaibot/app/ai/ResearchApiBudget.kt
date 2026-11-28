package com.mefuturesaibot.app.ai

// Process-wide budget for the new research path only. Existing private order
// protection must not be throttled behind optional analysis.
internal object ResearchApiBudget {
    val guard = AiRateLimitGuard(minSpacingMs=1000L, weightBudget=60)
}

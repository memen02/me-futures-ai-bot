package com.mefuturesaibot.app.account

/**
 * Process-wide cooldown ownership for private
 * account monitoring.
 *
 * Session replacement must not replace this guard.
 *
 * Both listen-key management and balance polling
 * must receive the same guard instance.
 *
 * This scope is intentionally separate from order
 * execution and public market-data rate limits.
 */
internal class AccountMonitoringRateLimitScope(
    val guard: AccountRateLimitGuard =
        AccountRateLimitGuard()
) {

    companion object {

        /**
         * Survives activity recreation, venue changes
         * and monitoring-session replacement within
         * the current application process.
         */
        val processWide: AccountMonitoringRateLimitScope
            by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
                AccountMonitoringRateLimitScope()
            }
    }
}

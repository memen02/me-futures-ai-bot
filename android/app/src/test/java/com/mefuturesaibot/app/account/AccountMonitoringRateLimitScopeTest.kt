package com.mefuturesaibot.app.account

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AccountMonitoringRateLimitScopeTest {

    @Test
    fun scopePreservesInjectedGuardIdentity() {
        val guard = AccountRateLimitGuard({ 0L }, { 0L })

        val scope =
            AccountMonitoringRateLimitScope(guard)

        assertSame(guard, scope.guard)
        assertSame(scope.guard, scope.guard)
    }

    @Test
    fun bothConsumersReceiveSameGuard() {
        val guard = AccountRateLimitGuard({ 0L }, { 0L })

        val scope =
            AccountMonitoringRateLimitScope(guard)

        val sessionGuard = scope.guard
        val balanceGuard = scope.guard

        assertSame(sessionGuard, balanceGuard)
    }

    @Test
    fun processWideScopeIsStable() {
        val first =
            AccountMonitoringRateLimitScope.processWide

        val second =
            AccountMonitoringRateLimitScope.processWide

        assertSame(first, second)
        assertSame(first.guard, second.guard)
    }

    @Test
    fun processWideScopeIsSharedAcrossThreads() {
        val start = CountDownLatch(1)
        val executor =
            Executors.newFixedThreadPool(4)

        try {
            val futures = (1..16).map {
                executor.submit<
                    AccountMonitoringRateLimitScope
                > {
                    check(
                        start.await(
                            5,
                            TimeUnit.SECONDS
                        )
                    )

                    AccountMonitoringRateLimitScope
                        .processWide
                }
            }

            start.countDown()

            val first = futures.first().get(
                5,
                TimeUnit.SECONDS
            )

            futures.forEach {
                val next = it.get(
                    5,
                    TimeUnit.SECONDS
                )

                assertSame(first, next)
                assertSame(first.guard, next.guard)
            }
        } finally {
            start.countDown()
            executor.shutdownNow()

            assertTrue(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            )
        }
    }

    @Test
    fun injectedScopeDoesNotReplaceProcessWideGuard() {
        val process =
            AccountMonitoringRateLimitScope.processWide

        val guard = AccountRateLimitGuard({ 0L }, { 0L })

        val injected =
            AccountMonitoringRateLimitScope(guard)

        assertNotSame(process, injected)

        assertNotSame(
            process.guard,
            injected.guard
        )

        assertSame(
            process,
            AccountMonitoringRateLimitScope.processWide
        )
    }
}

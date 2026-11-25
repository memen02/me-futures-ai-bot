package com.mefuturesaibot.app.ai

import java.time.Instant
import java.time.ZoneOffset

internal enum class Timeframe(
    val api: String,
    val durationMs: Long?
) {
    M1("1m", 60_000L),
    M3("3m", 180_000L),
    M5("5m", 300_000L),
    M15("15m", 900_000L),
    M30("30m", 1_800_000L),

    H1("1h", 3_600_000L),
    H2("2h", 7_200_000L),
    H4("4h", 14_400_000L),
    H6("6h", 21_600_000L),
    H8("8h", 28_800_000L),
    H12("12h", 43_200_000L),

    D1("1d", 86_400_000L),
    D3("3d", 259_200_000L),
    W1("1w", 604_800_000L),
    MN1("1M", null);

    fun validOpen(openTime: Long): Boolean {
        if (openTime < 0L) return false

        return when (this) {
            MN1 -> {
                val date = Instant.ofEpochMilli(openTime)
                    .atZone(ZoneOffset.UTC)

                date.dayOfMonth == 1 &&
                    date.toLocalTime() ==
                    java.time.LocalTime.MIDNIGHT
            }

            W1 -> {
                val date = Instant.ofEpochMilli(openTime)
                    .atZone(ZoneOffset.UTC)

                date.dayOfWeek ==
                    java.time.DayOfWeek.MONDAY &&
                    date.toLocalTime() ==
                    java.time.LocalTime.MIDNIGHT
            }

            D3 -> openTime % 86_400_000L == 0L

            else -> openTime % requireNotNull(durationMs) == 0L
        }
    }

    fun nextOpen(openTime: Long): Long {
        require(validOpen(openTime))

        return if (this == MN1) {
            Instant.ofEpochMilli(openTime)
                .atZone(ZoneOffset.UTC)
                .plusMonths(1)
                .toInstant()
                .toEpochMilli()
        } else {
            Math.addExact(
                openTime,
                requireNotNull(durationMs)
            )
        }
    }

    companion object {
        fun fromApi(value: String): Timeframe? =
            entries.firstOrNull { it.api == value }
    }
}

package com.mefuturesaibot.app.ai

internal class MultiTimeframePlanner(
    private val maxSymbols: Int = 3
) {
    init {
        require(maxSymbols in 1..8)
    }

    internal data class Task(
        val symbol: String,
        val timeframe: Timeframe
    )

    private var symbols = emptyList<String>()

    private var cursor = 0

    private val priority = listOf(
        Timeframe.D1,
        Timeframe.H4,
        Timeframe.H1,
        Timeframe.M15,
        Timeframe.M5,
        Timeframe.M3,
        Timeframe.M1,
        Timeframe.M30,
        Timeframe.H2,
        Timeframe.H6,
        Timeframe.H8,
        Timeframe.H12,
        Timeframe.D3,
        Timeframe.W1,
        Timeframe.MN1
    )

    @Synchronized
    fun updateSymbols(
        candidates: List<String>
    ) {
        val next = candidates.asSequence()
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    it.length <= 64
            }
            .distinct()
            .take(maxSymbols)
            .toList()

        if (next == symbols) return

        // Pertahankan urutan apabila kandidat
        // yang sama hanya berubah ranking.
        if (next.toSet() == symbols.toSet()) {
            return
        }

        val frameIndex =
            if (symbols.isEmpty()) 0
            else cursor / symbols.size

        val symbolIndex =
            if (symbols.isEmpty()) 0
            else cursor % symbols.size

        val nextSymbol =
            symbols.getOrNull(symbolIndex)

        symbols = next

        cursor = if (symbols.isEmpty()) {
            0
        } else {
            val preservedIndex =
                symbols.indexOf(nextSymbol)

            val offset =
                if (preservedIndex >= 0) {
                    preservedIndex
                } else {
                    symbolIndex % symbols.size
                }

            (frameIndex % priority.size) *
                symbols.size + offset
        }
    }

    @Synchronized
    fun activeSymbols(): List<String> =
        symbols.toList()

    @Synchronized
    fun next(
        available: Map<String, Set<Timeframe>>,
        excluded: Set<Task> = emptySet()
    ): Task? {

        if (symbols.isEmpty()) return null

        val total =
            symbols.size * priority.size

        repeat(total) {
            val index = cursor

            cursor = (cursor + 1) % total

            val symbol =
                symbols[index % symbols.size]

            val timeframe =
                priority[index / symbols.size]

            val task = Task(
                symbol,
                timeframe
            )

            if (
                timeframe !in
                    available[symbol].orEmpty() &&
                task !in excluded
            ) {
                return task
            }
        }

        return null
    }

    @Synchronized
    fun clear() {
        symbols = emptyList()
        cursor = 0
    }
}

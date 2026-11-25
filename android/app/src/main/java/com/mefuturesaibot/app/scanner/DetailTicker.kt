package com.mefuturesaibot.app.scanner

/**
 * Select a valid ticker for the detail screen.
 * Never replace a displayed ticker with older data.
 * Display-only: does not affect execution or training.
 */
internal fun detailTicker(
    snapshot: Scan,
    symbol: String,
    elapsed: Long,
    previous: Ticker?
): Ticker? {
    if (!snapshot.fresh(elapsed)) return null

    val marketTime =
        snapshot.time + elapsed - snapshot.received

    val ticker = snapshot.rows
        .firstOrNull { it.contract.symbol == symbol }
        ?.ticker ?: return null

    if (!ticker.fresh(marketTime)) return null

    if (!ticker.price.isFinite() ||
        ticker.price <= 0 ||
        !ticker.change.isFinite() ||
        !ticker.high.isFinite() ||
        !ticker.low.isFinite() ||
        ticker.low <= 0 ||
        ticker.price !in ticker.low..ticker.high ||
        !ticker.volume.isFinite() ||
        ticker.volume < 0 ||
        !ticker.average.isFinite() ||
        ticker.average <= 0 ||
        ticker.time <= 0
    ) return null

    if (previous != null &&
        ticker.time < previous.time
    ) return null

    return ticker
}

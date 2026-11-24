package com.mefuturesaibot.app.scanner

import android.os.Handler
import android.os.Looper
import com.mefuturesaibot.app.exchange.PositionFeed
import com.mefuturesaibot.app.exchange.PositionMark
import com.mefuturesaibot.app.exchange.PublicReader
import org.json.JSONArray

/** Main-thread owner of held Paper positions. An observed exit is frozen before an asynchronous funding lookup. */
internal class PaperPositionMonitor(private val store: DeskStore, private val bot: () -> Boolean) {
    private val main = Handler(Looper.getMainLooper())
    private val reader = PublicReader()
    private var feed: PositionFeed? = null
    private var opened: Long? = null
    private var busy = false
    private var retryAt = 0L

    fun start() = poll()
    private fun poll() {
        val pos = store.read().second.position
        if (pos?.opened != opened) {
            reset()
            opened = pos?.opened
            if (pos != null) feed = PositionFeed(pos.symbol,false,{ mark = it }) { quote -> observe(pos.opened,quote) }.also { it.start() }
        }
        if (pos?.exitQuote != null && !busy && android.os.SystemClock.elapsedRealtime() >= retryAt) settle(pos)
        main.postDelayed(::poll,500)
    }

    private fun observe(id: Long, q: Quote) {
        val (model,a) = store.read()
        val p = a.position ?: return
        if (p.opened != id || p.exitQuote != null || q.at <= a.lastAction) return
        val scan = Scan(time=q.at,ok=true)
        val preview = a.advance(scan,p.symbol,null,p.manual || (bot() && store.simulation),q,exitRequested=store.closeRequested(p))
        if (preview.position == null) {
            val reason = preview.ledger.recent.first().reason
            val frozen = p.copy(exitQuote=q,exitReason=reason)
            store.save(model,a.copy(position=frozen),durable=true)
            settle(frozen)
        } else if (preview.position.stop != p.stop || preview.drawdown > a.drawdown || preview.peak > a.peak || preview.day != a.day) {
            store.save(model,preview)
        }
    }

    private fun settle(p: PaperPosition) {
        val q = p.exitQuote ?: return
        busy = true
        val history = mutableListOf<Funding>()
        fun fail() {
            busy = false
            retryAt = android.os.SystemClock.elapsedRealtime() + 15_000
        }
        fun page(start: Long) {
            reader.read("fundingRate?symbol=${PublicReader.encode(p.symbol)}&startTime=$start&endTime=${q.at}&limit=1000") { body,_,_ ->
                val rows = runCatching {
                    val data = JSONArray(requireNotNull(body))
                    List(data.length()) { index ->
                        val d = data.getJSONObject(index)
                        require(d.getString("symbol")==p.symbol)
                        Funding(d.getLong("fundingTime"),d.getDouble("fundingRate"),d.getDouble("markPrice")).also {
                            require(it.time in start..q.at && it.mark.isFinite() && it.mark>0 && it.rate.isFinite())
                        }
                    }.also { require(it.zipWithNext().all { (a,b) -> a.time < b.time }) }
                }.getOrNull()
                if (rows == null) { fail(); return@read }
                history.addAll(rows)
                if (rows.size == 1000 && rows.last().time < q.at) { page(rows.last().time+1); return@read }
                val (model,account) = store.read()
                if (account.position == p) {
                    val closed = account.advance(Scan(time=q.at,ok=true),p.symbol,null,true,q,history,
                        latchedReason=p.exitReason)
                    store.save(model,closed,durable=true)
                }
                busy = false
            }
        }
        page(p.fundingAt+1)
    }
    private fun reset() {
        feed?.close()
        feed = null
        reader.cancel()
        opened = null
        busy = false
        retryAt = 0
        mark = null
    }
    fun close() { main.removeCallbacksAndMessages(null); reset() }
    companion object { var mark: PositionMark? = null; private set }
}

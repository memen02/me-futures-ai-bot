package com.mefuturesaibot.app.scanner

import android.content.Context
import org.json.JSONObject

internal class DeskStore(context: Context) {
    private val prefs = context.getSharedPreferences("market_desk_v1", Context.MODE_PRIVATE)
    var simulation: Boolean
        get() = prefs.getBoolean("paper_enabled", false)
        set(value) { prefs.edit().putBoolean("paper_enabled", value).apply() }
    fun requestClose(position: PaperPosition) {
        prefs.edit().putString("paper_close", "${position.symbol}|${position.opened}").apply()
    }
    fun closeRequested(position: PaperPosition?) = position != null &&
        prefs.getString("paper_close",null) == "${position.symbol}|${position.opened}"

    var trailing: Boolean
        get() = prefs.getBoolean("paper_trailing", false)
        set(value) { prefs.edit().putBoolean("paper_trailing", value).apply() }
    var favorites: Set<String>
        get() = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
        set(value) { prefs.edit().putStringSet("favorites", value.toSet()).apply() }

    private var raw: String? = null
    private var model = MarketModel()
    private var account = PaperAccount()
    fun read(): Pair<MarketModel, PaperAccount> {
        val saved = prefs.getString("desk", null)
        if (saved != null && saved != raw) {
            val data = JSONObject(saved)
            model = MarketModel.read(data.optJSONObject("model"))
            account = PaperAccount.read(data.optJSONObject("account"))
            raw = saved
        }
        return model to account
    }
    fun save(next: MarketModel, paper: PaperAccount) {
        if (next == model && paper == account) return
        val value = JSONObject().put("model", next.json()).put("account", paper.json()).toString()
        prefs.edit().putString("desk", value).apply()
        model = next
        account = paper
        raw = value
    }
}

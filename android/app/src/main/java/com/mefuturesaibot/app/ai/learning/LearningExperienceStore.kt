package com.mefuturesaibot.app.ai.learning

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

// Derived analytical cache, not the authoritative ledger. Use on a worker thread.
// Dataset identity must include venue and account scope; no credentials in identifiers.
internal class LearningExperienceStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext,"learning_experience.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE experience (dataset TEXT NOT NULL, id TEXT NOT NULL, version TEXT NOT NULL, symbol TEXT NOT NULL, opened INTEGER NOT NULL, closed INTEGER NOT NULL, gross REAL NOT NULL, fees REAL NOT NULL, funding REAL NOT NULL, risk REAL NOT NULL, PRIMARY KEY(dataset,id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Learning cache migration required")
    }

    @Synchronized fun record(trade: TradeExperience, nowMs: Long): Boolean {
        if (!trade.valid() || trade.closedAtMs > nowMs ||
            listOf(trade.datasetId,trade.tradeId,trade.strategyVersion,trade.symbol).any { it.length > 128 }) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.query("experience",null,"dataset=? AND id=?",arrayOf(trade.datasetId,trade.tradeId),null,null,null).use { c ->
                if (c.moveToFirst()) return read(c) == trade
            }
            db.rawQuery("SELECT COUNT(*) FROM experience",null).use { c ->
                if (!c.moveToFirst() || c.getLong(0) >= 10000) return false
            }
            val values = ContentValues().apply {
                put("dataset",trade.datasetId); put("id",trade.tradeId)
                put("version",trade.strategyVersion); put("symbol",trade.symbol)
                put("opened",trade.openedAtMs); put("closed",trade.closedAtMs)
                put("gross",trade.grossPnl); put("fees",trade.fees)
                put("funding",trade.fundingPaid); put("risk",trade.initialRisk)
            }
            db.insertOrThrow("experience",null,values)
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }

    @Synchronized fun snapshot(dataset: String, version: String, asOfMs: Long): List<TradeExperience> {
        require(dataset.isNotBlank() && version.isNotBlank() && asOfMs > 0)
        val result = mutableListOf<TradeExperience>()
        readableDatabase.query("experience",null,"dataset=? AND version=? AND closed<=?",
            arrayOf(dataset,version,asOfMs.toString()),null,null,"closed ASC,id ASC","10001").use { c ->
            while (c.moveToNext()) {
                val trade = read(c)
                check(trade.valid() && trade.datasetId == dataset && trade.strategyVersion == version)
                result.add(trade)
            }
        }
        check(result.size <= 10000)
        return result
    }

    private fun read(c: android.database.Cursor): TradeExperience {
        fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        fun l(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
        fun d(name: String) = c.getDouble(c.getColumnIndexOrThrow(name))
        return TradeExperience(s("id"),s("dataset"),s("version"),s("symbol"),l("opened"),l("closed"),
            d("gross"),d("fees"),d("funding"),d("risk"))
    }
}

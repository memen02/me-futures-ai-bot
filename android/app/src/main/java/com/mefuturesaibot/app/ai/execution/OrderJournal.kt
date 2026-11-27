package com.mefuturesaibot.app.ai.execution

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal enum class OrderState { RESERVED, SUBMITTING, UNKNOWN, ACKNOWLEDGED, PARTIALLY_FILLED, FILLED, REJECTED, CANCELED }

internal class OrderJournal(context: Context) :
    SQLiteOpenHelper(context.applicationContext,"order_journal_v1.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE intents (
            intent_key TEXT PRIMARY KEY NOT NULL,
            venue TEXT NOT NULL, account_id TEXT NOT NULL, symbol TEXT NOT NULL,
            strategy TEXT NOT NULL, signal_epoch INTEGER NOT NULL,
            client_id TEXT NOT NULL UNIQUE, state TEXT NOT NULL,
            risk_micros INTEGER NOT NULL CHECK(risk_micros > 0),
            released INTEGER NOT NULL DEFAULT 0 CHECK(released IN (0,1)),
            created_ms INTEGER NOT NULL,
            UNIQUE(venue,account_id,symbol,strategy,signal_epoch)
        )""")
        db.execSQL("CREATE UNIQUE INDEX active_symbol ON intents(venue,account_id,symbol) WHERE released=0")
        db.execSQL("CREATE INDEX account_active ON intents(venue,account_id,released)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Order journal migration required; do not discard order history")
    }
}

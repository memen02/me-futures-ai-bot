package com.mefuturesaibot.app.ai.decision

internal object DecisionExplanation {
    fun describe(decision: MasterDecision): String = when (decision.reason) {
        DecisionReason.AGREEMENT -> "Lima kelompok bukti searah dan timeframe mendukung: ${decision.direction.name}. Bukan estimasi probabilitas profit."
        DecisionReason.INSUFFICIENT_EVIDENCE -> "WAIT: konfirmasi atau keselarasan timeframe belum mencukupi."
        DecisionReason.CONFLICT -> "WAIT: terdapat konflik arah bukti."
        DecisionReason.INVALID_EVIDENCE -> "WAIT: data tidak valid, kedaluwarsa atau belum tersedia."
        DecisionReason.VETO -> "WAIT: filter keamanan memblokir keputusan."
    }
}

package com.mefuturesaibot.app.scanner

internal object ResearchSignalDisplay {
    fun text(active:Boolean, status:String): String {
        if (!active || status == "OFFLINE") return "Master AI · OFFLINE"
        if (status.isBlank() || status.length > 512) return "Master AI · WAIT"
        val rows = status.lines()
        if (rows.size !in 1..2) return "Master AI · WAIT"
        val parsed = rows.map { it.split(" · ", limit = 4) }
        if (parsed.any { it.size < 3 || it[0] !in setOf("BTCUSDT","ETHUSDT") ||
                it[1] !in setOf("LONG","SHORT","WAIT") } ||
            parsed.map { it[0] }.distinct().size != parsed.size) return "Master AI · WAIT"
        return "Master AI · Riset (bukan order)\n" + parsed.joinToString("\n") {
            val direction = when(it[1]) { "LONG" -> "↑ LONG"; "SHORT" -> "↓ SHORT"; else -> "— WAIT" }
            val reason = when(it[2]) {
                "AGREEMENT" -> "Bukti searah"
                "INSUFFICIENT_EVIDENCE" -> "Bukti belum cukup"
                "CONFLICT" -> "Bukti bertentangan"
                "INVALID_EVIDENCE" -> "Data tidak valid"
                "VETO" -> "Diblokir pengaman"
                else -> it[2]
            }
            val count = it.getOrNull(3)?.toIntOrNull()?.takeIf { n -> n in 0..6 }
            "${it[0]} · $direction · $reason" + (count?.let { n -> " · $n konfirmasi" } ?: "")
        }
    }
}

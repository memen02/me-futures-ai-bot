package com.mefuturesaibot.app.scanner

internal object ResearchSignalDisplay {
    fun text(active:Boolean, status:String): String {
        if (!active || status == "OFFLINE") return "Master AI · OFFLINE"
        if (status.isBlank() || status.length > 512) return "Master AI · WAIT"
        return "Master AI · Riset (bukan order)\n" + status
    }
}

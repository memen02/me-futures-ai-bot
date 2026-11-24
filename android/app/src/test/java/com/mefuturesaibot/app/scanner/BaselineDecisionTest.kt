package com.mefuturesaibot.app.scanner

import com.mefuturesaibot.app.exchange.*
import org.junit.Assert.*
import org.junit.Test

class BaselineDecisionTest {
    @Test fun allConsumersUseTheSameThresholdsAndMissingEvidenceVeto() {
        val row = MarketRow(Contract("ETHUSDT","USDT"),Ticker(2.0,1.0,3.0,1.0,1e7,2.0,1000),0.60)
        val up = SmaSignal(SignalSide.BUY,"SMA naik")
        assertEquals(SignalSide.BUY,BaselineDecision.evaluate(row,1000,up).side)
        for (score in listOf(null,Double.NaN,0.57,0.42))
            assertEquals(SignalSide.WAIT,BaselineDecision.evaluate(row.copy(score=score),1000,up).side)
        assertEquals(SignalSide.WAIT,BaselineDecision.evaluate(row,121001,up).side)
        assertEquals(SignalSide.SELL,BaselineDecision.evaluate(row.copy(score=0.4),1000,up.copy(side=SignalSide.SELL)).side)
    }
}

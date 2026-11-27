package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.*
import org.junit.Assert.*
import org.junit.Test

class UnifiedIntegrationSafetyTest {
    @Test fun liquidityRejectsUncertainClockAndWrongSymbol() {
        val book = LiquiditySnapshot("BTCUSDT",10000L,80.0,20.0,true)
        assertEquals(EvidenceDirection.LONG,UnifiedLiquidityIntegration.direction("BTCUSDT",book,10000,11999))
        assertEquals(EvidenceDirection.MISSING,UnifiedLiquidityIntegration.direction("BTCUSDT",book,10000,12000))
        assertEquals(EvidenceDirection.MISSING,UnifiedLiquidityIntegration.direction("BTCUSDT",book,9999,10000))
        assertEquals(EvidenceDirection.MISSING,UnifiedLiquidityIntegration.direction("ETHUSDT",book,10000,10000))
        assertEquals(EvidenceDirection.MISSING,UnifiedLiquidityIntegration.direction("BTCUSDT",book.copy(synchronized=false),10000,10000))
    }

    @Test fun emptyCaptureCannotPublish() {
        val source = requireNotNull(UnifiedTimeframeSnapshot.capture("BTCUSDT",emptyMap(),10000,10000))
        val publisher = UnifiedSignalPublisher()
        val bounds = AiExchangeClock.Bounds(10000,10000)
        assertFalse(publisher.publish(source,Timeframe.M1,setOf(Timeframe.M1,Timeframe.M5),emptyMap(),null,bounds))
        assertEquals(MasterDirection.WAIT,publisher.read("BTCUSDT",bounds).direction)
        publisher.close()
        assertFalse(publisher.publish(source,Timeframe.M1,setOf(Timeframe.M1,Timeframe.M5),emptyMap(),null,bounds))
    }

    @Test fun confirmationExpiresAndRejectsDuplicates() {
        val votes = EvidenceGroup.entries.map { DecisionEvidence(it,EvidenceDirection.LONG,10000,12000) }
        val decision = MasterDecisionEngine.evaluate(votes,10000,false)
        val assessment = UnifiedMasterAssessment(decision,votes,10000)
        assertEquals(MasterDirection.LONG,IndependentConfirmationIntegration.revalidate(assessment,10000,11999).direction)
        assertEquals(MasterDirection.WAIT,IndependentConfirmationIntegration.revalidate(assessment,10000,12000).direction)
        assertEquals(MasterDirection.WAIT,IndependentConfirmationIntegration.revalidate(assessment.copy(evidence=List(6){votes.first()}),10000,10000).direction)
    }

    @Test fun emptyLearningAndClosedWorkerFailClosed() {
        assertNull(UnifiedLearningIntegration.evaluate(emptyList(),"DEMO","v4",10000))
        val worker = UnifiedAnalysisWorker { null }
        worker.close()
        assertEquals(MasterDirection.WAIT,worker.read("BTCUSDT").direction)
        val source = requireNotNull(UnifiedTimeframeSnapshot.capture("BTCUSDT",emptyMap(),10000,10000))
        assertFalse(worker.submit(source,Timeframe.M1,setOf(Timeframe.M1,Timeframe.M5),emptyMap(),null))
    }
}

package com.mefuturesaibot.app.ai.learning

import org.junit.Assert.*
import org.junit.Test

class LearningSafetyTest {
    private fun sample(version: String = "v1", start: Long = 1000,
                       pnl: Double = 2.0, dataset: String = "shadow:test") =
        (0 until 100).map { i ->
            TradeExperience("$version:$start:$i",dataset,version,"BTCUSDT",
                start+i*10L,start+i*10L+5,pnl,0.1,0.0,1.0)
        }

    @Test fun memoryRejectsConflictsAndOverflow() {
        val memory = MarketMemory("shadow:test",1)
        val trades = sample()
        assertTrue(memory.record(trades[0],5000))
        assertTrue(memory.record(trades[0],5000))
        assertFalse(memory.record(trades[0].copy(grossPnl=3.0),5000))
        assertFalse(memory.record(trades[1],5000))
        assertFalse(memory.record(trades[0].copy(datasetId="real"),5000))
        assertEquals(1,memory.snapshot("v1",5000).size)
    }

    @Test fun weightsAndConfidenceRequireSamples() {
        val trades = sample()
        assertNull(AdaptiveWeightEngine.propose(1.0,trades.take(99),"shadow:test","v1",5000))
        assertEquals(1.05,AdaptiveWeightEngine.propose(1.0,trades,"shadow:test","v1",5000)!!,0.000001)
        assertEquals(1.5,AdaptiveWeightEngine.propose(1.49,trades,"shadow:test","v1",5000)!!,0.000001)
        assertNull(ConfidenceCalibrationEngine.estimate(trades.take(99),"shadow:test","v1",5000))
        val confidence = ConfidenceCalibrationEngine.estimate(trades,"shadow:test","v1",5000)!!
        assertTrue(confidence.lowerBound < 1.0)
    }

    @Test fun shadowRequiresMatchedSeparateVersions() {
        val pairs = sample().zip(sample("v2",pnl=3.0)).mapIndexed { i,p ->
            ShadowPair("op:$i",p.first,p.second)
        }
        val result = ShadowLearningEngine.compare(pairs,"shadow:test","v1","v2",1000,5000)!!
        assertEquals(1.0,result.meanImprovement,0.000001)
        assertNull(ShadowLearningEngine.compare(pairs,"real","v1","v2",1000,5000))
        assertNull(ShadowLearningEngine.compare(pairs,"shadow:test","v1","v2",1001,5000))
    }

    @Test fun driftRejectsOverlapAndWarnsOnLoss() {
        val old = sample()
        assertEquals(DriftStatus.INSUFFICIENT,PerformanceDriftEngine.assess(old,old,"shadow:test","v1",5000))
        assertEquals(DriftStatus.WARNING,PerformanceDriftEngine.assess(old,sample(start=3000,pnl=-1.0),"shadow:test","v1",5000))
        assertEquals(DriftStatus.STABLE,PerformanceDriftEngine.assess(old,sample(start=3000),"shadow:test","v1",5000))
    }
}

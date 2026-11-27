package com.mefuturesaibot.app.ai.learning

import org.junit.Assert.*
import org.junit.Test

class LearningIntegrationTest {
    @Test fun reportUsesOnlyMatchingVersionAndDataset() {
        val memory = MarketMemory("demo:test")
        for (i in 0 until 100) {
            assertTrue(memory.record(TradeExperience("t$i","demo:test","v1","BTCUSDT",
                1000,2000,-1.0,0.1,0.0,1.0),3000))
        }
        val report = LearningCoordinator.evaluate(memory,"demo:test","v1",3000)!!
        assertEquals(100,report.stats.count)
        assertEquals(1,report.lossPatterns.size)
        assertNotNull(report.confidence)
        assertNull(LearningCoordinator.evaluate(memory,"real:test","v1",3000))
        assertNull(LearningCoordinator.evaluate(memory,"demo:test","v2",3000))
    }

    @Test fun reviewRequiresDurationAndStableDrift() {
        val comparison = ShadowComparison(100,0.2,0.5,0.3)
        val end = 1000+30L*86400000L
        assertTrue(LearningSafetyGuardian.eligibleForReview(comparison,DriftStatus.STABLE,1000,end))
        assertFalse(LearningSafetyGuardian.eligibleForReview(comparison,DriftStatus.WARNING,1000,end))
        assertFalse(LearningSafetyGuardian.eligibleForReview(comparison,DriftStatus.STABLE,1000,end-1))
        assertFalse(LearningSafetyGuardian.eligibleForReview(comparison.copy(meanImprovement=0.9),DriftStatus.STABLE,1000,end))
    }

    @Test fun rollbackNeverAutomaticallyActivatesParent() {
        val registry = StrategyVersionRegistry()
        assertTrue(registry.register(StrategyVersion("v1",null,1000)))
        assertTrue(registry.register(StrategyVersion("v2","v1",2000)))
        assertFalse(registry.register(StrategyVersion("v2","v1",3000)))
        assertEquals(RollbackAction.REVIEW_PARENT,StrategyRollbackAdvisor.assess("v2",registry,DriftStatus.WARNING,true).action)
        assertEquals(RollbackAction.FREEZE_NEW_ENTRIES,StrategyRollbackAdvisor.assess("v2",registry,DriftStatus.STABLE,false).action)
    }
}

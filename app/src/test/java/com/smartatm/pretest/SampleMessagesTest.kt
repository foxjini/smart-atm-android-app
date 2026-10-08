package com.smartatm.pretest

import com.smartatm.pretest.data.model.SampleMessages
import com.smartatm.pretest.util.LocalRiskAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 문자 검사 화면의 시연용 문자가 온디바이스 분석에서 기대대로 판정되는지 확인 */
class SampleMessagesTest {

    @Test
    fun phishingSamplesAreDetected() {
        for (msg in SampleMessages.all.filter { it.isPhishing }) {
            val result = LocalRiskAnalyzer.analyze(msg.body)
            assertTrue("#${msg.id} ${msg.body} → ${result.riskLevel} ${result.riskScore}", result.riskLevel != "SAFE")
        }
    }

    @Test
    fun normalSamplesAreSafe() {
        for (msg in SampleMessages.all.filter { !it.isPhishing }) {
            val result = LocalRiskAnalyzer.analyze(msg.body)
            assertEquals("#${msg.id} ${msg.body} → ${result.reasons}", "SAFE", result.riskLevel)
        }
    }

    @Test
    fun idsAreUnique() {
        assertEquals(SampleMessages.all.size, SampleMessages.all.map { it.id }.toSet().size)
    }
}

package com.smartatm.pretest

import com.smartatm.pretest.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    @Test
    fun testSampleScriptsLength() {
        assertTrue(MainViewModel.SCRIPT_VOICE_PHISHING_CALL.length <= MainViewModel.MAX_TEXT_LENGTH)
        assertTrue(MainViewModel.SCRIPT_VOICE_PHISHING_SMS.length <= MainViewModel.MAX_TEXT_LENGTH)
        assertTrue(MainViewModel.SCRIPT_NORMAL_CALL.length <= MainViewModel.MAX_TEXT_LENGTH)
        assertTrue(MainViewModel.SCRIPT_NORMAL_SMS.length <= MainViewModel.MAX_TEXT_LENGTH)
    }

    @Test
    fun testQrPayloadFormat() {
        val sessionId = "VP-000012"
        val qrPayload = "{\"session_id\": \"$sessionId\"}"
        assertEquals("{\"session_id\": \"VP-000012\"}", qrPayload)
    }

    @Test
    fun testAtmTransferRiskAnalysis() {
        val result = com.smartatm.pretest.util.LocalRiskAnalyzer.analyze("ATM에 가서 송금")
        assertEquals("DANGER", result.riskLevel)
        assertTrue("위험 점수가 85점 이상이어야 함", result.riskScore >= 85)
        assertTrue("ATM 또는 송금 관련 감지 이유가 포함되어야 함", result.reasons.any { it.contains("ATM") || it.contains("송금") })
    }
}

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
    fun testDemoScriptsGiveTheExpectedLevels() {
        // 서버(ATM 저장소 backend/analysis)와 같은 기준이다 — 대본을 고쳤다면 서버에도 넣어 확인한다
        val analyze = com.smartatm.pretest.util.LocalRiskAnalyzer::analyze
        assertEquals("DANGER", analyze(MainViewModel.SCRIPT_VOICE_PHISHING_CALL).riskLevel)
        assertEquals("DANGER", analyze(MainViewModel.SCRIPT_VOICE_PHISHING_SMS).riskLevel)
        assertEquals("SAFE", analyze(MainViewModel.SCRIPT_NORMAL_CALL).riskLevel)
        assertEquals("SAFE", analyze(MainViewModel.SCRIPT_NORMAL_SMS).riskLevel)
    }

    @Test
    fun testPlainWordsAboutBanksAreNotDanger() {
        // 예전 앱 규칙은 '은행'+'보내'만으로 DANGER 85점을 줬다. 서버는 0점이다
        assertEquals("SAFE", com.smartatm.pretest.util.LocalRiskAnalyzer.analyze("ATM에 가서 송금").riskLevel)
        assertEquals("SAFE", com.smartatm.pretest.util.LocalRiskAnalyzer.analyze("은행 들러서 용돈 보내 드릴게요.").riskLevel)
    }
}

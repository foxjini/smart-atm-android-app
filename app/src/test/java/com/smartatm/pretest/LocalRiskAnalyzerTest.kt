package com.smartatm.pretest

import com.smartatm.pretest.util.LocalRiskAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRiskAnalyzerTest {

    // PRD 부록 시연 대본 (MainViewModel 상수와 동일한 문장)
    private val phishingCall =
        "여보세요, 서울중앙지검 수사관입니다. 선생님 명의로 개설된 계좌가 대포통장 사건에 연루되어 수사 중입니다. " +
        "지금 바로 은행에 가셔서 예금을 전액 인출하신 다음에, 저희가 알려드리는 안전계좌로 입금해 주셔야 합니다. " +
        "이건 수사 중인 사안이라 가족에게 말씀하지 마시고 혼자 오셔야 합니다."
    private val phishingSms =
        "[국세청] 귀하의 계좌가 범죄에 연루되어 있습니다. 즉시 500만원을 인출해 강남역 3번 출구에서 전달하세요. 타인에게 발설하지 마십시오."
    private val normalCall =
        "엄마, 나야. 오늘 저녁에 집에 갈 건데 뭐 사갈까? 병원 예약은 목요일 두 시로 해놨어. 아버지 약도 타와야 하니까 처방전 챙겨놔. 끊을게, 이따 봐."
    private val normalSms = "어머니, 목요일 병원 예약 2시입니다. 처방전 잊지 마세요."

    @Test
    fun phishingDemoScriptsAreDanger() {
        for (script in listOf(phishingCall, phishingSms)) {
            val result = LocalRiskAnalyzer.analyze(script)
            assertEquals(script, "DANGER", result.riskLevel)
            assertEquals(script, 100, result.riskScore)
        }
    }

    @Test
    fun normalDemoScriptsAreSafeWithZeroScore() {
        for (script in listOf(normalCall, normalSms)) {
            val result = LocalRiskAnalyzer.analyze(script)
            assertEquals(script, "SAFE", result.riskLevel)
            assertEquals(script, 0, result.riskScore)
        }
    }

    @Test
    fun everydaySentencesAreNotDanger() {
        val sentences = listOf(
            "건강검사 결과 확인하러 병원 다녀올게요.",
            "은행 가서 용돈 좀 찾아올게.",
            "택배 보내고 올게, 기기 충전 좀 해줘.",
            "자동차 정기검사 받으러 가요."
        )
        for (s in sentences) {
            val result = LocalRiskAnalyzer.analyze(s)
            assertTrue("$s → ${result.riskLevel} ${result.riskScore}", result.riskLevel != "DANGER")
        }
    }

    @Test
    fun prosecutorImpersonationIsStillDetected() {
        val result = LocalRiskAnalyzer.analyze("검사입니다. 계좌가 동결될 수 있어 확인이 필요합니다.")
        assertTrue(result.reasons.any { it.contains("'검사'") })
        assertEquals("DANGER", result.riskLevel)
    }

    @Test
    fun blankTextIsSafe() {
        val result = LocalRiskAnalyzer.analyze("   ")
        assertEquals("SAFE", result.riskLevel)
        assertEquals(0, result.riskScore)
    }
}

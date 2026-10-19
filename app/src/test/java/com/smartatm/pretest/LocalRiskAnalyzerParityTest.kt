package com.smartatm.pretest

import com.smartatm.pretest.util.LocalRiskAnalyzer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱의 판정이 서버와 똑같은지 확인한다.
 *
 * 기준 데이터(server_judgement_cases.json)는 서버 규칙 엔진이 직접 계산한 값이다
 * (tools/make_judgement_cases.py). ATM을 막을지는 서버가 정하므로, 앱이 다르게
 * 판정하면 통화 화면의 경고와 ATM의 동작이 서로 어긋난다.
 */
class LocalRiskAnalyzerParityTest {

    private val cases = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("server_judgement_cases.json")!!.readText()
    ).jsonArray.map { it.jsonObject }

    @Test
    fun everyCaseMatchesTheServer() {
        assertTrue("기준 데이터가 비어 있으면 아무것도 확인하지 않은 것이다", cases.size > 50)
        val mismatches = cases.mapNotNull { case ->
            val text = case["text"]!!.jsonPrimitive.content
            val expectedLevel = case["risk_level"]!!.jsonPrimitive.content
            val expectedScore = case["risk_score"]!!.jsonPrimitive.int
            val expectedReasons = case["reasons"]!!.jsonArray.map { it.jsonPrimitive.content }
            val expectedSummary = case["summary"]!!.jsonPrimitive.content
            val local = LocalRiskAnalyzer.analyze(text)
            val same = local.riskLevel == expectedLevel && local.riskScore == expectedScore &&
                local.reasons == expectedReasons && local.summary == expectedSummary
            if (same) null else "\"$text\" → 서버 $expectedLevel $expectedScore $expectedReasons / 앱 ${local.riskLevel} ${local.riskScore} ${local.reasons}"
        }
        assertEquals("서버와 다르게 판정한 문장:\n" + mismatches.joinToString("\n"), 0, mismatches.size)
    }
}

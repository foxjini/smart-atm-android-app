package com.smartatm.pretest

import com.smartatm.pretest.data.model.AnalysisRequest
import com.smartatm.pretest.data.model.AnalysisResponse
import com.smartatm.pretest.data.model.DataWrapper
import com.smartatm.pretest.data.model.ErrorResponse
import com.smartatm.pretest.data.model.LoginRequest
import com.smartatm.pretest.data.model.LoginResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testLoginSerialization() {
        val request = LoginRequest("halmeoni", "secret")
        val reqJson = json.encodeToString(request)
        assert(reqJson.contains("halmeoni"))

        val rawResponse = """
            {
                "data": {
                    "access_token": "mock_jwt_token_12345",
                    "token_type": "bearer"
                }
            }
        """.trimIndent()
        val parsed: DataWrapper<LoginResponse> = json.decodeFromString(rawResponse)
        assertEquals("mock_jwt_token_12345", parsed.data.accessToken)
        assertEquals("bearer", parsed.data.tokenType)
    }

    @Test
    fun testAnalysisSerialization() {
        val rawResponse = """
            {
                "data": {
                    "analysis_id": 12,
                    "risk_level": "DANGER",
                    "risk_score": 100,
                    "reasons": ["기관 사칭 표현 감지", "범죄 연루 언급 감지"],
                    "summary": "기관 사칭 표현 및 범죄 연루 언급 감지",
                    "detected_at": "2026-09-20T05:12:33",
                    "message_text": "...",
                    "session_id": "VP-000012",
                    "atm_action": "BLOCK"
                }
            }
        """.trimIndent()

        val parsed: DataWrapper<AnalysisResponse> = json.decodeFromString(rawResponse)
        assertEquals("DANGER", parsed.data.riskLevel)
        assertEquals(100, parsed.data.riskScore)
        assertEquals(2, parsed.data.reasons.size)
        assertEquals("VP-000012", parsed.data.sessionId)
        assertEquals("BLOCK", parsed.data.atmAction)
    }

    @Test
    fun testErrorSerialization() {
        val rawError = """
            {
                "detail": {
                    "code": "INVALID_CREDENTIALS",
                    "message": "아이디 또는 비밀번호가 올바르지 않습니다."
                }
            }
        """.trimIndent()

        val parsed: ErrorResponse = json.decodeFromString(rawError)
        assertNotNull(parsed.detail)
        assertEquals("INVALID_CREDENTIALS", parsed.detail?.code)
    }

    @Test
    fun testMockCallControlMessageSerialization() {
        val msg = com.smartatm.pretest.mockcall.ControlMessage(
            type = com.smartatm.pretest.mockcall.MessageType.CALL_OFFER,
            senderRole = "ATTACKER",
            senderIp = "192.168.0.45",
            roomId = "VP-CALL-001"
        )
        val raw = json.encodeToString(msg)
        assert(raw.contains("CALL_OFFER"))
        val decoded = json.decodeFromString<com.smartatm.pretest.mockcall.ControlMessage>(raw)
        assertEquals(com.smartatm.pretest.mockcall.MessageType.CALL_OFFER, decoded.type)
        assertEquals("VP-CALL-001", decoded.roomId)
    }
}

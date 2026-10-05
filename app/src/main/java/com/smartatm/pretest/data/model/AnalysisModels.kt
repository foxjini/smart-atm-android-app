package com.smartatm.pretest.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisRequest(
    val message: String
)

@Serializable
data class AnalysisResponse(
    @SerialName("analysis_id") val analysisId: Long? = null,
    @SerialName("risk_level") val riskLevel: String = "SAFE", // SAFE, CAUTION, DANGER
    @SerialName("risk_score") val riskScore: Int = 0,         // 0 ~ 100
    val reasons: List<String> = emptyList(),
    val summary: String? = null,
    @SerialName("detected_at") val detectedAt: String? = null,
    @SerialName("message_text") val messageText: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("atm_action") val atmAction: String? = null   // ALLOW, VERIFY, BLOCK
)

@Serializable
data class QrPayload(
    @SerialName("session_id") val sessionId: String
)
